package com.neolauncher.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Vsechna nastaveni a uzivatelska data na jednom miste (SharedPreferences).
 * Jediny zdroj pravdy - UI si hodnoty vzdy cte odsud.
 */
public final class Prefs {
    public static final int TAB_GAMES = 0;
    public static final int TAB_APPS = 1;
    public static final int TAB_ALL = 2;
    public static final int TAB_COUNT = 3;

    public static final int SORT_MANUAL = 0;
    public static final int SORT_ALPHA = 1;
    public static final int SORT_RECENT = 2;

    /** Styl skla panelu: tmave z preview_neo.html, nebo svetlejsi jako ve visionOS. */
    public static final int GLASS_DARK = 0;
    public static final int GLASS_VISION = 1;

    public static final int DOF_OFF = 0;
    public static final int DOF_SOFT = 1;
    public static final int DOF_STRONG = 2;

    private static final String K_TAB = "tab";
    private static final String K_SORT = "sort_mode";
    private static final String K_DOF = "dof_mode";
    private static final String K_GLASS = "glass_alpha";
    private static final String K_GLASS_STYLE = "glass_style";
    private static final String K_POPOUT = "popout_margin";
    private static final String K_COLUMNS = "columns";
    private static final String K_SYSTEM_BLUR = "system_blur";
    private static final String K_SHORTCUTS = "allow_shortcuts";
    private static final String K_MENU_HOLD = "menu_hold_ms";
    private static final String K_ONLINE_ART = "online_art";
    private static final String K_LAUNCH_ANIM = "launch_animation";
    private static final String K_CLOSE_AFTER_LAUNCH = "close_after_launch";
    private static final String K_CAROUSEL = "carousel_mode";
    private static final String K_UPDATE_TEST = "update_test_builds";
    private static final String K_UPDATE_LAST_CHECK = "update_last_check";
    private static final String K_UPDATE_DISMISSED = "update_dismissed";
    private static final String K_HIDDEN = "hidden";
    private static final String K_ORDER = "order_";
    private static final String K_LABEL = "label_";

    public interface Listener {
        void onPrefsChanged();
    }

