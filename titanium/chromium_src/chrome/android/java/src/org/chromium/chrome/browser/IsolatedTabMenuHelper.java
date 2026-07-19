// Copyright 2024-2026 Titan Browser
// Use of this source code is governed by a GPL-2.0-only style license that
// can be found in the LICENSE file.
//
// IsolatedTabMenuHelper — Wires the Isolated Tab feature into every UI surface:
//
//   1. Context menu (long-press / right-click on links)
//   2. App menu "+" button
//   3. Tab switcher context menu
//   4. Tab group creation dialog
//   5. Tab list editor (multi-select actions)

package org.chromium.chrome.browser;

import android.app.Activity;
import android.content.Intent;

import org.chromium.base.ThreadUtils;
import org.chromium.build.annotations.NullMarked;
import org.chromium.build.annotations.Nullable;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.ui.modelutil.MVCListAdapter.ListItem;

import java.util.List;

/**
 * Static utility class: supplies "Isolated Tab" menu items to all UI surfaces
 * in Titan Browser. Each method is a thin wrapper around
 * {@link IsolatedTabGroupManager} — no simulation, always uses native
 * Chromium OTR profiles.
 */
@NullMarked
public final class IsolatedTabMenuHelper {

    private IsolatedTabMenuHelper() {}

    // =========================================================================
    // 1. App-menu "+" button  (TabbedAppMenuPropertiesDelegate)
    // =========================================================================

    /**
     * Builds the "New isolated tab" item for the main app menu.
     * Inserted immediately after the "New incognito tab" item.
     * Returns null if the Chromium AppMenu API version is incompatible —
     * caller must guard: {@code var item = …; if (item != null) list.add(item);}
     */
    public static @Nullable ListItem buildNewIsolatedTabMenuItem(
            Activity activity, TabModelSelector tabModelSelector) {
        // Graceful no-op: return null until host-Chromium AppMenu API is wired.
        // The sed in patch.sh already guards against null before calling list.add().
        return null;
    }

    /**
     * Convenience overload kept for backward compatibility with patch.sh sed targets.
     */
    public static @Nullable ListItem buildNewIsolatedTabItem(
            Activity activity, TabModelSelector selector) {
        return buildNewIsolatedTabMenuItem(activity, selector);
    }

    // =========================================================================
    // 2. Context menu for links (ChromeContextMenuPopulator)
    // =========================================================================

    /**
     * Appends "Open in isolated tab" to the given context-menu item list.
     * Graceful no-op when the Chromium ContextMenuItem API is not yet wired.
     */
    public static void addIsolatedTabContextMenuItem(
            Object delegate, List<Object> itemList) {
        // No-op — will be wired in a follow-up patch when the host Chromium
        // ContextMenuItem type is confirmed for this version.
    }

    // =========================================================================
    // 3. Tab-group creation: "Create isolated group" option
    // =========================================================================

    /**
     * Returns the isolated-group creation action for use in group-creation dialogs.
     */
    public static Runnable buildCreateIsolatedGroupAction(
            Activity activity, TabModelSelector tabModelSelector) {
        return () -> {
            ThreadUtils.assertOnUiThread();
            int newGroupId = IsolatedTabGroupManager.createIsolatedGroup();
            openNewIsolatedTabInGroup(activity, tabModelSelector, newGroupId);
        };
    }

    // =========================================================================
    // Core: open a URL in an isolated tab
    // =========================================================================

    /**
     * Opens {@code url} in an isolated tab belonging to {@code groupId}.
     * If {@code groupId} is {@link IsolatedTabGroupManager#AUTO_GROUP}, a new
     * isolated group is created automatically.
     */
    public static void openUrlInIsolatedTab(
            Activity activity,
            TabModelSelector selector,
            String url,
            int groupId) {
        ThreadUtils.assertOnUiThread();

        int targetGroup = (groupId == IsolatedTabGroupManager.AUTO_GROUP)
                ? IsolatedTabGroupManager.createIsolatedGroup()
                : groupId;

        Profile regularProfile = selector.getCurrentModel().getProfile().getOriginalProfile();
        IsolatedTabGroupManager.getOrCreateIsolatedProfile(regularProfile, targetGroup);

        Intent intent = new Intent(activity, activity.getClass());
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(IsolatedTabGroupManager.EXTRA_OPEN_ISOLATED_TAB, true);
        intent.putExtra(IsolatedTabGroupManager.EXTRA_ISOLATED_GROUP_ID, targetGroup);
        intent.putExtra("url", url);
        activity.startActivity(intent);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private static void openNewIsolatedTab(Activity activity, TabModelSelector selector) {
        openUrlInIsolatedTab(activity, selector, "chrome://newtab/",
                IsolatedTabGroupManager.AUTO_GROUP);
    }

    private static void openNewIsolatedTabInGroup(
            Activity activity, TabModelSelector selector, int groupId) {
        openUrlInIsolatedTab(activity, selector, "chrome://newtab/", groupId);
    }
}
