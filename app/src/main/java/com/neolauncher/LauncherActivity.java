package com.neolauncher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.neolauncher.art.ArtworkLoader;
import com.neolauncher.data.AppEntry;
import com.neolauncher.data.AppRepository;
import com.neolauncher.data.Prefs;
import com.neolauncher.launch.AppLauncher;
import com.neolauncher.ui.AppMenu;
import com.neolauncher.ui.Glass;
import com.neolauncher.ui.NeoLauncherView;
import com.neolauncher.ui.OverlayHost;
import com.neolauncher.ui.SettingsSheet;

import java.util.ArrayList;
import java.util.List;

/** Jedina aktivita launcheru. Drzi NeoLauncherView a vrstvu dialogu. */
public class LauncherActivity extends Activity
        implements NeoLauncherView.Host, AppRepository.Listener, OverlayHost.Listener {

    private static final int REQ_PICK_IMAGE = 41;
    private static volatile boolean sForeground;

    private Prefs prefs;
    private AppRepository repo;
    private ArtworkLoader artwork;
    private NeoLauncherView launcher;
    private OverlayHost overlay;
    private String pendingImagePkg;
    private boolean receiversRegistered;
    private long lastRefreshMs;

    /** Pro addon Meta tlacitka: je launcher prave v popredi? */
    public static boolean isInForeground() {
        return sForeground;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Okno skoro uplne pruhledne (alfa 1/255 - Quest s alfou 0 muze
        // delat problemy s kompozici, viz Lightning Launcher). Sklo si kresli View.
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.argb(1, 0, 0, 0)));

        NeoApp app = (NeoApp) getApplication();
        prefs = app.prefs();
        repo = app.apps();
        artwork = app.artwork();

        FrameLayout root = new FrameLayout(this);
        launcher = new NeoLauncherView(this);
        launcher.bind(prefs, artwork);
        launcher.setHost(this);
        root.addView(launcher, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay = new OverlayHost(this);
        overlay.setListener(this);
        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        repo.addListener(this);
        repo.loadCache();
        if (!repo.apps().isEmpty()) showApps(false);
        repo.refreshAsync();
        lastRefreshMs = System.currentTimeMillis();
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceivers();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceivers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        sForeground = true;
        launcher.onClockTick();
        // Pri navratu do launcheru (napr. po odinstalaci ve Store) prekontrolovat aplikace.
        long now = System.currentTimeMillis();
        if (now - lastRefreshMs > 1500) {
            lastRefreshMs = now;
            repo.refreshAsync();
        }
        if (prefs.sortMode() == Prefs.SORT_RECENT) showApps(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sForeground = false;
        launcher.clearPointer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        repo.removeListener(this);
    }

    @Override
    public void onBackPressed() {
        if (overlay.isOpen()) overlay.close();
        // Jinak nic - launcher se tlacitkem zpet nezavira.
    }

    // --- Seznam aplikaci -------------------------------------------------------

    @Override
    public void onAppsChanged(List<AppEntry> apps) {
        showApps(true);
    }

    private void showApps(boolean animate) {
        List<AppEntry> list = repo.forTab(launcher.tab());
        launcher.setApps(list, labelsFor(list), animate);
    }

    private List<String> labelsFor(List<AppEntry> list) {
        List<String> labels = new ArrayList<>(list.size());
        for (AppEntry e : list) labels.add(prefs.labelFor(e));
        return labels;
    }

    // --- NeoLauncherView.Host ---------------------------------------------------

    @Override
    public void onLaunch(AppEntry app) {
        prefs.markLaunched(app.pkg);
        AppLauncher.launch(this, app);
    }

    @Override
    public void onOpenSettings() {
        overlay.show(SettingsSheet.build(this, prefs, repo, artwork,
                        () -> showApps(true), overlay::close),
                null, launcher.frameRect(), Glass.dpi(this, 620));
    }

    @Override
    public void onAppMenu(AppEntry app, RectF cardRect) {
        final String label = prefs.labelFor(app);
        overlay.show(AppMenu.build(this, app, label, artwork.hasCustomImage(app.pkg),
                new AppMenu.Actions() {
                    @Override
                    public void launch() {
                        overlay.close();
                        onLaunch(app);
                    }

                    @Override
                    public void rename() {
                        showRename(app);
                    }

                    @Override
                    public void pickImage() {
                        overlay.close();
                        pendingImagePkg = app.pkg;
                        try {
                            //noinspection deprecation
                            startActivityForResult(AppMenu.pickImageIntent(), REQ_PICK_IMAGE);
                        } catch (Exception e) {
                            Toast.makeText(LauncherActivity.this,
                                    "Výběr souboru není k dispozici", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void removeImage() {
                        overlay.close();
                        artwork.removeCustomImage(app);
                    }

                    @Override
                    public void reloadImage() {
                        overlay.close();
                        artwork.reload(app);
                        Toast.makeText(LauncherActivity.this, "Obrázek se stahuje znovu",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void hide() {
                        overlay.close();
                        prefs.setHidden(app.pkg, true);
                        showApps(true);
                        Toast.makeText(LauncherActivity.this,
                                "Skryto. Zpět jde v Nastavení → Skryté aplikace",
                                Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void info() {
                        overlay.close();
                        AppLauncher.openAppInfo(LauncherActivity.this, app.pkg);
                    }

                    @Override
                    public void uninstall() {
                        overlay.close();
                        AppLauncher.uninstall(LauncherActivity.this, app.pkg);
                    }
                }), cardRect, launcher.frameRect(), Glass.dpi(this, 300));
    }

    private void showRename(AppEntry app) {
        overlay.show(AppMenu.rename(this, prefs.labelFor(app), app.systemLabel, name -> {
            hideKeyboard();
            prefs.setCustomLabel(app.pkg, name);
            overlay.close();
            showApps(true);
        }, () -> {
            hideKeyboard();
            overlay.close();
        }), null, launcher.frameRect(), Glass.dpi(this, 440));
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    @Override
    public void onTabSelected(int tab) {
        prefs.setTab(tab);
        List<AppEntry> list = repo.forTab(tab);
        launcher.showTab(tab, list, labelsFor(list));
    }

    @Override
    public void onOrderChanged(int tab, List<String> pkgs) {
        prefs.setManualOrder(tab, pkgs);
        if (prefs.sortMode() != Prefs.SORT_MANUAL) {
            prefs.setSortMode(Prefs.SORT_MANUAL);
            Toast.makeText(this, "Řazení přepnuto na vlastní pořadí", Toast.LENGTH_SHORT).show();
        }
    }

    // --- OverlayHost.Listener ---------------------------------------------------

    @Override
    public void onOverlayShown() {
        launcher.setInteractive(false);
        launcher.setModalBlur(true);
    }

    @Override
    public void onOverlayClosed() {
        launcher.setModalBlur(false);
        launcher.setInteractive(true);
    }

    // --- Vlastni obrazek ----------------------------------------------------------

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_IMAGE) return;
        final String pkg = pendingImagePkg;
        pendingImagePkg = null;
        if (resultCode != RESULT_OK || data == null || data.getData() == null || pkg == null) return;
        AppEntry e = repo.find(pkg);
        if (e == null) return;
        Uri uri = data.getData();
        artwork.setCustomImage(e, uri, () -> Toast.makeText(this, "Obrázek nastaven",
                Toast.LENGTH_SHORT).show());
    }

    // --- Baterie, hodiny, zmeny balicku ---------------------------------------------

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            onBattery(i);
        }
    };

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            launcher.onClockTick();
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            repo.refreshAsync();
        }
    };

    private void registerReceivers() {
        if (receiversRegistered) return;
        receiversRegistered = true;
        Intent sticky = register(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (sticky != null) onBattery(sticky);

        IntentFilter time = new IntentFilter();
        time.addAction(Intent.ACTION_TIME_TICK);
        time.addAction(Intent.ACTION_TIME_CHANGED);
        time.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        register(timeReceiver, time);

        IntentFilter pkg = new IntentFilter();
        pkg.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkg.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkg.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pkg.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pkg.addDataScheme("package");
        register(packageReceiver, pkg);
    }

    private Intent register(BroadcastReceiver r, IntentFilter f) {
        if (Build.VERSION.SDK_INT >= 33) {
            return registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
        }
        return registerReceiver(r, f);
    }

    private void unregisterReceivers() {
        if (!receiversRegistered) return;
        receiversRegistered = false;
        for (BroadcastReceiver r : new BroadcastReceiver[]{batteryReceiver, timeReceiver, packageReceiver}) {
            try {
                unregisterReceiver(r);
            } catch (Exception ignored) {
            }
        }
    }

    private void onBattery(Intent i) {
        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int pct = level >= 0 && scale > 0 ? Math.round(level * 100f / scale) : -1;
        int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        boolean charging = plugged != 0 && (status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL);
        launcher.setBattery(pct, charging, charging && isFastCharging(i));
    }

    /**
     * Rychlonabijeni stejne jako v Android SystemUI: maximalni vykon nabijecky
     * nad 7.5 W. Zaloha: okamzity proud nad 1.5 A.
     */
    private boolean isFastCharging(Intent i) {
        int microAmp = i.getIntExtra("max_charging_current", -1);
        int microVolt = i.getIntExtra("max_charging_voltage", -1);
        if (microVolt <= 0) microVolt = 5_000_000;
        if (microAmp > 0) {
            long microWatt = (long) (microAmp / 1000) * (microVolt / 1000);
            if (microWatt > 7_500_000L) return true;
        }
        try {
            BatteryManager bm = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                int now = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                return Math.abs(now) > 1_500_000 && now != Integer.MIN_VALUE;
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
