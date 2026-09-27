package com.neolauncher.ui;

import android.content.Context;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Vrstva nad launcherem pro dialogy (nastaveni, menu aplikace, prejmenovani).
 * Klepnuti mimo panel dialog zavre. Stav je jen "otevreno/zavreno" -
 * zavirani je okamzite, nespoleha na konec animace.
 */
public final class OverlayHost extends FrameLayout {

    public interface Listener {
        void onOverlayShown();

        void onOverlayClosed();
    }

    private View panel;
    private Listener listener;

    public OverlayHost(Context c) {
        super(c);
        setVisibility(GONE);
        setBackgroundColor(0x33000000);
        setClickable(true);
        setOnClickListener(v -> close());
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public boolean isOpen() {
        return panel != null;
    }

    /**
     * @param content obsah dialogu (musi byt clickable, aby klik dovnitr nezaviral)
     * @param anchor  obdelnik karty, u ktere se ma dialog objevit, nebo null = na stred
     * @param area    oblast skleneneho panelu, ze ktere dialog nesmi vylezt
     * @param maxW    maximalni sirka dialogu v px
     */
    public void show(View content, RectF anchor, RectF area, int maxW) {
        final boolean wasOpen = panel != null;
        removeAllViews();
        panel = content;
        content.setClickable(true);

        final int margin = Glass.dpi(getContext(), 16);
        final int availW = Math.max(1, Math.round(area.width()) - 2 * margin);
        final int availH = Math.max(1, Math.round(area.height()) - 2 * margin);
        final int w = Math.min(maxW, availW);
        content.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(availH, MeasureSpec.AT_MOST));
        final int h = Math.min(content.getMeasuredHeight(), availH);

        float left, top;
        if (anchor == null) {
            left = area.centerX() - w / 2f;
            top = area.centerY() - h / 2f;
        } else {
            left = anchor.centerX() - w / 2f;
            top = anchor.bottom + Glass.dp(getContext(), 8);
            if (top + h > area.bottom - margin) top = anchor.top - h - Glass.dp(getContext(), 8);
        }
        left = Math.max(area.left + margin, Math.min(left, area.right - margin - w));
        top = Math.max(area.top + margin, Math.min(top, area.bottom - margin - h));

        LayoutParams lp = new LayoutParams(w, h, Gravity.TOP | Gravity.START);
        lp.leftMargin = Math.round(left);
        lp.topMargin = Math.round(top);
        addView(content, lp);

        setVisibility(VISIBLE);
        content.setAlpha(0f);
        content.setScaleX(0.96f);
        content.setScaleY(0.96f);
        content.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(170)
                .setInterpolator(Eased.NEO).start();
        if (!wasOpen && listener != null) listener.onOverlayShown();
    }

    public void close() {
        if (panel == null) return;
        panel.animate().cancel();
        removeAllViews();
        panel = null;
        setVisibility(GONE);
        if (listener != null) listener.onOverlayClosed();
    }
}
