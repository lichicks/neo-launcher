package com.neolauncher.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * Jednoduche linkove ikony kreslene kodem (jako SF Symbols ve visionOS) -
 * ostre v jakekoliv velikosti, bez obrazku v resources. Sdili je rychle
 * menu i nastaveni.
 */
public final class Icons {
    public static final int SUN = 0;
    public static final int SPEAKER = 1;
    public static final int WIFI = 2;
    public static final int BLUETOOTH = 3;
    public static final int GEAR = 4;
    public static final int SLIDERS = 5;
    public static final int FOLDER = 6;
    public static final int GLOBE = 7;
    public static final int CAMERA = 8;
    public static final int NEO = 9;
    public static final int APERTURE = 10;
    public static final int GLASS = 11;
    public static final int COLUMNS = 12;
    public static final int SPARKLE = 13;
    public static final int EXIT = 14;
    public static final int LAYERS = 15;
    public static final int CAROUSEL = 16;
    public static final int SORT = 17;
    public static final int HOLD = 18;
    public static final int CLOUD = 19;
    public static final int REFRESH = 20;
    public static final int EYE_OFF = 21;
    public static final int CHART = 22;
    public static final int HEADSET = 23;
    public static final int META = 24;
    public static final int UPDATE = 25;
    public static final int INFO = 26;
    public static final int FLASK = 27;
    public static final int CLOSE = 28;
    public static final int BLUR = 29;
    public static final int PALETTE = 30;

    private static final Path path = new Path();

    private Icons() {}

