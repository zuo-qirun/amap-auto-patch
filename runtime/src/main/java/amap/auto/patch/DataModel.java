package amap.auto.patch;

import android.os.Bundle;

import java.util.List;

final class DataModel {
    static final int CATEGORY_NONE = 0;
    static final int CATEGORY_NAV = 1;
    static final int CATEGORY_TRAFFIC_LIGHT = 2;
    static final int CATEGORY_CRUISE = 3;

    static final int KEY_NAV_STATE = 10019;
    static final int KEY_ROUTE_GUIDANCE = 10001;
    static final int KEY_CRUISE = 60021;
    static final int KEY_LANE = 13012;
    static final int KEY_TRAFFIC_LIGHT = 60073;

    int keyType = -1;
    String action = "";
    String mode = "待接收";
    String primary = "等待高德悬浮窗数据";
    String secondary = "";
    int lightStatus = -1;
    int lightSeconds = -1;
    int lightDir = -1;
    int lightsCount = 0;
    int category = CATEGORY_NONE;

    static DataModel fromBroadcast(String action, Bundle extras) {
        if (extras == null) {
            return null;
        }
        DataModel model = new DataModel();
        model.action = action != null ? action : "";
        model.keyType = intValue(extras, "KEY_TYPE", -1);
        if (model.keyType == KEY_NAV_STATE) {
            int state = intValue(extras, "EXTRA_STATE", -1);
            model.mode = state == 3 ? "高德前台" : state == 4 ? "高德后台" : "高德状态";
            model.primary = "EXTRA_STATE=" + state;
            model.secondary = readableExtras(extras);
            return model;
        }
        if (model.keyType == KEY_TRAFFIC_LIGHT || hasAny(extras, "trafficLightStatus", "redLightCountDownSeconds", "lightsData")) {
            model.mode = "红绿灯";
            model.category = CATEGORY_TRAFFIC_LIGHT;
            model.lightStatus = intValue(extras, "trafficLightStatus",
                    intValue(extras, "EXTRA_TRAFFICLIGHTSTATUS",
                            intValue(extras, "LIGHT_STATUS", intValue(extras, "status", -1))));
            model.lightSeconds = intValue(extras, "redLightCountDownSeconds",
                    intValue(extras, "EXTRA_REDLIGHTCOUNTDOWNSECONDS",
                            intValue(extras, "COUNTDOWN", intValue(extras, "countDown", -1))));
            model.lightDir = intValue(extras, "dir", intValue(extras, "direction", -1));
            model.lightsCount = intValue(extras, "lightsCount", intValue(extras, "trafficLightNum", 0));
            if (!model.hasValidTrafficLight()) {
                return null;
            }
            model.primary = lightText(model.lightStatus, model.lightSeconds);
            model.secondary = "方向 " + directionText(model.lightDir) + "  灯组 " + valueOrDash(model.lightsCount);
            return model;
        }
        if (model.keyType == KEY_ROUTE_GUIDANCE) {
            model.mode = "导航";
            model.category = CATEGORY_NAV;
            model.primary = firstString(extras,
                    "NEXT_ROAD_NAME", "NEXT_ROAD", "ROUTE_ROAD_NAME", "ROAD_NAME", "EXTRA_ROAD_NAME", "roadName");
            model.secondary = firstString(extras,
                    "SEG_REMAIN_DIS", "ROUTE_REMAIN_DIS", "NEXT_ROAD_DISTANCE", "EXTRA_DISTANCE", "distance");
            normalizeEmpty(model, "导航数据");
            return model;
        }
        if (model.keyType == KEY_CRUISE || hasAny(extras, "CUR_SPEED", "SPEED", "LIMITED_SPEED", "CAMERA_SPEED")) {
            model.mode = "巡航";
            model.category = CATEGORY_CRUISE;
            model.primary = firstString(extras, "ROAD_NAME", "EXTRA_ROAD_NAME", "roadName", "CURRENT_ROAD_NAME");
            int speed = intValue(extras, "CUR_SPEED", intValue(extras, "SPEED", -1));
            int limit = intValue(extras, "LIMITED_SPEED", intValue(extras, "CAMERA_SPEED", -1));
            model.secondary = speed >= 0 ? "车速 " + speed + (limit > 0 ? " / 限速 " + limit : "") : readableExtras(extras);
            normalizeEmpty(model, "巡航数据");
            return model;
        }
        if (model.keyType == KEY_LANE || hasAny(extras, "EXTRA_DRIVE_WAY", "LANE_INFO")) {
            model.mode = "车道";
            model.category = CATEGORY_CRUISE;
            model.primary = "车道信息";
            model.secondary = firstString(extras, "EXTRA_DRIVE_WAY", "LANE_INFO", "laneInfo");
            normalizeEmpty(model, "车道数据");
            return model;
        }
        model.mode = model.keyType >= 0 ? "KEY_TYPE " + model.keyType : "广播";
        model.primary = readableExtras(extras);
        model.secondary = "";
        normalizeEmpty(model, "广播数据");
        return model;
    }

