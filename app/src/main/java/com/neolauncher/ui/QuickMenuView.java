package com.neolauncher.ui;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.text.TextPaint;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

import java.util.Calendar;
import java.util.Locale;

/**
 * Rychle menu Nea - vlastni obdoba systemoveho menu Questu (Control Center
 * z iOS/visionOS): hodiny, baterie, Wi-Fi, posuvniky jasu a hlasitosti,
 * dlazdice hlavnich funkci Questu a volne misto. Otevira se klepnutim na
 * hodiny/baterii (vyjede z nich) nebo tlacitkem menu na ovladaci.
 * <p>
 * Jas = Settings.System.SCREEN_BRIGHTNESS (potrebuje "Upravu systemovych
 * nastaveni", stejne jako QuestDim), hlasitost = AudioManager (bez opravneni).
 * Vse kreslene v jednom View, pohyb na pruzinach, stav jen z casu.
 */
public final class QuickMenuView extends View {

    public static final int T_WIFI = 0;
    public static final int T_BLUETOOTH = 1;
    public static final int T_QUEST_SETTINGS = 2;
    public static final int T_QUEST_QUICK = 3;
    public static final int T_FILES = 4;
    public static final int T_BROWSER = 5;
    public static final int T_CAMERA = 6;
    public static final int T_NEO = 7;

    public interface Actions {
        void openTarget(int target);

        void requestBrightnessAccess();
    }

    private static final String[] TILE_LABELS = {"Wi-Fi", "Bluetooth", "Nastavení", "Menu Questu",
            "Soubory", "Prohlížeč", "Fotoaparát", "Neo"};
    private static final int[] TILE_ICONS = {Icons.WIFI, Icons.BLUETOOTH, Icons.GEAR, Icons.SLIDERS,
            Icons.FOLDER, Icons.GLOBE, Icons.CAMERA, Icons.NEO};
    private static final float W_DP = 540f;
    private static final float H_DP = 506f;
    private static final float PAD = 24f;
    private static final float SLIDER_H = 54f;
    private static final float TILE_H = 86f;
    private static final float GAP = 12f;
    /** Postupny nastup prvku (kaskada jako v iOS), ms mezi prvky. */
    private static final float STAGGER_MS = 28f;

    private static final int EL_BRIGHT = 0;
    private static final int EL_VOLUME = 1;
    private static final int EL_TILE0 = 2;
    private static final int EL_COUNT = EL_TILE0 + TILE_LABELS.length;

    private final float d;
    private final Actions actions;
    private final AudioManager audio;
    private final RectF[] rects = new RectF[EL_COUNT];
    private final Spring[] hover = new Spring[EL_COUNT];
    private final Spring brightShown = new Spring(0, 0.22f, 1f, 0.001f);
    private final Spring volumeShown = new Spring(0, 0.22f, 1f, 0.001f);
    private float bright, volume;
    private boolean brightOk;
    private int volMax = 15;
    private int batteryLevel = -1;
    private boolean charging, fastCharging;
    private boolean wifiOn;
    private int wifiBars;
    private long freeBytes, totalBytes;
    private long shownNs;

    private int hovered = -1;
    private int pressed = -1;
    private boolean draggingSlider;
    private long lastWheelMs;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint timeText = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final TextPaint dateText = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final TextPaint labelText = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final TextPaint tileText = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final TextPaint smallText = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Path path = new Path();
    private final Path boltPath = new Path();
    private final RectF tmp = new RectF();
    private Shader highlight, glass;

