package com.neolauncher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.neolauncher.art.Placeholders;
import com.neolauncher.data.AppEntry;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Hledani her a aplikaci (lupa v leve liste). Pise se klavesnici Questu,
 * vysledky se ukazuji hned pri psani (bez ohledu na diakritiku), Enter spusti
 * prvni. Prazdne pole = naposledy spustene.
 */
public final class SearchSheet {
    private SearchSheet() {}

    private static final int MAX_RESULTS = 12;

    /** Data pro hledani (dodava aktivita). */
    public interface Source {
        List<AppEntry> apps();

        String label(AppEntry e);

        long lastUsed(String pkg);

        Bitmap art(AppEntry e);
    }

    public static View build(Context c, Source src, Consumer<AppEntry> onLaunch, Runnable onClose) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Glass.panel(c, 28));
        final int pad = Glass.dpi(c, 20);
        root.setPadding(pad, Glass.dpi(c, 18), pad, pad);
        root.setMinimumHeight(Glass.dpi(c, 470));

        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Glass.text(c, "Hledat", 24, Color.WHITE, true),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        GlassWidgets.IconView close = new GlassWidgets.IconView(c, Icons.CLOSE, 38, Color.WHITE);
        close.setClickable(true);
        close.setOnClickListener(v -> onClose.run());
        header.addView(close);
        root.addView(header);

        // Pole: lupa + text (klavesnice Questu).
        LinearLayout field = new LinearLayout(c);
        field.setOrientation(LinearLayout.HORIZONTAL);
        field.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable fb = new GradientDrawable();
        fb.setColor(0x1FFFFFFF);
        fb.setCornerRadius(Glass.dp(c, 999));
        fb.setStroke(Glass.dpi(c, 1), 0x4DFFFFFF);
        field.setBackground(fb);
        field.setPadding(Glass.dpi(c, 8), Glass.dpi(c, 4), Glass.dpi(c, 16), Glass.dpi(c, 4));
        field.addView(new GlassWidgets.IconView(c, Icons.SEARCH, 38, Color.WHITE));
        final EditText edit = new EditText(c);
        edit.setSingleLine(true);
        edit.setHint("Název hry nebo aplikace");
        edit.setTextColor(Color.WHITE);
        edit.setHintTextColor(0x80FFFFFF);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        edit.setBackground(null);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        edit.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        edit.setPadding(Glass.dpi(c, 10), Glass.dpi(c, 8), 0, Glass.dpi(c, 8));
        field.addView(edit, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        flp.topMargin = Glass.dpi(c, 14);
        root.addView(field, flp);

        final TextView section = Glass.text(c, "", 12, 0x99FFFFFF, true);
        section.setLetterSpacing(0.08f);
        section.setPadding(Glass.dpi(c, 4), Glass.dpi(c, 16), 0, Glass.dpi(c, 8));
        root.addView(section);

        ScrollView scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        final LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        final List<AppEntry> current = new ArrayList<>();
        final Runnable refresh = () -> {
            final String q = norm(edit.getText().toString().trim());
            current.clear();
            current.addAll(q.isEmpty() ? recent(src) : search(src, q));
            section.setText((q.isEmpty() ? "NAPOSLEDY SPUŠTĚNÉ" : current.isEmpty() ? "NIC NENALEZENO" : "VÝSLEDKY"));
            fill(c, list, current, src, onLaunch);
        };
        edit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refresh.run();
            }
        });
        edit.setOnEditorActionListener((v, actionId, ev) -> {
            if (!current.isEmpty()) onLaunch.accept(current.get(0));
            return true;
        });
        refresh.run();
        edit.post(() -> {
            edit.requestFocus();
            InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT);
        });
        return root;
    }

    /** Vysledky ve dvou sloupcich: nahled banneru, nazev, typ. */
    private static void fill(Context c, LinearLayout list, List<AppEntry> apps, Source src,
                             Consumer<AppEntry> onLaunch) {
        list.removeAllViews();
        final int gap = Glass.dpi(c, 10);
        for (int i = 0; i < apps.size(); i += 2) {
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = 0; j < 2; j++) {
                View cell = i + j < apps.size() ? item(c, apps.get(i + j), src, onLaunch) : new View(c);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (j > 0) lp.leftMargin = gap;
                row.addView(cell, lp);
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) rlp.topMargin = gap;
            list.addView(row, rlp);
        }
    }

    private static View item(Context c, AppEntry e, Source src, Consumer<AppEntry> onLaunch) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Glass.tile(c));
        final int p = Glass.dpi(c, 8);
        row.setPadding(p, p, Glass.dpi(c, 14), p);
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> onLaunch.accept(e));

        ImageView img = new ImageView(c);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        final Bitmap art = src.art(e);
        if (art != null) {
            img.setImageBitmap(art);
        } else {
            int[] pair = Placeholders.colorsFor(e.pkg);
            img.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, pair));
        }
        final float radius = Glass.dp(c, 12);
        img.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        img.setClipToOutline(true);
        row.addView(img, new LinearLayout.LayoutParams(Glass.dpi(c, 80), Glass.dpi(c, 50)));

        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Glass.dpi(c, 12), 0, 0, 0);
        TextView t = Glass.text(c, src.label(e), 15, Color.WHITE, true);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(t);
        TextView s = Glass.text(c, e.isVr() ? "VR hra" : e.isSystemPanel() ? "Systém Questu" : "Aplikace",
                12.5f, 0xA6FFFFFF, false);
        texts.addView(s);
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private static List<AppEntry> recent(Source src) {
        List<AppEntry> all = new ArrayList<>(src.apps());
        all.removeIf(e -> src.lastUsed(e.pkg) <= 0);
        all.sort((a, b) -> Long.compare(src.lastUsed(b.pkg), src.lastUsed(a.pkg)));
        if (all.isEmpty()) {
            all.addAll(src.apps());
            all.sort((a, b) -> src.label(a).compareToIgnoreCase(src.label(b)));
        }
        return all.size() > 8 ? new ArrayList<>(all.subList(0, 8)) : all;
    }

    /** Shoda bez diakritiky: zacatek nazvu > zacatek slova > kdekoliv v nazvu > nazev balicku. */
    private static List<AppEntry> search(Source src, String q) {
        List<AppEntry> out = new ArrayList<>();
        final List<Integer> scores = new ArrayList<>();
        for (AppEntry e : src.apps()) {
            final String label = norm(src.label(e));
            int score;
            if (label.startsWith(q)) score = 0;
            else if (label.contains(" " + q)) score = 1;
            else if (label.contains(q)) score = 2;
            else if (e.pkg.toLowerCase(Locale.ROOT).contains(q)) score = 3;
            else continue;
            int at = 0;
            while (at < out.size() && (scores.get(at) < score
                    || (scores.get(at) == score && norm(src.label(out.get(at))).compareTo(label) <= 0))) at++;
            out.add(at, e);
            scores.add(at, score);
        }
        return out.size() > MAX_RESULTS ? new ArrayList<>(out.subList(0, MAX_RESULTS)) : out;
    }

    static String norm(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
