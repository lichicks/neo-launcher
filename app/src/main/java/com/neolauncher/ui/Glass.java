package com.neolauncher.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.IntConsumer;

/**
 * Stavebnice pro dialogy ve stylu matneho skla (nastaveni, menu aplikace,
 * aktualizace): sklo s bilym okrajem a svetlou horni hranou, dlazdice,
 * pilulky (vybrana = bila s tmavym textem). Vse programove, bez XML.
 * Prvky maji stav "hovered", ktery Quest nastavi, kdyz na ne mirite laserem.
 */
public final class Glass {
    public static final int ACCENT = 0xFF38BDF8;
    private static final int[] HOVERED = {android.R.attr.state_hovered};
    private static final int[] PRESSED = {android.R.attr.state_pressed};
    private static final int[] SELECTED = {android.R.attr.state_selected};
    private static final int[] EMPTY = {};

    private Glass() {}

    /*
     * Jednotny rytmus mezer a zaobleni (dp) pro vsechna okna i rychle menu.
     * Rohy jsou soustredne: vnitrni radius = vnejsi radius - odsazeni, takze
     * okraj dlazdice bezi s okrajem panelu rovnobezne (jinak roh "nesedi").
     */
    /** Vnitrni okraj panelu - vsude a ze vsech stran stejny. */
    public static final float PAD = 20f;
    /** Mezi nadpisem panelu a obsahem (a mezi velkymi celky). */
    public static final float SECTION = 20f;
    /** Mezi dlazdicemi a prvky v mrizce - vodorovne i svisle stejne. */
    public static final float GAP = 12f;
    /** Hlavni tlacitko pres celou sirku (Hotovo, Nastaveni Nea...). */
    public static final float BUTTON_H = 48f;
    /** Mezi malymi prvky vedle sebe (pilulky, tlacitka). */
    public static final float GAP_S = 8f;
    /** Dlazdice, karty, posuvniky. */
    public static final float R_TILE = 20f;
    /** Panel = dlazdice + okraj (soustredne). */
    public static final float R_PANEL = R_TILE + PAD;
    /** Vnitrni okraj dlazdice s textem (dlazdice nastaveni, widgety). */
    public static final float TILE_PAD = 16f;
    /** Odsazeni obsahu uvnitr dlazdice (nahled hry v karte). */
    public static final float INSET = 8f;
    /** Nahled uvnitr dlazdice (soustredne s dlazdici). */
    public static final float R_INNER = R_TILE - INSET;

    public static float dp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    public static int dpi(Context c, float v) {
        return Math.round(dp(c, v));
    }

    /*
     * Barvy skla dialogu (nahore svetlejsi) - sdili je i rychle menu. Ridi se
     * volbou "Styl skla", at dialog vypada jako zbytek launcheru: tmave sklo
     * (vychozi, jako panel z preview) nebo svetle sede.
     */
    public static int GLASS_TOP;
    public static int GLASS_BOTTOM;
    public static int GLASS_STROKE;
    public static int GLASS_SHINE;
    /** Dlazdice uvnitr skla. */
    public static int TILE;
    public static int TILE_HOVER;
    public static int TILE_STROKE;

    static {
        setStyle(false);
    }

    /** @param vision true = svetle sede sklo, false = tmave sklo jako panel launcheru */
    public static void setStyle(boolean vision) {
        if (vision) {
            GLASS_TOP = 0xD6646872;
            GLASS_BOTTOM = 0xCF484C55;
            GLASS_STROKE = 0x59FFFFFF;
            GLASS_SHINE = 0x2EFFFFFF;
            TILE = 0x1FFFFFFF;
            TILE_HOVER = 0x33FFFFFF;
            TILE_STROKE = 0x33FFFFFF;
        } else {
            GLASS_TOP = 0xF01C222D;
            GLASS_BOTTOM = 0xF2121720;
            GLASS_STROKE = 0x3DFFFFFF;
            GLASS_SHINE = 0x1FFFFFFF;
            TILE = 0x12FFFFFF;
            TILE_HOVER = 0x26FFFFFF;
            TILE_STROKE = 0x21FFFFFF;
        }
    }
    /** Tmavy text na bilem (vybrana pilulka, hlavni tlacitko). */
    public static final int INK = 0xFF1C212B;

    /** Matne sklo: bily okraj, svetla horni hrana, radius R_PANEL. */
    public static Drawable panel(Context c) {
        return panel(c, R_PANEL);
    }

