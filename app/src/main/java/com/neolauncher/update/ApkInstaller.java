package com.neolauncher.update;

import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Instalace APK primo z Nea (sideload bez PC): uzivatel vybere soubor stazeny
 * treba v prohlizeci Questu, Neo ho preda systemu (PackageInstaller) a Quest
 * se zepta na potvrzeni. Stejna cesta jako aktualizace Nea.
 */
public final class ApkInstaller {
    private static final String TAG = "NeoApk";
    /** Extra pro InstallReceiver: jde o cizi APK, ne aktualizaci Nea. */
    static final String EXTRA_KIND = "neo.install.kind";
    static final String KIND_APK = "apk";

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ApkInstaller() {}

    /** Vyber souboru (systemovy vyber dokumentu). APK nemivaji vzdy spravny typ, proto i obecne soubory. */
    public static Intent pickIntent() {
        final Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/vnd.android.package-archive", "application/octet-stream"});
        return i;
    }

    /**
     * Zkopiruje APK do instalacni relace a odesle ke schvaleni.
     * @param onError text chyby pro uzivatele (na hlavnim vlakne), nebo null = predano systemu
     */
    public static void install(Context c, Uri uri, Consumer<String> onError) {
        final Context app = c.getApplicationContext();
        EXEC.execute(() -> {
            String error = null;
            PackageInstaller.Session session = null;
            try {
                final ContentResolver cr = app.getContentResolver();
                final String name = displayName(cr, uri);
                if (name != null && !name.toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) {
                    error = "Tohle není soubor APK";
                } else {
                    final long size = fileSize(cr, uri);
                    final PackageInstaller pi = app.getPackageManager().getPackageInstaller();
                    final PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                            PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                    if (size > 0) params.setSize(size);
                    final int id = pi.createSession(params);
                    session = pi.openSession(id);
                    try (InputStream is = cr.openInputStream(uri);
                         OutputStream os = session.openWrite("app.apk", 0, size > 0 ? size : -1)) {
                        if (is == null) throw new java.io.FileNotFoundException(String.valueOf(uri));
                        final byte[] buf = new byte[256 * 1024];
                        int n;
                        while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
                        session.fsync(os);
                    }
                    final Intent result = new Intent(app, InstallReceiver.class).putExtra(EXTRA_KIND, KIND_APK);
                    final PendingIntent p = PendingIntent.getBroadcast(app, id, result,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                    session.commit(p.getIntentSender());
                    session.close();
                    session = null;
                }
            } catch (Exception e) {
                Log.w(TAG, "Instalace APK selhala", e);
                error = "APK se nepodařilo načíst";
                if (session != null) {
                    try {
                        session.abandon();
                    } catch (Exception ignored) {
                    }
                }
            } finally {
                if (session != null) {
                    try {
                        session.close();
                    } catch (Exception ignored) {
                    }
                }
            }
            final String err = error;
            if (err != null && onError != null) MAIN.post(() -> onError.accept(err));
        });
    }

    private static String displayName(ContentResolver cr, Uri uri) {
        try (Cursor cur = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cur != null && cur.moveToFirst() && !cur.isNull(0)) return cur.getString(0);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static long fileSize(ContentResolver cr, Uri uri) {
        try (Cursor cur = cr.query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cur != null && cur.moveToFirst() && !cur.isNull(0)) return cur.getLong(0);
        } catch (Exception ignored) {
        }
        return -1;
    }
}
