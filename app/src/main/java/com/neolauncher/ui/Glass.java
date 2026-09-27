package com.neolauncher.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.function.IntConsumer;

/**
 * Stavebnice pro dialogy ve stylu skla z preview (nastaveni, menu aplikace).
 * Vse programove, bez XML layoutu. Tlacitka maji stav "hovered", ktery Quest
 * nastavi, kdyz na ne mirite laserem.
 */
public final class Glass {
    public static final int ACCENT = 0xFF38BDF8;
    private static final int[] HOVERED = {android.R.attr.state_hovered};
    private static final int[] PRESSED = {android.R.attr.state_pressed};
    private static final int[] SELECTED = {android.R.attr.state_selected};
    private static final int[] EMPTY = {};

    private Glass() {}

    public static float dp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    public static int dpi(Context c, float v) {
        return Math.round(dp(c, v));
    }

    /** Tmave sklo s jemnym svetlym okrajem. */
    public static GradientDrawable panel(Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xEE10151F);
        g.setCornerRadius(dp(c, 24));
        g.setStroke(dpi(c, 1.5f), 0x33FFFFFF);
        return g;
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

    /** Tlacitko-pilulka. accent = modre zvyrazneni (hlavni akce). */
    public static TextView button(Context c, String label, boolean accent, View.OnClickListener l) {
        TextView b = text(c, label, 14, Color.WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dpi(c, 16), dpi(c, 9), dpi(c, 16), dpi(c, 9));
        if (accent) {
            b.setBackground(states(c, 999, 0x5538BDF8, 0x8038BDF8, 0xA038BDF8, 0, 0x8038BDF8));
        } else {
            b.setBackground(states(c, 999, 0x1AFFFFFF, 0x33FFFFFF, 0x4DFFFFFF, 0, 0x26FFFFFF));
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
        b.setBackground(states(c, 12, 0x00FFFFFF, 0x24FFFFFF, 0x3DFFFFFF, 0, 0));
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(l);
        return b;
    }

    /** Prepinac z nekolika moznosti (segmented control). */
    public static LinearLayout segmented(Context c, String[] items, int selected, IntConsumer onSelect) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int pad = dpi(c, 3);
        row.setPadding(pad, pad, pad, pad);
        row.setBackground(round(c, 0x38000000, 999, 0x24FFFFFF));
        final TextView[] views = new TextView[items.length];
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            TextView t = text(c, items[i], 13, Color.WHITE, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dpi(c, 14), dpi(c, 7), dpi(c, 14), dpi(c, 7));
            t.setBackground(states(c, 999, 0x00FFFFFF, 0x1FFFFFFF, 0x33FFFFFF, 0x33FFFFFF, 0));
            t.setClickable(true);
            t.setFocusable(true);
            t.setSelected(i == selected);
            t.setAlpha(i == selected ? 1f : 0.75f);
            t.setOnClickListener(v -> {
                for (int j = 0; j < views.length; j++) {
                    views[j].setSelected(j == idx);
                    views[j].setAlpha(j == idx ? 1f : 0.75f);
                }
                onSelect.accept(idx);
            });
            views[i] = t;
            row.addView(t);
        }
        return row;
    }

    public static Switch toggle(Context c, boolean on, java.util.function.Consumer<Boolean> onChange) {
        Switch s = new Switch(c);
        s.setChecked(on);
        int[][] st = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(st, new int[]{ACCENT, 0xFFD4D4D8}));
        s.setTrackTintList(new ColorStateList(st, new int[]{0x8038BDF8, 0x4DFFFFFF}));
        s.setOnCheckedChangeListener((b, v) -> onChange.accept(v));
        return s;
    }

    public static SeekBar slider(Context c, int min, int max, int value, IntConsumer onChange) {
        SeekBar s = new SeekBar(c);
        s.setMin(min);
        s.setMax(max);
        s.setProgress(value);
        s.setProgressTintList(ColorStateList.valueOf(ACCENT));
        s.setThumbTintList(ColorStateList.valueOf(ACCENT));
        s.setProgressBackgroundTintList(ColorStateList.valueOf(0x4DFFFFFF));
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) onChange.accept(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        return s;
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
        v.setBackgroundColor(0x1AFFFFFF);
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, dpi(c, 1))));
        return v;
    }
}