    private final SharedPreferences sp;
    private final SharedPreferences recents;
    private final SharedPreferences counts;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public Prefs(Context c) {
        sp = c.getSharedPreferences("neo", Context.MODE_PRIVATE);
        recents = c.getSharedPreferences("neo_recents", Context.MODE_PRIVATE);
        counts = c.getSharedPreferences("neo_launch_counts", Context.MODE_PRIVATE);
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void changed() {
        for (Listener l : listeners) l.onPrefsChanged();
    }

    // --- Vzhled -------------------------------------------------------------

    public int tab() {
        return clamp(sp.getInt(K_TAB, TAB_GAMES), 0, TAB_COUNT - 1);
    }

    public void setTab(int tab) {
        sp.edit().putInt(K_TAB, tab).apply();
    }

    public int sortMode() {
        return clamp(sp.getInt(K_SORT, SORT_MANUAL), 0, 2);
    }

    public void setSortMode(int m) {
        sp.edit().putInt(K_SORT, m).apply();
        changed();
    }

    /** Hloubka ostrosti = rozmazani okolnich karet pri hoveru. */
    public int dofMode() {
        return clamp(sp.getInt(K_DOF, DOF_SOFT), 0, 2);
    }

    public void setDofMode(int m) {
        sp.edit().putInt(K_DOF, m).apply();
        changed();
    }

    /** Kryti skleneneho panelu v procentech (preview: 42 %). */
    public int glassAlpha() {
        return clamp(sp.getInt(K_GLASS, 42), 5, 100);
    }

    public void setGlassAlpha(int a) {
        sp.edit().putInt(K_GLASS, a).apply();
        changed();
    }

    public int glassStyle() {
        return clamp(sp.getInt(K_GLASS_STYLE, GLASS_DARK), 0, 1);
    }

    public void setGlassStyle(int s) {
        sp.edit().putInt(K_GLASS_STYLE, s).apply();
        changed();
    }

    /** Pruhledny okraj okolo panelu, do ktereho muze vyskocit hovernuta karta. */
    public boolean popoutMargin() {
        return sp.getBoolean(K_POPOUT, true);
    }

    public void setPopoutMargin(boolean b) {
        sp.edit().putBoolean(K_POPOUT, b).apply();
        changed();
    }

    public int columns() {
        return clamp(sp.getInt(K_COLUMNS, 4), 3, 6);
    }

    public void setColumns(int c) {
        sp.edit().putInt(K_COLUMNS, c).apply();
        changed();
    }

    /** Jak dlouho drzet kartu bez pohybu, nez se otevre jeji menu (ms). */
    public int menuHoldMs() {
        return clamp(sp.getInt(K_MENU_HOLD, 1000), 600, 2500);
    }

    public void setMenuHoldMs(int ms) {
        sp.edit().putInt(K_MENU_HOLD, ms).apply();
        changed();
    }

    /** Animace spusteni "kukatko" (karta se pred spustenim priblizi). */
    public boolean launchAnimation() {
        return sp.getBoolean(K_LAUNCH_ANIM, true);
    }

    public void setLaunchAnimation(boolean b) {
        sp.edit().putBoolean(K_LAUNCH_ANIM, b).apply();
        changed();
    }

    /** Po spusteni hry/aplikace launcher zavrit (jako klasicky launcher). */
    public boolean closeAfterLaunch() {
        return sp.getBoolean(K_CLOSE_AFTER_LAUNCH, true);
    }

    public void setCloseAfterLaunch(boolean b) {
        sp.edit().putBoolean(K_CLOSE_AFTER_LAUNCH, b).apply();
        changed();
    }

    /** Testovaci karuselovy rezim (5x klepnout na logo Neo). */
    public boolean carouselMode() {
        return sp.getBoolean(K_CAROUSEL, false);
    }

    public void setCarouselMode(boolean b) {
        if (b == carouselMode()) return;
        sp.edit().putBoolean(K_CAROUSEL, b).apply();
        changed();
    }

    /** Stahovat bannery her z online repozitaru (jako Lightning Launcher). */
    public boolean onlineArt() {
        return sp.getBoolean(K_ONLINE_ART, true);
    }

    public void setOnlineArt(boolean b) {
        sp.edit().putBoolean(K_ONLINE_ART, b).apply();
        changed();
    }

    // --- Aktualizace --------------------------------------------------------

    /** Nabizet i testovaci buildy (z vyvojovych vetvi), ne jen z main. */
    public boolean updateTestBuilds() {
        return sp.getBoolean(K_UPDATE_TEST, true);
    }

    public void setUpdateTestBuilds(boolean b) {
        sp.edit().putBoolean(K_UPDATE_TEST, b).apply();
    }

    public long lastUpdateCheck() {
        return sp.getLong(K_UPDATE_LAST_CHECK, 0L);
    }

    public void setLastUpdateCheck(long t) {
        sp.edit().putLong(K_UPDATE_LAST_CHECK, t).apply();
    }

    /** versionCode aktualizace, kterou uzivatel odlozil ("Pozdeji"). */
    public long dismissedUpdate() {
        return sp.getLong(K_UPDATE_DISMISSED, 0L);
    }

    public void setDismissedUpdate(long versionCode) {
        sp.edit().putLong(K_UPDATE_DISMISSED, versionCode).apply();
    }

    // --- Quest --------------------------------------------------------------

    public boolean systemBlur() {
        return sp.getBoolean(K_SYSTEM_BLUR, true);
    }

    public void setSystemBlur(boolean b) {
        sp.edit().putBoolean(K_SYSTEM_BLUR, b).apply();
        changed();
    }

    public boolean allowShortcuts() {
        return sp.getBoolean(K_SHORTCUTS, true);
    }

    public void setAllowShortcuts(boolean b) {
        sp.edit().putBoolean(K_SHORTCUTS, b).apply();
        changed();
    }

    // --- Aplikace -----------------------------------------------------------

    public Set<String> hidden() {
        return new HashSet<>(sp.getStringSet(K_HIDDEN, Collections.emptySet()));
    }

    public void setHidden(String pkg, boolean hide) {
        Set<String> s = hidden();
        if (hide) s.add(pkg);
        else s.remove(pkg);
        sp.edit().putStringSet(K_HIDDEN, s).apply();
        changed();
    }

    /** Vlastni nazev od uzivatele, nebo null. */
    public String customLabel(String pkg) {
        return sp.getString(K_LABEL + pkg, null);
    }

    public void setCustomLabel(String pkg, String label) {
        SharedPreferences.Editor e = sp.edit();
        if (label == null || label.trim().isEmpty()) e.remove(K_LABEL + pkg);
        else e.putString(K_LABEL + pkg, label.trim());
        e.apply();
        changed();
    }

    public String labelFor(AppEntry e) {
        String c = customLabel(e.pkg);
        return c != null ? c : e.systemLabel;
    }

    /** Rucne serazene poradi balicku v dane zalozce. */
    public List<String> manualOrder(int tab) {
        String s = sp.getString(K_ORDER + tab, "");
        if (s.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(s.split("\n")));
    }

    public void setManualOrder(int tab, List<String> order) {
        sp.edit().putString(K_ORDER + tab, String.join("\n", order)).apply();
    }

    public long lastLaunch(String pkg) {
        return recents.getLong(pkg, 0L);
    }

    public void markLaunched(String pkg) {
        recents.edit().putLong(pkg, System.currentTimeMillis()).apply();
        counts.edit().putInt(pkg, launchCount(pkg) + 1).apply();
    }

    /** Kolikrat se aplikace spustila z Nea (pocita se od verze s karuselem). */
    public int launchCount(String pkg) {
        return counts.getInt(pkg, 0);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
