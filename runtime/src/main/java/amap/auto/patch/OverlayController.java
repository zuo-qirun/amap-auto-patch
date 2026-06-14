package amap.auto.patch;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class OverlayController {
    private static final String TAG = "AmapAutoPatch";
    private static final String PREFS = "amap_auto_patch_prefs";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_STYLE = "style";
    private static final String KEY_SCALE = "scale";
    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";
    private static final long SLOT_MS = 3000L;
    private static final long STALE_MS = 30000L;
    private static final int[] DISPLAY_ORDER = new int[] {
            DataModel.CATEGORY_NAV,
            DataModel.CATEGORY_TRAFFIC_LIGHT
    };

    private final Context context;
    private final Handler mainHandler;
    private final SharedPreferences prefs;

    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private LinearLayout panel;
    private TextView modeText;
    private TextView primaryText;
    private TextView secondaryText;
    private View hostView;
    private ViewGroup.LayoutParams hostParams;
    private WindowManager hostWindowManager;
    private View settingsView;
    private WindowManager.LayoutParams settingsParams;
    private DataModel lastModel;
    private DataModel navModel;
    private DataModel trafficLightModel;
    private DataModel cruiseModel;
    private long navUpdatedAt;
    private long trafficLightUpdatedAt;
    private long cruiseUpdatedAt;
    private int currentCategory = DataModel.CATEGORY_NONE;
    private boolean rotateScheduled;
    private int downRawX;
    private int downRawY;
    private int startX;
    private int startY;
    private final Runnable rotateRunnable = new Runnable() {
        @Override
        public void run() {
            rotateScheduled = false;
            renderNext(true);
            scheduleRotation();
        }
    };

    OverlayController(Context context, Handler mainHandler) {
        this.context = context;
        this.mainHandler = mainHandler;
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, true);
    }

    void rememberHost(WindowManager wm, View view, ViewGroup.LayoutParams rawParams) {
        hostWindowManager = wm;
        hostView = view;
        hostParams = rawParams;
    }

    void attach(WindowManager wm, View host, ViewGroup.LayoutParams rawParams) {
        if (wm == null && hostWindowManager == null) {
            return;
        }
        rememberHost(wm, host, rawParams);
        windowManager = wm != null ? wm : hostWindowManager;
        if (panel == null) {
            panel = buildPanel();
        }
        if (params == null) {
            params = buildLayoutParams(rawParams);
        }
        if (panel.getParent() == null) {
            try {
                windowManager.addView(panel, params);
            } catch (Throwable t) {
                Log.e(TAG, "add patch overlay failed", t);
                fallbackToHost();
                return;
            }
        } else {
            safeUpdateLayout();
        }
        renderNext(false);
        scheduleRotation();
    }

    void update(DataModel model) {
        if (model == null || !model.isDisplayable()) {
            return;
        }
        long now = System.currentTimeMillis();
        rememberModel(model, now);
        Log.i(TAG, "overlay cache: mode=" + model.mode + ", primary=" + model.primary + ", keyType=" + model.keyType);
        if (panel == null) {
            return;
        }
        if (lastModel == null || isCategoryStale(currentCategory, now) || currentCategory == model.category) {
            renderNext(false);
        }
        scheduleRotation();
    }

    private void rememberModel(DataModel model, long now) {
        if (model.category == DataModel.CATEGORY_NAV) {
            navModel = model;
            navUpdatedAt = now;
        } else if (model.category == DataModel.CATEGORY_TRAFFIC_LIGHT) {
            trafficLightModel = model;
            trafficLightUpdatedAt = now;
        } else if (model.category == DataModel.CATEGORY_CRUISE) {
            cruiseModel = model;
            cruiseUpdatedAt = now;
        }
    }

    private void renderNext(boolean advance) {
        DataModel model = chooseModel(advance);
        if (model != null) {
            render(model);
        }
    }

    private DataModel chooseModel(boolean advance) {
        long now = System.currentTimeMillis();
        int startIndex = indexOfCategory(currentCategory);
        if (startIndex < 0) {
            startIndex = 0;
        } else if (advance) {
            startIndex = (startIndex + 1) % DISPLAY_ORDER.length;
        }
        for (int i = 0; i < DISPLAY_ORDER.length; i++) {
            int category = DISPLAY_ORDER[(startIndex + i) % DISPLAY_ORDER.length];
            DataModel model = getFreshModel(category, now);
            if (model != null) {
                return model;
            }
        }
        return null;
    }

    private DataModel getFreshModel(int category, long now) {
        if (category == DataModel.CATEGORY_NAV) {
            return now - navUpdatedAt <= STALE_MS ? navModel : null;
        }
        if (category == DataModel.CATEGORY_TRAFFIC_LIGHT) {
            return now - trafficLightUpdatedAt <= STALE_MS ? trafficLightModel : null;
        }
        if (category == DataModel.CATEGORY_CRUISE) {
            return now - cruiseUpdatedAt <= STALE_MS ? cruiseModel : null;
        }
        return null;
    }

    private boolean isCategoryStale(int category, long now) {
        return getFreshModel(category, now) == null;
    }

    private int indexOfCategory(int category) {
        for (int i = 0; i < DISPLAY_ORDER.length; i++) {
            if (DISPLAY_ORDER[i] == category) {
                return i;
            }
        }
        return -1;
    }

    private void scheduleRotation() {
        if (rotateScheduled || mainHandler == null || panel == null || panel.getParent() == null) {
            return;
        }
        rotateScheduled = true;
        mainHandler.postDelayed(rotateRunnable, SLOT_MS);
    }

    private void render(DataModel model) {
        lastModel = model;
        currentCategory = model.category;
        Log.i(TAG, "overlay render: mode=" + model.mode + ", primary=" + model.primary + ", keyType=" + model.keyType);
        if (panel == null) {
            return;
        }
        modeText.setText(model.mode);
        primaryText.setText(model.primary);
        secondaryText.setText(model.secondary == null ? "" : model.secondary);
        applyStyle();
        safeUpdateLayout();
    }

    void removePatchWindows() {
        removeSettings();
        removePanel();
    }

    private LinearLayout buildPanel() {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(10), dp(7), dp(10), dp(7));
        root.setMinimumWidth(dp(184));
        root.setMinimumHeight(dp(82));
        root.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                toggleSettings();
                return true;
            }
        });
        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return handleDrag(event);
            }
        });

        modeText = text(12, Color.WHITE, true);
        primaryText = text(18, Color.WHITE, true);
        secondaryText = text(12, 0xFFE2E8F0, false);
        modeText.setGravity(Gravity.CENTER);
        modeText.setMinWidth(dp(64));
        modeText.setMinHeight(dp(22));
        primaryText.setMinWidth(dp(160));
        primaryText.setMinHeight(dp(30));
        secondaryText.setMinWidth(dp(160));
        secondaryText.setMinHeight(dp(18));

        root.addView(modeText, new LinearLayout.LayoutParams(dp(64), dp(22)));
        root.addView(primaryText, new LinearLayout.LayoutParams(-2, -2));
        root.addView(secondaryText, new LinearLayout.LayoutParams(-2, -2));
        applyStyle(root);
        return root;
    }

    private TextView text(int sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setSingleLine(false);
        if (bold) {
            view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    private WindowManager.LayoutParams buildLayoutParams(ViewGroup.LayoutParams raw) {
        WindowManager.LayoutParams out = new WindowManager.LayoutParams();
        if (raw instanceof WindowManager.LayoutParams) {
            WindowManager.LayoutParams source = (WindowManager.LayoutParams) raw;
            out.type = source.type;
            out.gravity = source.gravity != 0 ? source.gravity : (Gravity.TOP | Gravity.START);
            out.x = prefs.getInt(KEY_X, source.x);
            out.y = prefs.getInt(KEY_Y, source.y);
        } else {
            out.type = Build.VERSION.SDK_INT >= 26
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            out.gravity = Gravity.TOP | Gravity.START;
            out.x = prefs.getInt(KEY_X, dp(24));
            out.y = prefs.getInt(KEY_Y, dp(80));
        }
        out.width = WindowManager.LayoutParams.WRAP_CONTENT;
        out.height = WindowManager.LayoutParams.WRAP_CONTENT;
        out.format = PixelFormat.TRANSLUCENT;
        out.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        return out;
    }

    private boolean handleDrag(MotionEvent event) {
        if (params == null || windowManager == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = (int) event.getRawX();
                downRawY = (int) event.getRawY();
                startX = params.x;
                startY = params.y;
                return false;
            case MotionEvent.ACTION_MOVE:
                int dx = (int) event.getRawX() - downRawX;
                int dy = (int) event.getRawY() - downRawY;
                if (Math.abs(dx) < dp(3) && Math.abs(dy) < dp(3)) {
                    return false;
                }
                params.x = startX + dx;
                params.y = startY + dy;
                safeUpdateLayout();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                prefs.edit().putInt(KEY_X, params.x).putInt(KEY_Y, params.y).apply();
                return false;
            default:
                return false;
        }
    }

    private void toggleSettings() {
        if (settingsView != null && settingsView.getParent() != null) {
            removeSettings();
        } else {
            showSettings();
        }
    }

    private void showSettings() {
        if (windowManager == null || params == null) {
            return;
        }
        if (settingsView == null) {
            settingsView = buildSettingsView();
        }
        if (settingsParams == null) {
            settingsParams = new WindowManager.LayoutParams();
            settingsParams.type = params.type;
            settingsParams.width = dp(280);
            settingsParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
            settingsParams.format = PixelFormat.TRANSLUCENT;
            settingsParams.gravity = Gravity.TOP | Gravity.START;
            settingsParams.x = Math.max(0, params.x);
            settingsParams.y = Math.max(0, params.y + dp(72));
            settingsParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        }
        try {
            if (settingsView.getParent() == null) {
                windowManager.addView(settingsView, settingsParams);
            }
        } catch (Throwable t) {
            Log.e(TAG, "show settings failed", t);
        }
    }

    private View buildSettingsView() {
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.setBackground(roundRect(0xEE111827, dp(8), 0xFF334155));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = text(17, Color.WHITE, true);
        title.setText("悬浮窗设置");
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        root.addView(button("启用 / 禁用", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean enabled = !isEnabled();
                prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
                if (!enabled) {
                    removePanel();
                    fallbackToHost();
                } else {
                    attach(hostWindowManager, hostView, hostParams);
                }
            }
        }));
        root.addView(button("样式：经典", styleClick(0)));
        root.addView(button("样式：卡片", styleClick(1)));
        root.addView(button("样式：紧凑", styleClick(2)));
        root.addView(button("大小 -", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeScale(-10);
            }
        }));
        root.addView(button("大小 +", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeScale(10);
            }
        }));
        root.addView(button("关闭设置", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeSettings();
            }
        }));
        return scroll;
    }

    private View.OnClickListener styleClick(final int style) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit().putInt(KEY_STYLE, style).apply();
                applyStyle();
            }
        };
    }

    private Button button(String text, View.OnClickListener listener) {
        Button button = new Button(context);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(14);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(42));
        lp.topMargin = dp(8);
        button.setLayoutParams(lp);
        return button;
    }

    private void changeScale(int delta) {
        int scale = Math.max(70, Math.min(150, prefs.getInt(KEY_SCALE, 100) + delta));
        prefs.edit().putInt(KEY_SCALE, scale).apply();
        applyStyle();
        safeUpdateLayout();
    }

    private void applyStyle() {
        if (panel != null) {
            applyStyle(panel);
        }
    }

    private void applyStyle(LinearLayout root) {
        int style = prefs.getInt(KEY_STYLE, 0);
        int scale = prefs.getInt(KEY_SCALE, 100);
        float factor = scale / 100f;
        root.setScaleX(factor);
        root.setScaleY(factor);
        if (style == 1) {
            root.setBackground(roundRect(0xEE0F172A, dp(6), 0xFF22C55E));
            modeText.setTextColor(0xFFBBF7D0);
            primaryText.setTextColor(Color.WHITE);
            secondaryText.setTextColor(0xFFD1FAE5);
        } else if (style == 2) {
            root.setBackground(roundRect(0xDD020617, dp(18), 0xFF64748B));
            modeText.setTextColor(0xFFCBD5E1);
            primaryText.setTextColor(0xFFFFFFFF);
            secondaryText.setTextColor(0xFFCBD5E1);
        } else {
            root.setBackground(roundRect(0xEE111827, dp(8), 0xFFFACC15));
            modeText.setTextColor(0xFFFFF7A8);
            primaryText.setTextColor(Color.WHITE);
            secondaryText.setTextColor(0xFFE2E8F0);
        }
    }

    private GradientDrawable roundRect(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private void safeUpdateLayout() {
        if (windowManager == null || panel == null || params == null || panel.getParent() == null) {
            return;
        }
        try {
            windowManager.updateViewLayout(panel, params);
        } catch (Throwable t) {
            Log.e(TAG, "update overlay layout failed", t);
        }
    }

    private void removePanel() {
        if (windowManager == null || panel == null || panel.getParent() == null) {
            return;
        }
        try {
            windowManager.removeView(panel);
            if (mainHandler != null) {
                mainHandler.removeCallbacks(rotateRunnable);
            }
            rotateScheduled = false;
        } catch (Throwable t) {
            Log.e(TAG, "remove patch overlay failed", t);
        }
    }

    private void removeSettings() {
        if (windowManager == null || settingsView == null || settingsView.getParent() == null) {
            return;
        }
        try {
            windowManager.removeView(settingsView);
        } catch (Throwable t) {
            Log.e(TAG, "remove settings failed", t);
        }
    }

    private void fallbackToHost() {
        if (hostWindowManager == null || hostView == null || hostParams == null || hostView.getParent() != null) {
            return;
        }
        try {
            hostWindowManager.addView(hostView, hostParams);
        } catch (Throwable t) {
            Log.e(TAG, "fallback to host failed", t);
        }
    }

    private int dp(int value) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
