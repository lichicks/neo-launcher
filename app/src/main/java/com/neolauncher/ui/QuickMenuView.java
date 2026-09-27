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
import android.provider.Settings;
import android.text.TextPaint;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

import java.util.Calendar;
import java.util.Locale;

/**
 * Rychle menu Nea - vlastni obdoba systemoveho menu Questu (ovladaci
 * centrum): hodiny, baterie, Wi-Fi, posuvniky jasu a hlasitosti, dlazdice
 * hlavnich funkci Questu. Vyjede jako bocni panel vpravo po klepnuti na
 * hodiny/baterii nebo tlacitkem menu na ovladaci.
 * <p>
 * Rozmery jsou z tokenu v Glass (PAD, SECTION, GAP, R_TILE...), at mezery
 * a zaobleni sedi se vsemi ostatnimi okny.
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

        /** Klepnuti na kartu naposledy hrane hry. */
        void launch(com.neolauncher.data.AppEntry app);

        void requestBrightnessAccess();
    }

    private static final String[] TILE_LABELS = {"Wi-Fi", "Bluetooth", "Nastavení", "Menu Questu",
            "Soubory", "Prohlížeč", "Fotoaparát", "Neo"};
    private static final int[] TILE_ICONS = {Icons.WIFI, Icons.BLUETOOTH, Icons.GEAR, Icons.SLIDERS,
            Icons.FOLDER, Icons.GLOBE, Icons.CAMERA, Icons.NEO};
    private static final float W_DP = 400f;
    private static final float H_DP = 598f;
    /** Karta "Naposledy hrano": vyska a tlacitko uvnitr. */
    private static final float CARD_H = 68f;
    private static final float CARD_BTN_H = 36f;
    private static final float SLIDER_H = 44f;
    /**
     * Vrch cislic hodin: vic nez PAD - velke pismo u rohu s radiusem 40 by jinak
     * pusobilo namackane. Pod datem uz jen GAP ke karte.
     */
    private static final float HEADER_TOP = 30f;
    /** Pilulky baterie a Wi-Fi v hlavicce. */
    private static final float PILL_H = 32f;
    private static final float PILL_PAD = 12f;
    /** Dlazdice rostou do volneho mista, ale jen v rozumnych mezich. */
    private static final float TILE_MIN = 40f;
    private static final float TILE_MAX = 72f;
    /** Postupny nastup prvku (kaskada), ms mezi prvky. */
    private static final float STAGGER_MS = 28f;

    private static final int EL_BRIGHT = 0;
    private static final int EL_VOLUME = 1;
    private static final int EL_TILE0 = 2;
    /** Karta "Naposledy hrano". */
    private static final int EL_LAST = EL_TILE0 + TILE_LABELS.length;
    private static final int EL_COUNT = EL_LAST + 1;

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
    private long shownNs;
    /** Zakladni linky hlavicky (spocitane z metriky pisma, aby cislice zacinaly presne na okraji). */
    private float timeTop, digitH, timeBase, dateBase;
    private com.neolauncher.data.AppEntry lastApp;
    private String lastTitle, lastSub, lastButton;
    private android.graphics.BitmapShader lastShader;
    private android.graphics.Bitmap lastArt;
    private final android.graphics.Matrix lastMatrix = new android.graphics.Matrix();
    private final Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

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
    private final android.graphics.Rect textBounds = new android.graphics.Rect();
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

    /** Sirka bocniho panelu vpravo (dlazdice ve dvou sloupcich). */
    public static int sideWidth(Context c) {
        return Glass.dpi(c, W_DP);
    }

    /**
     * Naposledy hrana hra / aplikace - karta s tlacitkem "Hrat".
     * Klepnuti hru spusti znovu (bezi-li jeste na pozadi, Quest se do ni vrati).
     */
    public void setLastPlayed(com.neolauncher.data.AppEntry app, String title, String sub,
                              android.graphics.Bitmap art) {
        lastApp = app;
        lastTitle = title;
        lastSub = sub;
        lastButton = app != null && app.isVr() ? "Hrát" : "Otevřít";
        lastArt = art;
        lastShader = art != null ? new android.graphics.BitmapShader(art,
                Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) : null;
        if (getWidth() > 0) onSizeChanged(getWidth(), getHeight(), getWidth(), getHeight());
        invalidate();
    }

    public void setBattery(int level, boolean isCharging, boolean isFast) {
        batteryLevel = level;
        charging = isCharging;
        fastCharging = isFast;
        invalidate();
    }

    /** Nacte aktualni stav systemu (jas, hlasitost, Wi-Fi). */
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
        layout(w, h);
        highlight = new LinearGradient(0, 0, 0, h * 0.5f, Glass.GLASS_SHINE, 0x00FFFFFF, Shader.TileMode.CLAMP);
        glass = new LinearGradient(0, 0, 0, h, Glass.GLASS_TOP, Glass.GLASS_BOTTOM, Shader.TileMode.CLAMP);
    }

    /**
     * Svisle: HEADER_TOP | hodiny + datum | GAP | karta | GAP | jas | GAP | hlasitost
     * | GAP | dlazdice (GAP mezi nimi) | GAP | tlacitko | PAD. Vsude stejne mezery,
     * dlazdice si vezmou zbytek vysky.
     */
    private void layout(int w, int h) {
        final float pad = dp(Glass.PAD), gap = dp(Glass.GAP);
        // Hlavicka: hodiny s volnym mistem nad sebou, datum pod nimi, pak GAP ke karte.
        timeText.getTextBounds("0123456789", 0, 10, textBounds);
        timeTop = dp(HEADER_TOP);
        digitH = -textBounds.top;
        timeBase = timeTop + digitH;
        dateText.getTextBounds("0123456789", 0, 10, textBounds);
        dateBase = timeBase + dp(12) - textBounds.top;
        float y = dateBase + dp(4) + dp(Glass.GAP);
        if (lastApp != null) {
            rects[EL_LAST].set(pad, y, w - pad, y + dp(CARD_H));
            y += dp(CARD_H) + gap;
        } else {
            rects[EL_LAST].setEmpty();
        }
        final float sh = dp(SLIDER_H);
        rects[EL_BRIGHT].set(pad, y, w - pad, y + sh);
        y += sh + gap;
        rects[EL_VOLUME].set(pad, y, w - pad, y + sh);
        y += sh + gap;
        // Posledni "dlazdice" (Neo) = tlacitko "Nastaveni Nea" pres celou sirku dole.
        final float btnTop = h - pad - dp(Glass.BUTTON_H);
        rects[EL_TILE0 + T_NEO].set(pad, btnTop, w - pad, h - pad);
        final int rows = (T_NEO + 1) / 2;
        final float th = clamp((btnTop - gap - y - (rows - 1) * gap) / rows, dp(TILE_MIN), dp(TILE_MAX));
        final float tw = (w - 2 * pad - gap) / 2f;
        for (int i = 0; i < T_NEO; i++) {
            final int col = i % 2, row = i / 2;
            final float l = pad + col * (tw + gap);
            final float t = y + row * (th + gap);
            rects[EL_TILE0 + i].set(l, t, l + tw, t + th);
        }
    }

    // =========================================================================
    // Kresleni
    // =========================================================================

    /** Nastup prvku el: kriticky tlumena pruzina se zpozdenim (kaskada shora dolu). */
    private float appear(int el, long now) {
        final int order = el == EL_LAST ? 0 : el + 1;
        final float t = (now - shownNs) / 1e6f - order * STAGGER_MS;
        if (t <= 0f) return 0f;
        final float w = (float) (2 * Math.PI / 0.42) * t / 1000f;
        return 1f - (1f + w) * (float) Math.exp(-w);
    }

    @Override
    protected void onDraw(Canvas c) {
        final long now = System.nanoTime();
        final float w = getWidth(), h = getHeight();
        final float r = dp(Glass.R_PANEL);
        // Matne sklo jako ostatni dialogy: bily okraj, svetla horni hrana.
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
        if (lastApp != null) drawLastPlayed(c, now);

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
        final float pad = dp(Glass.PAD);
        c.drawText(time, pad, timeBase, timeText);
        c.drawText(date, pad, dateBase, dateText);

        // Vpravo nahore v jedne rade: Wi-Fi a baterie jako pilulky, svisle na stredu cislic hodin.
        final String pct = batteryLevel >= 0 ? batteryLevel + " %" : "–";
        final int bolts = charging ? (fastCharging ? 2 : 1) : 0;
        final float pp = dp(PILL_PAD), dot = dp(8), inner = dp(Glass.GAP_S);
        labelText.setColor(0xF2FFFFFF);
        final float pw = labelText.measureText(pct);
        final float bw = pp + dot + inner + pw + (bolts > 0 ? dp(6) + bolts * dp(9) - dp(1) : 0) + pp;
        final String wl = wifiOn ? "Wi-Fi" : "Offline";
        final float wifiIcon = dp(18);
        final float ww = pp + wifiIcon + inner + labelText.measureText(wl) + pp;
        final float ph = dp(PILL_H), top = timeTop + digitH / 2f - ph / 2f;
        final Paint.FontMetrics fm = labelText.getFontMetrics();
        final float base = top + ph / 2f - (fm.ascent + fm.descent) / 2f;

        // Baterie (barva nabiti je v tecce - na skle je bily text citelnejsi).
        tmp.set(w - pad - bw, top, w - pad, top + ph);
        final float batLeft = tmp.left;
        drawPill(c, tmp);
        fill.setShader(null);
        fill.setColor(batteryColor());
        fill.setShadowLayer(dp(5), 0, 0, batteryColor());
        c.drawCircle(tmp.left + pp + dot / 2f, tmp.centerY(), dot / 2f, fill);
        fill.clearShadowLayer();
        c.drawText(pct, tmp.left + pp + dot + inner, base, labelText);
        float x = tmp.left + pp + dot + inner + pw + dp(6);
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
        // Wi-Fi: vysec s ukazatelem signalu, vlevo od baterie.
        tmp.set(batLeft - inner - ww, top, batLeft - inner, top + ph);
        drawPill(c, tmp);
        drawWifiIcon(c, tmp.left + pp + wifiIcon / 2f, tmp.centerY() + dp(5), dp(9), wifiOn ? wifiBars : 0);
        labelText.setColor(wifiOn ? 0xF2FFFFFF : 0x99FFFFFF);
        c.drawText(wl, tmp.left + pp + wifiIcon + inner, base, labelText);
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
        // Bila vypln - zaoblena, oriznuta stopou.
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

        // Ikona vlevo ve ctverci o strane vysky posuvniku (stejny okraj zleva i shora),
        // tmava, kdyz je pod ni bila vypln.
        final float ix = tmp.left + tmp.height() / 2f, iy = tmp.centerY();
        final boolean onFill = fw > dp(44);
        final int ic = onFill ? Color.argb(alpha, 16, 21, 31) : Color.argb(alpha, 255, 255, 255);
        Icons.draw(c, el == EL_BRIGHT ? Icons.SUN : Icons.SPEAKER, ix, iy, dp(20), ic, dp(2), v, icon);
        final Paint.FontMetrics fm = labelText.getFontMetrics();
        final float base = iy - (fm.ascent + fm.descent) / 2f;
        final float lx = tmp.left + tmp.height();
        final boolean labelOnFill = fw > lx - tmp.left + labelText.measureText(label) + dp(4);
        labelText.setColor(labelOnFill ? Color.argb(alpha, 16, 21, 31) : Color.argb(alpha, 255, 255, 255));
        c.drawText(label, lx, base, labelText);
        if (enabled) {
            final String pct = Math.round(v * 100) + " %";
            final float pw = labelText.measureText(pct);
            final float px = tmp.right - dp(16) - pw;
            final boolean pctOnFill = fw > px - tmp.left + pw;
            labelText.setColor(pctOnFill ? Color.argb(alpha, 16, 21, 31) : Color.argb(Math.round(alpha * 0.8f), 255, 255, 255));
            c.drawText(pct, px, base, labelText);
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
        final boolean button = i == T_NEO;
        final float w2 = r.width() / 2f, h2 = r.height() / 2f, rr = button ? h2 : Math.min(dp(Glass.R_TILE), h2);
        final float hc = clamp(hv, 0f, 1f);
        fill.setShader(null);
        fill.setColor(Color.argb(Math.round(0x33 * a), 0, 0, 0));
        c.drawRoundRect(-w2, -h2 + dp(3), w2, h2 + dp(3), rr, rr, fill);
        if (!button) {
            fill.setColor(Color.argb(Math.round((0x1F + 0x22 * hc) * a), 255, 255, 255));
            if (hc > 0.01f) fill.setShadowLayer(dp(14) * hc, 0, 0, Color.argb(Math.round(110 * hc * a), 56, 189, 248));
            c.drawRoundRect(-w2, -h2, w2, h2, rr, rr, fill);
            fill.clearShadowLayer();
            stroke.setStrokeWidth(dp(1));
            stroke.setColor(Color.argb(Math.round((0x24 + 0x50 * hc) * a), 255, 255, 255));
            c.drawRoundRect(-w2, -h2, w2, h2, rr, rr, stroke);
        } else if (hc > 0.01f) {
            fill.setColor(Color.argb(Math.round(0x80 * hc * a), 255, 255, 255));
            fill.setShadowLayer(dp(16) * hc, 0, 0, Color.argb(Math.round(130 * hc * a), 255, 255, 255));
            c.drawRoundRect(-w2, -h2, w2, h2, h2, h2, fill);
            fill.clearShadowLayer();
        }
        final int col = Color.argb(Math.round(255 * clamp(a, 0f, 1f)), 255, 255, 255);
        if (button) {
            // Tlacitko "Nastaveni Nea": bila pilulka s tmavym textem (hlavni akce).
            fill.setColor(Color.argb(Math.round(0xF2 * clamp(a, 0f, 1f)), 255, 255, 255));
            c.drawRoundRect(-w2, -h2, w2, h2, h2, h2, fill);
            final int ink = (Glass.INK & 0x00FFFFFF) | (Math.round(255 * clamp(a, 0f, 1f)) << 24);
            final String t = "Nastavení Nea";
            labelText.setColor(ink);
            final float tw = labelText.measureText(t);
            final float total = dp(20) + dp(10) + tw;
            Icons.draw(c, Icons.GEAR, -total / 2f + dp(10), 0, dp(20), ink, dp(1.9f), 1f, icon);
            final Paint.FontMetrics fm = labelText.getFontMetrics();
            c.drawText(t, -total / 2f + dp(30), -(fm.ascent + fm.descent) / 2f, labelText);
        } else {
            // Vodorovna dlazdice: ikona vlevo (stejny okraj jako u posuvniku), nazev vedle.
            final float ix = -w2 + dp(24);
            Icons.draw(c, TILE_ICONS[i], ix, 0, dp(22), col, dp(2), 1f, icon);
            tileText.setTextAlign(Paint.Align.LEFT);
            tileText.setColor(Color.argb(Math.round(235 * clamp(a, 0f, 1f)), 255, 255, 255));
            final Paint.FontMetrics fm = tileText.getFontMetrics();
            // Presnejsi nazev (odliseni od nastaveni Nea).
            c.drawText(i == T_QUEST_SETTINGS ? "Nastavení Questu" : TILE_LABELS[i],
                    ix + dp(11) + dp(10), -(fm.ascent + fm.descent) / 2f, tileText);
        }
        c.restore();
    }

    /** Karta "Naposledy hrano": banner, nazev, kdy a tlacitko Hrat. */
    private void drawLastPlayed(Canvas c, long now) {
        final RectF r = rects[EL_LAST];
        final float a = appear(EL_LAST, now);
        if (a <= 0.004f || r.isEmpty()) return;
        final float hv = clamp(hover[EL_LAST].get(now), 0f, 1f);
        final float ac = clamp(a, 0f, 1f);
        c.save();
        c.translate(0, (1f - a) * dp(12));
        final float rr = dp(Glass.R_TILE);
        fill.setShader(null);
        fill.setColor(Color.argb(Math.round(0x33 * ac), 0, 0, 0));
        c.drawRoundRect(r.left, r.top + dp(3), r.right, r.bottom + dp(3), rr, rr, fill);
        fill.setColor(Color.argb(Math.round((0x1F + 0x1A * hv) * ac), 255, 255, 255));
        c.drawRoundRect(r, rr, rr, fill);
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(Color.argb(Math.round((0x2E + 0x40 * hv) * ac), 255, 255, 255));
        c.drawRoundRect(r, rr, rr, stroke);
        // Banner hry (na sirku) vlevo - odsazeny INSET, zaobleni soustredne s kartou.
        final float in = dp(Glass.INSET), ri = dp(Glass.R_INNER);
        final float th = r.height() - 2 * in, tw = th * 1.6f;
        final float tl = r.left + in, tt = r.top + in;
        if (lastShader != null && lastArt != null) {
            lastMatrix.setScale(tw / lastArt.getWidth(), th / lastArt.getHeight());
            lastMatrix.postTranslate(tl, tt);
            lastShader.setLocalMatrix(lastMatrix);
            artPaint.setShader(lastShader);
            artPaint.setAlpha(Math.round(255 * ac));
            c.drawRoundRect(tl, tt, tl + tw, tt + th, ri, ri, artPaint);
            artPaint.setShader(null);
        } else {
            fill.setColor(Color.argb(Math.round(0x33 * ac), 255, 255, 255));
            c.drawRoundRect(tl, tt, tl + tw, tt + th, ri, ri, fill);
        }
        // Tlacitko vpravo: bila pilulka s tmavym textem, od praveho okraje stejne jako shora.
        labelText.setColor(Glass.INK);
        final float bh = dp(CARD_BTN_H), bm = (r.height() - bh) / 2f;
        final float bw = labelText.measureText(lastButton) + dp(32);
        final float bl = r.right - bm - bw, bt = r.centerY() - bh / 2f;
        fill.setColor(Color.argb(Math.round(0xF2 * ac), 255, 255, 255));
        if (hv > 0.01f) fill.setShadowLayer(dp(12) * hv, 0, 0, Color.argb(Math.round(120 * hv), 255, 255, 255));
        c.drawRoundRect(bl, bt, bl + bw, bt + bh, bh / 2f, bh / 2f, fill);
        fill.clearShadowLayer();
        final Paint.FontMetrics fm = labelText.getFontMetrics();
        labelText.setColor((Glass.INK & 0x00FFFFFF) | (Math.round(255 * ac) << 24));
        c.drawText(lastButton, bl + dp(16), bt + bh / 2f - (fm.ascent + fm.descent) / 2f, labelText);
        // Texty mezi bannerem a tlacitkem.
        final float tx = tl + tw + dp(Glass.GAP);
        final float maxW = bl - dp(Glass.GAP) - tx;
        smallText.setColor(Color.argb(Math.round(0xB3 * ac), 255, 255, 255));
        c.drawText(android.text.TextUtils.ellipsize(lastSub, smallText, maxW,
                android.text.TextUtils.TruncateAt.END).toString(), tx, r.centerY() - dp(6), smallText);
        labelText.setColor(Color.argb(Math.round(255 * ac), 255, 255, 255));
        c.drawText(android.text.TextUtils.ellipsize(lastTitle, labelText, maxW,
                android.text.TextUtils.TruncateAt.END).toString(), tx, r.centerY() + dp(14), labelText);
        c.restore();
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
            if (rects[i].isEmpty()) continue;
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
                    else if (el == EL_LAST) {
                        if (lastApp != null) actions.launch(lastApp);
                    } else if (el >= EL_TILE0) actions.openTarget(el - EL_TILE0);
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