    public QuickMenuView(Context c, Actions actions) {
        super(c);
        this.actions = actions;
        d = getResources().getDisplayMetrics().density;
        audio = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        setClickable(true);
        for (int i = 0; i < EL_COUNT; i++) {
            rects[i] = new RectF();
            hover[i] = new Spring(0, 0.30f, 0.78f, 0.002f);
        }
        Typeface semi = Typeface.create(Typeface.SANS_SERIF, 600, false);
        Typeface bold = Typeface.create(Typeface.SANS_SERIF, 700, false);
        timeText.setTypeface(bold);
        timeText.setTextSize(dp(38));
        timeText.setFontFeatureSettings("tnum");
        timeText.setColor(Color.WHITE);
        dateText.setTypeface(semi);
        dateText.setTextSize(dp(13.5f));
        dateText.setColor(0xB3FFFFFF);
        labelText.setTypeface(semi);
        labelText.setTextSize(dp(14));
        labelText.setFontFeatureSettings("tnum");
        tileText.setTypeface(semi);
        tileText.setTextSize(dp(12.5f));
        tileText.setTextAlign(Paint.Align.CENTER);
        smallText.setTypeface(semi);
        smallText.setTextSize(dp(12.5f));
        smallText.setFontFeatureSettings("tnum");
        stroke.setStyle(Paint.Style.STROKE);
        icon.setStrokeCap(Paint.Cap.ROUND);
        icon.setStrokeJoin(Paint.Join.ROUND);
        boltPath.moveTo(11, 2);
        boltPath.lineTo(4, 13);
        boltPath.rLineTo(5, 0);
        boltPath.rLineTo(-1.5f, 9);
        boltPath.lineTo(18, 11);
        boltPath.rLineTo(-5.5f, 0);
        boltPath.lineTo(14, 2);
        boltPath.close();
        refresh();
        final long now = System.nanoTime();
        brightShown.snap(bright);
        volumeShown.snap(volume);
        shownNs = now;
    }

    private float dp(float v) {
        return v * d;
    }

    /** Preferovana velikost panelu (px) - pro OverlayHost. */
    public static int preferredWidth(Context c) {
        return Glass.dpi(c, W_DP);
    }

    public void setBattery(int level, boolean isCharging, boolean isFast) {
        batteryLevel = level;
        charging = isCharging;
        fastCharging = isFast;
        invalidate();
    }

