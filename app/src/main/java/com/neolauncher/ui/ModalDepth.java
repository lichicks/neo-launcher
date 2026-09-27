package com.neolauncher.ui;

import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.view.View;

/**
 * Kdyz se otevre dialog, launcher za nim "ustoupi do hloubky" (jako ve
 * visionOS): plynule se rozmaze a o kousek zmensi. Jeden efekt na cely View
 * (ne na karty), rizeny pruzinou - stav jen z casu.
 */
final class ModalDepth {
    private final View view;
    private final float d;
    private final Spring amount = new Spring(0, 0.40f, 1f, 0.002f);
    private final Runnable frame = this::apply;
    private boolean posted;

    ModalDepth(View view, float density) {
        this.view = view;
        this.d = density;
    }

    void set(boolean on) {
        amount.set(on ? 1f : 0f, System.nanoTime());
        apply();
    }

    private void apply() {
        posted = false;
        final long now = System.nanoTime();
        final float m = Math.max(0f, Math.min(1f, amount.get(now)));
        // Jen lehke rozmazani - silne "zakalilo" celou scenu (zpetna vazba uzivatele).
        final float r = 6f * d * m;
        view.setRenderEffect(r > 0.5f ? RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP) : null);
        final float s = 1f - 0.035f * m;
        view.setScaleX(s);
        view.setScaleY(s);
        view.setAlpha(1f - 0.08f * m);
        if (amount.active(now) && !posted) {
            posted = true;
            view.postOnAnimation(frame);
        }
    }
}
