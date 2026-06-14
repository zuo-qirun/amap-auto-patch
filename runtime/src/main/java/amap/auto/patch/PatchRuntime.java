package amap.auto.patch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
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
    private static OverlayController overlay;
    private static boolean receiverRegistered;

    private PatchRuntime() {
    }

    public static synchronized void init(Context context) {
        if (context == null) {
            return;
        }
        if (appContext == null) {
            appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
            mainHandler = new Handler(Looper.getMainLooper());
            overlay = new OverlayController(appContext, mainHandler);
        }
        registerReceiverOnce();
        Log.i(TAG, "runtime initialized");
    }

    public static void replaceHostFloatWindow(final WindowManager windowManager, final View hostView, final ViewGroup.LayoutParams params) {
        Context context = hostView != null ? hostView.getContext() : appContext;
        init(context);
        runOnMain(new Runnable() {
            @Override
            public void run() {
                try {
                    if (overlay == null) {
                        addHostWindow(windowManager, hostView, params);
                        return;
                    }
                    overlay.rememberHost(windowManager, hostView, params);
                    if (!overlay.isEnabled()) {
                        addHostWindow(windowManager, hostView, params);
                        return;
                    }
                    overlay.attach(windowManager, hostView, params);
                } catch (Throwable t) {
                    Log.e(TAG, "replace host float window failed", t);
                    addHostWindow(windowManager, hostView, params);
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
                    DataModel model = DataModel.fromTrafficLightWrapper(wrapper);
                    if (model != null && overlay != null) {
                        overlay.update(model);
                    }
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
                if (overlay != null) {
                    overlay.removePatchWindows();
                }
            }
        });
    }

    public static void showTestOverlay(Context context) {
        init(context);
        runOnMain(new Runnable() {
            @Override
            public void run() {
                if (overlay == null || appContext == null) {
                    return;
                }
                WindowManager wm = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
                overlay.attach(wm, null, null);
                DataModel model = new DataModel();
                model.keyType = DataModel.KEY_TRAFFIC_LIGHT;
                model.category = DataModel.CATEGORY_TRAFFIC_LIGHT;
                model.mode = "虚拟机测试";
                model.lightStatus = 1;
                model.lightSeconds = 18;
                model.lightDir = 0;
                model.lightsCount = 3;
                model.primary = "红灯 18s";
                model.secondary = "PatchRuntime 测试入口";
                overlay.update(model);
            }
        });
    }

    private static void registerReceiverOnce() {
        if (receiverRegistered || appContext == null) {
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SEND);
        appContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handleBroadcast(intent);
            }
        }, filter);
        receiverRegistered = true;
    }

    private static void handleBroadcast(final Intent intent) {
        if (intent == null) {
            return;
        }
        init(appContext);
        runOnMain(new Runnable() {
            @Override
            public void run() {
                try {
                    Bundle extras = intent.getExtras();
                    DataModel model = DataModel.fromBroadcast(intent.getAction(), extras);
                    if (model != null && overlay != null) {
                        overlay.update(model);
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "broadcast parse failed", t);
                }
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
