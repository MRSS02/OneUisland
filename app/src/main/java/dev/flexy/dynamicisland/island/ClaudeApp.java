package dev.flexy.dynamicisland.island;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.List;
import java.util.Locale;

import dev.flexy.dynamicisland.xposed.XLog;

public final class ClaudeApp {
    static final String PACKAGE = "com.anthropic.claude";
    static final int ACCENT = 0xFFD97757;
    private static final String ACTION = "dev.flexy.dynamicisland.CLAUDE";
    private static final long DONE_MS = 6000;
    private static final long STALE_MS = 20 * 60_000;

    private static final ClaudeApp INSTANCE = new ClaudeApp();

    private final Handler main = new Handler(Looper.getMainLooper());
    private String state = "idle";
    private String[] stages = new String[0];
    private String question;
    private int tasks;
    private String[] kinds = new String[0];
    private final java.util.Map<String, Drawable> kindIcons = new java.util.HashMap<>();
    private long updatedAt;
    private boolean registered;
    private Runnable onChange;
    private Context appContext;
    private boolean bound;
    private final android.content.ServiceConnection keepAlive = new android.content.ServiceConnection() {
        @Override
        public void onServiceConnected(android.content.ComponentName name, android.os.IBinder service) {
        }

        @Override
        public void onServiceDisconnected(android.content.ComponentName name) {
        }
    };
    private Drawable icon;
    private Drawable spark;
    private PendingIntent open;

    public static ClaudeApp get() {
        return INSTANCE;
    }

