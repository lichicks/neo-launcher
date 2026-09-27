package com.neolauncher.launch;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import com.neolauncher.data.AppEntry;
import com.neolauncher.data.Platform;

import java.util.UUID;

/**
 * Spousteni aplikaci na Questu. Logika prevzata z Lightning Launcheru
 * (Launch.java / LaunchExt.java, threethan, GPL-3.0) a zjednodusena na to,
 * co potrebuje Quest 3S s aktualnim Horizon OS.
 */
public final class AppLauncher {
    private static final String TAG = "NeoLaunch";

    private AppLauncher() {}

    /** @return true, kdyz se spusteni podarilo odeslat. */
    public static boolean launch(Activity a, AppEntry app) {
        try {
            return launchInternal(a, app);
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "Nejde spustit " + app.pkg, e);
            Toast.makeText(a, "Aplikaci nejde spustit", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private static boolean launchInternal(Activity a, AppEntry app) {
        final PackageManager pm = a.getPackageManager();
        final boolean quest = Platform.isQuest(a);

        // Systemove panely Questu (systemux://...) otevira primo vrshell.
        if (quest && app.type == AppEntry.TYPE_PANEL) {
            Intent i = pm.getLaunchIntentForPackage("com.oculus.vrshell");
            if (i == null) return false;
            i.setData(Uri.parse(app.pkg));
            a.startActivity(i);
            return true;
        }

        // Chaty na novejsich verzich maji vlastni aktivitu.
        if (quest && "com.oculus.socialplatform".equals(app.pkg)) {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setComponent(new ComponentName("com.oculus.socialplatform",
                    "com.oculus.panelapp.people.BlendedPeopleActivity"));
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
            return true;
        }

        Intent standard = pm.getLaunchIntentForPackage(app.pkg);
        if (standard == null) standard = pm.getLeanbackLaunchIntentForPackage(app.pkg);

        // Horizon OS v71+: aplikaci spusti shell stejne, jako kdyby se klikla
        // v oficialni knihovne (VR hry immersivne, 2D aplikace v novem panelu).
        if (quest && Platform.supportsVrOsChainLaunch()
                && (standard != null || app.type == AppEntry.TYPE_VR)) {
            vrOsLaunch(a, app.pkg);
            return true;
        }

        if (standard == null) return false;
        standard.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (quest) standard.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        a.startActivity(standard);
        return true;
    }

    private static void vrOsLaunch(Activity a, String pkg) {
        if (Platform.vrOsVersion() < 202) {
            Intent i = new Intent("com.oculus.vrshell.intent.action.LAUNCH");
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setData(Uri.parse("apk://" + pkg));
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION
                    | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
            i.setComponent(new ComponentName("com.oculus.vrshell",
                    "com.oculus.vrshell.MainActivity"));
            a.startActivity(i);
        } else {
            // Horizon OS v2.0.4+ zmenil zpusob - broadcast do shellu.
            Intent i = new Intent("com.oculus.vrshell.intent.action.LAUNCH");
            i.setComponent(new ComponentName("com.oculus.vrshell",
                    "com.oculus.vrshell.ShellControlBroadcastReceiver"));
            i.setFlags(Intent.FLAG_EXCLUDE_STOPPED_PACKAGES);
            i.putExtra("intent_pkg", "com.oculus.panelapp.library");
            i.putExtra("launchId", UUID.randomUUID().toString());
            i.putExtra("intent_data", pkg);
            i.putExtra("uri", "null");
            i.putExtra("timestamp", System.currentTimeMillis());
            a.sendBroadcast(i);
        }
    }

    public static void openAppInfo(Activity a, String pkg) {
        Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.setData(Uri.parse("package:" + pkg.replaceFirst("systemux://", "")));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            a.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(a, "Informace o aplikaci nejdou otevřít", Toast.LENGTH_SHORT).show();
        }
    }

    public static void uninstall(Activity a, String pkg) {
        Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            a.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(a, "Odinstalace se nepodařila spustit", Toast.LENGTH_SHORT).show();
        }
    }
}
