package amap.auto.patch;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import java.lang.reflect.Field;
import java.util.List;

public final class PatchRuntime {
    private static final String TAG = "AmapAutoPatch";
    private static final String ACTION_SEND = "AUTONAVI_STANDARD_BROADCAST_SEND";

    private static Context appContext;
    private static Handler mainHandler;

    private PatchRuntime() {
    }

    public static synchronized void init(Context context) {
        if (context == null) {
            return;
        }
        if (appContext == null) {
            appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
            mainHandler = new Handler(Looper.getMainLooper());
        }
        com.autonavi.companion.PatchBridge.init(appContext);
        Log.i(TAG, "runtime initialized");
    }

    public static void replaceHostFloatWindow(final WindowManager windowManager, final View hostView, final ViewGroup.LayoutParams params) {
        Context context = hostView != null ? hostView.getContext() : appContext;
        init(context);
        runOnMain(new Runnable() {
            @Override
            public void run() {
                try {
                    com.autonavi.companion.PatchBridge.startOverlayService(appContext);
                } catch (Throwable t) {
                    Log.e(TAG, "replace host float window failed", t);
                }
            }
        });
    }

    public static void onTrafficLightWrapper(final Object wrapper) {
        init(appContext);
        runOnMain(new Runnable() {
            @Override
            public void run() {
                try {
                    com.autonavi.companion.PatchBridge.onTrafficLightWrapper(appContext, wrapper);
                } catch (Throwable t) {
                    Log.e(TAG, "traffic light wrapper parse failed", t);
                }
            }
        });
    }

    public static void removePatchOverlay() {
        runOnMain(new Runnable() {
            @Override
            public void run() {
                // Companion OverlayService owns its own lifecycle; do not remove it
                // just because the host float window was recreated or dismissed.
            }
        });
    }

    public static void showTestOverlay(Context context) {
        init(context);
        runOnMain(new Runnable() {
            @Override
            public void run() {
                if (appContext == null) {
                    return;
                }
                com.autonavi.companion.PatchBridge.startOverlayService(appContext);
                Intent intent = new Intent(ACTION_SEND);
                intent.putExtra("KEY_TYPE", 60073);
                intent.putExtra("trafficLightStatus", 2);
                intent.putExtra("redLightCountDownSeconds", 18);
                intent.putExtra("dir", 4);
                intent.putExtra("lightsCount", 3);
                appContext.sendBroadcast(intent);
            }
        });
    }

    private static void addHostWindow(WindowManager windowManager, View hostView, ViewGroup.LayoutParams params) {
        if (windowManager == null || hostView == null || params == null || hostView.getParent() != null) {
            return;
        }
        try {
            windowManager.addView(hostView, params);
        } catch (Throwable t) {
            Log.e(TAG, "fallback add host window failed", t);
        }
    }

    private static void runOnMain(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        if (mainHandler == null) {
            mainHandler = new Handler(Looper.getMainLooper());
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }

    static Integer readIntField(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static Object readObjectField(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static List<?> readListField(Object target, String name) {
        Object value = readObjectField(target, name);
        if (value instanceof List) {
            return (List<?>) value;
        }
        return null;
    }
}
