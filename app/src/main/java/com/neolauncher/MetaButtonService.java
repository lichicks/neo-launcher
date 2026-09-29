package com.neolauncher;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import com.neolauncher.data.AppEntry;
import com.neolauncher.data.AppRepository;
import com.neolauncher.data.Prefs;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Meta tlacitko otevre Neo - sluzba pristupnosti primo v Neu (driv samostatny
 * addon RedirectServices z Lightning Launcheru). Uzivatel ji zapne jednou
 * v Nastaveni -> Pristupnost.
 * <p>
 * Meta tlacitko samo do aplikaci nechodi; pozname ho podle toho, ze systemova
 * aplikace Questu (com.oculus.systemux) otevre Navigator. Lightning Launcher
 * hleda jen titulek "Library"/"Navigator", takze funguje, jen kdyz se Navigator
 * otevre na zalozce aplikaci. Tady navic: Navigator na jakekoliv zalozce poznat
 * podle tlacitka Knihovna uvnitr okna.
 * <p>
 * Pravidla (prani uzivatele):
 * <ul>
 * <li>Doma: Meta = Neo.</li>
 * <li>Kdyz uz je Neo otevrene: Meta = opravdove menu Questu (k nastaveni Questu se da vzdy dostat).</li>
 * <li>Ve VR hre: menu Questu (Pokracovat / Ukoncit), ne Neo pres hru (volba).</li>
 * <li>Po skonceni hry se otevre Neo (volba).</li>
 * <li>Po zapnuti Questu se otevre Neo (volba) - sluzbu system pripoji hned po startu.</li>
 * <li>3x Meta ve hre/aplikaci: otevre Neo a dole nabidne Pokracovat / Ukoncit (volba).
 *     Znovu 3x Meta v Neu = zpet do hry.</li>
 * </ul>
 * Navic umi globalni akce, ktere obycejna aplikace nesmi: uspat headset, systemova
 * nabidka vypnuti/restartu, a "Ukoncit" bezici aplikaci (klepne v Informacich
 * o aplikaci na Vynutit ukonceni).
 * Vse jen heuristika z udalosti oken - loguje se pod tagem "NeoMeta"
 * (adb -P 5038 logcat -s NeoMeta), at jde na headsetu doladit.
 */
public class MetaButtonService extends AccessibilityService {
    private static final String TAG = "NeoMeta";
    private static final String SYSTEMUX = "com.oculus.systemux";
    /** Domovske prostredi Questu - kdyz se objevi po hre, hra skoncila. */
    private static final Set<String> HOME = new HashSet<>(Arrays.asList(
            "com.oculus.vrshell", "com.oculus.shellenv"));
    /** Titulky Navigatoru (jako v RedirectServices z Lightning Launcheru, ruzne jazyky). */
    private static final Set<String> NAV_TITLES = lower(
            "Library", "Navigator", "App Library", "Knihovna", "Navigátor", "Bibliothek", "Bibliothèque",
            "Navigateur", "Biblioteca", "Navegador", "Explorador", "Libreria", "Navigatore", "Biblioteka",
            "Nawigator", "Библиотека", "Навигатор", "ライブラリ", "ナビゲーター", "라이브러리", "내비게이터",
            "资源库", "导航工具", "資料庫", "導覽小幫手", "Bibliotek", "Navigatorn", "Kirjasto");
    /** Popisky tlacitka Knihovna uvnitr Navigatoru (Navigator otevreny na jine zalozce). */
    private static final Set<String> LIBRARY_LABELS = lower(
            "Library", "App Library", "Apps Library", "Knihovna", "Bibliothek", "Bibliothèque", "Biblioteca",
            "Libreria", "Biblioteka", "Библиотека", "ライブラリ", "라이브러리", "资源库", "資料庫", "Bibliotek");
    private static final int NODE_BUDGET = 400;
    /** Po otevreni Nea chvili nereagovat (Navigator muze poslat vic udalosti). */
    private static final long LAUNCH_COOLDOWN_MS = 1500;
    /** Hra musi byt v popredi aspon takhle dlouho, aby se "po hre" otevrelo Neo. */
    private static final long MIN_GAME_MS = 4000;
    private static final long AFTER_GAME_DELAY_MS = 700;
    /** Sluzba pripojena do takove doby od zapnuti = start Questu (ne zapnuti sluzby / aktualizace Nea). */
    private static final long BOOT_WINDOW_MS = 10 * 60 * 1000L;
    /** Po prvnim domovskem okne chvili pockat, az se prostredi Questu nacte. */
    private static final long BOOT_DELAY_MS = 2500;
    /** Kdyby po startu zadne domovske okno neprislo, otevrit Neo i tak. */
    private static final long BOOT_FALLBACK_MS = 25_000;

