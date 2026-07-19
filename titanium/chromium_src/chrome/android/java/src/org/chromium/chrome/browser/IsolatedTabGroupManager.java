// Copyright 2024-2026 Titan Browser
// Use of this source code is governed by a GPL-2.0-only style license that
// can be found in the LICENSE file.
//
// IsolatedTabGroupManager — Core isolation engine for Titan Browser.
//
// Each isolated tab group gets a fully independent Chromium OTR profile,
// providing complete data isolation:
//   - Cookie Store        - LocalStorage      - SessionStorage
//   - IndexedDB           - Cache Storage     - Service Workers
//   - Permissions         - Password Store    - History
//   - Downloads           - Site Settings
//
// Uses native Chromium OTRProfileID.createUnique() — no simulation.

package org.chromium.chrome.browser;

import android.content.Context;
import android.content.SharedPreferences;

import org.chromium.base.ContextUtils;
import org.chromium.base.ThreadUtils;
import org.chromium.build.annotations.NullMarked;
import org.chromium.build.annotations.Nullable;
import org.chromium.chrome.browser.profiles.OTRProfileID;
import org.chromium.chrome.browser.profiles.Profile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages isolated tab groups, each backed by a unique Chromium OTR profile.
 *
 * <p>Usage:
 * <pre>
 *   // Get or create an OTR profile for an isolated group
 *   OTRProfileID profileId = IsolatedTabGroupManager.getOrCreateProfileId(groupId);
 *   Profile otrProfile = regularProfile.getOffTheRecordProfile(profileId, true);
 *
 *   // Check if a group is isolated
 *   boolean isolated = IsolatedTabGroupManager.isIsolatedGroup(groupId);
 *
 *   // Create a brand-new isolated group
 *   int newGroupId = IsolatedTabGroupManager.createIsolatedGroup();
 * </pre>
 */
@NullMarked
public final class IsolatedTabGroupManager {

    /** SharedPreferences key prefix for persisting isolated group → profile ID mappings. */
    private static final String PREFS_NAME = "titan_isolated_tab_groups";
    private static final String KEY_PREFIX = "group_profile_";
    private static final String OTR_ID_PREFIX = "titan_isolated_";

    /** In-memory cache of groupId → serialised OTRProfileID string. */
    private static final Map<Integer, String> sGroupToProfileId = new HashMap<>();

    /** Counter for auto-generating group IDs when none is supplied. */
    private static int sNextAutoGroupId = 0x70000000; // high range to avoid collisions

    private IsolatedTabGroupManager() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link OTRProfileID} for the given isolated group, creating
     * one if it does not yet exist. Thread-safe (must be called on UI thread).
     *
     * @param groupId visual tab group ID
     * @return unique OTR profile ID for this group
     */
    public static OTRProfileID getOrCreateProfileId(int groupId) {
        ThreadUtils.assertOnUiThread();
        String serialised = sGroupToProfileId.get(groupId);
        if (serialised == null) {
            serialised = loadFromPrefs(groupId);
        }
        if (serialised == null) {
            // Create a brand-new unique OTR profile for this group
            OTRProfileID newId = OTRProfileID.createUnique(OTR_ID_PREFIX + groupId
                    + "_" + UUID.randomUUID().toString().substring(0, 8));
            serialised = newId.serialize();
            sGroupToProfileId.put(groupId, serialised);
            saveToPrefs(groupId, serialised);
        }
        return OTRProfileID.deserialize(serialised);
    }

    /**
     * Returns the OTR {@link Profile} for the given isolated group, creating
     * the OTR profile in the Chromium profile manager if necessary.
     *
     * @param regularProfile the regular (non-OTR) base profile
     * @param groupId        visual tab group ID
     * @return fully isolated OTR Profile
     */
    public static Profile getOrCreateIsolatedProfile(Profile regularProfile, int groupId) {
        ThreadUtils.assertOnUiThread();
        OTRProfileID profileId = getOrCreateProfileId(groupId);
        return regularProfile.getOffTheRecordProfile(profileId, /* createIfNeeded= */ true);
    }

    /**
     * Creates a new isolated group with a fresh OTR profile and returns its ID.
     * Use this when the user explicitly requests a "New isolated group."
     *
     * @return the new group's unique integer ID
     */
    public static int createIsolatedGroup() {
        ThreadUtils.assertOnUiThread();
        int groupId = sNextAutoGroupId++;
        // Pre-create the profile ID (lazy profile creation happens on first tab open)
        getOrCreateProfileId(groupId);
        return groupId;
    }

    /**
     * Returns {@code true} if the given tab group has an associated OTR profile,
     * i.e., it is an isolated group.
     */
    public static boolean isIsolatedGroup(int groupId) {
        if (sGroupToProfileId.containsKey(groupId)) return true;
        return loadFromPrefs(groupId) != null;
    }

    /**
     * Removes the isolated group mapping. Call this when the last tab of the
     * group is closed. The OTR profile will be destroyed automatically by
     * Chromium's ProfileDestroyer once no WebContents reference it.
     */
    public static void removeIsolatedGroup(int groupId) {
        ThreadUtils.assertOnUiThread();
        sGroupToProfileId.remove(groupId);
        getPrefs().edit().remove(KEY_PREFIX + groupId).apply();
    }

    /**
     * Returns a human-readable display label for the isolated group.
     * E.g., "Isolated Group 1"
     */
    public static String getGroupLabel(int groupId) {
        // Use a short hash of the group ID for readability
        int displayNum = (groupId & 0x0FFFFFFF) % 1000;
        return "Isolated Group " + displayNum;
    }

    // -------------------------------------------------------------------------
    // Intent extras — used by ChromeTabbedActivity and TabCreator
    // -------------------------------------------------------------------------

    /** Intent extra: set to {@code true} to open the URL in an isolated tab. */
    public static final String EXTRA_OPEN_ISOLATED_TAB = "titan_open_isolated_tab";

    /** Intent extra: integer isolated group ID. -1 means "create a new group". */
    public static final String EXTRA_ISOLATED_GROUP_ID = "titan_isolated_group_id";

    /** Sentinel value meaning "auto-create a new isolated group". */
    public static final int AUTO_GROUP = -1;

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    @Nullable
    private static String loadFromPrefs(int groupId) {
        String val = getPrefs().getString(KEY_PREFIX + groupId, null);
        if (val != null) {
            sGroupToProfileId.put(groupId, val);
        }
        return val;
    }

    private static void saveToPrefs(int groupId, String serialisedId) {
        getPrefs().edit().putString(KEY_PREFIX + groupId, serialisedId).apply();
    }

    private static SharedPreferences getPrefs() {
        return ContextUtils.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
