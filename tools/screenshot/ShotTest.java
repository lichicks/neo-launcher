package neoshot;

import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.PixelCopy;

import com.neolauncher.art.ArtworkLoader;
import com.neolauncher.data.AppEntry;
import com.neolauncher.data.Prefs;
import com.neolauncher.ui.NeoLauncherView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w1110dp-h664dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ShotTest {
    static final String[][] GAMES = {
            {"com.beatgames.beatsaber", "Beat Saber"},
            {"com.cloudheadgames.pistolwhip", "Pistol Whip"},
            {"com.kluge.SynthRiders", "Synth Riders"},
            {"com.MightyCoconut.WalkaboutMiniGolf", "Walkabout Mini Golf"},
            {"com.AnotherAxiom.GorillaTag", "Gorilla Tag"},
            {"com.StressLevelZero.BONELAB", "BONELAB"},
            {"com.Warpfrog.BladeAndSorcery", "Blade & Sorcery: Nomad"},
            {"com.owlchemylabs.jobsimulator", "Job Simulator"},
            {"com.vrchat.oculus.quest", "VRChat"},
            {"com.AgainstGravity.RecRoom", "Rec Room"},
            {"com.fitxr.boxvr", "FitXR"},
            {"com.bigscreenvr.bigscreen", "Bigscreen Beta"},
            {"VirtualDesktop.Android", "Virtual Desktop"},
    };

    private static void pump(long ms) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(20);
        }
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void capture(Activity a, NeoLauncherView v, String out) throws Exception {
        Bitmap bmp = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
        final int[] res = {-1};
        PixelCopy.request(a.getWindow(), bmp, r -> res[0] = r, new Handler(Looper.getMainLooper()));
        shadowOf(Looper.getMainLooper()).idle();
        System.out.println("PixelCopy result=" + res[0] + " size=" + bmp.getWidth() + "x" + bmp.getHeight());
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
    }

    private static void hover(NeoLauncherView v, float x, float y) {
        long t = SystemClock.uptimeMillis();
        MotionEvent e = MotionEvent.obtain(t, t, MotionEvent.ACTION_HOVER_MOVE, x, y, 0);
        e.setSource(InputDevice.SOURCE_MOUSE);
        v.dispatchGenericMotionEvent(e);
        e.recycle();
    }

    @Test
    public void shot() throws Exception {
        System.setProperty("robolectric.pixelCopyRenderMode", "hardware");
        final String banners = System.getProperty("neo.banners");
        final String outDir = System.getProperty("neo.out");

        org.robolectric.android.controller.ActivityController<Activity> ctl =
                Robolectric.buildActivity(Activity.class);
        ctl.get().setTheme(android.R.style.Theme_Material_NoActionBar_Fullscreen);
        Activity a = ctl.setup().get();
        a.getWindow().setBackgroundDrawable(new ColorDrawable(0));

        File cache = new File(a.getFilesDir(), "art-cache");
        cache.mkdirs();
        for (String[] g : GAMES) {
            File src = new File(banners, g[0] + ".img");
            Files.copy(src.toPath(), new File(cache, g[0] + ".webp").toPath());
        }

        Prefs prefs = new Prefs(a);
        ArtworkLoader art = new ArtworkLoader(a);
        art.setOnlineEnabled(false);
        NeoLauncherView v = new NeoLauncherView(a);
        v.bind(prefs, art);
        a.setContentView(v);
        pump(200);
        System.out.println("view size " + v.getWidth() + "x" + v.getHeight());

        List<AppEntry> apps = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (String[] g : GAMES) {
            apps.add(new AppEntry(g[0], g[1], AppEntry.TYPE_VR, false));
            labels.add(g[1]);
        }
        v.setApps(apps, labels, false);
        v.setBattery(92, true, true);
        pump(2500); // obrazky se nacitaji na pozadi
        capture(a, v, outDir + "/warmup.png");
        pump(1500);
        capture(a, v, outDir + "/neo-idle.png");

        final float d = a.getResources().getDisplayMetrics().density;
        // Kousek odrolovat (thumbstick), at prvni rada zajede pod matnou listu.
        {
            long t = SystemClock.uptimeMillis();
            MotionEvent.PointerProperties[] pp = {new MotionEvent.PointerProperties()};
            pp[0].id = 0;
            pp[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;
            MotionEvent.PointerCoords[] pc = {new MotionEvent.PointerCoords()};
            pc[0].x = 600 * d;
            pc[0].y = 400 * d;
            pc[0].setAxisValue(MotionEvent.AXIS_VSCROLL, -Float.parseFloat(System.getProperty("neo.scroll", "0.9")));
            MotionEvent e = MotionEvent.obtain(t, t, MotionEvent.ACTION_SCROLL, 1, pp, pc, 0, 0,
                    1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0);
            v.dispatchGenericMotionEvent(e);
            e.recycle();
            for (int i = 0; i < 90; i++) {
                capture(a, v, outDir + "/scrolling.png"); // kazdy snimek posune fyziku rolovani
                Thread.sleep(16);
            }
        }
        System.out.println("scroll done");
        float frameL = 24, frameT = 30, frameW = 1110 - 48;
        float gridL = frameL + 24, gridT = frameT + 76;
        float cardW = (frameW - 48 - 3 * 15) / 4f, cardH = cardW / 1.6f;
        float scroll = Float.parseFloat(System.getProperty("neo.scrollDp", "0"));
        float left = gridL + 1 * (cardW + 15), top = gridT + 1 * (cardH + 15) - scroll;
        hover(v, (left + cardW * 0.5f) * d, (top + cardH * 0.5f) * d);
        pump(150);
        hover(v, (left + cardW * 0.80f) * d, (top + cardH * 0.28f) * d);
        pump(900);
        dumpState(v);
        capture(a, v, outDir + "/neo-hover.png");
        dumpState(v);

        // Kliknuti na kartu -> animace spusteni "kukatko", 6x zpomalena
        // (jako "Meritko animace" ve vyvojarskych volbach), snimky po fazich.
        java.lang.reflect.Method setScale =
                android.animation.ValueAnimator.class.getDeclaredMethod("setDurationScale", float.class);
        setScale.setAccessible(true);
        setScale.invoke(null, 6f);
        float tx = (left + cardW * 0.80f) * d, ty = (top + cardH * 0.28f) * d;
        long t0 = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, tx, ty, 0);
        v.dispatchTouchEvent(down);
        MotionEvent up = MotionEvent.obtain(t0, t0 + 80, MotionEvent.ACTION_UP, tx, ty, 0);
        v.dispatchTouchEvent(up);
        long start = System.currentTimeMillis();
        long[] at = {700, 1900, 2600, 3300, 4600};
        for (int i = 0; i < at.length; i++) {
            long wait = start + at[i] - System.currentTimeMillis();
            if (wait > 0) Thread.sleep(wait);
            shadowOf(Looper.getMainLooper()).idle();
            capture(a, v, outDir + "/neo-launch-" + (i + 1) + ".png");
            System.out.println("launch frame " + (i + 1) + " at " + (System.currentTimeMillis() - start) + " ms");
        }
        setScale.invoke(null, 1f);
    }

    static Object f(Object o, String name) throws Exception {
        java.lang.reflect.Field fl = o.getClass().getDeclaredField(name);
        fl.setAccessible(true);
        return fl.get(o);
    }

    static void dumpState(NeoLauncherView v) throws Exception {
        Object foc = f(v, "focused");
        System.out.println("scrollCur=" + f(v, "scrollCur") + " squish=" + f(v, "squish")
                + " pivot=" + f(v, "squishPivot") + " scrollActive=" + f(v, "scrollActive")
                + " minScroll=" + f(v, "minScroll") + " pointer=" + f(v, "px") + "," + f(v, "py"));
        if (foc != null) {
            Object app = f(foc, "app");
            long now = System.nanoTime();
            Object hov = f(foc, "hover");
            java.lang.reflect.Method get = hov.getClass().getDeclaredMethod("get", long.class);
            get.setAccessible(true);
            Object y = f(foc, "y");
            System.out.println("focused=" + f(app, "pkg") + " hover=" + get.invoke(hov, now)
                    + " rotX=" + get.invoke(f(foc, "rotX"), now) + " rotY=" + get.invoke(f(foc, "rotY"), now)
                    + " y=" + get.invoke(y, now) + " x=" + get.invoke(f(foc, "x"), now)
                    + " press=" + get.invoke(f(foc, "press"), now) + " flash=" + get.invoke(f(foc, "flash"), now));
        } else System.out.println("focused=null");
    }
}