    /**
     * 3x Meta: kazde zmacknuti, ktere otevre menu Questu, posle davku udalosti
     * systemux (vic udalosti do BURST_MS = jedno otevreni). Otevrit - zavrit - otevrit
     * = dve davky do TRIPLE_WINDOW_MS.
     */
    private static final long BURST_MS = 350;
    private static final long TRIPLE_WINDOW_MS = 1600;
    /** Vynutit ukonceni: jak dlouho zkouset klepat v Informacich o aplikaci. */
    private static final long STOP_TIMEOUT_MS = 8000;
    private static final long STOP_POLL_MS = 150;
    /** Tlacitko "Vynutit ukonceni" v ruznych jazycich (Informace o aplikaci v Androidu). */
    private static final Set<String> FORCE_STOP_LABELS = lower(
            "Force stop", "Force Stop", "Vynutit ukončení", "Beenden erzwingen", "Stoppen erzwingen",
            "Forcer l'arrêt", "Forzar detención", "Forzar cierre", "Forza interruzione",
            "Wymuś zatrzymanie", "Остановить", "Принудительно остановить", "強制停止", "강제 중지",
            "强行停止", "強制停止", "Forçar parada", "Forçar paragem", "Tvinga stopp", "Pakota lopetus");
    /** Pro LauncherActivity: Neo otevrene po ukonceni aplikace / 3x Meta v Neu. */
    public static final String EXTRA_STOPPED = "neo.stopped";
    public static final String EXTRA_RESUME = "neo.resume";

    private static volatile long suppressUntil;
    private static volatile boolean connected;
    private static volatile MetaButtonService instance;
    /** Aplikace, ze ktere se odeslo do Nea (bezi na pozadi) - lista Pokracovat / Ukoncit. */
    private static volatile String runningPkg;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Boolean> vrCache = new HashMap<>();
    private String fgPkg;
    private boolean fgVr;
    private long fgSince;
    private long lastLaunch;
    private Runnable pendingAfterGame;
    private boolean bootPending;
    private final Runnable bootLaunch = this::onBootLaunch;
    private long lastSysUx;
    private long firstOpen;
    private int opens;
    private String stopPkg, stopLabel;
    private long stopDeadline;
    private boolean stopConfirming;
    private final Runnable stopPoll = this::pollForceStop;

    /** Neo samo otevira system Questu (Nastaveni, Menu Questu...) - chvili to nebrat jako Meta tlacitko. */
    public static void suppress(long ms) {
        suppressUntil = SystemClock.uptimeMillis() + ms;
    }

    /** Je sluzba zapnuta v Nastaveni -> Pristupnost? */
    public static boolean isEnabled(Context c) {
        if (connected) return true;
        try {
            final String s = Settings.Secure.getString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return s != null && s.contains(c.getPackageName() + "/");
        } catch (Exception e) {
            return false;
        }
    }

    /** Bezici aplikace na pozadi (odesel jsi z ni do Nea), nebo null. */
    public static String runningApp() {
        return runningPkg;
    }

    public static void clearRunning() {
        runningPkg = null;
    }

