package com.neolauncher;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import com.neolauncher.data.ForegroundApps;
import com.neolauncher.data.Platform;
import com.neolauncher.data.Prefs;

/**
 * Stav Nea pro doplnek "Neo - Meta tlacitko" (MetaAddon) a starsi addon
 * RedirectServices z Lightning Launcheru: isOpen / isVisible (je Neo otevrene?),
 * shouldBlur, allowShortcuts a volby Meta tlacitka z nastaveni Nea
 * (metaGameMenu, metaAfterGame, openOnBoot, metaTriple) a co je opravdu v popredi
 * podle UsageStats (usageKnown, topPkg, vrPkg - viz ForegroundApps).
 */
public class ShortcutStateProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        final MatrixCursor c = new MatrixCursor(new String[]{"isOpen", "shouldBlur", "allowShortcuts",
                "isVisible", "metaGameMenu", "metaAfterGame", "openOnBoot", "metaTriple",
                "usageKnown", "topPkg", "vrPkg", "endedVrPkg"});
        final NeoApp app = NeoApp.get();
        final Prefs prefs = app != null ? app.prefs() : null;
        final int visible = LauncherActivity.isVisible() ? 1 : 0;
        // Co je opravdu v popredi (UsageStats) - udalosti oken na Questu klamou (menu ve hre).
        final ForegroundApps.Result fg = getContext() != null ? ForegroundApps.query(getContext())
                : new ForegroundApps.Result();
        if (prefs == null) {
            c.addRow(new Object[]{LauncherActivity.isInForeground() ? 1 : 0, 0, 1, visible, 1, 1, 1, 1,
                    fg.known ? 1 : 0, fg.topPkg, fg.vrPkg, fg.endedVrPkg});
            return c;
        }
        final boolean allow = prefs.allowShortcuts();
        final boolean blur = allow && prefs.systemBlur() && Platform.supportsBlendEffects();
        c.addRow(new Object[]{allow && LauncherActivity.isInForeground() ? 1 : 0, blur ? 1 : 0, allow ? 1 : 0,
                visible, prefs.metaGameMenu() ? 1 : 0, prefs.metaAfterGame() ? 1 : 0,
                prefs.openOnBoot() ? 1 : 0, prefs.metaTriple() ? 1 : 0,
                fg.known ? 1 : 0, fg.topPkg, fg.vrPkg, fg.endedVrPkg});
        return c;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