    public static Drawable panel(Context c, float radiusDp) {
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{GLASS_TOP, GLASS_BOTTOM});
        base.setCornerRadius(dp(c, radiusDp));
        base.setStroke(dpi(c, 1.5f), GLASS_STROKE);
        GradientDrawable shine = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{GLASS_SHINE, 0x00FFFFFF, 0x00FFFFFF});
        shine.setCornerRadius(dp(c, radiusDp));
        return new android.graphics.drawable.LayerDrawable(new Drawable[]{base, shine});
    }

    /** Dlazdice (karta nastaveni, widget) - pri miren laserem se rozsviti. */
    public static Drawable tile(Context c) {
        StateListDrawable s = new StateListDrawable();
        s.addState(PRESSED, round(c, 0x40FFFFFF, R_TILE, 0x80FFFFFF));
        s.addState(HOVERED, round(c, TILE_HOVER, R_TILE, 0x66FFFFFF));
        s.addState(EMPTY, round(c, TILE, R_TILE, TILE_STROKE));
        return s;
    }

    /** Staticka dlazdice bez hoveru (widgety). */
    public static Drawable widget(Context c) {
        return round(c, TILE, R_TILE, TILE_STROKE);
    }

    /** Pilulka (kategorie, volba): vybrana = bila s tmavym textem, jinak obrys. */
    public static TextView pill(Context c, String label, boolean selected, View.OnClickListener l) {
        TextView t = text(c, label, 13.5f, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dpi(c, 16), dpi(c, 8), dpi(c, 16), dpi(c, 8));
        t.setClickable(true);
        t.setFocusable(true);
        setPillSelected(c, t, selected);
        t.setOnClickListener(l);
        return t;
    }

    public static void setPillSelected(Context c, TextView t, boolean selected) {
        t.setSelected(selected);
        t.setTextColor(selected ? INK : 0xF2FFFFFF);
        t.setBackground(selected
                ? states(c, 999, 0xF2FFFFFF, 0xFFFFFFFF, 0xFFE2E8F0, 0, 0)
                : states(c, 999, 0x00FFFFFF, 0x26FFFFFF, 0x40FFFFFF, 0, 0x59FFFFFF));
    }

    private static GradientDrawable round(Context c, int color, float radius, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radius));
        if (strokeColor != 0) g.setStroke(dpi(c, 1), strokeColor);
        return g;
    }

    private static Drawable states(Context c, float radius, int normal, int hovered,
                                   int pressed, int selected, int stroke) {
        StateListDrawable s = new StateListDrawable();
        s.addState(PRESSED, round(c, pressed, radius, stroke));
        if (selected != 0) s.addState(SELECTED, round(c, selected, radius, 0x55FFFFFF));
        s.addState(HOVERED, round(c, hovered, radius, 0x40FFFFFF));
        s.addState(EMPTY, round(c, normal, radius, stroke));
        return s;
    }

    public static TextView text(Context c, String s, float sizeDp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(Typeface.SANS_SERIF, bold ? 700 : 400, false));
        return t;
    }

    public static TextView title(Context c, String s) {
        return text(c, s, 20, Color.WHITE, true);
    }

    public static TextView section(Context c, String s) {
        TextView t = text(c, s.toUpperCase(), 12, 0x99FFFFFF, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(0, dpi(c, 18), 0, dpi(c, 6));
        return t;
    }

    /** Tlacitko-pilulka. accent = hlavni akce (bila s tmavym textem), jinak obrys. */
    public static TextView button(Context c, String label, boolean accent, View.OnClickListener l) {
        TextView b = text(c, label, 14, accent ? INK : Color.WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dpi(c, 18), dpi(c, 9), dpi(c, 18), dpi(c, 9));
        if (accent) {
            b.setBackground(states(c, 999, 0xF2FFFFFF, 0xFFFFFFFF, 0xFFE2E8F0, 0, 0));
        } else {
            b.setBackground(states(c, 999, 0x14FFFFFF, 0x33FFFFFF, 0x4DFFFFFF, 0, 0x66FFFFFF));
        }
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(l);
        return b;
    }

    /** Polozka menu pres celou sirku, zarovnana vlevo. */
    public static TextView menuItem(Context c, String label, boolean danger, View.OnClickListener l) {
        TextView b = text(c, label, 15, danger ? 0xFFFCA5A5 : Color.WHITE, false);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(dpi(c, 14), dpi(c, 11), dpi(c, 14), dpi(c, 11));
        b.setBackground(states(c, R_TILE, 0x00FFFFFF, 0x2EFFFFFF, 0x47FFFFFF, 0, 0));
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(l);
        return b;
    }

    /** Prepinac z nekolika moznosti - rada pilulek (vybrana je bila). */
    public static LinearLayout segmented(Context c, String[] items, int selected, IntConsumer onSelect) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final TextView[] views = new TextView[items.length];
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            TextView t = pill(c, items[i], i == selected, v -> {
                for (int j = 0; j < views.length; j++) setPillSelected(c, views[j], j == idx);
                onSelect.accept(idx);
            });
            t.setPadding(dpi(c, 14), dpi(c, 7), dpi(c, 14), dpi(c, 7));
            views[i] = t;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.leftMargin = dpi(c, 6);
            row.addView(t, lp);
        }
        return row;
    }

    public static GlassWidgets.Toggle toggle(Context c, boolean on, java.util.function.Consumer<Boolean> onChange) {
        return new GlassWidgets.Toggle(c, on, onChange);
    }

    /** Radek nastaveni: nazev + popis vlevo, ovladaci prvek vpravo nebo pod nim. */
    public static LinearLayout row(Context c, String title, String subtitle, View control, boolean below) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(below ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        row.setGravity(below ? Gravity.START : Gravity.CENTER_VERTICAL);
        row.setPadding(0, dpi(c, 8), 0, dpi(c, 8));
        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(c, title, 15, Color.WHITE, true));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = text(c, subtitle, 12.5f, 0x99FFFFFF, false);
            s.setPadding(0, dpi(c, 2), 0, 0);
            texts.addView(s);
        }
        if (below) {
            row.addView(texts);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dpi(c, 8);
            row.addView(control, lp);
        } else {
            row.addView(texts, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dpi(c, 16);
            row.addView(control, lp);
        }
        return row;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(0x26FFFFFF);
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, dpi(c, 1))));
        return v;
    }
}
