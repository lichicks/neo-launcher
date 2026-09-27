package com.neolauncher.ui;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.neolauncher.art.ArtworkLoader;
import com.neolauncher.data.AppEntry;
import com.neolauncher.data.AppRepository;
import com.neolauncher.data.Platform;
import com.neolauncher.data.Prefs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Panel nastaveni (otevira se klepnutim na logo Neo). */
public final class SettingsSheet {
    private SettingsSheet() {}

    public static View build(Context c, Prefs prefs, AppRepository repo, ArtworkLoader art,
                             Runnable onAppsChanged, Runnable onCheckUpdates, Runnable onClose) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Glass.panel(c));
        int pad = Glass.dpi(c, 22);
        root.setPadding(pad, Glass.dpi(c, 18), pad, Glass.dpi(c, 14));

        // Hlavicka
        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Glass.title(c, "Nastavení"),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(Glass.button(c, "Hotovo", true, v -> onClose.run()));
        root.addView(header);

        ScrollView scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // --- Vzhled ---
        body.addView(Glass.section(c, "Vzhled"));
        body.addView(Glass.row(c, "Hloubka ostrosti",
                "Rozmazání okolních karet, když na hru míříš",
                Glass.segmented(c, new String[]{"Vypnuto", "Jemná", "Silná"}, prefs.dofMode(),
                        prefs::setDofMode), true));

        final TextView glassValue = Glass.text(c, prefs.glassAlpha() + " %", 13, 0xCCFFFFFF, true);
        LinearLayout glassRow = new LinearLayout(c);
        glassRow.setOrientation(LinearLayout.HORIZONTAL);
        glassRow.setGravity(Gravity.CENTER_VERTICAL);
        glassRow.addView(Glass.slider(c, 5, 100, prefs.glassAlpha(), v -> {
            prefs.setGlassAlpha(v);
            glassValue.setText(v + " %");
        }), new LinearLayout.LayoutParams(Glass.dpi(c, 280), LinearLayout.LayoutParams.WRAP_CONTENT));
        glassRow.addView(glassValue);
        body.addView(Glass.row(c, "Krytí skla",
                "Jak moc je panel průhledný (preview: 42 %)", glassRow, true));

        final int[] colOptions = {3, 4, 5, 6};
        int colSel = 1;
        for (int i = 0; i < colOptions.length; i++) if (colOptions[i] == prefs.columns()) colSel = i;
        body.addView(Glass.row(c, "Počet sloupců", null,
                Glass.segmented(c, new String[]{"3", "4", "5", "6"}, colSel,
                        i -> prefs.setColumns(colOptions[i])), false));

        body.addView(Glass.row(c, "Animace spuštění (kukátko)",
                "Okolí se stáhne do kruhu a hrou jakoby projdeš kukátkem",
                Glass.toggle(c, prefs.launchAnimation(), prefs::setLaunchAnimation), false));

        body.addView(Glass.row(c, "Karty vyskakují z panelu",
                "Průhledný okraj okolo skla, do kterého se zvětšená karta vejde",
                Glass.toggle(c, prefs.popoutMargin(), prefs::setPopoutMargin), false));

        // --- Aplikace ---
        body.addView(Glass.section(c, "Aplikace"));
        body.addView(Glass.row(c, "Řazení",
                "Vlastní pořadí se mění podržením a přetažením karty",
                Glass.segmented(c, new String[]{"Vlastní", "Abecedně", "Naposledy"},
                        prefs.sortMode(), m -> {
                            prefs.setSortMode(m);
                            onAppsChanged.run();
                        }), true));

        final int[] holdOptions = {700, 1000, 1500};
        int holdSel = 1;
        for (int i = 0; i < holdOptions.length; i++) if (holdOptions[i] == prefs.menuHoldMs()) holdSel = i;
        body.addView(Glass.row(c, "Podržení pro menu karty",
                "Jak dlouho držet kartu bez pohybu, než se otevře její menu",
                Glass.segmented(c, new String[]{"0,7 s", "1 s", "1,5 s"}, holdSel,
                        i -> prefs.setMenuHoldMs(holdOptions[i])), false));

        body.addView(Glass.row(c, "Stahovat obrázky her z internetu",
                "Stejné zdroje jako Lightning Launcher: QuestLauncherImages a MetaMetadata "
                        + "(threethan), záložně veticia/binaries",
                Glass.toggle(c, prefs.onlineArt(), prefs::setOnlineArt), false));

        body.addView(Glass.row(c, "Obrázky her",
                "Smaže stažené bannery a stáhne je znovu",
                Glass.button(c, "Stáhnout znovu", false, v -> {
                    art.clearDownloaded();
                    Toast.makeText(c, "Obrázky se stahují znovu", Toast.LENGTH_SHORT).show();
                }), false));

        body.addView(Glass.text(c, "Skryté aplikace", 15, Color.WHITE, true));
        final LinearLayout hiddenList = new LinearLayout(c);
        hiddenList.setOrientation(LinearLayout.VERTICAL);
        fillHidden(c, hiddenList, prefs, repo, onAppsChanged);
        body.addView(hiddenList);

        // --- Quest ---
        body.addView(Glass.section(c, "Quest"));
        final boolean blendOk = Platform.supportsBlendEffects();
        body.addView(Glass.row(c, "Rozmazat prostředí za panelem",
                blendOk ? "Systémové matné sklo (Quest 3/3S). Projeví se při dalším otevření."
                        : "Toto zařízení to nepodporuje (Quest 3/3S, Horizon OS v77+)",
                Glass.toggle(c, prefs.systemBlur(), prefs::setSystemBlur), false));
        body.addView(Glass.row(c, "Otevírat Meta tlačítkem",
                "Povolí addon Shortcut/Redirect z Lightning Launcheru",
                Glass.toggle(c, prefs.allowShortcuts(), prefs::setAllowShortcuts), false));

        String version = "?";
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            version = pi.versionName;
        } catch (Exception ignored) {
        }

        // --- Aktualizace ---
        body.addView(Glass.section(c, "Aktualizace"));
        body.addView(Glass.row(c, "Neo " + version,
                "Nové verze se stahují z GitHubu (zkontroluje se i samo při otevření)",
                Glass.button(c, "Zkontrolovat", false, v -> onCheckUpdates.run()), false));
        body.addView(Glass.row(c, "Nabízet testovací verze",
                "I buildy z vývojových větví – během ladění doporučeno",
                Glass.toggle(c, prefs.updateTestBuilds(), prefs::setUpdateTestBuilds), false));

        // --- O aplikaci ---
        body.addView(Glass.section(c, "O aplikaci"));
        TextView about = Glass.text(c, "Neo Launcher " + version
                + " · vlastní launcher pro Meta Quest 3S\n"
                + "Načítání obrázků a spouštění her vychází z Lightning Launcheru (threethan, GPL-3.0).",
                12.5f, 0x99FFFFFF, false);
        about.setPadding(0, 0, 0, Glass.dpi(c, 6));
        body.addView(about);
        return root;
    }

    private static void fillHidden(Context c, LinearLayout list, Prefs prefs, AppRepository repo,
                                   Runnable onAppsChanged) {
        list.removeAllViews();
        List<String> hidden = new ArrayList<>(prefs.hidden());
        Collections.sort(hidden);
        if (hidden.isEmpty()) {
            TextView t = Glass.text(c, "Žádné skryté aplikace. Skrýt jde v menu karty "
                    + "(podržet kartu a pustit).", 12.5f, 0x99FFFFFF, false);
            t.setPadding(0, Glass.dpi(c, 4), 0, Glass.dpi(c, 8));
            list.addView(t);
            return;
        }
        for (String pkg : hidden) {
            AppEntry e = repo.find(pkg);
            String label = e != null ? prefs.labelFor(e) : pkg;
            list.addView(Glass.row(c, label, pkg, Glass.button(c, "Zobrazit", false, v -> {
                prefs.setHidden(pkg, false);
                onAppsChanged.run();
                fillHidden(c, list, prefs, repo, onAppsChanged);
            }), false));
        }
    }
}
