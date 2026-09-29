package com.neolauncher.update;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;
import android.widget.Toast;

/** Vysledek instalace od systemu (PackageInstaller): aktualizace Nea nebo APK vybrane v Neu. */
public class InstallReceiver extends BroadcastReceiver {
    /** Nazev nainstalovane aplikace (nebo balicek, kdyz nazev nejde zjistit). */
    private static String label(Context c, String pkg) {
        if (pkg == null) return "aplikace";
        try {
            return String.valueOf(c.getPackageManager().getApplicationLabel(
                    c.getPackageManager().getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return pkg;
        }
    }

    private static final String TAG = "NeoUpdater";

    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
        final boolean apk = ApkInstaller.KIND_APK.equals(intent.getStringExtra(ApkInstaller.EXTRA_KIND));
        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION: {
                // System chce potvrzeni od uzivatele - otevrit jeho dialog.
                @SuppressWarnings("deprecation")
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        context.startActivity(confirm);
                    } catch (Exception e) {
                        Log.w(TAG, "Potvrzovaci dialog instalace nejde otevrit", e);
                        Toast.makeText(context, "Instalaci nejde potvrdit", Toast.LENGTH_LONG).show();
                    }
                }
                break;
            }
            case PackageInstaller.STATUS_SUCCESS:
                // Po aktualizaci sebe sama nas system ukonci - neni co delat.
                if (apk) {
                    Toast.makeText(context, "Nainstalováno: " + label(context,
                            intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)), Toast.LENGTH_LONG).show();
                }
                break;
            default: {
                String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Log.w(TAG, "Instalace selhala: " + status + " " + msg);
                if (status != PackageInstaller.STATUS_FAILURE_ABORTED) {
                    Toast.makeText(context, (apk ? "Aplikace se nenainstalovala" : "Aktualizace se nenainstalovala")
                            + (msg != null ? ": " + msg : ""), Toast.LENGTH_LONG).show();
                }
                break;
            }
        }
    }
}
