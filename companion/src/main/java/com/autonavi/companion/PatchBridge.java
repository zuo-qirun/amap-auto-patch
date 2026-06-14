package com.autonavi.companion;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.List;

public final class PatchBridge {
    private static final String TAG = "AmapCompanion";

    private PatchBridge() {
    }

    public static void init(Context context) {
        if (context == null) {
            return;
        }
        Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        R.init(app);
        ensureDefaults(app);
    }

    public static void startOverlayService(Context context) {
        if (context == null) {
            return;
        }
        Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        R.init(app);
        ensureDefaults(app);
        Intent intent = new Intent(app, OverlayService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent);
            } else {
                app.startService(intent);
            }
        } catch (Throwable t) {
            Log.e(TAG, "start integrated overlay service failed", t);
        }
    }

    public static void openSettings(Context context) {
        if (context == null) {
            return;
        }
        Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        R.init(app);
        Intent intent = new Intent(app, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        app.startActivity(intent);
    }

    public static void onTrafficLightWrapper(Context context, Object wrapper) {
        if (context == null || wrapper == null) {
            return;
        }
        R.init(context);
        Bundle extras = wrapperToExtras(wrapper);
        if (extras == null) {
            return;
        }
        Intent intent = new Intent(AppPrefs.ACTION_DIAGNOSTIC_REPLAY);
        intent.setClass(context, OverlayService.class);
        intent.putExtra("KEY_TYPE", AmapConstants.KEY_TYPE_TRAFFIC_LIGHT);
        intent.putExtras(extras);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Throwable t) {
            Log.e(TAG, "dispatch traffic light wrapper failed", t);
        }
    }

    private static void ensureDefaults(Context context) {
        context.getSharedPreferences(AppPrefs.PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(AppPrefs.KEY_TARGET_PACKAGE, context.getPackageName())
                .putBoolean(AppPrefs.KEY_MAIN_OVERLAY_ENABLED, true)
                .apply();
    }

    private static Bundle wrapperToExtras(Object wrapper) {
        List<?> lights = readListField(wrapper, "a");
        if (lights == null || lights.isEmpty()) {
            return null;
        }
        StringBuilder json = new StringBuilder("[");
        int count = 0;
        for (Object item : lights) {
            Integer dir = readIntField(item, "c");
            Integer status = readIntField(item, "d");
            Integer seconds = readIntField(item, "e");
            if (seconds == null || seconds.intValue() <= 0) {
                continue;
            }
            if (count > 0) {
                json.append(',');
            }
            json.append('{')
                    .append("\"dir\":").append(dir != null ? dir.intValue() : -1).append(',')
                    .append("\"status\":").append(status != null ? status.intValue() : -1).append(',')
                    .append("\"countDown\":").append(seconds.intValue())
                    .append('}');
            count++;
        }
        if (count == 0) {
            return null;
        }
        json.append(']');
        Bundle extras = new Bundle();
        extras.putInt("KEY_TYPE", AmapConstants.KEY_TYPE_TRAFFIC_LIGHT);
        extras.putString("lightsData", json.toString());
        extras.putInt("TRAFFIC_LIGHT_NUM", count);
        return extras;
    }

    private static Integer readIntField(Object target, String name) {
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

    private static Object readObjectField(Object target, String name) {
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

    private static List<?> readListField(Object target, String name) {
        Object value = readObjectField(target, name);
        if (value instanceof List) {
            return (List<?>) value;
        }
        return null;
    }
}