    /**
     * @param size  velikost ikony (px), ikona se vejde do ctverce size x size
     * @param level jen pro SPEAKER (0..1 = kolik "vln")
     */
    public static void draw(Canvas c, int id, float x, float y, float size, int color, float stroke,
                            float level, Paint p) {
        final float s = size / 2f;
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(stroke);
        p.setColor(color);
        switch (id) {
            case SUN:
                c.drawCircle(x, y, s * 0.42f, p);
                for (int i = 0; i < 8; i++) {
                    final double an = i * Math.PI / 4;
                    final float cs = (float) Math.cos(an), sn = (float) Math.sin(an);
                    c.drawLine(x + cs * s * 0.7f, y + sn * s * 0.7f, x + cs * s * 0.95f, y + sn * s * 0.95f, p);
                }
                break;
            case SPEAKER:
                p.setStyle(Paint.Style.FILL);
                path.reset();
                path.moveTo(x - s * 0.9f, y - s * 0.32f);
                path.lineTo(x - s * 0.5f, y - s * 0.32f);
                path.lineTo(x - s * 0.05f, y - s * 0.75f);
                path.lineTo(x - s * 0.05f, y + s * 0.75f);
                path.lineTo(x - s * 0.5f, y + s * 0.32f);
                path.lineTo(x - s * 0.9f, y + s * 0.32f);
                path.close();
                c.drawPath(path, p);
                p.setStyle(Paint.Style.STROKE);
                if (level > 0.01f) c.drawArc(x - s * 0.1f, y - s * 0.45f, x + s * 0.6f, y + s * 0.45f, -50, 100, false, p);
                if (level > 0.5f) c.drawArc(x - s * 0.3f, y - s * 0.85f, x + s * 1.0f, y + s * 0.85f, -50, 100, false, p);
                break;
            case WIFI:
                for (int k = 1; k <= 3; k++) {
                    final float rr = s * k / 3f * 1.25f;
                    c.drawArc(x - rr, y + s * 0.5f - rr, x + rr, y + s * 0.5f + rr, 225, 90, false, p);
                }
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(x, y + s * 0.42f, stroke, p);
                break;
            case BLUETOOTH:
                path.reset();
                path.moveTo(x - s * 0.45f, y - s * 0.42f);
                path.lineTo(x + s * 0.45f, y + s * 0.42f);
                path.lineTo(x, y + s * 0.9f);
                path.lineTo(x, y - s * 0.9f);
                path.lineTo(x + s * 0.45f, y - s * 0.42f);
                path.lineTo(x - s * 0.45f, y + s * 0.42f);
                c.drawPath(path, p);
                break;
            case GEAR:
                c.drawCircle(x, y, s * 0.3f, p);
                c.drawCircle(x, y, s * 0.64f, p);
                for (int k = 0; k < 8; k++) {
                    final double an = k * Math.PI / 4;
                    final float cs = (float) Math.cos(an), sn = (float) Math.sin(an);
                    c.drawLine(x + cs * s * 0.64f, y + sn * s * 0.64f, x + cs * s * 0.9f, y + sn * s * 0.9f, p);
                }
                break;
            case SLIDERS:
                for (int k = -1; k <= 1; k++) c.drawLine(x - s * 0.85f, y + k * s * 0.55f, x + s * 0.85f, y + k * s * 0.55f, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(x - s * 0.35f, y - s * 0.55f, s * 0.22f, p);
                c.drawCircle(x + s * 0.4f, y, s * 0.22f, p);
                c.drawCircle(x - s * 0.1f, y + s * 0.55f, s * 0.22f, p);
                break;
            case FOLDER:
                path.reset();
                path.moveTo(x - s * 0.9f, y - s * 0.6f);
                path.lineTo(x - s * 0.25f, y - s * 0.6f);
                path.lineTo(x, y - s * 0.35f);
                path.lineTo(x + s * 0.9f, y - s * 0.35f);
                path.lineTo(x + s * 0.9f, y + s * 0.65f);
                path.lineTo(x - s * 0.9f, y + s * 0.65f);
                path.close();
                c.drawPath(path, p);
                break;
            case GLOBE:
                c.drawCircle(x, y, s * 0.85f, p);
                c.drawOval(x - s * 0.38f, y - s * 0.85f, x + s * 0.38f, y + s * 0.85f, p);
                c.drawLine(x - s * 0.85f, y, x + s * 0.85f, y, p);
                break;
            case CAMERA:
                c.drawRoundRect(x - s * 0.9f, y - s * 0.5f, x + s * 0.9f, y + s * 0.7f, s * 0.25f, s * 0.25f, p);
                c.drawCircle(x, y + s * 0.1f, s * 0.33f, p);
                c.drawLine(x - s * 0.3f, y - s * 0.5f, x - s * 0.15f, y - s * 0.75f, p);
                c.drawLine(x - s * 0.15f, y - s * 0.75f, x + s * 0.15f, y - s * 0.75f, p);
                c.drawLine(x + s * 0.15f, y - s * 0.75f, x + s * 0.3f, y - s * 0.5f, p);
                break;
            case NEO:
                p.setStyle(Paint.Style.FILL);
                final int col = p.getColor();
                p.setColor(0xFF000000);
                c.drawRoundRect(x - s * 0.85f, y - s * 0.85f, x + s * 0.85f, y + s * 0.85f, s * 0.42f, s * 0.42f, p);
                p.setColor(col);
                c.drawCircle(x, y, s * 0.36f, p);
                break;
            case APERTURE:
                c.drawCircle(x, y, s * 0.85f, p);
                for (int k = 0; k < 6; k++) {
                    final double a0 = k * Math.PI / 3, a1 = a0 + Math.PI / 2.2;
                    c.drawLine(x + (float) Math.cos(a0) * s * 0.3f, y + (float) Math.sin(a0) * s * 0.3f,
                            x + (float) Math.cos(a1) * s * 0.85f, y + (float) Math.sin(a1) * s * 0.85f, p);
                }
                break;
            case GLASS:
                c.drawRoundRect(x - s * 0.8f, y - s * 0.6f, x + s * 0.6f, y + s * 0.6f, s * 0.25f, s * 0.25f, p);
                c.drawRoundRect(x - s * 0.5f, y - s * 0.3f, x + s * 0.9f, y + s * 0.85f, s * 0.25f, s * 0.25f, p);
                break;
            case COLUMNS:
                for (int k = 0; k < 2; k++) {
                    for (int j = 0; j < 2; j++) {
                        final float l = x - s * 0.85f + j * s * 0.95f, t = y - s * 0.85f + k * s * 0.95f;
                        c.drawRoundRect(l, t, l + s * 0.75f, t + s * 0.75f, s * 0.18f, s * 0.18f, p);
                    }
                }
                break;
            case SPARKLE:
                path.reset();
                path.moveTo(x, y - s * 0.9f);
                path.quadTo(x + s * 0.1f, y - s * 0.1f, x + s * 0.9f, y);
                path.quadTo(x + s * 0.1f, y + s * 0.1f, x, y + s * 0.9f);
                path.quadTo(x - s * 0.1f, y + s * 0.1f, x - s * 0.9f, y);
                path.quadTo(x - s * 0.1f, y - s * 0.1f, x, y - s * 0.9f);
                c.drawPath(path, p);
                break;
            case EXIT:
                c.drawRoundRect(x - s * 0.85f, y - s * 0.8f, x + s * 0.2f, y + s * 0.8f, s * 0.2f, s * 0.2f, p);
                c.drawLine(x - s * 0.2f, y, x + s * 0.9f, y, p);
                c.drawLine(x + s * 0.55f, y - s * 0.35f, x + s * 0.9f, y, p);
                c.drawLine(x + s * 0.55f, y + s * 0.35f, x + s * 0.9f, y, p);
                break;
            case LAYERS:
                path.reset();
                path.moveTo(x, y - s * 0.8f);
                path.lineTo(x + s * 0.9f, y - s * 0.3f);
                path.lineTo(x, y + s * 0.2f);
                path.lineTo(x - s * 0.9f, y - s * 0.3f);
                path.close();
                c.drawPath(path, p);
                path.reset();
                path.moveTo(x - s * 0.9f, y + s * 0.2f);
                path.lineTo(x, y + s * 0.7f);
                path.lineTo(x + s * 0.9f, y + s * 0.2f);
                c.drawPath(path, p);
                break;
            case CAROUSEL:
                c.drawRoundRect(x - s * 0.45f, y - s * 0.6f, x + s * 0.45f, y + s * 0.6f, s * 0.15f, s * 0.15f, p);
                c.drawLine(x - s * 0.75f, y - s * 0.4f, x - s * 0.75f, y + s * 0.4f, p);
                c.drawLine(x + s * 0.75f, y - s * 0.4f, x + s * 0.75f, y + s * 0.4f, p);
                break;
            case SORT:
                c.drawLine(x - s * 0.8f, y - s * 0.55f, x + s * 0.8f, y - s * 0.55f, p);
                c.drawLine(x - s * 0.8f, y, x + s * 0.35f, y, p);
                c.drawLine(x - s * 0.8f, y + s * 0.55f, x - s * 0.1f, y + s * 0.55f, p);
                break;
            case HOLD:
                c.drawCircle(x, y, s * 0.85f, p);
                c.drawLine(x, y, x, y - s * 0.5f, p);
                c.drawLine(x, y, x + s * 0.35f, y + s * 0.2f, p);
                break;
            case CLOUD:
                path.reset();
                path.moveTo(x - s * 0.55f, y + s * 0.45f);
                path.cubicTo(x - s * 1.05f, y + s * 0.45f, x - s * 1.0f, y - s * 0.3f, x - s * 0.45f, y - s * 0.2f);
                path.cubicTo(x - s * 0.35f, y - s * 0.8f, x + s * 0.5f, y - s * 0.8f, x + s * 0.5f, y - s * 0.15f);
                path.cubicTo(x + s * 1.05f, y - s * 0.15f, x + s * 1.0f, y + s * 0.45f, x + s * 0.5f, y + s * 0.45f);
                c.drawPath(path, p);
                c.drawLine(x, y - s * 0.1f, x, y + s * 0.8f, p);
                c.drawLine(x - s * 0.25f, y + s * 0.55f, x, y + s * 0.8f, p);
                c.drawLine(x + s * 0.25f, y + s * 0.55f, x, y + s * 0.8f, p);
                break;
            case REFRESH:
                c.drawArc(x - s * 0.75f, y - s * 0.75f, x + s * 0.75f, y + s * 0.75f, -60, 290, false, p);
                c.drawLine(x + s * 0.4f, y - s * 0.65f, x + s * 0.4f, y - s * 0.95f, p);
                c.drawLine(x + s * 0.4f, y - s * 0.65f, x + s * 0.72f, y - s * 0.6f, p);
                break;
            case EYE_OFF:
                path.reset();
                path.moveTo(x - s * 0.9f, y);
                path.quadTo(x, y - s * 0.9f, x + s * 0.9f, y);
                path.quadTo(x, y + s * 0.9f, x - s * 0.9f, y);
                c.drawPath(path, p);
                c.drawCircle(x, y, s * 0.25f, p);
                c.drawLine(x - s * 0.8f, y - s * 0.8f, x + s * 0.8f, y + s * 0.8f, p);
                break;
            case CHART:
                c.drawLine(x - s * 0.8f, y + s * 0.8f, x + s * 0.85f, y + s * 0.8f, p);
                c.drawLine(x - s * 0.5f, y + s * 0.5f, x - s * 0.5f, y + s * 0.1f, p);
                c.drawLine(x, y + s * 0.5f, x, y - s * 0.6f, p);
                c.drawLine(x + s * 0.5f, y + s * 0.5f, x + s * 0.5f, y - s * 0.2f, p);
                break;
            case HEADSET:
                c.drawRoundRect(x - s * 0.9f, y - s * 0.45f, x + s * 0.9f, y + s * 0.5f, s * 0.35f, s * 0.35f, p);
                c.drawCircle(x - s * 0.4f, y + s * 0.02f, s * 0.18f, p);
                c.drawCircle(x + s * 0.4f, y + s * 0.02f, s * 0.18f, p);
                break;
            case META:
                path.reset();
                path.moveTo(x, y);
                path.cubicTo(x - s * 0.3f, y - s * 0.7f, x - s * 0.95f, y - s * 0.5f, x - s * 0.9f, y + s * 0.15f);
                path.cubicTo(x - s * 0.85f, y + s * 0.6f, x - s * 0.3f, y + s * 0.4f, x, y);
                path.cubicTo(x + s * 0.3f, y - s * 0.7f, x + s * 0.95f, y - s * 0.5f, x + s * 0.9f, y + s * 0.15f);
                path.cubicTo(x + s * 0.85f, y + s * 0.6f, x + s * 0.3f, y + s * 0.4f, x, y);
                c.drawPath(path, p);
                break;
            case UPDATE:
                c.drawCircle(x, y, s * 0.85f, p);
                c.drawLine(x, y + s * 0.45f, x, y - s * 0.45f, p);
                c.drawLine(x - s * 0.35f, y - s * 0.1f, x, y - s * 0.45f, p);
                c.drawLine(x + s * 0.35f, y - s * 0.1f, x, y - s * 0.45f, p);
                break;
            case INFO:
                c.drawCircle(x, y, s * 0.85f, p);
                c.drawLine(x, y - s * 0.05f, x, y + s * 0.45f, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(x, y - s * 0.38f, stroke * 0.7f, p);
                break;
            case FLASK:
                path.reset();
                path.moveTo(x - s * 0.3f, y - s * 0.85f);
                path.lineTo(x - s * 0.3f, y - s * 0.25f);
                path.lineTo(x - s * 0.8f, y + s * 0.65f);
                path.quadTo(x - s * 0.85f, y + s * 0.85f, x - s * 0.6f, y + s * 0.85f);
                path.lineTo(x + s * 0.6f, y + s * 0.85f);
                path.quadTo(x + s * 0.85f, y + s * 0.85f, x + s * 0.8f, y + s * 0.65f);
                path.lineTo(x + s * 0.3f, y - s * 0.25f);
                path.lineTo(x + s * 0.3f, y - s * 0.85f);
                c.drawPath(path, p);
                c.drawLine(x - s * 0.45f, y - s * 0.85f, x + s * 0.45f, y - s * 0.85f, p);
                break;
            case CLOSE:
                c.drawLine(x - s * 0.5f, y - s * 0.5f, x + s * 0.5f, y + s * 0.5f, p);
                c.drawLine(x + s * 0.5f, y - s * 0.5f, x - s * 0.5f, y + s * 0.5f, p);
                break;
            case BLUR:
                c.drawCircle(x, y, s * 0.3f, p);
                p.setAlpha(Math.round(p.getAlpha() * 0.6f));
                c.drawCircle(x, y, s * 0.58f, p);
                p.setAlpha(Math.round(p.getAlpha() * 0.6f));
                c.drawCircle(x, y, s * 0.86f, p);
                break;
            default: // PALETTE
                c.drawCircle(x, y, s * 0.85f, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(x - s * 0.35f, y - s * 0.2f, s * 0.13f, p);
                c.drawCircle(x + s * 0.05f, y - s * 0.45f, s * 0.13f, p);
                c.drawCircle(x + s * 0.4f, y - s * 0.1f, s * 0.13f, p);
                break;
        }
        p.setStyle(Paint.Style.FILL);
    }
}