    /** Nacte aktualni stav systemu (jas, hlasitost, Wi-Fi, uloziste). */
    public void refresh() {
        final ContentResolver cr = getContext().getContentResolver();
        try {
            brightOk = Settings.System.canWrite(getContext());
        } catch (Exception e) {
            brightOk = false;
        }
        try {
            final int b = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128);
            bright = clamp((b - 10) / 245f, 0f, 1f);
        } catch (Exception e) {
            bright = 0.5f;
        }
        if (audio != null) {
            volMax = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) volMax;
        }
        wifiOn = false;
        wifiBars = 0;
        try {
            ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm != null ? cm.getActiveNetwork() : null;
            NetworkCapabilities nc = n != null ? cm.getNetworkCapabilities(n) : null;
            if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                wifiOn = true;
                final int rssi = nc.getSignalStrength();
                wifiBars = rssi == Integer.MIN_VALUE ? 3 : rssi >= -55 ? 4 : rssi >= -66 ? 3 : rssi >= -77 ? 2 : 1;
            }
        } catch (Exception ignored) {
        }
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            freeBytes = fs.getAvailableBytes();
            totalBytes = fs.getTotalBytes();
        } catch (Exception e) {
            freeBytes = totalBytes = 0;
        }
        final long now = System.nanoTime();
        if (!draggingSlider) {
            brightShown.set(bright, now);
            volumeShown.set(volume, now);
        }
        invalidate();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        // Navrat ze systemoveho nastaveni (povoleni jasu) nebo zmena hlasitosti tlacitky.
        if (hasWindowFocus) refresh();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int w = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED
                ? Math.round(dp(W_DP)) : MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(w, resolveSize(Math.round(dp(H_DP)), heightMeasureSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        final float pad = dp(PAD);
        float y = dp(108);
        rects[EL_BRIGHT].set(pad, y, w - pad, y + dp(SLIDER_H));
        y += dp(SLIDER_H) + dp(GAP);
        rects[EL_VOLUME].set(pad, y, w - pad, y + dp(SLIDER_H));
        y += dp(SLIDER_H) + dp(20);
        final float tw = (w - 2 * pad - 3 * dp(GAP)) / 4f;
        for (int i = 0; i < TILE_LABELS.length; i++) {
            final int col = i % 4, row = i / 4;
            final float l = pad + col * (tw + dp(GAP));
            final float t = y + row * (dp(TILE_H) + dp(GAP));
            rects[EL_TILE0 + i].set(l, t, l + tw, t + dp(TILE_H));
        }
        highlight = new LinearGradient(0, 0, 0, h * 0.5f, 0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
        glass = new LinearGradient(0, 0, 0, h, Glass.GLASS_TOP, Glass.GLASS_BOTTOM, Shader.TileMode.CLAMP);
    }

    // =========================================================================
    // Kresleni
    // =========================================================================

    /** Nastup prvku i: kriticky tlumena pruzina se zpozdenim (kaskada). */
    private float appear(int i, long now) {
        final float t = (now - shownNs) / 1e6f - i * STAGGER_MS;
        if (t <= 0f) return 0f;
        final float w = (float) (2 * Math.PI / 0.42) * t / 1000f;
        return 1f - (1f + w) * (float) Math.exp(-w);
    }

    @Override
    protected void onDraw(Canvas c) {
        final long now = System.nanoTime();
        final float w = getWidth(), h = getHeight();
        final float r = dp(28);
        // Matne sklo jako ostatni dialogy (visionOS): svetle sede, bily okraj, svetla horni hrana.
        fill.setShader(glass);
        fill.setColor(Color.WHITE);
        c.drawRoundRect(0, 0, w, h, r, r, fill);
        fill.setShader(highlight);
        fill.setColor(Color.WHITE);
        c.drawRoundRect(0, 0, w, h, r, r, fill);
        fill.setShader(null);
        stroke.setStrokeWidth(dp(1.5f));
        stroke.setColor(Glass.GLASS_STROKE);
        c.drawRoundRect(dp(0.75f), dp(0.75f), w - dp(0.75f), h - dp(0.75f), r, r, stroke);

        drawHeader(c, w);
        drawSlider(c, EL_BRIGHT, now, brightOk ? brightShown.get(now) : 0f,
                brightOk ? "Jas" : "Jas – klepni pro povolení", brightOk);
        drawSlider(c, EL_VOLUME, now, volumeShown.get(now), "Hlasitost", true);
        for (int i = 0; i < TILE_LABELS.length; i++) drawTile(c, i, now);
        drawStorage(c, w, h);

        boolean anim = brightShown.active(now) || volumeShown.active(now)
                || (now - shownNs) / 1e6f < EL_COUNT * STAGGER_MS + 900f;
        for (Spring s : hover) anim |= s.active(now);
        if (anim) postInvalidateOnAnimation();
    }

    private void drawHeader(Canvas c, float w) {
        final Calendar cal = Calendar.getInstance();
        final String time = String.format(Locale.ROOT, "%02d:%02d",
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
        final String[] days = {"neděle", "pondělí", "úterý", "středa", "čtvrtek", "pátek", "sobota"};
        final String[] months = {"ledna", "února", "března", "dubna", "května", "června", "července",
                "srpna", "září", "října", "listopadu", "prosince"};
        final String date = days[cal.get(Calendar.DAY_OF_WEEK) - 1] + " " + cal.get(Calendar.DAY_OF_MONTH)
                + ". " + months[cal.get(Calendar.MONTH)];
        final float pad = dp(PAD);
        c.drawText(time, pad, dp(62), timeText);
        c.drawText(date, pad, dp(86), dateText);

        // Vpravo: baterie a Wi-Fi jako pilulky (stejne jako stav v horni liste).
        final String pct = batteryLevel >= 0 ? batteryLevel + " %" : "–";
        final int bolts = charging ? (fastCharging ? 2 : 1) : 0;
        // Na svetlem skle bily text; barva nabiti je v tecce pred nim (zelena/modra/oranzova/cervena).
        labelText.setColor(0xF2FFFFFF);
        final float pw = labelText.measureText(pct);
        final float dot = dp(8);
        final float bw = dp(12) + dot + dp(8) + pw + (bolts > 0 ? dp(6) + bolts * dp(9) : 0) + dp(14);
        final float top = dp(28), ph = dp(32);
        tmp.set(w - pad - bw, top, w - pad, top + ph);
        drawPill(c, tmp);
        fill.setShader(null);
        fill.setColor(batteryColor());
        fill.setShadowLayer(dp(5), 0, 0, batteryColor());
        c.drawCircle(tmp.left + dp(12) + dot / 2f, tmp.centerY(), dot / 2f, fill);
        fill.clearShadowLayer();
        final Paint.FontMetrics fm = labelText.getFontMetrics();
        final float base = tmp.centerY() - (fm.ascent + fm.descent) / 2f;
        c.drawText(pct, tmp.left + dp(12) + dot + dp(8), base, labelText);
        float x = tmp.left + dp(12) + dot + dp(8) + pw + dp(6);
        if (bolts > 0) {
            fill.setShader(null);
            fill.setColor(Color.WHITE);
            for (int i = 0; i < bolts; i++) {
                c.save();
                c.translate(x, tmp.centerY() - dp(6));
                c.scale(dp(8) / 14f, dp(12) / 20f);
                c.translate(-4f, -2f);
                c.drawPath(boltPath, fill);
                c.restore();
                x += dp(9);
            }
        }
        // Wi-Fi: vysec s ukazatelem signalu.
        final String wl = wifiOn ? "Wi-Fi" : "Offline";
        labelText.setColor(wifiOn ? 0xF2FFFFFF : 0x99FFFFFF);
        final float ww = dp(12) + dp(18) + dp(8) + labelText.measureText(wl) + dp(14);
        final float right = tmp.left - dp(10);
        tmp.set(right - ww, top, right, top + ph);
        drawPill(c, tmp);
        drawWifiIcon(c, tmp.left + dp(12) + dp(9), tmp.centerY() + dp(5), dp(9), wifiOn ? wifiBars : 0);
        c.drawText(wl, tmp.left + dp(12) + dp(18) + dp(8), base, labelText);
    }

    private void drawPill(Canvas c, RectF r) {
        final float rr = r.height() / 2f;
        fill.setShader(null);
        fill.setColor(0x33000000);
        c.drawRoundRect(r.left, r.top + dp(2), r.right, r.bottom + dp(2), rr, rr, fill);
        fill.setColor(0x26FFFFFF);
        c.drawRoundRect(r, rr, rr, fill);
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(0x40FFFFFF);
        c.drawRoundRect(r, rr, rr, stroke);
    }

    private void drawSlider(Canvas c, int el, long now, float value, String label, boolean enabled) {
        final RectF r = rects[el];
        final float a = appear(el, now);
        if (a <= 0.004f) return;
        final float hv = clamp(hover[el].get(now), 0f, 1.2f);
        c.save();
        c.translate(0, (1f - a) * dp(12));
        final float grow = dp(2) * hv;
        tmp.set(r.left - grow, r.top - grow, r.right + grow, r.bottom + grow);
        final float rr = tmp.height() / 2f;
        final int alpha = Math.round(255 * clamp(a, 0f, 1f));
        // Stopa (sklo) + plochy stin.
        fill.setShader(null);
        fill.setColor(Color.argb(Math.round(0x33 * a), 0, 0, 0));
        c.drawRoundRect(tmp.left, tmp.top + dp(3), tmp.right, tmp.bottom + dp(3), rr, rr, fill);
        fill.setColor(Color.argb(Math.round((0x26 + 0x14 * hv) * a), 255, 255, 255));
        c.drawRoundRect(tmp, rr, rr, fill);
        // Vypln (bila jako v iOS) - zaoblena, oriznuta stopou.
        final float v = clamp(value, 0f, 1f);
        final float fw = Math.max(v > 0.004f ? tmp.height() : 0f, tmp.width() * v);
        if (fw > 0f) {
            c.save();
            path.reset();
            path.addRoundRect(tmp, rr, rr, Path.Direction.CW);
            c.clipPath(path);
            fill.setColor(Color.argb(Math.round(0xF2 * a), 255, 255, 255));
            fill.setShadowLayer(dp(12) * hv, 0, 0, Color.argb(Math.round(120 * hv * a), 56, 189, 248));
            c.drawRoundRect(tmp.left, tmp.top, tmp.left + fw, tmp.bottom, rr, rr, fill);
            fill.clearShadowLayer();
            c.restore();
        }
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(enabled ? Color.argb(Math.round((0x2E + 0x40 * hv) * a), 255, 255, 255)
                : Color.argb(Math.round(0xB0 * a), 56, 189, 248));
        c.drawRoundRect(tmp, rr, rr, stroke);

        // Ikona vlevo - tmava, kdyz je pod ni bila vypln.
        final float ix = tmp.left + dp(28), iy = tmp.centerY();
        final boolean onFill = fw > dp(44);
        final int ic = onFill ? Color.argb(alpha, 16, 21, 31) : Color.argb(alpha, 255, 255, 255);
        Icons.draw(c, el == EL_BRIGHT ? Icons.SUN : Icons.SPEAKER, ix, iy, dp(20), ic, dp(2), v, icon);
        final Paint.FontMetrics fm = labelText.getFontMetrics();
        final float base = iy - (fm.ascent + fm.descent) / 2f;
        final boolean labelOnFill = fw > dp(56) + labelText.measureText(label);
        labelText.setColor(labelOnFill ? Color.argb(alpha, 16, 21, 31) : Color.argb(alpha, 255, 255, 255));
        c.drawText(label, tmp.left + dp(52), base, labelText);
        if (enabled) {
            final String pct = Math.round(v * 100) + " %";
            final float pw = labelText.measureText(pct);
            final boolean pctOnFill = fw > tmp.width() - dp(18);
            labelText.setColor(pctOnFill ? Color.argb(alpha, 16, 21, 31) : Color.argb(Math.round(alpha * 0.8f), 255, 255, 255));
            c.drawText(pct, tmp.right - dp(18) - pw, base, labelText);
        }
        c.restore();
    }

    private void drawTile(Canvas c, int i, long now) {
        final int el = EL_TILE0 + i;
        final RectF r = rects[el];
        final float a = appear(el, now);
        if (a <= 0.004f) return;
        final float hv = hover[el].get(now);
        final float s = 1f + 0.05f * hv - (pressed == el ? 0.03f : 0f);
        c.save();
        c.translate(r.centerX(), r.centerY() + (1f - a) * dp(14));
        c.scale(s, s);
        final float w2 = r.width() / 2f, h2 = r.height() / 2f, rr = dp(20);
        final float hc = clamp(hv, 0f, 1f);
        fill.setShader(null);
        fill.setColor(Color.argb(Math.round(0x33 * a), 0, 0, 0));
        c.drawRoundRect(-w2, -h2 + dp(3), w2, h2 + dp(3), rr, rr, fill);
        fill.setColor(Color.argb(Math.round((0x1F + 0x22 * hc) * a), 255, 255, 255));
        if (hc > 0.01f) fill.setShadowLayer(dp(14) * hc, 0, 0, Color.argb(Math.round(110 * hc * a), 56, 189, 248));
        c.drawRoundRect(-w2, -h2, w2, h2, rr, rr, fill);
        fill.clearShadowLayer();
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(Color.argb(Math.round((0x24 + 0x50 * hc) * a), 255, 255, 255));
        c.drawRoundRect(-w2, -h2, w2, h2, rr, rr, stroke);
        final int col = Color.argb(Math.round(255 * clamp(a, 0f, 1f)), 255, 255, 255);
        Icons.draw(c, TILE_ICONS[i], 0, -dp(10), dp(26), col, dp(2), 1f, icon);
        tileText.setColor(Color.argb(Math.round(230 * clamp(a, 0f, 1f)), 255, 255, 255));
        c.drawText(TILE_LABELS[i], 0, h2 - dp(14), tileText);
        c.restore();
    }

    private void drawStorage(Canvas c, float w, float h) {
        if (totalBytes <= 0) return;
        final float pad = dp(PAD);
        final float y = h - dp(30);
        final String txt = "Úložiště: " + gb(freeBytes) + " volných z " + gb(totalBytes);
        smallText.setColor(0x99FFFFFF);
        c.drawText(txt, pad, y, smallText);
        final float bl = pad + smallText.measureText(txt) + dp(14), br = w - pad;
        if (br - bl < dp(40)) return;
        final float bh = dp(6), bt = y - dp(8);
        fill.setShader(null);
        fill.setColor(0x26FFFFFF);
        c.drawRoundRect(bl, bt, br, bt + bh, bh / 2f, bh / 2f, fill);
        final float used = 1f - freeBytes / (float) totalBytes;
        fill.setColor(used > 0.9f ? 0xFFF97316 : Glass.ACCENT);
        c.drawRoundRect(bl, bt, bl + (br - bl) * clamp(used, 0.02f, 1f), bt + bh, bh / 2f, bh / 2f, fill);
    }

    private static String gb(long bytes) {
        return Math.round(bytes / 1e9) + " GB";
    }

    private int batteryColor() {
        if (batteryLevel > 80) return 0xFF22C55E;
        if (batteryLevel > 50) return 0xFF3B82F6;
        if (batteryLevel > 20) return 0xFFF97316;
        if (batteryLevel >= 0) return 0xFFEF4444;
        return 0xFFFFFFFF;
    }

    // --- Ikony (vektorove, at jsou ostre v jakekoliv velikosti) ------------------

    private void strokeIcon(int color, float width) {
        icon.setShader(null);
        icon.setStyle(Paint.Style.STROKE);
        icon.setStrokeWidth(width);
        icon.setColor(color);
    }



    private void drawWifiIcon(Canvas c, float x, float y, float s, int bars) {
        strokeIcon(0, dp(2));
        for (int i = 1; i <= 3; i++) {
            icon.setColor(bars > i ? 0xF2FFFFFF : 0x4DFFFFFF);
            final float rr = s * i / 3f * 1.35f;
            c.drawArc(x - rr, y - rr, x + rr, y + rr, 225, 90, false, icon);
        }
        icon.setStyle(Paint.Style.FILL);
        icon.setColor(bars > 0 ? 0xF2FFFFFF : 0x4DFFFFFF);
        c.drawCircle(x, y - dp(1), dp(1.8f), icon);
    }


    // =========================================================================
    // Vstup
    // =========================================================================

    private int elementAt(float x, float y) {
        for (int i = 0; i < EL_COUNT; i++) {
            tmp.set(rects[i]);
            tmp.inset(-dp(3), -dp(3));
            if (tmp.contains(x, y)) return i;
        }
        return -1;
    }

    private void setHovered(int el) {
        if (el == hovered) return;
        final long now = System.nanoTime();
        if (hovered >= 0) hover[hovered].set(0f, now);
        hovered = el;
        if (el >= 0) hover[el].set(1f, now);
        invalidate();
    }

    @Override
    public boolean onHoverEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_HOVER_ENTER:
            case MotionEvent.ACTION_HOVER_MOVE:
                setHovered(elementAt(e.getX(), e.getY()));
                break;
            case MotionEvent.ACTION_HOVER_EXIT:
                setHovered(-1);
                break;
            default:
                break;
        }
        return true;
    }

    /** Joystick nad posuvnikem = jemne pridavani/ubirani (5 % na krok). */
    @Override
    public boolean onGenericMotionEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_SCROLL
                && (e.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0) {
            final int el = elementAt(e.getX(), e.getY());
            if (el != EL_BRIGHT && el != EL_VOLUME) return true;
            final float hs = e.getAxisValue(MotionEvent.AXIS_HSCROLL);
            final float vs = e.getAxisValue(MotionEvent.AXIS_VSCROLL);
            final float v = Math.abs(hs) >= Math.abs(vs) ? hs : vs;
            final long now = System.currentTimeMillis();
            if (Math.abs(v) < 0.2f || now - lastWheelMs < 70) return true;
            lastWheelMs = now;
            if (el == EL_BRIGHT) setBrightness(bright + Math.signum(v) * 0.05f);
            else setVolume(volume + Math.signum(v) / Math.max(volMax, 1));
            return true;
        }
        return super.onGenericMotionEvent(e);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        final float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = elementAt(x, y);
                setHovered(pressed);
                if ((pressed == EL_BRIGHT && brightOk) || pressed == EL_VOLUME) {
                    draggingSlider = true;
                    sliderTo(pressed, x);
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (draggingSlider) sliderTo(pressed, x);
                else setHovered(elementAt(x, y));
                return true;
            case MotionEvent.ACTION_UP: {
                final int el = elementAt(x, y);
                if (draggingSlider) {
                    sliderTo(pressed, x);
                } else if (el == pressed && el >= 0) {
                    if (el == EL_BRIGHT && !brightOk) actions.requestBrightnessAccess();
                    else if (el >= EL_TILE0) actions.openTarget(el - EL_TILE0);
                }
                draggingSlider = false;
                pressed = -1;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                draggingSlider = false;
                pressed = -1;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private void sliderTo(int el, float x) {
        final RectF r = rects[el];
        final float f = clamp((x - r.left) / r.width(), 0f, 1f);
        if (el == EL_BRIGHT) setBrightness(f);
        else setVolume(f);
    }

    private void setBrightness(float f) {
        if (!brightOk) return;
        bright = clamp(f, 0f, 1f);
        try {
            final ContentResolver cr = getContext().getContentResolver();
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, Math.round(10 + bright * 245));
        } catch (Exception e) {
            brightOk = false;
        }
        brightShown.set(bright, System.nanoTime());
        invalidate();
    }

    private void setVolume(float f) {
        if (audio == null) return;
        final int steps = Math.round(clamp(f, 0f, 1f) * volMax);
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, steps, 0);
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) volMax;
        } catch (Exception e) {
            volume = steps / (float) volMax;
        }
        volumeShown.set(volume, System.nanoTime());
        invalidate();
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
