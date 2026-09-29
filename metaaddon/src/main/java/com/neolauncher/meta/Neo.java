package com.neolauncher.meta;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

/**
 * Most do Nea: stav (je otevrene? co chce uzivatel?) cte z ShortcutStateProvider
 * v Neu a Neo otevira primo jeho aktivitou. Doplnek je samostatna aplikace,
 * takze nastaveni Nea vidi jen takhle.
 */
final class Neo {
    static final String PKG = "com.neolauncher.v1";
    private static final String ACTIVITY = "com.neolauncher.LauncherActivity";
    private static final Uri STATE = Uri.parse("content://" + PKG + ".shortcutStateProvider");

    /** Extra pro Neo (stejne klice jako v Neu - MetaAddon). */
    static final String EXTRA_RUNNING = "neo.running";
    static final String EXTRA_STOPPED = "neo.stopped";
    static final String EXTRA_RESUME = "neo.resume";

    private Neo() {}

    /** Nastaveni a stav Nea v okamziku dotazu. */
    static final class State {
        boolean allowShortcuts = true;
        boolean visible;
        boolean gameMenu = true;
        boolean afterGame = true;
        boolean openOnBoot = true;
        boolean triple = true;
    }

    /** @return null = Neo neni nainstalovane / neodpovida */
    static State state(Context c) {
        try (Cursor cur = c.getContentResolver().query(STATE, null, null, null, null)) {
            if (cur == null || !cur.moveToFirst()) return null;
            final State s = new State();
            s.allowShortcuts = flag(cur, "allowShortcuts", true);
            s.visible = flag(cur, "isVisible", flag(cur, "isOpen", false));
            s.gameMenu = flag(cur, "metaGameMenu", true);
            s.afterGame = flag(cur, "metaAfterGame", true);
            s.openOnBoot = flag(cur, "openOnBoot", true);
            s.triple = flag(cur, "metaTriple", true);
            return s;
        } catch (Exception e) {
            Log.w(MetaService.TAG, "Stav Nea nejde precist", e);
            return null;
        }
    }

    private static boolean flag(Cursor cur, String col, boolean def) {
        final int i = cur.getColumnIndex(col);
        if (i < 0) return def;
        try {
            return cur.getInt(i) != 0;
        } catch (Exception e) {
            return def;
        }
    }

    /** Otevre Neo (s pripadnymi extra). */
    static boolean open(Context c, Intent extras) {
        final Intent i = new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(PKG, ACTIVITY));
        if (extras != null) i.putExtras(extras);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            Log.w(MetaService.TAG, "Neo nejde otevrit", e);
            return false;
        }
    }
}
