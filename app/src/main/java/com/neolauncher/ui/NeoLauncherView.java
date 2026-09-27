package com.neolauncher.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

import com.neolauncher.art.ArtworkLoader;
import com.neolauncher.art.Placeholders;
import com.neolauncher.data.AppEntry;
import com.neolauncher.data.Prefs;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cely launcher v JEDNOM View. Vsechno (sklo, horni lista, mrizka, hover,
 * rozmazani, rolovani, presouvani) se kresli a ridi tady.
 * <p>
 * Proc ne RecyclerView jako ve forku: hover efekty tam zavisely na
 * ACTION_HOVER_ENTER/EXIT jednotlivych karet, ktere Quest pri presunu
 * laseru mezi sousednimi kartami neposila spolehlive (CLAUDE.md, bug 1b).
 * Tady se hover pocita z pozice ukazatele pri KAZDEM pohybu i snimku:
 * jeden zdroj pravdy ({@link #focused}), zadne callbacky, zadna recyklace.
 * <p>
 * Rozmery jsou v dp a odpovidaji CSS pixelum z docs/preview_neo.html.
 */
public final class NeoLauncherView extends View implements ArtworkLoader.Listener, Prefs.Listener {

    /** Akce, ktere View nechava na aktivite. */
    public interface Host {
        void onLaunch(AppEntry app);

        void onOpenSettings();

        void onAppMenu(AppEntry app, RectF cardRect);

        void onTabSelected(int tab);

        void onOrderChanged(int tab, List<String> pkgs);

        /** Konec animace spusteni - launcher se muze zavrit. */
        void onLaunchSequenceDone();

        /** Logo Neo klepnuto 5x rychle za sebou - prepnout testovaci karusel. */
        void onToggleCarousel();
    }

    // --- Rozmery z preview (dp) ----------------------------------------------
    private static final float FRAME_RADIUS = 28f;
    private static final float TOPBAR_H = 60f;
    private static final float GRID_TOP = 76f;
    private static final float GRID_SIDE = 24f;
    private static final float GRID_BOTTOM = 24f;
    private static final float GAP = 15f;
    private static final float CARD_RADIUS = 16f;
    private static final float POPOUT_MARGIN_X = 24f;
    private static final float POPOUT_MARGIN_Y = 30f;

    // --- Hover ----------------------------------------------------------------
    private static final float HOVER_SCALE = 1.18f;
    private static final float HOVER_Z = 32f;
    private static final float PERSPECTIVE = 900f;
    private static final float TILT_DEG = 6f;
    /**
     * Smer naklonu. +1 = strana pod ukazatelem se "zamackne" dozadu
     * (jako CSS rotateX/rotateY v preview). Kdyby to na Questu pusobilo
     * obracene, staci zmenit na -1.
     */
    private static final float TILT_SIGN = 1f;
    private static final long HOVER_MS = 380;
    private static final long BLUR_MS = 350;
    private static final long REORDER_MS = 260;
    /** Po tak dlouhem podrzeni se karta zvedne a jde tahat. */
    private static final long LONG_PRESS_MS = 450;
    private static final long HOVER_EXIT_GRACE_MS = 90;
    /** Logo: dalsi klepnuti do teto doby se pocita do "5x klepnout" (karusel). */
    private static final long BRAND_TAP_MS = 380;
    private static final int BRAND_TAPS_CAROUSEL = 5;
    /** Animace spusteni "kukatko": celkova delka a okamzik, kdy se opravdu spusti aplikace. */
    private static final long LAUNCH_MS = 1150;
    private static final float LAUNCH_FIRE_AT = 0.48f;
    /** Pojistka: bez jakekoliv udalosti ukazatele tak dlouho = ukazatel je pryc. */
    private static final long POINTER_STALE_MS = 6000;

    // --- Rolovani (konstanty z preview, prepoctene na 60 snimku/s) -------------
    private static final float SCROLL_LERP = 0.16f;
    private static final float OVERSCROLL_RESIST = 0.38f;
    private static final float SQUISH_MAX = 0.045f;
    private static final float SQUISH_PER_DP = 0.0028f;
    private static final float WHEEL_GAIN = 1.0f;

    private static final int ZONE_NONE = 0;
    private static final int ZONE_BRAND = 1;
    private static final int ZONE_TAB0 = 2; // +index zalozky
    private static final int ZONE_BATTERY = 10;

    private static final String[] TAB_NAMES = {"Hry", "Aplikace", "Vše"};

    /** Stav jedne karty. Pozice jsou v "obsahovych" souradnicich mrizky. */
    private static final class Card {
        AppEntry app;
        String label;
        String shownLabel;
        float shownLabelWidth;
        float shownLabelFor = -1;
        int index;
        final Eased hover = new Eased(0, HOVER_MS);
        final Eased rotX = new Eased(0, HOVER_MS);
        final Eased rotY = new Eased(0, HOVER_MS);
        final Eased press = new Eased(0, 140, Eased.EASE_OUT);
        final Eased x = new Eased(0, REORDER_MS);
        final Eased y = new Eased(0, REORDER_MS);
        final Eased flash = new Eased(0, 420, Eased.EASE_OUT);
        Bitmap art;
        BitmapShader artShader;
        float artShaderW = -1;
        Shader placeholder;
        float placeholderW = -1;
        /** Vlastni RenderNode jen pro kartu, ktera je prave naklonena (3D rotace). */
        RenderNode node;
    }

    private final float d; // density
    private Host host;
    private Prefs prefs;
    private ArtworkLoader artwork;

    private final List<Card> cards = new ArrayList<>();
    private final Map<String, Card> cardByPkg = new HashMap<>();
    private int tab = Prefs.TAB_GAMES;
    private boolean appsLoaded;

    // --- Geometrie (px) ---------------------------------------------------------
    private final RectF frame = new RectF();
    private float topBarBottom, gridLeft, gridTop, gridRight;
    private float cardW, cardH, gap;
    private int cols = 4;
    private float minScroll;
    private float hoverScaleEff;

    // --- Ukazatel a fokus ---------------------------------------------------------
    private boolean pointerIn;
    private float px, py;
    private long lastPointerEventMs;
    private boolean interactive = true;
    /** JEDINY zdroj pravdy o tom, ktera karta je pod ukazatelem. */
    private Card focused;
    private int hoverZone = ZONE_NONE;
    private final Eased blurAmount = new Eased(0, BLUR_MS);

    // --- Animace spusteni ("kukatko") - samotna animace je v PeepholeAnimation --------
    private Card launchCard;
    private boolean launchCloses;
    private PeepholeAnimation peephole;
    private float blurCx, blurCy;

    // --- Dotyk / rolovani / presouvani --------------------------------------------
    private final float touchSlop;
    private boolean touching, touchScrolling;
    private float downX, downY, lastTouchY;
    private int downZone = ZONE_NONE;
    private Card pressedCard;
    private VelocityTracker velocity;
    private float scrollCur, scrollTarget, flingV, squish = 1f, squishPivot;
    private long lastStepNs;
    private boolean scrollActive;

    private boolean dragging;
    private Card dragCard;
    private float dragDX, dragDY, liftX, liftY;
    private boolean dragMoved, dragOrderChanged;
    private final Eased dragLift = new Eased(0, 220);
    private final Eased dragTilt = new Eased(0, 300);
    private float lastDragX;
    private long lastDragStepNs;

    // --- Horni lista ---------------------------------------------------------------
    private final Eased tabPos = new Eased(0, 320);
    private final Eased brandHover = new Eased(0, 180, Eased.EASE_OUT);
    private final Eased brandTap = new Eased(0, 420, Eased.EASE_OUT);
    private int brandTaps;
    private final Eased batteryHover = new Eased(0, 180, Eased.EASE_OUT);
    private final Eased[] tabHover = {new Eased(0, 180, Eased.EASE_OUT),
            new Eased(0, 180, Eased.EASE_OUT), new Eased(0, 180, Eased.EASE_OUT)};
    private final Eased contentFade = new Eased(1, 280, Eased.EASE_OUT);
    private final RectF brandRect = new RectF();
    private final RectF tabsRect = new RectF();
    private final RectF[] tabRects = {new RectF(), new RectF(), new RectF()};
    private final RectF statusRect = new RectF();
    private final RectF batteryRect = new RectF();
    private int batteryLevel = -1;
    private boolean charging, fastCharging;
    private long clockMinute = -1;
    private String clockTime = "", clockDate = "";

    // --- Kresleni ------------------------------------------------------------------
    private final Matrix shaderMatrix = new Matrix();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint artPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint spritePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint pillText = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final TextPaint brandText = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final TextPaint tabText = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final TextPaint clockText = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final TextPaint batteryText = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final TextPaint emptyText = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Path topBarPath = new Path();
    private final Path framePath = new Path();
    private final Path boltPath = new Path();
    private final RectF tmp = new RectF();
    private Shader bottomShade;
    private Shader frameHighlight;
    private Shader barShadow;
    private ShadowSprite restShadow, hoverShadow, glowSprite;
    private float spriteForW = -1;

    private final RenderNode contentNode = new RenderNode("neo-content");
    private final RenderNode backdropNode = new RenderNode("neo-topbar-backdrop");
    private final DepthBlur depthBlur = new DepthBlur();
    private RenderEffect backdropEffect;
    private boolean modalBlur;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hoverExitRunnable = () -> {
        pointerIn = false;
        updateFocus(System.nanoTime());
        invalidate();
    };
    private boolean stalePosted;
    private final Runnable staleRunnable = new Runnable() {
        @Override
        public void run() {
            stalePosted = false;
            if (!pointerIn) return;
            long idle = System.currentTimeMillis() - lastPointerEventMs;
            if (idle >= POINTER_STALE_MS && !touching && !dragging) {
                clearPointer();
            } else {
                stalePosted = true;
                handler.postDelayed(this, Math.max(200, POINTER_STALE_MS - idle + 50));
            }
        }
    };
    private final Runnable longPressRunnable = this::onLongPress;
    /** Po poslednim klepnuti na logo: mene nez 5 klepnuti = otevrit nastaveni. */
    private final Runnable brandTapRunnable = () -> {
        final int taps = brandTaps;
        brandTaps = 0;
        if (taps > 0 && taps < BRAND_TAPS_CAROUSEL && host != null) host.onOpenSettings();
    };
    /** Karta zvednuta a porad drzena bez pohybu -> po prodleve z nastaveni otevrit menu. */
    private final Runnable menuHoldRunnable = this::onMenuHold;
    private long liftNs;

    public NeoLauncherView(Context c) {
        super(c);
        d = getResources().getDisplayMetrics().density;
        touchSlop = Math.max(ViewConfiguration.get(c).getScaledTouchSlop(), dp(10));
        setFocusable(false);
        setClickable(true);

        Typeface semi = Typeface.create(Typeface.SANS_SERIF, 600, false);
        Typeface bold = Typeface.create(Typeface.SANS_SERIF, 700, false);

        pillText.setTypeface(semi);
        pillText.setTextSize(dp(11.5f));
        pillText.setColor(Color.WHITE);

        brandText.setTypeface(bold);
        brandText.setTextSize(dp(19));
        brandText.setLetterSpacing(0.5f / 19f);
        brandText.setColor(Color.WHITE);

        tabText.setTypeface(semi);
        tabText.setTextSize(dp(14));
        tabText.setTextAlign(Paint.Align.CENTER);

        clockText.setTypeface(semi);
        clockText.setTextSize(dp(14));
        clockText.setFontFeatureSettings("tnum");
        clockText.setLetterSpacing(0.5f / 14f);
        clockText.setColor(0xF2FFFFFF);

        batteryText.setTypeface(bold);
        batteryText.setTextSize(dp(12.5f));
        batteryText.setFontFeatureSettings("tnum");

        emptyText.setTypeface(semi);
        emptyText.setTextSize(dp(16));
        emptyText.setTextAlign(Paint.Align.CENTER);
        emptyText.setColor(0x99FFFFFF);

        peephole = new PeepholeAnimation(d);

        stroke.setStyle(Paint.Style.STROKE);

        // Blesk z preview: SVG path "M11 2L4 13h5l-1.5 9L18 11h-5.5L14 2Z" (viewBox 24x24)
        boltPath.moveTo(11, 2);
        boltPath.lineTo(4, 13);
        boltPath.rLineTo(5, 0);
        boltPath.rLineTo(-1.5f, 9);
        boltPath.lineTo(18, 11);
        boltPath.rLineTo(-5.5f, 0);
        boltPath.lineTo(14, 2);
        boltPath.close();
    }

    private float dp(float v) {
        return v * d;
    }

    // =========================================================================
    // Verejne API
    // =========================================================================

    public void setHost(Host h) {
        host = h;
    }

    public void bind(Prefs p, ArtworkLoader a) {
        prefs = p;
        artwork = a;
        tab = p.tab();
        tabPos.snap(tab);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (artwork != null) artwork.addListener(this);
        if (prefs != null) prefs.addListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (artwork != null) artwork.removeListener(this);
        if (prefs != null) prefs.removeListener(this);
        handler.removeCallbacksAndMessages(null);
        stalePosted = false;
        if (velocity != null) {
            velocity.recycle();
            velocity = null;
        }
        contentNode.discardDisplayList();
        backdropNode.discardDisplayList();
        for (Card k : cards) if (k.node != null) k.node.discardDisplayList();
        peephole.discard();
    }

    public int tab() {
        return tab;
    }

    /**
     * Nastavi aplikace zobrazene v mrizce. Existujici karty si drzi svuj
     * stav (obrazek, animace), jen se plynule presunou na nove misto.
     */
    public void setApps(List<AppEntry> list, List<String> labels, boolean animate) {
        final long now = System.nanoTime();
        appsLoaded = true;
        Map<String, Card> old = new HashMap<>(cardByPkg);
        cards.clear();
        cardByPkg.clear();
        for (int i = 0; i < list.size(); i++) {
            AppEntry e = list.get(i);
            Card k = old.remove(e.pkg);
            boolean fresh = k == null;
            if (fresh) k = new Card();
            k.app = e;
            String label = labels.get(i);
            if (!label.equals(k.label)) {
                k.label = label;
                k.shownLabelFor = -1;
            }
            k.index = i;
            cards.add(k);
            cardByPkg.put(e.pkg, k);
            float sx = slotX(i), sy = slotY(i);
            if (animate && !fresh) {
                k.x.set(sx, now);
                k.y.set(sy, now);
            } else {
                k.x.snap(sx);
                k.y.snap(sy);
            }
        }
        // Karta, ktera zmizela, nesmi zustat jako fokus/tazena.
        if (focused != null && !cardByPkg.containsKey(focused.app.pkg)) focused = null;
        if (pressedCard != null && !cardByPkg.containsKey(pressedCard.app.pkg)) pressedCard = null;
        if (dragging && (dragCard == null || !cardByPkg.containsKey(dragCard.app.pkg))) {
            dragging = false;
            dragCard = null;
        }
        updateScrollBounds();
        if (artwork != null) {
            List<AppEntry> pre = new ArrayList<>(list);
            artwork.prefetch(pre);
        }
        updateFocus(now);
        invalidate();
    }

    /** Prepnuti zalozky: obsah se kratce prolne a odroluje nahoru. */
    public void showTab(int newTab, List<AppEntry> list, List<String> labels) {
        final long now = System.nanoTime();
        tab = newTab;
        tabPos.set(newTab, now);
        resetHoverState();
        flingV = 0;
        scrollCur = scrollTarget = 0;
        setApps(list, labels, false);
        contentFade.snap(0f);
        contentFade.set(1f, now);
        invalidate();
    }

    public void setBattery(int level, boolean isCharging, boolean isFast) {
        if (level == batteryLevel && isCharging == charging && isFast == fastCharging) return;
        batteryLevel = level;
        charging = isCharging;
        fastCharging = isFast;
        invalidate();
    }

    /** Volat pri tiknuti minuty (hodiny) - prekresli horni listu. */
    public void onClockTick() {
        clockMinute = -1;
        invalidate();
    }

    /** Pri otevrenem nastaveni/menu: karty nereaguji a cely launcher se rozmaze. */
    public void setInteractive(boolean b) {
        interactive = b;
        if (!b) {
            // Rozpracovane gesto uz nedobehne (UP pujde do prazdna) - uklidit hned.
            cancelTouch();
            resetHoverState();
        }
        invalidate();
    }

    public void setModalBlur(boolean b) {
        if (modalBlur == b) return;
        modalBlur = b;
        setRenderEffect(b ? RenderEffect.createBlurEffect(dp(10), dp(10), Shader.TileMode.CLAMP) : null);
    }

    /** Skleneny panel v souradnicich View (pro umisteni dialogu). */
    public RectF frameRect() {
        return new RectF(frame);
    }

    /** Tvrdy reset hoveru - napr. kdyz Quest vezme oknu fokus (CLAUDE.md, oprava #1). */
    public void clearPointer() {
        handler.removeCallbacks(hoverExitRunnable);
        pointerIn = false;
        cancelTouch();
        resetHoverState();
        invalidate();
    }

    private void resetHoverState() {
        final long now = System.nanoTime();
        for (Card k : cards) {
            k.hover.set(0, now);
            k.rotX.set(0, now);
            k.rotY.set(0, now);
            k.press.set(0, now);
        }
        focused = null;
        hoverZone = ZONE_NONE;
        blurAmount.set(0, now);
        updateZoneHover(now);
    }

    @Override
    public void onArtworkChanged(String pkg) {
        invalidate();
    }

    @Override
    public void onPrefsChanged() {
        relayout();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        // Quest umi panelu docasne sebrat fokus a pak neposila zadne hover
        // udalosti (ani HOVER_EXIT). Proto pri kazde zmene tvrdy reset.
        clearPointer();
    }

    // =========================================================================
    // Rozlozeni
    // =========================================================================

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        relayout();
    }

    private void relayout() {
        final float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0 || prefs == null) return;
        final boolean popout = prefs.popoutMargin();
        final float mx = popout ? dp(POPOUT_MARGIN_X) : 0f;
        final float my = popout ? dp(POPOUT_MARGIN_Y) : 0f;
        frame.set(mx, my, w - mx, h - my);
        topBarBottom = frame.top + dp(TOPBAR_H);
        gridLeft = frame.left + dp(GRID_SIDE);
        gridRight = frame.right - dp(GRID_SIDE);
        gridTop = frame.top + dp(GRID_TOP);
        final int oldCols = cols;
        final float oldCardW = cardW;
        cols = prefs.columns();
        gap = dp(GAP);
        cardW = Math.max(dp(60), (gridRight - gridLeft - gap * (cols - 1)) / cols);
        cardH = cardW / ArtworkLoader.CARD_ASPECT;
        final boolean geometryChanged = oldCols != cols || Math.abs(oldCardW - cardW) > 0.01f;

        final float persp = dp(PERSPECTIVE);
        // translateZ(32px) v perspective(900px) karte opticky prida ~3.7 % velikosti.
        hoverScaleEff = HOVER_SCALE * (persp / (persp - dp(HOVER_Z)));

        if (Math.abs(spriteForW - cardW) > 0.5f) {
            spriteForW = cardW;
            final float r = dp(CARD_RADIUS);
            restShadow = ShadowSprite.create(cardW, cardH, r, dp(10));   // 0 6px 20px
            hoverShadow = ShadowSprite.create(cardW, cardH, r, dp(27));  // 0 26px 55px
            glowSprite = ShadowSprite.create(cardW, cardH, r, dp(16));   // 0 0 32px
            bottomShade = new LinearGradient(0, cardH / 2f - dp(44), 0, cardH / 2f,
                    0x00000000, 0x8C000000, Shader.TileMode.CLAMP);
        }
        if (artwork != null) artwork.setTargetSize(Math.round(cardW * hoverScaleEff));

        final float fr = dp(FRAME_RADIUS);
        frameHighlight = new LinearGradient(0, frame.top, 0, frame.top + dp(22),
                0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
        barShadow = new LinearGradient(0, topBarBottom, 0, topBarBottom + dp(18),
                0x47000000, 0x00000000, Shader.TileMode.CLAMP);
        framePath.reset();
        framePath.addRoundRect(frame, fr, fr, Path.Direction.CW);
        topBarPath.reset();
        topBarPath.addRoundRect(frame.left, frame.top, frame.right, topBarBottom,
                new float[]{fr, fr, fr, fr, 0, 0, 0, 0}, Path.Direction.CW);

        // Vrstva karet: pod horni listou az ke spodnimu okraji, dole zaoblena.
        contentNode.setPosition(Math.round(frame.left), Math.round(topBarBottom),
                Math.round(frame.right), Math.round(frame.bottom));
        Outline o = new Outline();
        o.setRoundRect(0, -Math.round(fr), Math.round(frame.width()),
                Math.round(frame.bottom) - Math.round(topBarBottom), fr);
        contentNode.setOutline(o);
        contentNode.setClipToOutline(true);

        // Kopie karet pod horni listou, silne rozmazana = "matne sklo".
        backdropNode.setPosition(Math.round(frame.left), Math.round(frame.top),
                Math.round(frame.right), Math.round(topBarBottom));
        Outline ob = new Outline();
        ob.setRoundRect(0, 0, Math.round(frame.width()),
                Math.round(topBarBottom - frame.top) + Math.round(fr), fr);
        backdropNode.setOutline(ob);
        backdropNode.setClipToOutline(true);
        final float sigma = dp(32); // backdrop-filter: blur(32px) z preview
        final float radius = (sigma - 0.5f) / 0.57735f;
        backdropEffect = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP);
        backdropNode.setRenderEffect(backdropEffect);

        final long now = System.nanoTime();
        // Jen pri zmene mrizky - jinak by zmena nastaveni zarizla rozbehnute animace.
        if (geometryChanged) {
            for (Card k : cards) {
                k.x.snap(slotX(k.index));
                k.y.snap(slotY(k.index));
                k.shownLabelFor = -1;
            }
        }
        updateScrollBounds();
        updateFocus(now);
        invalidate();
    }

    private float slotX(int i) {
        return (i % cols) * (cardW + gap);
    }

    private float slotY(int i) {
        return (i / cols) * (cardH + gap);
    }

    private void updateScrollBounds() {
        final int rows = (cards.size() + cols - 1) / cols;
        final float contentH = rows * cardH + Math.max(0, rows - 1) * gap;
        final float visibleBottom = frame.bottom - dp(GRID_BOTTOM);
        minScroll = Math.min(0f, visibleBottom - (gridTop + contentH));
        if (!touching) kickScroll();
    }

    // =========================================================================
    // Transformace souradnic
    // =========================================================================
    // "scrolled" prostor = obsah posunuty o scrollCur. Na obrazovce se na nej
    // navic aplikuje gumove "zmacknuti" (squish) kolem squishPivot.

    private float screenToScrolledY(float sy) {
        return squishPivot + (sy - squishPivot) / squish;
    }

    private float scrolledToScreenY(float y) {
        return squishPivot + (y - squishPivot) * squish;
    }

    private float cardLeft(Card k, long now) {
        return gridLeft + k.x.get(now);
    }

    private float cardTop(Card k, long now) {
        return gridTop + k.y.get(now) + scrollCur;
    }

    private float visualScale(Card k, long now) {
        float h = k.hover.get(now);
        float p = k.press.get(now);
        return 1f + h * (hoverScaleEff - 1f) - p * 0.045f;
    }

    private Card cardAt(float x, float y, long now) {
        final float yy = screenToScrolledY(y);
        for (Card k : cards) {
            if (k == dragCard && dragging) continue;
            float l = cardLeft(k, now), t = cardTop(k, now);
            if (x >= l && x < l + cardW && yy >= t && yy < t + cardH) return k;
        }
        return null;
    }

    /** Obdelnik karty na obrazovce vcetne aktualniho zvetseni. */
    private void poppedRect(Card k, long now, RectF out) {
        float s = visualScale(k, now);
        float cx = cardLeft(k, now) + cardW / 2f;
        float cy = scrolledToScreenY(cardTop(k, now) + cardH / 2f);
        out.set(cx - cardW * s / 2f, cy - cardH * s * squish / 2f,
                cx + cardW * s / 2f, cy + cardH * s * squish / 2f);
    }

    // =========================================================================
    // Fokus (hover) - jediny zdroj pravdy
    // =========================================================================

    private void updateFocus(long now) {
        Card target = null;
        int zone = ZONE_NONE;
        if (pointerIn && interactive && !dragging && !cards.isEmpty()) {
            zone = zoneAt(px, py);
            if (focused != null) {
                poppedRect(focused, now, tmp);
                if (tmp.contains(px, py)) target = focused;
            }
            if (target == null && zone == ZONE_NONE && py >= topBarBottom
                    && frame.contains(px, py)) {
                target = cardAt(px, py, now);
            }
        } else if (pointerIn && interactive && !dragging) {
            zone = zoneAt(px, py);
        }
        if (target != null) zone = ZONE_NONE;

        if (target != focused) {
            if (focused != null) {
                focused.hover.set(0, now);
                focused.rotX.set(0, now);
                focused.rotY.set(0, now);
                focused.press.set(0, now);
            }
            focused = target;
            if (target != null) target.hover.set(1, now);
            blurAmount.set(target != null ? 1f : 0f, now);
        }
        if (focused != null) {
            poppedRect(focused, now, tmp);
            float nx = clamp((px - tmp.centerX()) / (tmp.width() / 2f), -1f, 1f);
            float ny = clamp((py - tmp.centerY()) / (tmp.height() / 2f), -1f, 1f);
            float ry = TILT_SIGN * nx * TILT_DEG;
            float rx = -TILT_SIGN * ny * TILT_DEG;
            if (Math.abs(ry - focused.rotY.target()) > 0.05f) focused.rotY.set(ry, now);
            if (Math.abs(rx - focused.rotX.target()) > 0.05f) focused.rotX.set(rx, now);
            blurCx = cardLeft(focused, now) + cardW / 2f;
            blurCy = scrolledToScreenY(cardTop(focused, now) + cardH / 2f);
        }
        if (zone != hoverZone) {
            hoverZone = zone;
            updateZoneHover(now);
        }
    }

    private void updateZoneHover(long now) {
        brandHover.set(hoverZone == ZONE_BRAND ? 1f : 0f, now);
        batteryHover.set(hoverZone == ZONE_BATTERY ? 1f : 0f, now);
        for (int i = 0; i < tabHover.length; i++) {
            tabHover[i].set(hoverZone == ZONE_TAB0 + i ? 1f : 0f, now);
        }
    }

    private int zoneAt(float x, float y) {
        if (y < frame.top || y > topBarBottom || x < frame.left || x > frame.right) return ZONE_NONE;
        if (brandRect.contains(x, y)) return ZONE_BRAND;
        for (int i = 0; i < tabRects.length; i++) if (tabRects[i].contains(x, y)) return ZONE_TAB0 + i;
        if (batteryRect.contains(x, y)) return ZONE_BATTERY;
        return ZONE_NONE;
    }

    // =========================================================================
    // Vstup
    // =========================================================================

    private void touchPointer(float x, float y) {
        px = x;
        py = y;
        pointerIn = true;
        lastPointerEventMs = System.currentTimeMillis();
        handler.removeCallbacks(hoverExitRunnable);
        if (!stalePosted) {
            stalePosted = true;
            handler.postDelayed(staleRunnable, POINTER_STALE_MS);
        }
    }

    @Override
    public boolean onHoverEvent(MotionEvent e) {
        if (!interactive || launchCard != null) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_HOVER_ENTER:
            case MotionEvent.ACTION_HOVER_MOVE:
                touchPointer(e.getX(), e.getY());
                updateFocus(System.nanoTime());
                invalidate();
                break;
            case MotionEvent.ACTION_HOVER_EXIT:
                // HOVER_EXIT chodi i tesne pred stiskem spouste - s kratkym
                // odkladem, aby karta pri kliknuti neblikla.
                if (!touching) {
                    handler.removeCallbacks(hoverExitRunnable);
                    handler.postDelayed(hoverExitRunnable, HOVER_EXIT_GRACE_MS);
                }
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_SCROLL
                && (e.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0) {
            if (!interactive || dragging || launchCard != null) return true;
            touchPointer(e.getX(), e.getY());
            float v = e.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if (v != 0f) {
                float factor = ViewConfiguration.get(getContext()).getScaledVerticalScrollFactor();
                float delta = v * factor * WHEEL_GAIN;
                flingV = 0;
                scrollTarget += delta;
                if (scrollTarget > 0f) scrollTarget = scrollTarget * OVERSCROLL_RESIST;
                else if (scrollTarget < minScroll)
                    scrollTarget = minScroll + (scrollTarget - minScroll) * OVERSCROLL_RESIST;
                kickScroll();
            }
            return true;
        }
        return super.onGenericMotionEvent(e);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!interactive) return false;
        if (launchCard != null) return true; // behem animace spusteni se nic nedeje
        final float x = e.getX(), y = e.getY();
        final long now = System.nanoTime();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                touchPointer(x, y);
                touching = true;
                touchScrolling = false;
                downX = x;
                downY = y;
                lastTouchY = y;
                if (velocity == null) velocity = VelocityTracker.obtain();
                velocity.clear();
                velocity.addMovement(e);
                // Klepnuti behem setrvacneho rolovani ho zastavi.
                flingV = 0;
                if (scrollCur <= 0f && scrollCur >= minScroll) scrollTarget = scrollCur;
                updateFocus(now);
                downZone = zoneAt(x, y);
                pressedCard = downZone == ZONE_NONE ? focused : null;
                if (pressedCard != null) {
                    pressedCard.press.set(1f, now);
                    handler.postDelayed(longPressRunnable, LONG_PRESS_MS);
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                touchPointer(x, y);
                if (velocity != null) velocity.addMovement(e);
                if (dragging) {
                    if (!dragMoved && Math.hypot(x - liftX, y - liftY) > touchSlop) {
                        dragMoved = true;
                        handler.removeCallbacks(menuHoldRunnable);
                    }
                    dragTo(x, y, now);
                    invalidate();
                    return true;
                }
                if (!touchScrolling && (Math.abs(y - downY) > touchSlop
                        || Math.abs(x - downX) > touchSlop)) {
                    touchScrolling = true;
                    lastTouchY = y;
                    handler.removeCallbacks(longPressRunnable);
                    if (pressedCard != null) pressedCard.press.set(0f, now);
                    pressedCard = null;
                }
                if (touchScrolling) {
                    float dy = y - lastTouchY;
                    lastTouchY = y;
                    float next = scrollCur + dy;
                    if (next > 0f || next < minScroll) next = scrollCur + dy * OVERSCROLL_RESIST;
                    scrollCur = scrollTarget = next;
                    kickScroll();
                }
                updateFocus(now);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP: {
                handler.removeCallbacks(longPressRunnable);
                handler.removeCallbacks(menuHoldRunnable);
                if (velocity != null) velocity.addMovement(e);
                if (dragging) {
                    endDrag(now, false);
                } else if (touchScrolling) {
                    if (velocity != null) {
                        velocity.computeCurrentVelocity(1000);
                        flingV = velocity.getYVelocity();
                        if (Math.abs(flingV) < dp(80)) flingV = 0;
                    }
                    kickScroll();
                } else {
                    click(x, y, now);
                }
                if (pressedCard != null) pressedCard.press.set(0f, now);
                pressedCard = null;
                touching = false;
                touchScrolling = false;
                updateFocus(now);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                cancelTouch();
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private void cancelTouch() {
        final long now = System.nanoTime();
        handler.removeCallbacks(longPressRunnable);
        handler.removeCallbacks(menuHoldRunnable);
        if (dragging) endDrag(now, true);
        if (pressedCard != null) pressedCard.press.set(0f, now);
        pressedCard = null;
        if (touching && touchScrolling) kickScroll();
        touching = false;
        touchScrolling = false;
    }

    private void click(float x, float y, long now) {
        final int zone = zoneAt(x, y);
        if (zone != ZONE_NONE && zone == downZone) {
            if (zone == ZONE_BRAND) {
                // Jedno klepnuti = nastaveni (s kratkou prodlevou), 5x rychle = karusel.
                handler.removeCallbacks(brandTapRunnable);
                brandTaps++;
                brandTap.snap(1f);
                brandTap.set(0f, now);
                if (brandTaps >= BRAND_TAPS_CAROUSEL) {
                    brandTaps = 0;
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    if (host != null) host.onToggleCarousel();
                } else {
                    handler.postDelayed(brandTapRunnable, BRAND_TAP_MS);
                }
            } else if (zone >= ZONE_TAB0 && zone < ZONE_TAB0 + TAB_NAMES.length) {
                int t = zone - ZONE_TAB0;
                if (t != tab && host != null) host.onTabSelected(t);
            }
            return;
        }
        Card k = pressedCard;
        if (k != null && k == focused) {
            final float animScale = ValueAnimator.getDurationScale();
            if (prefs.launchAnimation() && animScale > 0f && isHardwareAccelerated()) {
                startLaunch(k, now, animScale);
                return;
            }
            k.flash.snap(1f);
            k.flash.set(0f, now);
            final AppEntry app = k.app;
            // Kratka prodleva, aby byl videt zablesk karty.
            handler.postDelayed(() -> {
                if (host != null) host.onLaunch(app);
            }, 90);
        }
    }

    /**
     * "Kukatko" (viz PeepholeAnimation). Aplikace se spusti v polovine animace,
     * na konci se launcher zavre (pokud je to zapnute v nastaveni). Obojí ridi
     * casovace, ne vykreslovani - pri spusteni VR hry se okno prestane kreslit.
     */
    private void startLaunch(Card k, long now, float animScale) {
        launchCard = k;
        launchCloses = prefs.closeAfterLaunch();
        poppedRect(k, now, tmp);
        final Shader shader;
        if (k.art != null) {
            BitmapShader bs = new BitmapShader(k.art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix m = new Matrix();
            m.setScale(cardW / k.art.getWidth(), cardH / k.art.getHeight());
            m.postTranslate(-cardW / 2f, -cardH / 2f);
            bs.setLocalMatrix(m);
            shader = bs;
        } else {
            int[] pair = Placeholders.colorsFor(k.app.pkg);
            shader = new LinearGradient(-cardW / 2f, -cardH / 2f, cardW / 2f, cardH / 2f,
                    pair[0], pair[1], Shader.TileMode.CLAMP);
        }
        peephole.start(now, animScale, tmp.centerX(), tmp.centerY(), cardW, cardH,
                visualScale(k, now), dp(CARD_RADIUS), shader, k.label, launchCloses);
        final AppEntry app = k.app;
        final long dur = (long) (PeepholeAnimation.DURATION_MS * animScale);
        handler.postDelayed(() -> {
            if (host != null) host.onLaunch(app);
        }, (long) (dur * PeepholeAnimation.FIRE_AT));
        if (launchCloses) {
            handler.postDelayed(() -> {
                if (host != null) host.onLaunchSequenceDone();
            }, dur);
        }
        invalidate();
    }

    private void drawLaunch(Canvas c, long now) {
        if (launchCard == null) return;
        if (!peephole.isRunning(now)) {
            launchCard = null; // konec - odvozeno z casu
            return;
        }
        peephole.draw(c, now, getWidth(), getHeight());
    }


    // =========================================================================
    // Presouvani karet (dlouhe podrzeni)
    // =========================================================================

    private void onLongPress() {
        if (!touching || touchScrolling || pressedCard == null || pressedCard != focused) return;
        final long now = System.nanoTime();
        dragCard = pressedCard;
        pressedCard = null;
        dragging = true;
        dragMoved = false;
        dragOrderChanged = false;
        liftX = px;
        liftY = py;
        lastDragX = px;
        lastDragStepNs = now;
        final float cx = cardLeft(dragCard, now) + cardW / 2f;
        final float cy = scrolledToScreenY(cardTop(dragCard, now) + cardH / 2f);
        dragDX = px - cx;
        dragDY = py - cy;
        dragCard.press.snap(0f);
        dragLift.snap(0f);
        dragLift.set(1f, now);
        dragTilt.snap(0f);
        // Behem presouvani bez fokusu a bez rozmazani - at je videt, kam pustit.
        for (Card k : cards) {
            k.hover.set(0, now);
            k.rotX.set(0, now);
            k.rotY.set(0, now);
        }
        dragCard.hover.snap(0f);
        focused = null;
        blurAmount.set(0f, now);
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        liftNs = now;
        handler.removeCallbacks(menuHoldRunnable);
        handler.postDelayed(menuHoldRunnable, Math.max(100, menuHoldMs() - LONG_PRESS_MS));
        kickScroll();
        invalidate();
    }

    private long menuHoldMs() {
        return prefs != null ? prefs.menuHoldMs() : 1000;
    }

    /** Karta drzena bez pohybu celou prodlevu -> menu karty. */
    private void onMenuHold() {
        if (!dragging || dragCard == null || dragMoved || dragOrderChanged) return;
        final long now = System.nanoTime();
        final Card k = dragCard;
        endDrag(now, true);
        if (host != null) {
            poppedRect(k, now, tmp);
            host.onAppMenu(k.app, new RectF(tmp));
        }
        invalidate();
    }

    private void dragTo(float x, float y, long now) {
        if (dragCard == null) return;
        // Stred tazene karty v obsahovych souradnicich mrizky.
        final float cxc = (x - dragDX) - gridLeft;
        final float cyc = screenToScrolledY(y - dragDY) - gridTop - scrollCur;
        final int rows = Math.max(1, (cards.size() + cols - 1) / cols);
        int col = Math.round((cxc - cardW / 2f) / (cardW + gap));
        int row = Math.round((cyc - cardH / 2f) / (cardH + gap));
        col = Math.max(0, Math.min(cols - 1, col));
        row = Math.max(0, Math.min(rows - 1, row));
        int slot = Math.max(0, Math.min(cards.size() - 1, row * cols + col));
        if (slot != dragCard.index) {
            cards.remove(dragCard);
            cards.add(slot, dragCard);
            for (int i = 0; i < cards.size(); i++) {
                Card k = cards.get(i);
                k.index = i;
                if (k != dragCard) {
                    k.x.set(slotX(i), now);
                    k.y.set(slotY(i), now);
                }
            }
            dragOrderChanged = true;
        }
        // Lehky naklon podle rychlosti tazeni do strany - pusobi fyzicky.
        float dt = Math.max(0.001f, (now - lastDragStepNs) / 1e9f);
        float vx = (x - lastDragX) / dt / d;
        lastDragX = x;
        lastDragStepNs = now;
        dragTilt.set(clamp(vx * 0.012f, -9f, 9f), now);
    }

    /** Automaticke rolovani, kdyz se tazena karta priblizi k hornimu/dolnimu okraji. */
    private boolean stepDragAutoScroll(float dt) {
        if (!dragging) return false;
        final float zone = dp(64);
        float speed = 0f;
        if (py < topBarBottom + zone) speed = (topBarBottom + zone - py) / zone;
        else if (py > frame.bottom - zone) speed = -(py - (frame.bottom - zone)) / zone;
        if (speed == 0f) return false;
        speed = clamp(speed, -1.2f, 1.2f) * dp(900);
        float next = clamp(scrollCur + speed * dt, minScroll, 0f);
        if (next == scrollCur) return false;
        scrollCur = scrollTarget = next;
        dragTo(px, py, System.nanoTime());
        return true;
    }

    private void endDrag(long now, boolean cancelled) {
        if (!dragging || dragCard == null) {
            dragging = false;
            return;
        }
        final Card k = dragCard;
        // Karta doleti z mista, kde byla pustena, na svuj slot.
        final float curLeft = (px - dragDX) - cardW / 2f - gridLeft;
        final float curTop = screenToScrolledY(py - dragDY) - cardH / 2f - gridTop - scrollCur;
        k.x.snap(curLeft);
        k.y.snap(curTop);
        k.x.set(slotX(k.index), now);
        k.y.set(slotY(k.index), now);
        dragging = false;
        dragCard = null;
        dragLift.set(0f, now);
        if (cancelled) return;
        // Pusteni bez pohybu pred uplynutim prodlevy menu = nic (karta jen dosedne).
        if (dragOrderChanged && host != null) {
            List<String> order = new ArrayList<>(cards.size());
            for (Card c : cards) order.add(c.app.pkg);
            host.onOrderChanged(tab, order);
        }
    }

    // =========================================================================
    // Fyzika rolovani
    // =========================================================================

    private void kickScroll() {
        if (!scrollActive) {
            scrollActive = true;
            lastStepNs = System.nanoTime() - 16_000_000L;
        }
        invalidate();
    }

    /** @return true, pokud se jeste bude hybat (potreba dalsi snimek). */
    private boolean stepScroll(long now) {
        if (!scrollActive) return false;
        final float dt = clamp((now - lastStepNs) / 1e9f, 0f, 0.05f);
        lastStepNs = now;
        final float frames = Math.max(dt * 60f, 0.0001f);
        final float lerp = 1f - (float) Math.pow(1f - SCROLL_LERP, frames);
        final float prev = scrollCur;

        stepDragAutoScroll(dt);

        if (touching && touchScrolling) {
            // Primo za prstem/laserem (resi onTouchEvent).
        } else if (flingV != 0f) {
            scrollCur += flingV * dt;
            final boolean out = scrollCur > 0f || scrollCur < minScroll;
            flingV *= (float) Math.pow(out ? 0.55f : 0.955f, frames);
            if (Math.abs(flingV) < dp(30)) flingV = 0f;
            scrollTarget = scrollCur;
        } else if (!dragging) {
            // Gumovy navrat, kdyz je obsah pretazeny za okraj.
            if (scrollTarget > 0f) scrollTarget += (0f - scrollTarget) * lerp;
            else if (scrollTarget < minScroll) scrollTarget += (minScroll - scrollTarget) * lerp;
            scrollCur += (scrollTarget - scrollCur) * lerp;
        }

        final float v60 = (scrollCur - prev) / frames / d; // dp za 1/60 s
        squish = 1f - Math.min(SQUISH_MAX, Math.abs(v60) * SQUISH_PER_DP);
        // Obsah jede nahoru -> stlaci se k hornimu okraji, jinak ke spodnimu.
        squishPivot = v60 < 0 ? topBarBottom : frame.bottom;

        final boolean settled = !(touching && touchScrolling) && !dragging && flingV == 0f
                && Math.abs(scrollTarget - scrollCur) < 0.08f * d && Math.abs(v60) < 0.08f
                && scrollTarget <= 0f && scrollTarget >= minScroll;
        if (settled) {
            scrollCur = scrollTarget = clamp(scrollCur, minScroll, 0f);
            squish = 1f;
            scrollActive = false;
            return false;
        }
        return true;
    }

    // =========================================================================
    // Kresleni
    // =========================================================================

    @Override
    protected void onDraw(Canvas canvas) {
        if (prefs == null || frame.isEmpty()) return;
        final long now = System.nanoTime();
        final boolean again = stepScroll(now);
        // Behem rolovani jede karta pod stojicim ukazatelem - fokus prepocitat kazdy snimek.
        if (again) updateFocus(now);

        drawGlass(canvas);
        if (canvas.isHardwareAccelerated()) {
            drawContentLayer(canvas, now);
            drawTopBarBackdrop(canvas, now);
        } else {
            canvas.save();
            canvas.clipRect(frame.left, topBarBottom, frame.right, frame.bottom);
            drawCardsInto(canvas, now, frame.top, frame.bottom, 0f, false);
            canvas.restore();
        }
        drawTopBar(canvas, now);
        drawEmptyState(canvas);
        drawFocusedCard(canvas, now);
        drawDraggedCard(canvas, now);
        drawLaunch(canvas, now);

        if (again || dragging || launchCard != null || isAnimating(now)) postInvalidateOnAnimation();
    }

    private boolean isAnimating(long now) {
        if (blurAmount.active(now) || tabPos.active(now) || contentFade.active(now)
                || brandHover.active(now) || brandTap.active(now) || batteryHover.active(now)
                || dragLift.active(now)
                || dragTilt.active(now)) return true;
        for (Eased e : tabHover) if (e.active(now)) return true;
        for (Card k : cards) {
            if (k.hover.active(now) || k.rotX.active(now) || k.rotY.active(now)
                    || k.press.active(now) || k.x.active(now) || k.y.active(now)
                    || k.flash.active(now)) return true;
        }
        return false;
    }

    private void drawGlass(Canvas c) {
        final float r = dp(FRAME_RADIUS);
        final int alpha = Math.round(prefs.glassAlpha() * 2.55f);
        if (prefs.popoutMargin()) {
            // Jemna modra zare okolo skla (jen v pruhlednem okraji - uvnitr oriznuta).
            // Stin tvaru pocita GPU analyticky, zadna bitmapa ani vrstva navic.
            c.save();
            c.clipOutPath(framePath);
            fill.setShader(null);
            fill.setColor(0xFF10151F);
            fill.setShadowLayer(dp(16), 0, dp(3), 0x4838BDF8);
            c.drawRoundRect(frame, r, r, fill);
            fill.clearShadowLayer();
            c.restore();
        }
        // Pozadi rgba(16, 21, 31, 0.42) z preview (kryti nastavitelne).
        fill.setShader(null);
        fill.setColor(Color.argb(alpha, 16, 21, 31));
        c.drawRoundRect(frame, r, r, fill);
        // Okraj 1.5px rgba(255,255,255,0.16) + svetly horni hrana.
        final float sw = dp(1.5f);
        stroke.setStrokeWidth(sw);
        stroke.setShader(null);
        stroke.setColor(0x29FFFFFF);
        tmp.set(frame);
        tmp.inset(sw / 2f, sw / 2f);
        c.drawRoundRect(tmp, r, r, stroke);
        stroke.setShader(frameHighlight);
        stroke.setColor(Color.WHITE);
        c.drawRoundRect(tmp, r, r, stroke);
        stroke.setShader(null);
    }

    /** Karty pod listou, ve vlastni vrstve s jedinym efektem hloubky ostrosti. */
    private void drawContentLayer(Canvas canvas, long now) {
        final int w = Math.round(frame.width());
        final int h = Math.round(frame.bottom) - Math.round(topBarBottom);
        if (w <= 0 || h <= 0) return;
        RecordingCanvas rc = contentNode.beginRecording(w, h);
        try {
            rc.translate(-Math.round(frame.left), -Math.round(topBarBottom));
            drawCardsInto(rc, now, topBarBottom, frame.bottom, dimStrength(), true);
        } finally {
            contentNode.endRecording();
        }

        final float amt = blurAmount.get(now);
        final int dof = prefs.dofMode();
        if (dof != Prefs.DOF_OFF && amt > 0.01f && !dragging) {
            final float k = (dof == Prefs.DOF_STRONG ? 2f : 1f) * amt;
            contentNode.setRenderEffect(depthBlur.effect(
                    blurCx - Math.round(frame.left), blurCy - Math.round(topBarBottom),
                    cardW * 0.55f, dp(850), dp(2.2f) * k, dp(4.4f) * k));
        } else {
            contentNode.setRenderEffect(null);
        }
        contentNode.setAlpha(contentFade.get(now));
        canvas.drawRenderNode(contentNode);
    }

    /** Matne sklo horni listy: rozmazana kopie karet, ktere pod ni zajely. */
    private void drawTopBarBackdrop(Canvas canvas, long now) {
        boolean any = false;
        for (Card k : cards) {
            if (k == focused || k == launchCard || (dragging && k == dragCard)) continue;
            float t = scrolledToScreenY(cardTop(k, now));
            float b = scrolledToScreenY(cardTop(k, now) + cardH);
            if (b > frame.top && t < topBarBottom) {
                any = true;
                break;
            }
        }
        if (!any) return;
        final int w = Math.round(frame.width());
        final int h = Math.round(topBarBottom) - Math.round(frame.top);
        RecordingCanvas rc = backdropNode.beginRecording(w, h);
        try {
            rc.translate(-Math.round(frame.left), -Math.round(frame.top));
            // Kopie pod listou je silne rozmazana - naklon neni videt, kresli se plocha
            // (a hlavne: RenderNode karty nesmi byt ve dvou rodicich naraz).
            drawCardsInto(rc, now, frame.top, topBarBottom, 0f, false);
        } finally {
            backdropNode.endRecording();
        }
        backdropNode.setAlpha(contentFade.get(now));
        canvas.drawRenderNode(backdropNode);
    }

    private float dimStrength() {
        return prefs.dofMode() == Prefs.DOF_OFF ? 0.38f : 0.10f;
    }

    /**
     * Nakresli vsechny karty krome hovernute a tazene, jen ty, ktere zasahuji
     * do svisleho rozsahu [top, bottom] obrazovky. Karty, ktere jeste
     * dojizdeji z hoveru, se kresli az nakonec (jsou "nad" ostatnimi).
     */
    private void drawCardsInto(Canvas c, long now, float top, float bottom, float dim,
                               boolean allow3d) {
        c.save();
        c.translate(0, squishPivot);
        c.scale(1f, squish);
        c.translate(0, -squishPivot);
        final float amt = blurAmount.get(now);
        final float maxDist = dp(850);
        List<Card> late = null;
        for (Card k : cards) {
            if (k == focused || k == launchCard || (dragging && k == dragCard)) continue;
            final float t = cardTop(k, now);
            final float st = scrolledToScreenY(t - cardH * 0.3f);
            final float sb = scrolledToScreenY(t + cardH * 1.3f);
            if (sb < top || st > bottom) continue;
            if (k.hover.get(now) > 0.001f || k.x.active(now) || k.y.active(now)) {
                if (late == null) late = new ArrayList<>();
                late.add(k);
                continue;
            }
            drawCard(c, k, now, dimFor(k, now, amt, dim, maxDist), allow3d);
        }
        if (late != null) {
            late.sort((a, b) -> Float.compare(a.hover.get(now), b.hover.get(now)));
            for (Card k : late) drawCard(c, k, now, dimFor(k, now, amt, dim, maxDist), allow3d);
        }
        c.restore();
    }

    private float dimFor(Card k, long now, float amt, float dim, float maxDist) {
        if (dim <= 0f || amt <= 0f) return 0f;
        float cx = cardLeft(k, now) + cardW / 2f;
        float cy = scrolledToScreenY(cardTop(k, now) + cardH / 2f);
        float dist = (float) Math.hypot(cx - blurCx, cy - blurCy);
        float t = clamp(dist / maxDist, 0f, 1f);
        return amt * dim * (0.55f + 0.45f * t);
    }

    private void drawFocusedCard(Canvas c, long now) {
        if (focused == null || focused == launchCard) return;
        c.save();
        c.translate(0, squishPivot);
        c.scale(1f, squish);
        c.translate(0, -squishPivot);
        drawCard(c, focused, now, 0f, true);
        c.restore();
    }

    private void drawDraggedCard(Canvas c, long now) {
        if (!dragging || dragCard == null) return;
        final float lift = dragLift.get(now);
        final float cx = px - dragDX;
        final float cy = py - dragDY;
        final float scale = 1f + 0.12f * lift;
        drawCardAt(c, dragCard, now, cx, cy, scale, 0f,
                dragTilt.get(now), Math.max(lift, 0.6f), 0f, true);
        if (!dragMoved) {
            // Tenky ukazatel pod kartou: az se naplni, otevre se menu karty.
            final float total = Math.max(1f, menuHoldMs() - LONG_PRESS_MS);
            final float p = clamp((now - liftNs) / 1e6f / total, 0f, 1f);
            final float w = cardW * 0.42f * scale;
            final float top = cy + cardH * scale / 2f + dp(10);
            final float bh = dp(4);
            fill.setShader(null);
            fill.setColor(0x40FFFFFF);
            c.drawRoundRect(cx - w / 2f, top, cx + w / 2f, top + bh, bh / 2f, bh / 2f, fill);
            fill.setColor(Glass.ACCENT);
            c.drawRoundRect(cx - w / 2f, top, cx - w / 2f + w * p, top + bh, bh / 2f, bh / 2f, fill);
        }
    }

    private void drawCard(Canvas c, Card k, long now, float dim, boolean allow3d) {
        final float cx = cardLeft(k, now) + cardW / 2f;
        final float cy = cardTop(k, now) + cardH / 2f;
        final float h = k.hover.get(now);
        drawCardAt(c, k, now, cx, cy, visualScale(k, now), k.rotX.get(now), k.rotY.get(now),
                h, dim, allow3d);
    }

    /**
     * Jedna karta se stredem v (cx, cy). Naklonena karta (rx/ry) jde pres vlastni
     * RenderNode s rotaci - stejna cesta, jakou Android pouziva pro View.setRotationX/Y.
     * (Vlastni perspektivni matice pres android.graphics.Camera se v nekterych
     * stavech vubec nevykreslila - odhaleno v Robolectric snimku.)
     */
    private void drawCardAt(Canvas c, Card k, long now, float cx, float cy, float scale,
                            float rx, float ry, float h, float dim, boolean allow3d) {
        if (allow3d && c.isHardwareAccelerated() && (Math.abs(rx) > 0.01f || Math.abs(ry) > 0.01f)) {
            drawCardNode(c, k, now, cx, cy, scale, rx, ry, h, dim);
            return;
        }
        c.save();
        c.translate(cx, cy);
        if (scale != 1f) c.scale(scale, scale);
        drawCardBody(c, k, now, h, dim);
        c.restore();
    }

    private void drawCardNode(Canvas c, Card k, long now, float cx, float cy, float scale,
                              float rx, float ry, float h, float dim) {
        if (k.node == null) k.node = new RenderNode("neo-card");
        // Rezerva kolem karty, aby se nezarizl velky stin pod kartou.
        final float m = (hoverShadow != null ? hoverShadow.margin : dp(90)) + dp(30);
        final int nw = (int) Math.ceil(cardW + 2 * m);
        final int nh = (int) Math.ceil(cardH + 2 * m);
        final RenderNode n = k.node;
        RecordingCanvas rc = n.beginRecording(nw, nh);
        try {
            rc.translate(nw / 2f, nh / 2f);
            drawCardBody(rc, k, now, h, dim);
        } finally {
            n.endRecording();
        }
        n.setPosition(0, 0, nw, nh);
        n.setClipToBounds(false);
        n.setTranslationX(cx - nw / 2f);
        n.setTranslationY(cy - nh / 2f);
        n.setPivotX(nw / 2f);
        n.setPivotY(nh / 2f);
        n.setScaleX(scale);
        n.setScaleY(scale);
        n.setRotationX(rx);
        n.setRotationY(ry);
        n.setCameraDistance(dp(PERSPECTIVE) / 72f);
        c.drawRenderNode(n);
    }

    /** Obsah karty v lokalnich souradnicich se stredem v (0, 0). */
    private void drawCardBody(Canvas c, Card k, long now, float h, float dim) {
        final float w2 = cardW / 2f, h2 = cardH / 2f;
        final float r = dp(CARD_RADIUS);

        // Stiny: klidovy 0 6px 20px rgba(0,0,0,.4) -> hover 0 26px 55px rgba(0,0,0,.85)
        // + bila zare 0 0 32px rgba(255,255,255,.3).
        if (restShadow != null) {
            if (h < 0.999f) {
                spritePaint.setColor(Color.argb(Math.round(102 * (1f - h)), 0, 0, 0));
                restShadow.draw(c, -w2, -h2, w2, h2, dp(6), spritePaint);
                // Plochy (ostry) stin pod kartou - "flat shadow" styl.
                fill.setShader(null);
                fill.setColor(Color.argb(Math.round(64 * (1f - h)), 0, 0, 0));
                c.drawRoundRect(-w2 + dp(2), -h2 + dp(5), w2 - dp(2), h2 + dp(5), r, r, fill);
            }
            if (h > 0.001f) {
                // Tmavy stin slabsi nez v preview (.85 -> .6), jinak by barevnou zari prebil.
                spritePaint.setColor(Color.argb(Math.round(153 * h), 0, 0, 0));
                hoverShadow.draw(c, -w2, -h2, w2, h2, dp(26) * h, spritePaint);
                // Zare v barve obrazku hry (ambientni svetlo jako u PS5) + jemna bila.
                // Siroka (sprite velkeho stinu), aby presahla tmavy stin i okoli karty.
                final int glow = artwork != null ? artwork.glowColor(k.app.pkg, 0xFFBFE6FF) : 0xFFBFE6FF;
                // Aditivne (PLUS) - svetlo se pricte k okoli, misto aby ho zasedilo.
                spritePaint.setColor((glow & 0x00FFFFFF) | (Math.round(200 * h) << 24));
                spritePaint.setBlendMode(BlendMode.PLUS);
                hoverShadow.draw(c, -w2, -h2, w2, h2, dp(3) * h, spritePaint);
                spritePaint.setBlendMode(null);
                spritePaint.setColor(Color.argb(Math.round(46 * h), 255, 255, 255));
                glowSprite.draw(c, -w2, -h2, w2, h2, 0f, spritePaint);
            }
        }

        // Obrazek (cover) oriznuty na zaoblene rohy pres BitmapShader - bez clipPath.
        Bitmap art = artwork != null ? artwork.get(k.app) : null;
        if (art != k.art) {
            k.art = art;
            k.artShader = art != null
                    ? new BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) : null;
            k.artShaderW = -1;
        }
        if (k.artShader != null) {
            if (k.artShaderW != cardW) {
                shaderMatrix.setScale(cardW / k.art.getWidth(), cardH / k.art.getHeight());
                shaderMatrix.postTranslate(-w2, -h2);
                k.artShader.setLocalMatrix(shaderMatrix);
                k.artShaderW = cardW;
            }
            artPaint.setShader(k.artShader);
        } else {
            if (k.placeholder == null || k.placeholderW != cardW) {
                int[] pair = Placeholders.colorsFor(k.app.pkg);
                k.placeholder = new LinearGradient(-w2, -h2, w2, h2, pair[0], pair[1],
                        Shader.TileMode.CLAMP);
                k.placeholderW = cardW;
            }
            artPaint.setShader(k.placeholder);
        }
        c.drawRoundRect(-w2, -h2, w2, h2, r, r, artPaint);
        artPaint.setShader(null);

        // Tmavy prechod dole (::after v preview), pod nazvem.
        shadePaint.setShader(bottomShade);
        c.drawRoundRect(-w2, -h2, w2, h2, r, r, shadePaint);
        shadePaint.setShader(null);

        if (dim > 0.004f) {
            fill.setShader(null);
            fill.setColor(Color.argb(Math.round(255 * dim), 4, 6, 10));
            c.drawRoundRect(-w2, -h2, w2, h2, r, r, fill);
        }
        final float flash = k.flash.get(now);
        if (flash > 0.004f) {
            fill.setShader(null);
            fill.setColor(Color.argb(Math.round(110 * flash), 255, 255, 255));
            c.drawRoundRect(-w2, -h2, w2, h2, r, r, fill);
        }

        // Ramecek 1px: rgba(255,255,255,.12) -> .85 pri hoveru.
        final float bw = dp(1f);
        stroke.setShader(null);
        stroke.setStrokeWidth(bw);
        stroke.setColor(Color.argb(Math.round(255 * lerp(0.12f, 0.85f, h)), 255, 255, 255));
        c.drawRoundRect(-w2 + bw / 2f, -h2 + bw / 2f, w2 - bw / 2f, h2 - bw / 2f,
                r - bw / 2f, r - bw / 2f, stroke);

        drawTitlePill(c, k, h, w2, h2);
    }

    /** Plovouci "pilulka" s nazvem: rgba(10,14,22,.9), radius 999, 11.5px, ellipsis. */
    private void drawTitlePill(Canvas c, Card k, float h, float w2, float h2) {
        drawTitlePill(c, k, h, w2, h2, 1f);
    }

    private void drawTitlePill(Canvas c, Card k, float h, float w2, float h2, float alpha) {
        final float padH = dp(12), padV = dp(3);
        final float maxPillW = cardW * 0.86f;
        if (k.shownLabelFor != cardW) {
            final float maxText = Math.max(dp(10), maxPillW - 2 * padH);
            k.shownLabel = TextUtils.ellipsize(k.label, pillText, maxText,
                    TextUtils.TruncateAt.END).toString();
            k.shownLabelWidth = pillText.measureText(k.shownLabel);
            k.shownLabelFor = cardW;
        }
        final Paint.FontMetrics fm = pillText.getFontMetrics();
        final float textH = fm.descent - fm.ascent;
        final float pillH = textH + 2 * padV;
        final float pillW = Math.min(maxPillW, k.shownLabelWidth + 2 * padH);
        final float bottom = h2 - dp(8);
        final float top = bottom - pillH;
        final float left = -pillW / 2f, right = pillW / 2f;
        final float rr = pillH / 2f;

        fill.setShader(null);
        // Plochy stin pod pilulkou.
        fill.setColor(Color.argb(Math.round(90 * alpha), 0, 0, 0));
        c.drawRoundRect(left, top + dp(2), right, bottom + dp(2), rr, rr, fill);
        fill.setColor(Color.argb(Math.round(255 * lerp(0.90f, 0.96f, h) * alpha),
                Math.round(lerp(10, 6, h)), Math.round(lerp(14, 9, h)), Math.round(lerp(22, 15, h))));
        c.drawRoundRect(left, top, right, bottom, rr, rr, fill);
        final float bw = dp(1f);
        stroke.setStrokeWidth(bw);
        stroke.setColor(Color.argb(Math.round(255 * lerp(0.28f, 0.65f, h) * alpha), 255, 255, 255));
        c.drawRoundRect(left + bw / 2f, top + bw / 2f, right - bw / 2f, bottom - bw / 2f,
                rr, rr, stroke);
        final float baseline = top + padV - fm.ascent;
        pillText.setAlpha(Math.round(255 * alpha));
        c.drawText(k.shownLabel, -k.shownLabelWidth / 2f, baseline, pillText);
        pillText.setAlpha(255);
    }

    // --- Horni lista ------------------------------------------------------------

    private void drawTopBar(Canvas c, long now) {
        // Svetle matne sklo rgba(255,255,255,.12).
        fill.setShader(null);
        fill.setColor(0x1FFFFFFF);
        c.drawPath(topBarPath, fill);
        // Stin listy dolu pres karty.
        fill.setShader(barShadow);
        fill.setColor(Color.WHITE);
        c.save();
        c.clipRect(frame.left + dp(1.5f), topBarBottom, frame.right - dp(1.5f), topBarBottom + dp(18));
        c.drawRect(frame.left, topBarBottom, frame.right, topBarBottom + dp(18), fill);
        c.restore();
        fill.setShader(null);
        // Spodni hrana 1.5px rgba(255,255,255,.24).
        fill.setColor(0x3DFFFFFF);
        c.drawRect(frame.left + dp(1.5f), topBarBottom - dp(1.5f), frame.right - dp(1.5f),
                topBarBottom, fill);

        final float midY = (frame.top + topBarBottom) / 2f;
        drawBrand(c, now, midY);
        drawStatus(c, now, midY);
        drawTabs(c, now, midY);
    }

    /** Plochy (ostry, bez rozmazani) stin posunuty dolu - premium "flat shadow" detail. */
    private void flatShadow(Canvas c, RectF r, float radius) {
        fill.setShader(null);
        fill.setColor(0x33000000);
        c.drawRoundRect(r.left, r.top + dp(2), r.right, r.bottom + dp(2), radius, radius, fill);
    }

    private void drawBrand(Canvas c, long now, float midY) {
        final float left = frame.left + dp(28);
        final float icon = dp(32);
        final float textX = left + icon + dp(12);
        final float textW = brandText.measureText("Neo");
        brandRect.set(left - dp(8), midY - dp(22), textX + textW + dp(10), midY + dp(22));

        final float hv = brandHover.get(now);
        if (hv > 0.004f) {
            fill.setShader(null);
            fill.setColor(Color.argb(Math.round(26 * hv), 255, 255, 255));
            c.drawRoundRect(brandRect, dp(14), dp(14), fill);
        }
        // Ikona: cerny ctverec 32px, radius 8, ramecek rgba(255,255,255,.3), bila tecka 14px.
        final float it = midY - icon / 2f;
        fill.setShader(null);
        fill.setColor(0x47000000);
        c.drawRoundRect(left, it + dp(2), left + icon, it + icon + dp(2), dp(8), dp(8), fill);
        fill.setColor(0xFF000000);
        c.drawRoundRect(left, it, left + icon, it + icon, dp(8), dp(8), fill);
        stroke.setShader(null);
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(0x4DFFFFFF);
        c.drawRoundRect(left + dp(0.5f), it + dp(0.5f), left + icon - dp(0.5f),
                it + icon - dp(0.5f), dp(7.5f), dp(7.5f), stroke);
        fill.setColor(Color.WHITE);
        // Klepnuti: tecka kratce "pulzne" s modrou zari (odezva pro 5x klepnuti).
        final float tap = brandTap.get(now);
        if (tap > 0.004f) fill.setShadowLayer(dp(10) * tap, 0, 0, Color.argb(Math.round(220 * tap), 56, 189, 248));
        c.drawCircle(left + icon / 2f, midY, dp(7) * (1f + 0.25f * tap), fill);
        fill.clearShadowLayer();

        final Paint.FontMetrics fm = brandText.getFontMetrics();
        c.drawText("Neo", textX, midY - (fm.ascent + fm.descent) / 2f, brandText);
    }

    private void drawTabs(Canvas c, long now, float midY) {
        final float padX = dp(16);
        final float itemH = dp(34);
        float[] widths = new float[TAB_NAMES.length];
        float total = 0;
        for (int i = 0; i < TAB_NAMES.length; i++) {
            widths[i] = tabText.measureText(TAB_NAMES[i]) + 2 * padX;
            total += widths[i];
        }
        final float inner = dp(4);
        float left = frame.centerX() - total / 2f - inner;
        // Nesmi se prekryvat se znackou ani se stavem.
        left = Math.max(left, brandRect.right + dp(12));
        left = Math.min(left, statusRect.left - dp(12) - total - 2 * inner);
        tabsRect.set(left, midY - itemH / 2f - inner, left + total + 2 * inner, midY + itemH / 2f + inner);

        fill.setShader(null);
        flatShadow(c, tabsRect, tabsRect.height() / 2f);
        fill.setColor(0x38000000);
        c.drawRoundRect(tabsRect, tabsRect.height() / 2f, tabsRect.height() / 2f, fill);
        stroke.setShader(null);
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(0x24FFFFFF);
        c.drawRoundRect(tabsRect, tabsRect.height() / 2f, tabsRect.height() / 2f, stroke);

        float x = left + inner;
        for (int i = 0; i < TAB_NAMES.length; i++) {
            tabRects[i].set(x, midY - itemH / 2f, x + widths[i], midY + itemH / 2f);
            x += widths[i];
        }
        // Posuvny indikator vybrane zalozky.
        final float pos = clamp(tabPos.get(now), 0f, TAB_NAMES.length - 1);
        final int i0 = (int) Math.floor(pos);
        final int i1 = Math.min(TAB_NAMES.length - 1, i0 + 1);
        final float f = pos - i0;
        final float il = lerp(tabRects[i0].left, tabRects[i1].left, f);
        final float ir = lerp(tabRects[i0].right, tabRects[i1].right, f);
        // Indikator: sklo s jemnou modrou zari.
        fill.setColor(0x2EFFFFFF);
        fill.setShadowLayer(dp(10), 0, 0, 0x5538BDF8);
        c.drawRoundRect(il, midY - itemH / 2f, ir, midY + itemH / 2f, itemH / 2f, itemH / 2f, fill);
        fill.clearShadowLayer();
        stroke.setColor(0x6638BDF8);
        c.drawRoundRect(il + dp(0.5f), midY - itemH / 2f + dp(0.5f), ir - dp(0.5f),
                midY + itemH / 2f - dp(0.5f), itemH / 2f, itemH / 2f, stroke);

        final Paint.FontMetrics fm = tabText.getFontMetrics();
        final float base = midY - (fm.ascent + fm.descent) / 2f;
        for (int i = 0; i < TAB_NAMES.length; i++) {
            float hv = tabHover[i].get(now);
            if (hv > 0.004f && i != tab) {
                fill.setColor(Color.argb(Math.round(20 * hv), 255, 255, 255));
                c.drawRoundRect(tabRects[i], itemH / 2f, itemH / 2f, fill);
            }
            float a = i == tab ? 1f : lerp(0.62f, 0.95f, hv);
            tabText.setColor(Color.argb(Math.round(255 * a), 255, 255, 255));
            c.drawText(TAB_NAMES[i], tabRects[i].centerX(), base, tabText);
        }
    }

    private void drawStatus(Canvas c, long now, float midY) {
        final long minute = System.currentTimeMillis() / 60000L;
        if (minute != clockMinute) {
            clockMinute = minute;
            Calendar cal = Calendar.getInstance();
            clockTime = String.format(Locale.ROOT, "%02d:%02d",
                    cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
            clockDate = cal.get(Calendar.DAY_OF_MONTH) + ". " + (cal.get(Calendar.MONTH) + 1) + ".";
        }
        // Baterie: pilulka s procenty, barva dle nabiti, bile blesky pri nabijeni.
        final String pct = batteryLevel >= 0 ? batteryLevel + "%" : "–";
        final int bolts = charging ? (fastCharging ? 2 : 1) : 0;
        final float boltW = dp(8.5f), boltH = dp(12f), boltGap = dp(1.5f);
        final float pctW = batteryText.measureText(pct);
        final float batW = dp(10) + pctW + (bolts > 0 ? dp(5) + bolts * boltW + (bolts - 1) * boltGap : 0)
                + dp(10);
        final float batH = dp(24);

        final float timeW = clockText.measureText(clockTime);
        final float dateW = clockText.measureText(clockDate);
        final float widgetW = dp(16) + timeW + dp(20) + dateW + dp(16) + batW + dp(16);
        final float widgetH = dp(36);
        final float right = frame.right - dp(28);
        statusRect.set(right - widgetW, midY - widgetH / 2f, right, midY + widgetH / 2f);

        fill.setShader(null);
        flatShadow(c, statusRect, dp(14));
        fill.setColor(0x40000000);
        c.drawRoundRect(statusRect, dp(14), dp(14), fill);
        stroke.setShader(null);
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(0x2EFFFFFF);
        c.drawRoundRect(statusRect, dp(14), dp(14), stroke);

        final Paint.FontMetrics fm = clockText.getFontMetrics();
        final float base = midY - (fm.ascent + fm.descent) / 2f;
        float x = statusRect.left + dp(16);
        c.drawText(clockTime, x, base, clockText);
        x += timeW + dp(20);
        c.drawText(clockDate, x, base, clockText);
        x += dateW + dp(16);

        batteryRect.set(x, midY - batH / 2f, x + batW, midY + batH / 2f);
        final int color = batteryColor();
        final float bh = batteryHover.get(now);
        fill.setColor(Color.argb(Math.round(lerp(102, 51, bh)), bh > 0.5f ? 255 : 0,
                bh > 0.5f ? 255 : 0, bh > 0.5f ? 255 : 0));
        c.drawRoundRect(batteryRect, dp(8), dp(8), fill);
        stroke.setColor((color & 0x00FFFFFF) | 0x66000000);
        c.drawRoundRect(batteryRect.left + dp(0.5f), batteryRect.top + dp(0.5f),
                batteryRect.right - dp(0.5f), batteryRect.bottom - dp(0.5f), dp(7.5f), dp(7.5f), stroke);

        final Paint.FontMetrics bfm = batteryText.getFontMetrics();
        batteryText.setColor(color);
        float bx = batteryRect.left + dp(10);
        c.drawText(pct, bx, midY - (bfm.ascent + bfm.descent) / 2f, batteryText);
        bx += pctW + dp(5);
        if (bolts > 0) {
            fill.setColor(Color.WHITE);
            fill.setShadowLayer(dp(2), 0, 0, 0xCCFFFFFF);
            for (int i = 0; i < bolts; i++) {
                c.save();
                c.translate(bx, midY - boltH / 2f);
                c.scale(boltW / 14f, boltH / 20f); // path zabira x 4..18, y 2..22
                c.translate(-4f, -2f);
                c.drawPath(boltPath, fill);
                c.restore();
                bx += boltW + boltGap;
            }
            fill.clearShadowLayer();
        }
    }

    private int batteryColor() {
        if (batteryLevel > 80) return 0xFF22C55E;
        if (batteryLevel > 50) return 0xFF3B82F6;
        if (batteryLevel > 20) return 0xFFF97316;
        if (batteryLevel >= 0) return 0xFFEF4444;
        return 0xFFFFFFFF;
    }

    private void drawEmptyState(Canvas c) {
        if (!cards.isEmpty()) return;
        final String msg = !appsLoaded ? "Načítám aplikace…"
                : tab == Prefs.TAB_GAMES ? "Žádné VR hry" : "Žádné aplikace";
        final float cy = (topBarBottom + frame.bottom) / 2f;
        c.drawText(msg, frame.centerX(), cy, emptyText);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
