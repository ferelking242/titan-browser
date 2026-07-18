// Copyright 2024-2026 Titan Browser
// Use of this source code is governed by a GPL-2.0-only style license that
// can be found in the LICENSE file.
//
// isolated_profile_manager.h — Desktop (Linux / Windows / macOS) counterpart
// of IsolatedTabGroupManager.java.
//
// Uses Chromium's native Profile::CreateOffTheRecordProfile() to create fully
// independent browser contexts per isolated group. No simulation.

#ifndef CHROME_BROWSER_TITAN_ISOLATED_PROFILE_MANAGER_H_
#define CHROME_BROWSER_TITAN_ISOLATED_PROFILE_MANAGER_H_

#include <map>
#include <string>

#include "base/no_destructor.h"
#include "chrome/browser/profiles/profile.h"

class Browser;

namespace titan {

// OTRProfileID prefix used for all Titan isolated groups.
inline constexpr char kIsolatedGroupPrefix[] = "titan_isolated_group_";

/**
 * IsolatedProfileManager (Desktop)
 *
 * Manages a mapping from tab-group tokens to unique Chromium OTR profiles.
 * Each isolated group gets:
 *   - Its own Profile (OTR)           → own BrowserContext
 *   - Own StoragePartition            → own Cookie Store
 *   - Own LocalStorage / SessionStorage / IndexedDB
 *   - Own Cache Storage               → own Service Workers
 *   - Own Permissions                 → own Password Store
 *   - Own History                     → own Downloads
 *
 * Singleton — access via IsolatedProfileManager::Get().
 */
class IsolatedProfileManager {
 public:
  static IsolatedProfileManager& Get();

  IsolatedProfileManager(const IsolatedProfileManager&) = delete;
  IsolatedProfileManager& operator=(const IsolatedProfileManager&) = delete;

  /**
   * Returns the OTR Profile for |group_token|, creating it if necessary.
   * |regular_profile| must be the non-OTR base profile of the current session.
   */
  Profile* GetOrCreateIsolatedProfile(Profile* regular_profile,
                                       const std::string& group_token);

  /**
   * Creates a new isolated group token (UUID-based) and pre-registers it.
   * Returns the token string for use as a tab-group label / key.
   */
  std::string CreateIsolatedGroup(Profile* regular_profile);

  /** Returns true if |group_token| has an associated OTR profile. */
  bool IsIsolatedGroup(const std::string& group_token) const;

  /** Removes the isolated group. The OTR Profile is destroyed by Chromium
   *  automatically once no WebContents hold a reference to it. */
  void RemoveIsolatedGroup(const std::string& group_token);

  // ── Desktop entry points ──────────────────────────────────────────────────

  /**
   * Opens a new tab in a new isolated group in |browser|.
   * Triggered by IDC_NEW_ISOLATED_TAB command.
   */
  static void OpenIsolatedTab(Browser* browser);

  /**
   * Opens |url| in an isolated tab in |browser|, creating a new group.
   * Triggered from the context menu IDC_CONTENT_CONTEXT_OPEN_LINK_IN_ISOLATED_TAB.
   */
  static void OpenLinkInIsolatedTab(Browser* browser, const GURL& url);

 private:
  friend class base::NoDestructor<IsolatedProfileManager>;
  IsolatedProfileManager() = default;

  // group_token → serialised OTRProfileID string
  std::map<std::string, std::string> group_to_profile_id_;
};

}  // namespace titan

#endif  // CHROME_BROWSER_TITAN_ISOLATED_PROFILE_MANAGER_H_