    void register(Context ctx, Runnable changed) {
        onChange = changed;
        appContext = ctx;
        if (registered) return;
        registered = true;
        ctx.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                tasks = i.getIntExtra("tasks", 0);
                String[] k = i.getStringArrayExtra("kinds");
                kinds = k == null ? new String[0] : k;
                accept(i.getStringExtra("state"), i.getStringArrayExtra("stages"), i.getStringExtra("question"));
            }
        }, new IntentFilter(ACTION), Context.RECEIVER_EXPORTED);
    }

    private void accept(String newState, String[] newStages, String newQuestion) {
        String next = newState == null ? "idle" : newState;
        boolean keepTime = "done".equals(next) && "done".equals(state);
        long keep = updatedAt;
        state = next;
        stages = newStages == null ? new String[0] : newStages;
        question = newQuestion;
        updatedAt = keepTime ? keep : SystemClock.uptimeMillis();
        main.removeCallbacks(expire);
        if ("done".equals(state)) main.postDelayed(expire, Math.max(50, DONE_MS + 50 - (SystemClock.uptimeMillis() - updatedAt)));
        else if (!"idle".equals(state) || tasks > 0) main.postDelayed(expire, STALE_MS + 50);
        updateKeepAlive();
        if (onChange != null) onChange.run();
    }

    private final Runnable expire = () -> {
        updateKeepAlive();
        if (onChange != null) onChange.run();
    };

    private void updateKeepAlive() {
        long now = SystemClock.uptimeMillis();
        boolean busy = updatedAt != 0 && now - updatedAt <= STALE_MS
                && (working(state) || waiting(state) || tasks > 0);
        if (busy == bound || appContext == null) return;
        try {
            if (busy) {
                Intent i = new Intent("androidx.health.platform.client.ACTION_BIND_SDK_SERVICE")
                        .setClassName(PACKAGE, "androidx.health.platform.client.impl.sdkservice.HealthDataSdkService");
                bound = appContext.bindService(i, keepAlive, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);
                XLog.i("claude keep alive " + bound);
            } else {
                appContext.unbindService(keepAlive);
                bound = false;
                XLog.i("claude keep alive released");
            }
        } catch (Throwable t) {
            bound = false;
            XLog.e("claude keep alive", t);
        }
    }

    static boolean waiting(String state) {
        return "question".equals(state) || "permission".equals(state);
    }

    static boolean working(String state) {
        return "working".equals(state);
    }

    void collect(List<IslandItem> out, Context ctx) {
        long now = SystemClock.uptimeMillis();
        if (updatedAt == 0 || now - updatedAt > STALE_MS) return;
        boolean finished = "idle".equals(state) || ("done".equals(state) && now - updatedAt > DONE_MS);
        if (finished && tasks <= 0) return;
        String shown = finished ? "background" : state;
        load(ctx);
        IslandItem it = new IslandItem("claude");
        it.kind = IslandItem.Kind.LIVE;
        it.packageName = PACKAGE;
        it.claude = true;
        it.claudeState = shown;
        it.claudeStages = stages;
        it.claudeQuestion = question;
        it.claudeTasks = tasks;
        Drawable[] icons = new Drawable[stages.length];
        for (int n = 0; n < icons.length; n++) icons[n] = kindIcon(ctx, n < kinds.length ? kinds[n] : null);
        it.claudeIcons = icons;
        it.claudeTaskIcon = kindIcon(ctx, "cmd");
        it.claudeSpark = spark;
        it.title = "Claude";
        it.subtitle = stages.length > 0 ? stages[stages.length - 1] : null;
        it.icon = icon;
        it.cardImage = icon;
        it.iconIsImage = true;
        it.cardImageIsImage = true;
        it.iconColor = true;
        it.accent = ACCENT;
        it.trailing = IslandItem.Trailing.CLAUDE;
        it.contentIntent = open;
        it.postTime = updatedAt;
        out.add(it);
    }

    private Drawable kindIcon(Context ctx, String kind) {
        String name;
        switch (kind == null ? "tool" : kind) {
            case "cmd": name = "cds_icon_code"; break;
            case "read":
            case "edit": name = "cds_icon_file"; break;
            case "search": name = "cds_icon_search"; break;
            case "web": name = "cds_icon_globe"; break;
            case "agent": name = "cds_icon_dispatch"; break;
            case "artifact": name = "cds_icon_artifacts"; break;
            case "text":
            case "user":
            case "ask": name = "cds_icon_chat"; break;
            case "think": name = "claude_spark_icon"; break;
            default: name = "cds_icon_tasks"; break;
        }
        if (kindIcons.containsKey(name)) return kindIcons.get(name);
        Drawable d = null;
        try {
            Context c = ctx.createPackageContext(PACKAGE, 0);
            int id = c.getResources().getIdentifier(name, "drawable", PACKAGE);
            if (id != 0) d = c.getDrawable(id);
        } catch (Throwable ignored) {
        }
        kindIcons.put(name, d);
        return d;
    }

    static String text(String en, String ru) {
        return "ru".equals(Locale.getDefault().getLanguage()) ? ru : en;
    }

    static String tasksLabel(int n) {
        if ("ru".equals(Locale.getDefault().getLanguage())) {
            int m10 = n % 10, m100 = n % 100;
            String word = m10 == 1 && m100 != 11 ? "фоновая задача"
                    : m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14) ? "фоновые задачи" : "фоновых задач";
            return n + " " + word;
        }
        return n + (n == 1 ? " running task" : " running tasks");
    }

    static String stateLabel(String state) {
        switch (state) {
            case "question":
                return text("Question for you", "Вопрос к вам");
            case "permission":
                return text("Needs permission", "Нужно разрешение");
            case "done":
                return text("Done", "Готово");
            case "background":
                return text("Running in background", "Работает в фоне");
            default:
                return text("Working", "Работает");
        }
    }

    private void load(Context ctx) {
        if (icon != null && open != null) return;
        try {
            Context c = ctx.createPackageContext(PACKAGE, 0);
            int id = c.getResources().getIdentifier("claude_spark_icon", "drawable", PACKAGE);
            if (id != 0 && icon == null) icon = c.getDrawable(id);
            int anim = c.getResources().getIdentifier("claude_spark_animated_thinking", "drawable", PACKAGE);
            if (anim != 0 && spark == null) spark = c.getDrawable(anim);
            Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(PACKAGE);
            if (launch != null && open == null) {
                open = PendingIntent.getActivity(ctx, 0x0C1A, launch, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            }
        } catch (Throwable t) {
            XLog.e("claude resources", t);
        }
    }
}
