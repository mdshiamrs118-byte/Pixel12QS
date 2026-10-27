package com.pixel12.qs;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Restyles Android 10 SystemUI quick settings tiles into Pixel / Android 12 style pills.
 * Every hook is wrapped in try/catch so a ROM difference can never crash SystemUI.
 * Check LSPosed logs for the tag "Pixel12QS" if something doesn't apply.
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "Pixel12QS";
    private static final String SYSTEMUI = "com.android.systemui";

    // ---- Tweak these ----
    private static final int COLUMNS = 2;
    private static final int RADIUS_DP = 28;
    private static final int COLOR_ACTIVE = 0xFF1A73E8;
    private static final int COLOR_INACTIVE = 0xFF3C4043;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_SECOND = 0xCCFFFFFF;

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        if (!SYSTEMUI.equals(lpparam.packageName)) return;
        log("Loaded in SystemUI");
        hookColumns();
        hookTileView(lpparam.classLoader);
    }

    // ---- 2 columns ----
    private void hookColumns() {
        try {
            XposedHelpers.findAndHookMethod(Resources.class, "getInteger", int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Resources r = (Resources) param.thisObject;
                                String name = r.getResourceEntryName((Integer) param.args[0]);
                                if ("quick_settings_num_columns".equals(name)) {
                                    param.setResult(COLUMNS);
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        } catch (Throwable t) {
            log("hookColumns failed", t);
        }
    }

    // ---- Tile layout and colors ----
    private void hookTileView(ClassLoader cl) {
        final Class<?> tileView;
        try {
            tileView = XposedHelpers.findClass("com.android.systemui.qs.tileimpl.QSTileView", cl);
        } catch (Throwable t) {
            log("QSTileView not found", t);
            return;
        }

        // Rearrange: icon left, labels right
        try {
            XposedBridge.hookAllConstructors(tileView, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        restyleLayout((LinearLayout) param.thisObject);
                    } catch (Throwable t) {
                        log("restyleLayout failed", t);
                    }
                }
            });
        } catch (Throwable t) {
            log("constructor hook failed", t);
        }

        // Colors per state
        try {
            Class<?> stateClass = XposedHelpers.findClass(
                    "com.android.systemui.plugins.qs.QSTile$State", cl);
            XposedHelpers.findAndHookMethod(tileView, "handleStateChanged", stateClass,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                applyState((LinearLayout) param.thisObject, param.args[0]);
                            } catch (Throwable t) {
                                log("applyState failed", t);
                            }
                        }
                    });
        } catch (Throwable t) {
            log("handleStateChanged hook failed", t);
        }
    }

    private void restyleLayout(LinearLayout tile) {
        Context c = tile.getContext();
        tile.setOrientation(LinearLayout.HORIZONTAL);
        tile.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(c, 16), dp(c, 8), dp(c, 12), dp(c, 8));
        tile.setClipChildren(false);
        tile.setClipToPadding(false);

        // Hide the Android 10 circle behind the icon
        View bg = (View) getField(tile, "mBg");
        if (bg != null) bg.setVisibility(View.GONE);

        // Label container: fill remaining width, left aligned
        View container = (View) getField(tile, "mLabelContainer");
        if (container != null) {
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMarginStart(dp(c, 12));
            lp.gravity = Gravity.CENTER_VERTICAL;
            container.setLayoutParams(lp);
            if (container instanceof LinearLayout) {
                ((LinearLayout) container).setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            }
        }
        TextView label = (TextView) getField(tile, "mLabel");
        if (label != null) {
            label.setGravity(Gravity.START);
            label.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            label.setSingleLine(true);
        }
        TextView second = (TextView) getField(tile, "mSecondLine");
        if (second != null) {
            second.setGravity(Gravity.START);
            second.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            second.setSingleLine(true);
        }
    }

    private void applyState(LinearLayout tile, Object state) {
        Context c = tile.getContext();
        int s = XposedHelpers.getIntField(state, "state"); // 0 unavailable, 1 inactive, 2 active
        int color = (s == 2) ? COLOR_ACTIVE : COLOR_INACTIVE;

        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(c, RADIUS_DP));
        tile.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x33FFFFFF), shape, null));
        tile.setAlpha(s == 0 ? 0.5f : 1f);

        TextView label = (TextView) getField(tile, "mLabel");
        if (label != null) label.setTextColor(COLOR_TEXT);
        TextView second = (TextView) getField(tile, "mSecondLine");
        if (second != null) second.setTextColor(COLOR_TEXT_SECOND);
    }

    // ---- helpers ----
    private static Object getField(Object o, String name) {
        try {
            return XposedHelpers.getObjectField(o, name);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    private static void log(String msg) {
        XposedBridge.log(TAG + ": " + msg);
    }

    private static void log(String msg, Throwable t) {
        XposedBridge.log(TAG + ": " + msg);
        XposedBridge.log(t);
    }
}
