package com.neolauncher.update;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;
import android.widget.Toast;

/** Vysledek instalace aktualizace od systemu (PackageInstaller). */
public class InstallReceiver extends BroadcastReceiver {
    private static final String TAG = "NeoUpdater";

    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
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
                break;
            default: {
                String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Log.w(TAG, "Instalace selhala: " + status + " " + msg);
                if (status != PackageInstaller.STATUS_FAILURE_ABORTED) {
                    Toast.makeText(context, "Aktualizace se nenainstalovala"
                            + (msg != null ? ": " + msg : ""), Toast.LENGTH_LONG).show();
                }
                break;
            }
        }
    }
}