    static DataModel fromTrafficLightWrapper(Object wrapper) {
        if (wrapper == null) {
            return null;
        }
        List<?> lights = PatchRuntime.readListField(wrapper, "a");
        if (lights == null || lights.isEmpty()) {
            return null;
        }
        Object best = null;
        int bestScore = -1;
        for (int i = 0; i < lights.size(); i++) {
            Object item = lights.get(i);
            Integer status = PatchRuntime.readIntField(item, "d");
            Integer seconds = PatchRuntime.readIntField(item, "e");
            int score = 0;
            if (seconds != null && seconds.intValue() > 0) {
                score += 4;
            }
            if (status != null && status.intValue() > 0) {
                score += 2;
            }
            if (seconds != null && seconds.intValue() >= 4 && seconds.intValue() <= 10) {
                score += 1;
            }
            if (score > bestScore) {
                best = item;
                bestScore = score;
            }
        }
        if (best == null || bestScore <= 0) {
            return null;
        }
        Integer status = PatchRuntime.readIntField(best, "d");
        Integer seconds = PatchRuntime.readIntField(best, "e");
        Integer dir = PatchRuntime.readIntField(best, "c");
        Integer waitNum = PatchRuntime.readIntField(best, "a");
        Integer showType = PatchRuntime.readIntField(best, "f");
        DataModel model = new DataModel();
        model.keyType = KEY_TRAFFIC_LIGHT;
        model.mode = "红绿灯";
        model.category = CATEGORY_TRAFFIC_LIGHT;
        model.lightStatus = status != null ? status : -1;
        model.lightSeconds = seconds != null ? seconds : -1;
        model.lightDir = dir != null ? dir : -1;
        model.lightsCount = lights.size();
        if (!model.hasValidTrafficLight()) {
            return null;
        }
        model.primary = lightText(model.lightStatus, model.lightSeconds);
        model.secondary = "方向 " + directionText(model.lightDir)
                + "  灯组 " + model.lightsCount
                + (waitNum != null ? "  等待 " + waitNum : "")
                + (showType != null ? "  类型 " + showType : "");
        return model;
    }

    boolean isDisplayable() {
        return category == CATEGORY_NAV || category == CATEGORY_CRUISE
                || (category == CATEGORY_TRAFFIC_LIGHT && hasValidTrafficLight());
    }

    private boolean hasValidTrafficLight() {
        return lightSeconds > 0 || lightStatus > 0;
    }

    private static void normalizeEmpty(DataModel model, String fallback) {
        if (model.primary == null || model.primary.length() == 0) {
            model.primary = fallback;
        }
        if (model.secondary == null) {
            model.secondary = "";
        }
    }

    private static boolean hasAny(Bundle extras, String... keys) {
        for (String key : keys) {
            if (extras.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    private static int intValue(Bundle extras, String key, int fallback) {
        if (extras == null || !extras.containsKey(key)) {
            return fallback;
        }
        Object value = extras.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static String firstString(Bundle extras, String... keys) {
        if (extras == null) {
            return "";
        }
        for (String key : keys) {
            if (!extras.containsKey(key)) {
                continue;
            }
            Object value = extras.get(key);
            if (value != null) {
                String text = String.valueOf(value);
                if (text.length() > 0) {
                    return text;
                }
            }
        }
        return "";
    }

    private static String readableExtras(Bundle extras) {
        if (extras == null || extras.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String key : extras.keySet()) {
            if (sb.length() > 0) {
                sb.append("  ");
            }
            Object value = extras.get(key);
            sb.append(key).append('=').append(value);
            if (sb.length() > 80) {
                break;
            }
        }
        return sb.toString();
    }

    private static String lightText(int status, int seconds) {
        String color;
        if (status == 1) {
            color = "即将绿灯";
        } else if (status == 2) {
            color = "绿灯";
        } else if (status == 3) {
            color = "黄灯";
        } else if (status == 4) {
            color = "即将红灯";
        } else {
            color = "信号灯";
        }
        return seconds >= 0 ? color + " " + seconds + "s" : color;
    }

    private static String directionText(int value) {
        if (value == 1) {
            return "左转";
        }
        if (value == 2) {
            return "右转";
        }
        if (value == 4) {
            return "直行";
        }
        if (value == 3) {
            return "掉头";
        }
        return valueOrDash(value);
    }

    private static String valueOrDash(int value) {
        return value >= 0 ? String.valueOf(value) : "--";
    }
}
