#!/bin/bash
# Titan Browser — Desktop-specific patches (Linux/Windows/macOS)

# ===== PACKAGE / BRANDING =====
sed -i 's|"Google Chrome"|"Titan Browser"|g' chrome/app/chromium_strings.grd 2>/dev/null || true
sed -i 's|"Chromium"|"Titan Browser"|g' chrome/app/chromium_strings.grd 2>/dev/null || true

# ===== DESKTOP: ISOLATED TAB — Context menu =====
# Add "Open in isolated tab" to the right-click context menu for links
# Targets: chrome/browser/renderer_context_menu/render_view_context_menu.cc
if [ -f chrome/browser/renderer_context_menu/render_view_context_menu.cc ]; then
  # Add isolated tab command ID include
  sed -i '/#include "chrome\/app\/chrome_command_ids.h"/a #include "chrome/browser/titan/isolated_profile_manager.h"' \
    chrome/browser/renderer_context_menu/render_view_context_menu.cc

  # Add "Open in isolated tab" after "Open in incognito window" in the link menu
  sed -i 's|IDC_CONTENT_CONTEXT_OPENLINKOFFTHERECORD|IDC_CONTENT_CONTEXT_OPENLINKOFFTHERECORD;\n  AppendMenuItem(IDC_CONTENT_CONTEXT_OPEN_LINK_IN_ISOLATED_TAB);|' \
    chrome/browser/renderer_context_menu/render_view_context_menu.cc || true
fi

# ===== DESKTOP: ISOLATED TAB — Command IDs =====
if [ -f chrome/app/chrome_command_ids.h ]; then
  # Add isolated tab command IDs
  sed -i '/^#define IDC_CONTENT_CONTEXT_OPENLINKOFFTHERECORD/a #define IDC_CONTENT_CONTEXT_OPEN_LINK_IN_ISOLATED_TAB  33901\n#define IDC_NEW_ISOLATED_TAB                           33902\n#define IDC_NEW_ISOLATED_GROUP                         33903' \
    chrome/app/chrome_command_ids.h || true
fi

# ===== DESKTOP: ISOLATED TAB — Browser commands =====
if [ -f chrome/browser/ui/browser_commands.cc ]; then
  sed -i '/#include "chrome\/browser\/profiles\/profile.h"/a #include "chrome/browser/titan/isolated_profile_manager.h"' \
    chrome/browser/ui/browser_commands.cc

  # Handle IDC_NEW_ISOLATED_TAB command
  sed -i 's|case IDC_NEW_INCOGNITO_WINDOW:|case IDC_NEW_ISOLATED_TAB:\n      titan::IsolatedProfileManager::OpenIsolatedTab(browser);\n      break;\n    case IDC_NEW_INCOGNITO_WINDOW:|' \
    chrome/browser/ui/browser_commands.cc || true
fi

# ===== Copy Titan desktop source files =====
mkdir -p chrome/browser/titan
cp $SCRIPT_DIR/titanium/chromium_src/chrome/browser/titan/isolated_profile_manager.h \
   chrome/browser/titan/ 2>/dev/null || true
cp $SCRIPT_DIR/titanium/chromium_src/chrome/browser/titan/isolated_profile_manager.cc \
   chrome/browser/titan/ 2>/dev/null || true

export PATCHED_DESKTOP=1