    /** Uspat headset (jako kratke zmacknuti tlacitka napajeni). */
    public static boolean sleep() {
        final MetaButtonService s = instance;
        return s != null && s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    /** Systemova nabidka napajeni (vypnout / restartovat). */
    public static boolean powerMenu() {
        final MetaButtonService s = instance;
        return s != null && s.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
    }

    /**
     * Ukonci aplikaci: otevre Informace o aplikaci a klepne na Vynutit ukonceni
     * (+ potvrzeni). Pak otevre Neo. Obycejna aplikace cizi aplikaci zavrit nesmi.
     * @return false = sluzba nebezi
     */
    public static boolean forceStop(String pkg, String label) {
        final MetaButtonService s = instance;
        if (s == null) return false;
        s.handler.post(() -> s.startForceStop(pkg, label));
        return true;
    }

    /** Obrazovka, kde se sluzba zapina. */
    public static Intent settingsIntent() {
        return new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        connected = true;
        instance = this;
        Log.i(TAG, "Sluzba Meta tlacitka bezi");
        final Prefs prefs = prefs();
        if (SystemClock.elapsedRealtime() < BOOT_WINDOW_MS && (prefs == null || prefs.openOnBoot())) {
            // Quest se prave zapnul -> az se ukaze domov, otevrit Neo.
            bootPending = true;
            handler.postDelayed(bootLaunch, BOOT_FALLBACK_MS);
            Log.i(TAG, "Start Questu, po nacteni domova otevru Neo");
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        connected = false;
        instance = null;
        handler.removeCallbacksAndMessages(null);
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        final CharSequence p = e.getPackageName();
        if (p == null) return;
        final String pkg = p.toString();
        final long now = SystemClock.uptimeMillis();
        if (bootPending) onBootEvent(pkg);
        if (SYSTEMUX.equals(pkg)) {
            onSystemUx(e, now);
        } else if (HOME.contains(pkg)) {
            onHome(now);
        } else if (!isTransient(pkg)) {
            onForeground(pkg, now);
        }
    }

    // --- Start Questu -----------------------------------------------------------

    private void onBootEvent(String pkg) {
        if (HOME.contains(pkg) || SYSTEMUX.equals(pkg)) {
            // Domov je nacteny -> za chvilku Neo.
            bootPending = false;
            handler.removeCallbacks(bootLaunch);
            handler.postDelayed(bootLaunch, BOOT_DELAY_MS);
        } else if (pkg.equals(getPackageName()) || isLaunchable(pkg)) {
            // Uzivatel uz neco spustil (nebo je Neo otevrene) - nerusit.
            // Systemove veci po startu (Guardian, zamykaci obrazovka...) se nepocitaji.
            bootPending = false;
            handler.removeCallbacks(bootLaunch);
            Log.i(TAG, "Po startu uz bezi " + pkg + ", Neo neotviram");
        }
    }

    private void onBootLaunch() {
        bootPending = false;
        final Prefs prefs = prefs();
        if (prefs != null && !prefs.openOnBoot()) return;
        if (LauncherActivity.isVisible() || (fgPkg != null && isLaunchable(fgPkg))) return;
        launchNeo("po zapnuti Questu");
    }

    /** Aplikace, kterou jde spustit (hra, aplikace) - ne systemovy prekryv Questu. */
    private boolean isLaunchable(String pkg) {
        try {
            return getPackageManager().getLaunchIntentForPackage(pkg) != null;
        } catch (Exception e) {
            return false;
        }
    }

    // --- Aplikace v popredi ---------------------------------------------------

    private void onForeground(String pkg, long now) {
        if (pkg.equals(fgPkg)) return;
        // Hra se vratila (napr. Pokracovat v menu) - "po hre" uz neplati.
        cancelAfterGame();
        if (runningPkg != null && !pkg.equals(getPackageName()) && isLaunchable(pkg)) {
            // Bezici aplikace je zase v popredi (Pokracovat), nebo se spustila jina VR hra
            // (Quest drzi jen jednu) -> lista v Neu uz neplati.
            if (pkg.equals(runningPkg) || (isVrApp(pkg) && isVrApp(runningPkg))) runningPkg = null;
        }
        fgPkg = pkg;
        fgSince = now;
        fgVr = !pkg.equals(getPackageName()) && isVrApp(pkg);
        Log.d(TAG, "Popredi: " + pkg + (fgVr ? " (VR hra)" : ""));
    }

    private void onHome(long now) {
        Log.d(TAG, "Domovske prostredi, predtim " + fgPkg);
        final Prefs prefs = prefs();
        if (fgVr && now - fgSince > MIN_GAME_MS && prefs != null && prefs.metaAfterGame()
                && pendingAfterGame == null) {
            // Hra skoncila (nebo odesla domu) -> po chvilce otevrit Neo.
            pendingAfterGame = () -> {
                pendingAfterGame = null;
                launchNeo("po skonceni hry");
            };
            handler.postDelayed(pendingAfterGame, AFTER_GAME_DELAY_MS);
        }
        fgPkg = null;
        fgVr = false;
    }

    private void cancelAfterGame() {
        if (pendingAfterGame != null) {
            handler.removeCallbacks(pendingAfterGame);
            pendingAfterGame = null;
        }
    }

    /** Klavesnice a systemove prekryvy nejsou "aplikace v popredi". */
    private static boolean isTransient(String pkg) {
        return pkg.equals("android") || pkg.equals("com.android.systemui")
                || pkg.contains("inputmethod") || pkg.contains("keyboard");
    }

    private boolean isVrApp(String pkg) {
        final Boolean cached = vrCache.get(pkg);
        if (cached != null) return cached;
        boolean vr;
        final NeoApp app = NeoApp.get();
        final AppEntry e = app != null ? app.apps().find(pkg) : null;
        vr = e != null ? e.isVr() : AppRepository.isVrPackage(getPackageManager(), pkg);
        vrCache.put(pkg, vr);
        return vr;
    }

    // --- Navigator (Meta tlacitko) -------------------------------------------

    private void onSystemUx(AccessibilityEvent e, long now) {
        // 3x Meta (kazda davka udalosti systemux = jedno otevreni menu Questu).
        if (now - lastSysUx > BURST_MS && onMenuOpened(now)) {
            lastSysUx = now;
            return;
        }
        lastSysUx = now;
        if (!isNavigator(e)) {
            Log.v(TAG, "systemux okno: " + e.getText() + " / " + e.getClassName());
            return;
        }
        Log.i(TAG, "Navigator: " + e.getText() + ", popredi=" + fgPkg + (fgVr ? " (VR)" : ""));
        if (now < suppressUntil) return;                  // Neo samo otevrelo system Questu
        if (now - lastLaunch < LAUNCH_COOLDOWN_MS) return;
        if (getPackageName().equals(fgPkg) && LauncherActivity.isVisible()) {
            // Neo uz je otevrene -> druhe zmacknuti = opravdove menu Questu.
            Log.i(TAG, "Neo je otevrene, necham menu Questu");
            return;
        }
        final Prefs prefs = prefs();
        if (prefs != null && !prefs.allowShortcuts()) return;
        if (fgVr && (prefs == null || prefs.metaGameMenu())) {
            Log.i(TAG, "Bezi VR hra, necham menu Questu");
            return;
        }
        // Z bezici aplikace rovnou do Nea -> v Neu lista Pokracovat / Ukoncit.
        if (fgPkg != null && !getPackageName().equals(fgPkg) && isLaunchable(fgPkg)) runningPkg = fgPkg;
        launchNeo("Meta tlacitko");
    }

    private boolean isNavigator(AccessibilityEvent e) {
        // 1) Jako Lightning Launcher: titulek okna je "Library" / "Navigator".
        final List<CharSequence> texts = e.getText();
        if (texts != null && texts.size() == 1 && matches(texts.get(0), NAV_TITLES)) return true;
        if (matches(e.getContentDescription(), NAV_TITLES)) return true;
        // 2) Navigator otevreny na jine zalozce: uvnitr okna je tlacitko Knihovna.
        AccessibilityNodeInfo root = null;
        try {
            root = e.getSource();
            if (root == null) return false;
            return containsLabel(root, LIBRARY_LABELS, new int[]{NODE_BUDGET}, 0);
        } catch (Exception ex) {
            return false;
        } finally {
            if (root != null) {
                try {
                    root.recycle();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static boolean containsLabel(AccessibilityNodeInfo n, Set<String> labels, int[] budget, int depth) {
        if (n == null || budget[0]-- <= 0 || depth > 14) return false;
        if (matches(n.getText(), labels) || matches(n.getContentDescription(), labels)) return true;
        for (int i = 0; i < n.getChildCount(); i++) {
            final AccessibilityNodeInfo ch = n.getChild(i);
            if (ch == null) continue;
            final boolean hit = containsLabel(ch, labels, budget, depth + 1);
            try {
                ch.recycle();
            } catch (Exception ignored) {
            }
            if (hit) return true;
        }
        return false;
    }

    private static boolean matches(CharSequence s, Set<String> set) {
        return s != null && set.contains(s.toString().trim().toLowerCase(Locale.ROOT));
    }

    private static Set<String> lower(String... items) {
        final Set<String> out = new HashSet<>();
        for (String s : items) out.add(s.toLowerCase(Locale.ROOT));
        return out;
    }

    // --- 3x Meta ---------------------------------------------------------------

    /** @return true = bylo to 3x Meta a uz je vyrizene */
    private boolean onMenuOpened(long now) {
        if (opens > 0 && now - firstOpen <= TRIPLE_WINDOW_MS) {
            opens++;
        } else {
            opens = 1;
            firstOpen = now;
        }
        Log.d(TAG, "Menu Questu otevreno (" + opens + "), popredi=" + fgPkg);
        if (opens < 2) return false;
        opens = 0;
        final Prefs prefs = prefs();
        if (prefs != null && !prefs.metaTriple()) return false;
        if (now < suppressUntil) return false;
        final boolean inNeo = getPackageName().equals(fgPkg) && LauncherActivity.isVisible();
        if (inNeo) {
            // V Neu: 3x Meta = zpet do aplikace, ktera bezi na pozadi.
            if (runningPkg == null) return false;
            Log.i(TAG, "3x Meta v Neu -> zpet do " + runningPkg);
            startNeo(new Intent().putExtra(EXTRA_RESUME, true));
            return true;
        }
        if (fgPkg == null || getPackageName().equals(fgPkg) || !isLaunchable(fgPkg)) return false;
        Log.i(TAG, "3x Meta ve " + fgPkg + " -> Neo s nabidkou Pokracovat / Ukoncit");
        runningPkg = fgPkg;
        cancelAfterGame();
        lastLaunch = 0;
        launchNeo("3x Meta");
        return true;
    }

    // --- Vynutit ukonceni (Ukoncit v liste bezici aplikace) ----------------------

    private void startForceStop(String pkg, String label) {
        if (pkg == null) return;
        stopPkg = pkg;
        stopLabel = label;
        stopConfirming = false;
        stopDeadline = SystemClock.uptimeMillis() + STOP_TIMEOUT_MS;
        if (pkg.equals(runningPkg)) runningPkg = null;
        Log.i(TAG, "Ukoncuji " + pkg);
        try {
            final Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(i);
        } catch (Exception ex) {
            Log.w(TAG, "Informace o aplikaci nejdou otevrit", ex);
            stopPkg = null;
            return;
        }
        handler.removeCallbacks(stopPoll);
        handler.postDelayed(stopPoll, 400);
    }

    private void pollForceStop() {
        if (stopPkg == null) return;
        if (SystemClock.uptimeMillis() > stopDeadline) {
            // Nepovedlo se klepnout samo - Informace o aplikaci zustanou otevrene.
            Log.w(TAG, "Vynutit ukonceni se nenaslo (" + stopPkg + ")");
            stopPkg = null;
            Toast.makeText(this, "Klepni na „Vynutit ukončení“", Toast.LENGTH_LONG).show();
            return;
        }
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            if (root != null) {
                if (!stopConfirming) {
                    final AccessibilityNodeInfo btn = findLabel(root, FORCE_STOP_LABELS, new int[]{NODE_BUDGET}, 0);
                    if (btn != null) {
                        if (!btn.isEnabled()) {
                            finishForceStop("uz nebezi");
                            return;
                        }
                        if (click(btn)) stopConfirming = true;
                    }
                } else {
                    // Potvrzovaci dialog "Vynutit ukonceni?" -> OK.
                    final List<AccessibilityNodeInfo> ok = root.findAccessibilityNodeInfosByViewId("android:id/button1");
                    if (ok != null && !ok.isEmpty() && click(ok.get(0))) {
                        finishForceStop("ukonceno");
                        return;
                    }
                    final AccessibilityNodeInfo btn = findLabel(root, FORCE_STOP_LABELS, new int[]{NODE_BUDGET}, 0);
                    if (btn != null && !btn.isEnabled()) {
                        finishForceStop("ukonceno bez dialogu");
                        return;
                    }
                }
            }
        } catch (Exception ex) {
            Log.w(TAG, "Chyba pri ukoncovani", ex);
        }
        handler.postDelayed(stopPoll, STOP_POLL_MS);
    }

    private void finishForceStop(String why) {
        Log.i(TAG, "Vynutit ukonceni " + stopPkg + ": " + why);
        final String pkg = stopPkg;
        stopPkg = null;
        // Zavrit Informace o aplikaci a vratit se do Nea s hlaskou "Ukonceno".
        handler.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_BACK), 250);
        handler.postDelayed(() -> startNeo(new Intent().putExtra(EXTRA_STOPPED, pkg)), 600);
    }

    /** Uzel s jednim z popisku (vcetne rodicu, na ktere jde klepnout). */
    private static AccessibilityNodeInfo findLabel(AccessibilityNodeInfo n, Set<String> labels, int[] budget, int depth) {
        if (n == null || budget[0]-- <= 0 || depth > 20) return null;
        if (matches(n.getText(), labels) || matches(n.getContentDescription(), labels)) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            final AccessibilityNodeInfo hit = findLabel(n.getChild(i), labels, budget, depth + 1);
            if (hit != null) return hit;
        }
        return null;
    }

    private static boolean click(AccessibilityNodeInfo n) {
        for (AccessibilityNodeInfo p = n; p != null; p = p.getParent()) {
            if (p.isClickable()) return p.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        return false;
    }

    // --- Otevreni Nea ------------------------------------------------------------

    private void startNeo(Intent extras) {
        final Intent i = new Intent(this, LauncherActivity.class);
        i.putExtras(extras);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            startActivity(i);
        } catch (Exception ex) {
            Log.w(TAG, "Neo nejde otevrit", ex);
        }
    }

    private void launchNeo(String why) {
        final long now = SystemClock.uptimeMillis();
        if (now - lastLaunch < LAUNCH_COOLDOWN_MS) return;
        lastLaunch = now;
        Log.i(TAG, "Oteviram Neo (" + why + ")");
        final Intent i = new Intent(this, LauncherActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            startActivity(i);
        } catch (Exception ex) {
            Log.w(TAG, "Neo nejde otevrit", ex);
        }
    }

    private static Prefs prefs() {
        final NeoApp app = NeoApp.get();
        return app != null ? app.prefs() : null;
    }
}
