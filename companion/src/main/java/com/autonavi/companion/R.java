package com.autonavi.companion;

import android.content.Context;
import android.content.res.Resources;

public final class R {
    private static String packageName;
    private static Resources resources;

    private R() {
    }

    public static synchronized void init(Context context) {
        if (context == null) {
            return;
        }
        String currentPackage = context.getPackageName();
        if (currentPackage == null || currentPackage.length() == 0) {
            return;
        }
        if (currentPackage.equals(packageName) && resources != null) {
            return;
        }
        packageName = currentPackage;
        resources = context.getResources();
        layout.init();
        id.init();
        drawable.init();
    }

    private static int resolve(String type, String name) {
        return resources != null ? resources.getIdentifier(name, type, packageName) : 0;
    }

    public static final class layout {
        public static int panel_card;
        public static int panel_classic;
        public static int panel_dashboard;
        public static int panel_dynamic_island_full;

        private layout() {
        }

        private static void init() {
            panel_card = resolve("layout", "panel_card");
            panel_classic = resolve("layout", "panel_classic");
            panel_dashboard = resolve("layout", "panel_dashboard");
            panel_dynamic_island_full = resolve("layout", "panel_dynamic_island_full");
        }
    }

    public static final class id {
        public static int alert_caption_text;
        public static int alert_card;
        public static int alert_row;
        public static int alert_text;
        public static int card_cruise_edog_row;
        public static int card_cruise_lane_box;
        public static int card_cruise_lane_placeholder;
        public static int card_cruise_light_row;
        public static int card_cruise_row1;
        public static int card_cruise_row2;
        public static int card_nav_area;
        public static int card_nav_edog_row;
        public static int compact_cruise_dir_text;
        public static int compact_cruise_left;
        public static int compact_cruise_road_text;
        public static int compact_nav_turn_road_text;
        public static int compact_widget_row;
        public static int content_row;
        public static int detail_text;
        public static int eta_text;
        public static int full_mode_eta_arrive_time;
        public static int full_mode_eta_info_col;
        public static int full_mode_eta_remain_dist;
        public static int full_mode_turn_info_col;
        public static int heading_info_text;
        public static int lane_bar_placeholder;
        public static int lane_section;
        public static int light_row;
        public static int limit_badge_text;
        public static int mode_badge;
        public static int mode_row;
        public static int mode_text;
        public static int nav_turn_box;
        public static int nav_turn_dist;
        public static int nav_turn_icon;
        public static int road_info_text;
        public static int service_area_text;
        public static int summary_divider;
        public static int summary_mid_divider;
        public static int summary_row;
        public static int title_text;
        public static int turn_card;
        public static int turn_distance;
        public static int turn_icon;
        public static int turn_lead_icon;
        public static int turn_lead_text;
        public static int turn_row;
        public static int turn_text;

        private id() {
        }

        private static void init() {
            alert_caption_text = resolve("id", "alert_caption_text");
            alert_card = resolve("id", "alert_card");
            alert_row = resolve("id", "alert_row");
            alert_text = resolve("id", "alert_text");
            card_cruise_edog_row = resolve("id", "card_cruise_edog_row");
            card_cruise_lane_box = resolve("id", "card_cruise_lane_box");
            card_cruise_lane_placeholder = resolve("id", "card_cruise_lane_placeholder");
            card_cruise_light_row = resolve("id", "card_cruise_light_row");
            card_cruise_row1 = resolve("id", "card_cruise_row1");
            card_cruise_row2 = resolve("id", "card_cruise_row2");
            card_nav_area = resolve("id", "card_nav_area");
            card_nav_edog_row = resolve("id", "card_nav_edog_row");
            compact_cruise_dir_text = resolve("id", "compact_cruise_dir_text");
            compact_cruise_left = resolve("id", "compact_cruise_left");
            compact_cruise_road_text = resolve("id", "compact_cruise_road_text");
            compact_nav_turn_road_text = resolve("id", "compact_nav_turn_road_text");
            compact_widget_row = resolve("id", "compact_widget_row");
            content_row = resolve("id", "content_row");
            detail_text = resolve("id", "detail_text");
            eta_text = resolve("id", "eta_text");
            full_mode_eta_arrive_time = resolve("id", "full_mode_eta_arrive_time");
            full_mode_eta_info_col = resolve("id", "full_mode_eta_info_col");
            full_mode_eta_remain_dist = resolve("id", "full_mode_eta_remain_dist");
            full_mode_turn_info_col = resolve("id", "full_mode_turn_info_col");
            heading_info_text = resolve("id", "heading_info_text");
            lane_bar_placeholder = resolve("id", "lane_bar_placeholder");
            lane_section = resolve("id", "lane_section");
            light_row = resolve("id", "light_row");
            limit_badge_text = resolve("id", "limit_badge_text");
            mode_badge = resolve("id", "mode_badge");
            mode_row = resolve("id", "mode_row");
            mode_text = resolve("id", "mode_text");
            nav_turn_box = resolve("id", "nav_turn_box");
            nav_turn_dist = resolve("id", "nav_turn_dist");
            nav_turn_icon = resolve("id", "nav_turn_icon");
            road_info_text = resolve("id", "road_info_text");
            service_area_text = resolve("id", "service_area_text");
            summary_divider = resolve("id", "summary_divider");
            summary_mid_divider = resolve("id", "summary_mid_divider");
            summary_row = resolve("id", "summary_row");
            title_text = resolve("id", "title_text");
            turn_card = resolve("id", "turn_card");
            turn_distance = resolve("id", "turn_distance");
            turn_icon = resolve("id", "turn_icon");
            turn_lead_icon = resolve("id", "turn_lead_icon");
            turn_lead_text = resolve("id", "turn_lead_text");
            turn_row = resolve("id", "turn_row");
            turn_text = resolve("id", "turn_text");
        }
    }

