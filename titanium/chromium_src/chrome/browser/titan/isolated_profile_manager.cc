// Copyright 2024-2026 Titan Browser
// Use of this source code is governed by a GPL-2.0-only style license that
// can be found in the LICENSE file.

#include "chrome/browser/titan/isolated_profile_manager.h"

#include <sstream>

#include "base/no_destructor.h"
#include "base/rand_util.h"
#include "base/strings/string_number_conversions.h"
#include "chrome/browser/profiles/profile.h"
#include "chrome/browser/ui/browser.h"
#include "chrome/browser/ui/browser_navigator.h"
#include "chrome/browser/ui/browser_navigator_params.h"
#include "chrome/browser/ui/tabs/tab_strip_model.h"
#include "components/keep_alive_registry/keep_alive_types.h"
#include "url/gurl.h"

namespace titan {

// static
IsolatedProfileManager& IsolatedProfileManager::Get() {
  static base::NoDestructor<IsolatedProfileManager> instance;
  return *instance;
}

Profile* IsolatedProfileManager::GetOrCreateIsolatedProfile(
    Profile* regular_profile,
    const std::string& group_token) {
  DCHECK(regular_profile);
  DCHECK(!regular_profile->IsOffTheRecord());

  auto it = group_to_profile_id_.find(group_token);

  Profile::OTRProfileID otr_id(
      (it != group_to_profile_id_.end())
          ? it->second
          : kIsolatedGroupPrefix + group_token);

  if (it == group_to_profile_id_.end()) {
    group_to_profile_id_[group_token] = otr_id.ToString();
  }

  // GetOffTheRecordProfile creates the OTR profile if it does not yet exist,
  // returning a Profile that owns its own BrowserContext / StoragePartition.
  return regular_profile->GetOffTheRecordProfile(otr_id,
                                                  /*create_if_needed=*/true);
}

std::string IsolatedProfileManager::CreateIsolatedGroup(
    Profile* regular_profile) {
  // Generate a unique group token using a random 64-bit value.
  uint64_t rand_val = base::RandUint64();
  std::ostringstream oss;
  oss << std::hex << rand_val;
  std::string token = oss.str();

  // Pre-create the profile entry (actual Profile created on first tab open).
  group_to_profile_id_[token] = kIsolatedGroupPrefix + token;
  return token;
}

bool IsolatedProfileManager::IsIsolatedGroup(
    const std::string& group_token) const {
  return group_to_profile_id_.count(group_token) > 0;
}

void IsolatedProfileManager::RemoveIsolatedGroup(
    const std::string& group_token) {
  group_to_profile_id_.erase(group_token);
  // Chromium's ProfileDestroyer handles OTR profile lifecycle automatically.
}

// static
void IsolatedProfileManager::OpenIsolatedTab(Browser* browser) {
  DCHECK(browser);
  Profile* regular_profile = browser->profile()->GetOriginalProfile();
  std::string group_token =
      IsolatedProfileManager::Get().CreateIsolatedGroup(regular_profile);
  Profile* isolated_profile =
      IsolatedProfileManager::Get().GetOrCreateIsolatedProfile(
          regular_profile, group_token);

  // Open chrome://newtab/ in the isolated OTR profile.
  NavigateParams params(isolated_profile, GURL("chrome://newtab/"),
                        ui::PAGE_TRANSITION_AUTO_TOPLEVEL);
  params.disposition = WindowOpenDisposition::NEW_FOREGROUND_TAB;
  Navigate(&params);
}

// static
void IsolatedProfileManager::OpenLinkInIsolatedTab(Browser* browser,
                                                    const GURL& url) {
  DCHECK(browser);
  Profile* regular_profile = browser->profile()->GetOriginalProfile();
  std::string group_token =
      IsolatedProfileManager::Get().CreateIsolatedGroup(regular_profile);
  Profile* isolated_profile =
      IsolatedProfileManager::Get().GetOrCreateIsolatedProfile(
          regular_profile, group_token);

  NavigateParams params(isolated_profile, url,
                        ui::PAGE_TRANSITION_LINK);
  params.disposition = WindowOpenDisposition::NEW_FOREGROUND_TAB;
  Navigate(&params);
}

}  // namespace titan
