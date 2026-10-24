package dev.flexy.dynamicisland.tests;

import android.app.Instrumentation;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import dev.flexy.dynamicisland.island.IslandItem;
import dev.flexy.dynamicisland.island.IslandView;

public final class RegressionRunner extends Instrumentation {
    private int checks;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        Throwable[] failure = {null};
        runOnMainSync(() -> {
            try { progressCard(); mobileLeaves(); duplicates(); metrics(); splash(); scorer(); shadeLayoutCallbacks(); alerts(); maxChars(); batteryIcon(); torchCard(); }
            catch (Throwable t) { failure[0] = t; }
        });
        if (failure[0] != null) {
            result.putString("stream", android.util.Log.getStackTraceString(failure[0]));
            finish(1, result);
        } else {
            result.putString("stream", "PASS: " + checks + " regression checks\n");
            finish(-1, result);
        }
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private void shadeLayoutCallbacks() throws Exception {
        Class<?> fix = Class.forName("dev.flexy.dynamicisland.xposed.ShadeLayoutCallbacks");
        Class<?> observerClass = android.view.ViewTreeObserver.class;
        Class<?> listenerClass = android.view.ViewTreeObserver.OnGlobalLayoutListener.class;
        Method enter = method(fix, "enter", observerClass);
        Method leave = method(fix, "leave");
        Method remove = method(fix, "removeFromDispatch", observerClass, listenerClass);
        Method dispatch = method(observerClass, "dispatchOnGlobalLayout");
        android.view.ViewTreeObserver root = new View(getTargetContext()).getViewTreeObserver();
        android.view.ViewTreeObserver detached = new View(getTargetContext()).getViewTreeObserver();
        android.view.ViewTreeObserver nested = new View(getTargetContext()).getViewTreeObserver();
        int[] calls = {0};
        android.view.ViewTreeObserver.OnGlobalLayoutListener callback = new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                calls[0]++;
                try {
                    check((Boolean) remove.invoke(null, detached, this), "detached callback removed from its dispatch owner");
                } catch (Exception e) { throw new RuntimeException(e); }
            }
        };
        check(!(Boolean) remove.invoke(null, detached, callback), "removal outside dispatch left alone");
        root.addOnGlobalLayoutListener(callback);
        enter.invoke(null, root);
        try {
            enter.invoke(null, nested);
            try {
                check(!(Boolean) remove.invoke(null, nested, callback), "same-observer removal left to framework");
            } finally { leave.invoke(null); }
            for (int update = 0; update < 64; update++) {
                if (update > 0) root.addOnGlobalLayoutListener(callback);
                dispatch.invoke(root);
                dispatch.invoke(root);
                check(calls[0] == update + 1, "decoration callbacks do not accumulate after update " + update);
            }
        } finally { leave.invoke(null); }
        check(!(Boolean) remove.invoke(null, detached, callback), "nested dispatch stack cleared");
        final int[] ordinaryCalls = {0};
        android.view.ViewTreeObserver.OnGlobalLayoutListener ordinary = () -> ordinaryCalls[0]++;
        root.addOnGlobalLayoutListener(ordinary);
        dispatch.invoke(root);
        dispatch.invoke(root);
        check(ordinaryCalls[0] == 2, "ordinary persistent listener keeps receiving layouts");
        root.removeOnGlobalLayoutListener(ordinary);
    }
    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private static Method method(Class<?> c, String name, Class<?>... args) throws Exception {
        Method m = c.getDeclaredMethod(name, args); m.setAccessible(true); return m;
    }

    private void progressCard() throws Exception {
        Context ctx = getTargetContext();
        Notification n = new Notification.Builder(ctx, "test").setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle("ОГСЭ.04 Иностранный язык в профессиональной деятельности")
                .setContentText("3 пара · кабинет 608 · до 13:10 · осталось 3 мин")
                .setProgress(100, 93, false).build();
        n.extras.putString("android.template", "android.app.Notification$ProgressStyle");
        Bundle segment = new Bundle(); segment.putInt("colorInt", 0xFFD0BCFF);
        n.extras.putParcelableArrayList("android.progressSegments", new java.util.ArrayList<>(Arrays.asList(segment)));
        Bitmap art = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888); art.eraseColor(Color.GREEN);
        n.extras.putParcelable(Notification.EXTRA_LARGE_ICON, Icon.createWithBitmap(art));
        Class<?> sourceClass = Class.forName("dev.flexy.dynamicisland.island.OaSource");
        Constructor<?> ctor = sourceClass.getDeclaredConstructor(Context.class, ClassLoader.class); ctor.setAccessible(true);
        Object source = ctor.newInstance(ctx, getClass().getClassLoader());
        Method build = method(sourceClass, "build", String.class, Object.class, String.class,
                StatusBarNotification.class, Notification.class, boolean.class);
        IslandItem item = (IslandItem) build.invoke(source, "test", new OngoingData(), ctx.getPackageName(), null, n, false);
        check(item.progressStyle, "AOSP ProgressStyle detected");
        check(n.extras.getCharSequence(Notification.EXTRA_TITLE).equals(item.title), "original full title retained");
        check(n.extras.getCharSequence(Notification.EXTRA_TEXT).equals(item.subtitle), "room, lesson and end time retained");
        check(Math.abs(item.progress - .93f) < .001, "source progress used instead of missing Samsung progress");
        check(item.accent == 0xFFD0BCFF, "progress segment color retained");
        IslandView renderer = new IslandView(ctx, null);
        float d = ctx.getResources().getDisplayMetrics().density, width = 380 * d;
        int height = (int) Math.ceil(renderer.shadeCardHeight(item, width));
        check(height > 80 * d && height < 220 * d, "progress card fits wrapped title, detail and bar");
        Bitmap image = Bitmap.createBitmap((int)width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image); canvas.drawColor(0xFF48434E);
        renderer.drawShadeCard(canvas, item, width);
        java.io.File output = new java.io.File(ctx.getExternalFilesDir(null), "progress-card.png");
        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(output)) {
            image.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
        boolean cornerIcon = false;
        for (int px = (int)(width - 40*d); px < (int)(width - 18*d) && !cornerIcon; px++)
            for (int py = (int)(18*d); py < (int)(40*d) && !cornerIcon; py++)
                cornerIcon = image.getPixel(px, py) != 0xFF48434E;
        check(cornerIcon, "app icon drawn in the top-right corner");
        n.extras.remove("android.template");
        IslandItem normal = (IslandItem) build.invoke(source, "normal", new OngoingData(), ctx.getPackageName(), null, n, false);
        check(!normal.progressStyle && n.extras.getCharSequence(Notification.EXTRA_TEXT).equals(normal.subtitle),
                "AOSP live update keeps its own text");
        n.extras.putInt("android.ongoingActivityNoti.style", 1);
        IslandItem samsung = (IslandItem) build.invoke(source, "samsung", new OngoingData(), ctx.getPackageName(), null, n, false);
        check("13 мин".equals(samsung.subtitle), "Samsung ongoing activity keeps its Now Bar text");
    }

    private void alerts() throws Exception {
        Class<?> src = Class.forName("dev.flexy.dynamicisland.island.AlertSource");
        Method parse = method(src, "parse", CharSequence.class, CharSequence.class);
        Object both = parse.invoke(null, "AirPods Pro", "L: 80% R: ⚡ 70% Case: 50%");
        check(both != null && intField(both, "left") == 80 && intField(both, "right") == 70 && intField(both, "single") == -1,
                "two buds and the case");
        Method percent = method(both.getClass(), "percent");
        check((Integer) percent.invoke(both) == 75, "two buds: their average charge");
        Object left = parse.invoke(null, "AirPods Pro", "L: 64% Case: 50%");
        check(left != null && intField(left, "left") == 64 && intField(left, "right") == -1, "one bud: only the left one");
        Object max = parse.invoke(null, "Airpods Max", "53%");
        check(max != null && intField(max, "single") == 53, "one-battery model");
        check(parse.invoke(null, "Airpods Max", "") == null, "no charge yet: not shown");
        Method buds = method(src, "galaxyBudsImage", String.class, String.class);
        check(Arrays.equals(new String[] {"com.samsung.accessory.budsunitemgr", "home_image_bach_white"},
                (String[]) buds.invoke(null, "SM-R640", "Josh's Buds4 Pro")), "Buds4 Pro by model: bach");
        check(Arrays.equals(new String[] {"com.samsung.accessory.budsunitemgr", "home_image_handel_while"},
                (String[]) buds.invoke(null, null, "Galaxy Buds4")), "Buds4 by name: handel");
        check(Arrays.equals(new String[] {"com.samsung.accessory.paranmgr", "home_image_white"},
                (String[]) buds.invoke(null, "SM-R630", "Josh's Buds3 Pro")), "Buds3 Pro: its own plugin");
        check(Arrays.equals(new String[] {"com.samsung.accessory.pearlmgr", "home_image_white"},
                (String[]) buds.invoke(null, null, "Josh's Buds FE")), "Buds FE by name");
        check(buds.invoke(null, "SM-R170", "Galaxy Buds") == null, "original Galaxy Buds: no photo, the glyph");
        Object unread = parse.invoke(null, "AirPods Pro", "L: 100% R: 127%");
        check(unread != null && intField(unread, "left") == 100 && intField(unread, "right") == -1
                && (Integer) percent.invoke(unread) == 100, "127 = unread bud: ignored, not 114%");

        Context ctx = getTargetContext();
        float d = ctx.getResources().getDisplayMetrics().density;
        IslandItem pods = new IslandItem("alert:pods");
        pods.alert = true;
        pods.iconColor = true;
        pods.accent = 0xFF30D158;
        pods.overline = "Connected";
        pods.title = "Airpods Max";
        pods.trailing = IslandItem.Trailing.TEXT;
        pods.trailingText = "53%";
        pods.ringPercent = 53;
        IslandView renderer = new IslandView(ctx, null);
        float w = 380 * d;
        int h = (int) Math.ceil(renderer.shadeCardHeight(pods, w));
        Bitmap card = Bitmap.createBitmap((int) w, h, Bitmap.Config.ARGB_8888);
        renderer.drawShadeCard(new Canvas(card), pods, w);
        int rx = (int) (w - 18 * d - 26 * d), ry = (int) (h / 2f - 26 * d + 2.2f * d);
        int px = card.getPixel(rx, ry);
        check(Color.green(px) > 150 && Color.alpha(px) > 200, "battery ring drawn in green (" + Integer.toHexString(px) + ")");
    }

    private void batteryIcon() throws Exception {
        Context sui = getTargetContext().createPackageContext("com.android.systemui",
                Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
        Class<?> src = Class.forName("dev.flexy.dynamicisland.island.AlertSource");
        Constructor<?> ctor = src.getDeclaredConstructor(Context.class, Runnable.class);
        ctor.setAccessible(true);
        Object alerts = ctor.newInstance(sui, (Runnable) () -> { });
        Method make = method(src, "batteryItem", int.class);
        IslandItem item = (IslandItem) make.invoke(alerts, 20);
        check(item.icon != null && item.cardImage != null, "low battery: the status bar's battery icon");
        check("20%".contentEquals(item.trailingText) && item.trailingColor != 0, "low battery: red level on the right");
        Context ctx = getTargetContext();
        float d = ctx.getResources().getDisplayMetrics().density;
        IslandView renderer = new IslandView(ctx, null);
        float w = 380 * d;
        int h = (int) Math.ceil(renderer.shadeCardHeight(item, w));
        Bitmap card = Bitmap.createBitmap((int) w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(card);
        c.drawColor(0xFF000000);
        renderer.drawShadeCard(c, item, w);
        java.io.File file = new java.io.File(ctx.getExternalFilesDir(null), "battery-card.png");
        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(file)) {
            card.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
    }

    private void torchCard() throws Exception {
        Context ctx = getTargetContext();
        float d = ctx.getResources().getDisplayMetrics().density;
        float w = 380 * d;
        int[][] states = {{0, 1}, {1, 2}, {1, 5}};
        Bitmap sheet = null;
        int y = 0;
        for (int[] s : states) {
            IslandItem item = new IslandItem("torch");
            item.torch = true;
            item.torchOn = s[0] == 1;
            item.torchLevel = s[1];
            item.torchMax = 5;
            IslandView renderer = new IslandView(ctx, null);
            int h = (int) Math.ceil(renderer.shadeCardHeight(item, w));
            if (sheet == null) sheet = Bitmap.createBitmap((int) w, h * states.length, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(sheet);
            c.translate(0, y);
            c.clipRect(0, 0, w, h);
            c.drawColor(0xFF000000);
            renderer.drawShadeCard(c, item, w);
            y += h;
        }
        int lit = sheet.getPixel((int) (130 * d), sheet.getHeight() * 5 / 6);
        check(Color.blue(lit) > 60, "torch beam glows at full level (" + Integer.toHexString(lit) + ")");
        java.io.File file = new java.io.File(ctx.getExternalFilesDir(null), "torch-card.png");
        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(file)) {
            sheet.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
    }

    private void maxChars() throws Exception {
        Method limit = method(IslandView.class, "limitChars", String.class, int.class);
        check("Downloading upd…".equals(limit.invoke(null, "Downloading update.zip", 16)), "cut to 16 characters with an ellipsis");
        check("2 - 1".equals(limit.invoke(null, "2 - 1", 4 + 1)), "short text untouched");
        check("Downloading update.zip".equals(limit.invoke(null, "Downloading update.zip", 21)), "slider top: no limit");
    }

    private static int intField(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.getInt(o);
    }

    private void scorer() throws Exception {
        IslandView renderer = new IslandView(getTargetContext(), null);
        Method logo = method(IslandView.class, "scorerLogo", IslandItem.class);
        IslandItem match = new IslandItem("match");
        match.leftImage = new android.graphics.drawable.ColorDrawable(Color.RED);
        match.rightImage = new android.graphics.drawable.ColorDrawable(Color.BLUE);
        match.trailingText = "0 - 0";
        check(logo.invoke(renderer, match) == match.leftImage, "no goals: first team");
        match.trailingText = "0 - 1";
        check(logo.invoke(renderer, match) == match.rightImage, "away goal: away team");
        match.trailingText = "1 - 1";
        check(logo.invoke(renderer, match) == match.leftImage, "home equaliser: home team");
        IslandItem late = new IslandItem("late");
        late.leftImage = match.leftImage;
        late.rightImage = match.rightImage;
        late.trailingText = "1:3";
        check(logo.invoke(renderer, late) == late.rightImage, "first seen mid-match: the leading team");
    }

    private void splash() throws Exception {
        Context ctx = getTargetContext();
        float d = ctx.getResources().getDisplayMetrics().density;
        IslandItem item = new IslandItem("splash");
        item.kind = IslandItem.Kind.TIMER;
        item.title = "Stopwatch";
        android.graphics.drawable.Drawable icon = ctx.getDrawable(android.R.drawable.ic_media_pause);
        item.actions.add(new IslandItem.Action("Pause", icon, null, null, false));
        item.actions.add(new IslandItem.Action("Lap", icon, null, null, false));
        IslandView renderer = new IslandView(ctx, null);
        float w = 380 * d;
        IslandItem wide = new IslandItem("wide");
        wide.title = "Parking";
        wide.actions.add(new IslandItem.Action("Extend", null, null, null, false));
        int wh = (int) Math.ceil(renderer.shadeCardHeight(wide, w));
        float wy = wh - 18 * d - 20 * d;
        check(renderer.shadeButtonDown(wide, w, w / 2f, wy), "wide button found under the finger");
        renderer.shadeButtonUp();
        Thread.sleep(600);
        Canvas cw = new Canvas(Bitmap.createBitmap((int) w, wh, Bitmap.Config.ARGB_8888));
        renderer.drawShadeCard(cw, wide, w);
        int h = (int) Math.ceil(renderer.shadeCardHeight(item, w));
        Bitmap idle = Bitmap.createBitmap((int) w, h, Bitmap.Config.ARGB_8888);
        Canvas ci = new Canvas(idle); ci.drawColor(0xFF48434E);
        renderer.drawShadeCard(ci, item, w);
        float bx = w - 16 * d - 23 * d, by = h / 2f;
        check(renderer.shadeButtonDown(item, w, bx, by), "round button found under the finger");
        Thread.sleep(220);
        Bitmap pressed = Bitmap.createBitmap((int) w, h, Bitmap.Config.ARGB_8888);
        Canvas cp = new Canvas(pressed); cp.drawColor(0xFF48434E);
        renderer.drawShadeCard(cp, item, w);
        renderer.shadeButtonUp();
        int changed = 0;
        for (int a = 0; a < 360; a += 10) {
            double rad = Math.toRadians(a);
            for (float rr = 30 * d; rr < 48 * d; rr += 2 * d) {
                int x = (int) (bx + Math.cos(rad) * rr), y = (int) (by + Math.sin(rad) * rr);
                if (x < 0 || y < 0 || x >= idle.getWidth() || y >= h) continue;
                int pi = idle.getPixel(x, y), pp = pressed.getPixel(x, y);
                if (Color.red(pp) - Color.red(pi) > 12 && Color.green(pp) - Color.green(pi) > 12) changed++;
            }
        }
        java.io.File output = new java.io.File(ctx.getExternalFilesDir(null), "splash-card.png");
        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(output)) {
            pressed.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
        check(changed > 20, "splash ring drawn around the pressed button (" + changed + " px)");
    }

    @SuppressWarnings("unchecked") private void metrics() throws Exception {
        long now = System.currentTimeMillis();
        Bundle time = new Bundle(); time.putInt("_type", 1); time.putInt("format", 2);
        time.putLong("zeroTime", now - 65_000); time.putBoolean("countDown", false);
        Bundle paused = new Bundle(); paused.putInt("_type", 1); paused.putInt("format", 1);
        paused.putLong("pausedDuration", 42 * 60_000L); paused.putBoolean("countDown", true);
        Bundle km = new Bundle(); km.putInt("_type", 5); km.putFloat("value", 0.9215f); km.putString("unit", "km");
        km.putInt("minDigits", 2); km.putInt("maxDigits", 2);
        Bundle pct = new Bundle(); pct.putInt("_type", 4); pct.putInt("value", 64); pct.putString("unit", "%");
        Bundle text = new Bundle(); text.putInt("_type", 6); text.putString("value", "B12");
        java.util.ArrayList<Bundle> list = new java.util.ArrayList<>();
        String[] labels = {"Time", "Boarding in", "Distance", "Battery", "Gate"};
        Bundle[] values = {time, paused, km, pct, text};
        for (int i = 0; i < values.length; i++) {
            Bundle m = new Bundle(); m.putString("label", labels[i]); m.putBundle("value", values[i]);
            m.putInt("semanticStyle", i == 3 ? 2 : 0); list.add(m);
        }
        Bundle extras = new Bundle(); extras.putParcelableArrayList("android.metrics", list);
        Class<?> c = Class.forName("dev.flexy.dynamicisland.island.Metrics");
        List<IslandItem.Metric> parsed = (List<IslandItem.Metric>) method(c, "parse", Context.class, Bundle.class)
                .invoke(null, getTargetContext(), extras);
        check(parsed != null && parsed.size() == 5, "all metric types parsed");
        Method text0 = method(c, "text", IslandItem.Metric.class, long.class);
        check(parsed.get(0).running() && "1:05".equals(text0.invoke(null, parsed.get(0), now)), "running stopwatch metric");
        check("42 min".equals(text0.invoke(null, parsed.get(1), now)), "paused adaptive timer metric");
        String dist = (String) text0.invoke(null, parsed.get(2), now);
        check(dist.endsWith(" km") && dist.length() == 7, "float metric with digits and unit: " + dist);
        check("64%".equals(text0.invoke(null, parsed.get(3), now)) && parsed.get(3).semantic == 2, "int metric with % and semantic");
        check("B12".equals(text0.invoke(null, parsed.get(4), now)), "text metric");
        IslandItem item = new IslandItem("metric");
        item.title = "Outdoor run";
        item.metrics = parsed;
        item.criticalMetric = 0;
        IslandView renderer = new IslandView(getTargetContext(), null);
        float d = getTargetContext().getResources().getDisplayMetrics().density;
        float height = renderer.shadeCardHeight(item, 380 * d);
        check(height > 120 * d && height < 260 * d, "metric card: title and two rows of metrics");
        Bitmap image = Bitmap.createBitmap((int) (380 * d), (int) Math.ceil(height), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image); canvas.drawColor(0xFF48434E);
        renderer.drawShadeCard(canvas, item, 380 * d);
        java.io.File output = new java.io.File(getTargetContext().getExternalFilesDir(null), "metric-card.png");
        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(output)) {
            image.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
    }

    public static final class OngoingData {
        public String mNowbarPrimaryInfo = "Samsung chip title", mNowbarSecondaryInfo = "13 мин";
        public int mProgress = 0, mProgressMax = 0;
    }
    public static final class ModernStatusBarMobileView extends FrameLayout {
        ModernStatusBarMobileView(Context ctx) { super(ctx); }
    }
    private void mobileLeaves() throws Exception {
        Context ctx = getTargetContext();
        Class<?> fader = Class.forName("dev.flexy.dynamicisland.island.StatusBarIconFader");
        ModernStatusBarMobileView root = new ModernStatusBarMobileView(ctx);
        FrameLayout typeContainer = new FrameLayout(ctx);
        ImageView type = new ImageView(ctx), signal = new ImageView(ctx), roaming = new ImageView(ctx);
        typeContainer.addView(type); root.addView(typeContainer); root.addView(signal); root.addView(roaming);
        check((Boolean)method(fader, "isMobileGroup", View.class).invoke(null, root), "mobile slot recognized");
        List<View> leaves = new java.util.ArrayList<>();
        method(fader, "collectMobileLeaves", View.class, List.class).invoke(null, root, leaves);
        check(leaves.contains(type) && leaves.contains(signal) && leaves.size() == 3, "4G and signal are separate candidates");
        check(!leaves.contains(root) && !leaves.contains(typeContainer), "parents do not fade both indicators together");
    }

    private StatusBarNotification sbn(String pkg, int id, Notification n, int user) {
        return new StatusBarNotification(pkg, pkg, id, null, user * 100000 + 10000, 1, 0, n,
                UserHandle.getUserHandleForUid(user * 100000 + 10000), 1);
    }
    private Notification notification(String channel, String title) {
        return new Notification.Builder(getTargetContext(), channel).setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle(title).build();
    }
    @SuppressWarnings("unchecked") private void duplicates() throws Exception {
        Class<?> c = Class.forName("dev.flexy.dynamicisland.xposed.HideDuplicated");
        Method matches = method(c, "matches", StatusBarNotification.class, StatusBarNotification.class);
        Method refresh = method(c, "refresh", List.class);
        Notification original = notification("scooter", "KM757T · 3 min");
        StatusBarNotification source = sbn("test.scooter", 44, original, 0);
        int hash = source.getKey().hashCode(); int copyId = hash == Integer.MIN_VALUE ? 0 : Math.abs(hash);
        StatusBarNotification copy = sbn("com.locnall.KimGiSa", copyId,
                notification("livebridge_smart_conversions", "KM757T"), 0);
        check((Boolean) matches.invoke(null, source, copy), "non-ongoing custom source matches without app-name subtitle");
        check(!(Boolean) matches.invoke(null, sbn("other.app", 44, original, 0), copy), "unrelated source retained");
        check(!(Boolean) matches.invoke(null, source, sbn("com.locnall.KimGiSa", copyId, copy.getNotification(), 10)), "users kept separate");
        Notification autoSummary = notification("scooter", "summary"); autoSummary.flags |= Notification.FLAG_GROUP_SUMMARY;
        Notification grouped = new Notification.Builder(getTargetContext(), "scooter").setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setGroup("rides").setContentTitle("KM757T").build();
        source = sbn("test.scooter", 44, grouped, 0);
        Notification summaryN = new Notification.Builder(getTargetContext(), "scooter").setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setGroup("rides").setGroupSummary(true).build();
        StatusBarNotification summary = sbn("test.scooter", 99, summaryN, 0);
        refresh.invoke(null, Arrays.asList(source, copy, summary));
        Field keys = c.getDeclaredField("COPIES"); keys.setAccessible(true);
        Set<String> hidden = (Set<String>)keys.get(null);
        check(hidden.contains(source.getKey()) && hidden.contains(summary.getKey()), "mirrored child and empty summary hidden");
        StatusBarNotification unmatched = sbn("test.scooter", 45, grouped, 0);
        copy.getNotification().extras.putCharSequence(Notification.EXTRA_SUB_TEXT, "test.scooter");
        refresh.invoke(null, Arrays.asList(source, copy, summary, unmatched));
        check(!hidden.contains(summary.getKey()) && !hidden.contains(unmatched.getKey()), "mixed groups and unmirrored child retained");
        refresh.invoke(null, java.util.Collections.emptyList());
        check(hidden.isEmpty(), "empty source snapshot clears stale matches");
        Set<String> capsuleKeys = new java.util.HashSet<>();
        Intent clear = new Intent("com.kakao.taxi.action.CLEAR_NOTIFICATION_CAPSULE_KEYS")
                .putStringArrayListExtra("notification_keys", new java.util.ArrayList<>(Arrays.asList(source.getKey(), unmatched.getKey())));
        method(c, "collectKeys", Intent.class, Set.class).invoke(null, clear, capsuleKeys);
        check(capsuleKeys.contains(source.getKey()) && capsuleKeys.contains(unmatched.getKey()), "capsule exact source keys recovered");
        capsuleKeys.clear(); clear.setAction("unrelated.action");
        method(c, "collectKeys", Intent.class, Set.class).invoke(null, clear, capsuleKeys);
        check(capsuleKeys.isEmpty(), "unrelated intents never hide source notifications");
        Notification lockOnly = notification("livebridge_charging_info", "KM757T");
        lockOnly.extras.putInt("android.ongoingActivityNoti.style", 2);
        check(!(Boolean) matches.invoke(null, sbn("test.scooter", 44, original, 0),
                sbn("com.locnall.KimGiSa", copyId, lockOnly, 0)), "lock-screen-only lives never hide their sources");
        PendingIntent open = PendingIntent.getActivity(getTargetContext(), 1, new Intent("test.OPEN"), PendingIntent.FLAG_IMMUTABLE);
        original.contentIntent = open;
        Notification legacy = notification("livebridge_legacy", "KM757T · 3 min"); legacy.contentIntent = open;
        check((Boolean)matches.invoke(null, sbn("test.scooter", 44, original, 0),
                sbn("com.locnall.KimGiSa", 7, legacy, 0)), "legacy mirror matches title and exact intent");
        legacy.extras.putCharSequence(Notification.EXTRA_TITLE, "different event");
        check(!(Boolean)matches.invoke(null, sbn("test.scooter", 44, original, 0),
                sbn("com.locnall.KimGiSa", 7, legacy, 0)), "shared app-opening intent alone never hides another event");
        open.cancel();
    }
}
