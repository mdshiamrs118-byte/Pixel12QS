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
                                else if ("quick_qs_total_height".equals(name)) {
                                    // keep the collapsed header tall enough for the taller quick tiles
                                    int origId = r.getIdentifier("qs_quick_tile_size", "dimen",
                                            r.getResourcePackageName((Integer) param.args[0]));
                                    if (origId != 0) {
                                        int orig = r.getDimensionPixelSize(origId);
                                        int want = Math.round(get(appContext(), Config.QUICK_HEIGHT)
                                                * r.getDisplayMetrics().density);
                                        param.setResult(((Integer) param.getResult()) + (want - orig));
                                    }
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

        hookQuickTiles(cl, tileView);
        hookQuickBarLayout(cl);
    }

    // ---- quick bar: same pill tiles as the big grid, filling the row evenly ----
    private void hookQuickBarLayout(ClassLoader cl) {
        // 1) Make the quick bar use the full tile view (icon + label) instead of the icon-only one
        String[] factories = {
                "com.android.systemui.qs.QSTileHost",
                "com.android.systemui.qs.tileimpl.QSFactoryImpl"
        };
        for (String name : factories) {
            try {
                Class<?> f = XposedHelpers.findClass(name, cl);
                XposedBridge.hookAllMethods(f, "createTileView", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            if (param.args.length >= 2 && param.args[1] instanceof Boolean) {
                                param.args[1] = false; // collapsedView = false
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                });
                log("createTileView hooked in " + name);
            } catch (Throwable t) {
                log("createTileView hook skipped for " + name);
            }
        }

        // 2) Spread the tiles evenly across the row (no empty middle, no edge-hugging icons)
        try {
            Class<?> header = XposedHelpers.findClass(
                    "com.android.systemui.qs.QuickQSPanel$HeaderTileLayout", cl);
            XC_MethodHook relayout = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        fixQuickRow((android.view.ViewGroup) param.thisObject);
                    } catch (Throwable t) {
                        log("fixQuickRow failed", t);
                    }
                }
            };
            XposedBridge.hookAllMethods(header, "addTile", relayout);
            XposedBridge.hookAllMethods(header, "removeTile", relayout);
            XposedBridge.hookAllMethods(header, "updateResources", relayout);
            log("HeaderTileLayout hooked");
        } catch (Throwable t) {
            log("HeaderTileLayout hook failed", t);
        }
    }

    private void fixQuickRow(android.view.ViewGroup row) {
        Context c = row.getContext();
        int h = dp(c, get(c, Config.QUICK_HEIGHT));
        int gap = dp(c, get(c, Config.QUICK_GAP));
        if (row instanceof LinearLayout) {
            ((LinearLayout) row).setGravity(Gravity.CENTER_VERTICAL);
        }
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            if (v instanceof android.widget.Space) {
                // the spacers are what pushed the icons to the edges
                if (v.getVisibility() != View.GONE) v.setVisibility(View.GONE);
                continue;
            }
            android.view.ViewGroup.LayoutParams p = v.getLayoutParams();
            if (p instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams o = (LinearLayout.LayoutParams) p;
                if (o.width == 0 && o.height == h && o.weight == 1f
                        && o.leftMargin == gap && o.rightMargin == gap) continue;
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, h, 1f);
            lp.setMargins(gap, 0, gap, 0);
            lp.gravity = Gravity.CENTER_VERTICAL;
            v.setLayoutParams(lp);
        }
    }

    // ---- quick bar (the collapsed row of icon tiles at the top) ----
    private void hookQuickTiles(ClassLoader cl, final Class<?> tileView) {
        try {
            Class<?> baseClass = XposedHelpers.findClass(
                    "com.android.systemui.qs.tileimpl.QSTileBaseView", cl);
            Class<?> stateClass = XposedHelpers.findClass(
                    "com.android.systemui.plugins.qs.QSTile$State", cl);
            XposedHelpers.findAndHookMethod(baseClass, "handleStateChanged", stateClass,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                // Big tiles are handled by the QSTileView hook
                                if (tileView.isInstance(param.thisObject)) return;
                                applyQuickState((View) param.thisObject, param.args[0]);
                            } catch (Throwable t) {
                                log("applyQuickState failed", t);
                            }
                        }
                    });
        } catch (Throwable t) {
            log("quick tile hook failed", t);
        }
    }

    /**
     * Collapsed quick bar tile (icon-only class). We turn it into the same pill as the big tiles:
     * rounded background, icon on the left, our own label + status text on the right.
     * Works without knowing any RR class names: the row is found through the tile's parent.
     */
    private void applyQuickState(View tile, Object state) {
        Context c = tile.getContext();
        int s = XposedHelpers.getIntField(state, "state");
        int color = get(c, s == 2 ? Config.ACTIVE : Config.INACTIVE);

        View circle = (View) getField(tile, "mBg");
        if (circle != null) circle.setVisibility(View.GONE);

        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(c, get(c, Config.RADIUS)));
        tile.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape, null));
        tile.setAlpha(s == 0 ? 0.5f : 1f);

        if (tile instanceof LinearLayout) {
            addQuickLabels((LinearLayout) tile, state);
        }

        android.view.ViewParent parent = tile.getParent();
        if (parent instanceof LinearLayout) {
            fixQuickRow((LinearLayout) parent);
        }
    }

    private void addQuickLabels(LinearLayout tile, Object state) {
        Context c = tile.getContext();
        TextView label = (TextView) XposedHelpers.getAdditionalInstanceField(tile, "p12_label");
        TextView second = (TextView) XposedHelpers.getAdditionalInstanceField(tile, "p12_second");
        if (label == null) {
            int padH = dp(c, get(c, Config.PAD));
            tile.setOrientation(LinearLayout.HORIZONTAL);
            tile.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            tile.setPadding(padH, dp(c, 8), padH - dp(c, 4), dp(c, 8));
            tile.setClipChildren(false);
            tile.setClipToPadding(false);

            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);

            label = new TextView(c);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            second = new TextView(c);
            second.setSingleLine(true);
            second.setEllipsize(android.text.TextUtils.TruncateAt.END);
            second.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            col.addView(label);
            col.addView(second);

            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMarginStart(dp(c, 12));
            lp.gravity = Gravity.CENTER_VERTICAL;
            tile.addView(col, lp);

            XposedHelpers.setAdditionalInstanceField(tile, "p12_label", label);
            XposedHelpers.setAdditionalInstanceField(tile, "p12_second", second);
            log("Styled a quick bar tile, parent = " + tile.getParent().getClass().getName());
        }

        int sp = get(c, Config.TEXT_SP);
        label.setTextColor(get(c, Config.TEXT));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        second.setTextColor(get(c, Config.SECOND));
        second.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(8, sp - 2));

        Object l = getField(state, "label");
        Object sec = getField(state, "secondaryLabel");
        label.setText(l instanceof CharSequence ? (CharSequence) l : "");
        CharSequence secText = sec instanceof CharSequence ? (CharSequence) sec : "";
        second.setText(secText);
        second.setVisibility(secText.length() == 0 ? View.GONE : View.VISIBLE);
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
        android.view.ViewParent parent = tile.getParent();
        if (parent instanceof LinearLayout) {
            fixQuickRow((android.view.ViewGroup) parent);
        }
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
