package com.pixel12.qs;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
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
 * Values come from the module app (Pixel 12 QS) through its SettingsProvider.
 * Every hook is wrapped in try/catch so a ROM difference can never crash SystemUI.
 * Check the LSPosed log for the tag "Pixel12QS" if something doesn't apply.
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "Pixel12QS";
    private static final String SYSTEMUI = "com.android.systemui";
    private static final Uri URI = Uri.parse("content://" + Config.AUTHORITY);

    // ---- settings cache ----
    private static Bundle cache;
    private static long lastLoad;
    private static boolean loading;

    private static Bundle settings(Context ctx) {
        if (loading) return cache;
        long now = SystemClock.uptimeMillis();
        if (cache != null && now - lastLoad < 1500) return cache;
        loading = true;
        try {
            if (ctx != null) {
                Bundle b = ctx.getContentResolver().call(URI, "get", null, null);
                if (b != null) cache = b;
            }
        } catch (Throwable ignored) {
        } finally {
            loading = false;
            lastLoad = now;
        }
        return cache;
    }

    private static int get(Context ctx, String key) {
        Bundle b = settings(ctx);
        if (b != null && b.containsKey(key)) return b.getInt(key);
        return Config.def(key);
    }

    private static Context appContext() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            return (Context) XposedHelpers.callStaticMethod(at, "currentApplication");
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        if (!SYSTEMUI.equals(lpparam.packageName)) return;
        log("Loaded in SystemUI");
        hookResources();
        hookTileView(lpparam.classLoader);
    }

    // ---- columns and tile height ----
    private void hookResources() {
        try {
            XposedHelpers.findAndHookMethod(Resources.class, "getInteger", int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Resources r = (Resources) param.thisObject;
                                String name = r.getResourceEntryName((Integer) param.args[0]);
                                if ("quick_settings_num_columns".equals(name)) {
                                    param.setResult(get(appContext(), Config.COLUMNS));
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        } catch (Throwable t) {
            log("columns hook failed", t);
        }

        try {
            XposedHelpers.findAndHookMethod(Resources.class, "getDimensionPixelSize", int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Resources r = (Resources) param.thisObject;
                                String name = r.getResourceEntryName((Integer) param.args[0]);
                                if ("qs_tile_height".equals(name)) {
                                    int dp = get(appContext(), Config.HEIGHT);
                                    param.setResult(Math.round(dp * r.getDisplayMetrics().density));
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        } catch (Throwable t) {
            log("height hook failed", t);
        }
    }

    // ---- tile layout and colors ----
    private void hookTileView(ClassLoader cl) {
        final Class<?> tileView;
        try {
            tileView = XposedHelpers.findClass("com.android.systemui.qs.tileimpl.QSTileView", cl);
        } catch (Throwable t) {
            log("QSTileView not found", t);
            return;
        }

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
        int padH = dp(c, get(c, Config.PAD));
        tile.setOrientation(LinearLayout.HORIZONTAL);
        tile.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        tile.setPadding(padH, dp(c, 8), padH - dp(c, 4), dp(c, 8));
        tile.setClipChildren(false);
        tile.setClipToPadding(false);

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
        int color = get(c, s == 2 ? Config.ACTIVE : Config.INACTIVE);

        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(c, get(c, Config.RADIUS)));
        tile.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape, null));
        tile.setAlpha(s == 0 ? 0.5f : 1f);

        // Icon circle behind the icon
        View bg = (View) getField(tile, "mBg");
        if (bg != null) {
            bg.setVisibility(get(c, Config.HIDE_CIRCLE) == 1 ? View.GONE : View.VISIBLE);
        }

        int sp = get(c, Config.TEXT_SP);
        TextView label = (TextView) getField(tile, "mLabel");
        if (label != null) {
            label.setTextColor(get(c, Config.TEXT));
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        }
        TextView second = (TextView) getField(tile, "mSecondLine");
        if (second != null) {
            second.setTextColor(get(c, Config.SECOND));
            second.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(8, sp - 2));
        }
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
