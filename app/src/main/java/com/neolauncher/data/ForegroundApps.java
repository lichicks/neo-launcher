package com.neolauncher.data;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Co je ted opravdu v popredi - pro doplnek Meta tlacitka (pta se pres
 * ShortcutStateProvider). Udalosti oken na Questu nestaci: kdyz se ve hre
 * zmackne Meta, Quest posle udalost domovskeho prostredi, jako by hra skoncila.
 * <p>
 * Zdroj pravdy = UsageStats (stejne opravneni "Pristup k vyuziti" jako herni
 * cas): posledni udalost kazde aktivity RESUMED / PAUSED / STOPPED. Hra
 * "bezi", kdyz jeji aktivita je RESUMED, nebo byla PAUSED pred chvilkou
 * (menu Questu pres hru) - ukoncena hra je STOPPED.
 */
public final class ForegroundApps {
    private static final String TAG = "NeoMeta";
    /** Kolik historie projit (udalosti jsou po startu headsetu, hra delsi nez tohle je vzacnost). */
    private static final long WINDOW_MS = 8L * 60 * 60 * 1000;
    /** PAUSED pred mene nez tolika ms = hra porad bezi pod menu Questu. */
    private static final long PAUSED_GRACE_MS = 5000;
    private static final long CACHE_MS = 400;
    /** Hra skoncena pred mene nez tolika ms a hrala aspon MIN_PLAY_MS = "po hre otevrit Neo". */
    private static final long ENDED_WINDOW_MS = 15_000;
    private static final long MIN_PLAY_MS = 4000;
    private static final Set<String> SYSTEM = new HashSet<>(java.util.Arrays.asList(
            "com.oculus.vrshell", "com.oculus.shellenv", "com.oculus.systemux", "com.oculus.panelapp.library",
            "com.android.systemui", "android", "com.neolauncher.meta"));

    /** Vysledek dotazu. */
    public static final class Result {
        /** Mame opravneni a dotaz se povedl (jinak se doplnek ridi udalostmi oken). */
        public boolean known;
        /** Posledni aplikace v popredi (RESUMED), krome Nea a systemu, nebo null. */
        public String topPkg;
        /** VR hra, ktera ted bezi (i pod menu Questu), nebo null. */
        public String vrPkg;
        /** VR hra, ktera prave skoncila (STOPPED pred chvilkou po aspon par vterinach hrani), nebo null. */
        public String endedVrPkg;
    }

    private static Result cached;
    private static long cachedAt;

    private ForegroundApps() {}

    public static synchronized Result query(Context c) {
        final long now = SystemClock.uptimeMillis();
        if (cached != null && now - cachedAt < CACHE_MS) return cached;
        final Result r = new Result();
        try {
            final AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            final boolean allowed = ops != null && ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), c.getPackageName()) == AppOpsManager.MODE_ALLOWED;
            final UsageStatsManager usm = c.getSystemService(UsageStatsManager.class);
            if (allowed && usm != null) {
                compute(c, usm, r);
                r.known = true;
            }
        } catch (Exception e) {
            Log.w(TAG, "Popredi z UsageStats nejde zjistit", e);
        }
        cached = r;
        cachedAt = now;
        return r;
    }

    private static void compute(Context c, UsageStatsManager usm, Result r) {
        final long now = System.currentTimeMillis();
        final UsageEvents events = usm.queryEvents(now - WINDOW_MS, now);
        // Posledni stav kazde aktivity (balicek/trida) a kdy.
        final Map<String, Integer> state = new HashMap<>();
        final Map<String, Long> when = new HashMap<>();
        final Map<String, Long> resumedAt = new HashMap<>();
        final UsageEvents.Event e = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            final int t = e.getEventType();
            if (t == UsageEvents.Event.DEVICE_STARTUP || t == UsageEvents.Event.DEVICE_SHUTDOWN) {
                state.clear();
                when.clear();
                continue;
            }
            if (t != UsageEvents.Event.ACTIVITY_RESUMED && t != UsageEvents.Event.ACTIVITY_PAUSED
                    && t != UsageEvents.Event.ACTIVITY_STOPPED) continue;
            final String key = e.getPackageName() + "/" + e.getClassName();
            final Integer prev = state.get(key);
            if (t == UsageEvents.Event.ACTIVITY_RESUMED
                    && (prev == null || prev == UsageEvents.Event.ACTIVITY_STOPPED)) {
                resumedAt.put(key, e.getTimeStamp()); // zacatek "sezeni" aktivity
            }
            state.put(key, t);
            when.put(key, e.getTimeStamp());
        }
        final String self = c.getPackageName();
        final PackageManager pm = c.getPackageManager();
        long topAt = 0, vrAt = 0, endedAt = 0;
        final Set<String> runningPkgs = new HashSet<>();
        for (Map.Entry<String, Integer> en : state.entrySet()) {
            final String pkg = en.getKey().substring(0, en.getKey().indexOf('/'));
            if (pkg.equals(self) || SYSTEM.contains(pkg) || AppRepository.isExcluded(pkg)) continue;
            final int t = en.getValue();
            final long at = when.get(en.getKey());
            if (t == UsageEvents.Event.ACTIVITY_STOPPED && now - at < ENDED_WINDOW_MS && at > endedAt) {
                final Long start = resumedAt.get(en.getKey());
                if (start != null && at - start >= MIN_PLAY_MS && AppRepository.isVrPackage(pm, pkg)) {
                    endedAt = at;
                    r.endedVrPkg = pkg;
                }
            }
            final boolean resumed = t == UsageEvents.Event.ACTIVITY_RESUMED;
            final boolean justPaused = t == UsageEvents.Event.ACTIVITY_PAUSED && now - at < PAUSED_GRACE_MS;
            if (!resumed && !justPaused) continue;
            if (pm.getLaunchIntentForPackage(pkg) == null && !AppRepository.isVrPackage(pm, pkg)) continue;
            if (resumed && at > topAt) {
                topAt = at;
                r.topPkg = pkg;
            }
            runningPkgs.add(pkg);
            if (at > vrAt && AppRepository.isVrPackage(pm, pkg)) {
                vrAt = at;
                r.vrPkg = pkg;
            }
        }
        // Hra, ktera porad bezi (jina aktivita), neskoncila.
        if (r.endedVrPkg != null && runningPkgs.contains(r.endedVrPkg)) r.endedVrPkg = null;
    }
}