    public static final class drawable {
        public static int ic_stat;
        public static int navigation_widget_icon_car_position_blue;
        public static int widget_drawable_auto_ic_edog_bicycle_lane_loading;
        public static int widget_drawable_auto_ic_edog_bus_loading;
        public static int widget_drawable_auto_ic_edog_camera_loading;
        public static int widget_drawable_auto_ic_edog_emergency_line_loading;
        public static int widget_drawable_auto_ic_edog_hov;
        public static int widget_drawable_auto_ic_edog_lamp;
        public static int widget_drawable_auto_ic_edog_limit_speed_loading;
        public static int widget_drawable_auto_ic_edog_line_loading;
        public static int widget_drawable_auto_ic_edog_parking_loading;
        public static int widget_drawable_auto_ic_edog_phone_loading;
        public static int widget_drawable_auto_ic_edog_railway;
        public static int widget_drawable_auto_ic_edog_recycle;
        public static int widget_drawable_auto_ic_edog_reverse;
        public static int widget_drawable_auto_ic_edog_seatbelt_loading;
        public static int widget_drawable_auto_ic_edog_sidewalk_loading;
        public static int widget_drawable_auto_ic_edog_space;
        public static int widget_drawable_auto_ic_edog_speaker_loading;
        public static int widget_drawable_auto_ic_edog_speed_etc_loading;
        public static int widget_drawable_auto_ic_edog_tail;
        public static int widget_drawable_auto_ic_edog_traffic_loading;

        private drawable() {
        }

        private static void init() {
            ic_stat = resolve("drawable", "ic_stat");
            navigation_widget_icon_car_position_blue = resolve("drawable", "navigation_widget_icon_car_position_blue");
            widget_drawable_auto_ic_edog_bicycle_lane_loading = resolve("drawable", "widget_drawable_auto_ic_edog_bicycle_lane_loading");
            widget_drawable_auto_ic_edog_bus_loading = resolve("drawable", "widget_drawable_auto_ic_edog_bus_loading");
            widget_drawable_auto_ic_edog_camera_loading = resolve("drawable", "widget_drawable_auto_ic_edog_camera_loading");
            widget_drawable_auto_ic_edog_emergency_line_loading = resolve("drawable", "widget_drawable_auto_ic_edog_emergency_line_loading");
            widget_drawable_auto_ic_edog_hov = resolve("drawable", "widget_drawable_auto_ic_edog_hov");
            widget_drawable_auto_ic_edog_lamp = resolve("drawable", "widget_drawable_auto_ic_edog_lamp");
            widget_drawable_auto_ic_edog_limit_speed_loading = resolve("drawable", "widget_drawable_auto_ic_edog_limit_speed_loading");
            widget_drawable_auto_ic_edog_line_loading = resolve("drawable", "widget_drawable_auto_ic_edog_line_loading");
            widget_drawable_auto_ic_edog_parking_loading = resolve("drawable", "widget_drawable_auto_ic_edog_parking_loading");
            widget_drawable_auto_ic_edog_phone_loading = resolve("drawable", "widget_drawable_auto_ic_edog_phone_loading");
            widget_drawable_auto_ic_edog_railway = resolve("drawable", "widget_drawable_auto_ic_edog_railway");
            widget_drawable_auto_ic_edog_recycle = resolve("drawable", "widget_drawable_auto_ic_edog_recycle");
            widget_drawable_auto_ic_edog_reverse = resolve("drawable", "widget_drawable_auto_ic_edog_reverse");
            widget_drawable_auto_ic_edog_seatbelt_loading = resolve("drawable", "widget_drawable_auto_ic_edog_seatbelt_loading");
            widget_drawable_auto_ic_edog_sidewalk_loading = resolve("drawable", "widget_drawable_auto_ic_edog_sidewalk_loading");
            widget_drawable_auto_ic_edog_space = resolve("drawable", "widget_drawable_auto_ic_edog_space");
            widget_drawable_auto_ic_edog_speaker_loading = resolve("drawable", "widget_drawable_auto_ic_edog_speaker_loading");
            widget_drawable_auto_ic_edog_speed_etc_loading = resolve("drawable", "widget_drawable_auto_ic_edog_speed_etc_loading");
            widget_drawable_auto_ic_edog_tail = resolve("drawable", "widget_drawable_auto_ic_edog_tail");
            widget_drawable_auto_ic_edog_traffic_loading = resolve("drawable", "widget_drawable_auto_ic_edog_traffic_loading");
        }
    }
}
