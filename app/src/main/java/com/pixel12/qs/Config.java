package com.pixel12.qs;

/** Shared setting keys and default values (used by the app and by the SystemUI hook). */
public final class Config {
    private Config() {}

    public static final String AUTHORITY = "com.pixel12.qs.settings";
    public static final String PREFS = "pixel12qs_settings";

    public static final String COLUMNS = "columns";
    public static final String HEIGHT = "height_dp";
    public static final String RADIUS = "radius_dp";
    public static final String PAD = "pad_h_dp";
    public static final String TEXT_SP = "text_sp";
    public static final String ACTIVE = "active_color";
    public static final String INACTIVE = "inactive_color";
    public static final String TEXT = "text_color";
    public static final String SECOND = "second_color";
    public static final String HIDE_CIRCLE = "hide_circle";
    public static final String QUICK_RADIUS = "quick_radius_dp";
    public static final String QUICK_HEIGHT = "quick_height_dp";
    public static final String QUICK_GAP = "quick_gap_dp";

    public static final String[] KEYS = {
            COLUMNS, HEIGHT, RADIUS, PAD, TEXT_SP, ACTIVE, INACTIVE, TEXT, SECOND, HIDE_CIRCLE, QUICK_RADIUS, QUICK_HEIGHT, QUICK_GAP
    };
    public static final int[] DEFAULTS = {
            2, 72, 28, 16, 14, 0xFF1A73E8, 0xFF3C4043, 0xFFFFFFFF, 0xCCFFFFFF, 1, 24, 60, 4
    };

    public static int def(String key) {
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i].equals(key)) return DEFAULTS[i];
        }
        return 0;
    }
}
