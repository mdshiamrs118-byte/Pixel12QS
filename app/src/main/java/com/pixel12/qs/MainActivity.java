package com.pixel12.qs;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private SharedPreferences sp;
    private LinearLayout root;

    private final LinearLayout[] pvTile = new LinearLayout[2];
    private final TextView[] pvLabel = new TextView[2];
    private final TextView[] pvSecond = new TextView[2];
    private final View[] pvDot = new View[2];

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences(Config.PREFS, MODE_PRIVATE);

        ScrollView sv = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(32));
        sv.addView(root);
        setContentView(sv);

        TextView title = new TextView(this);
        title.setText("Pixel 12 QS");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Changes save automatically. Tap \"Apply\" to restart SystemUI and see them.");
        hint.setAlpha(0.7f);
        hint.setPadding(0, dp(4), 0, dp(12));
        root.addView(hint);

        buildPreview();

        header("Layout");
        slider("Columns", Config.COLUMNS, 1, 5, "");
        slider("Tile height", Config.HEIGHT, 48, 120, " dp");
        slider("Corner roundness", Config.RADIUS, 0, 60, " dp");
        slider("Side padding", Config.PAD, 4, 32, " dp");
        slider("Text size", Config.TEXT_SP, 10, 20, " sp");
        slider("Quick bar roundness", Config.QUICK_RADIUS, 0, 40, " dp");

        header("Colors");
        colorRow("Active tile color", Config.ACTIVE);
        colorRow("Inactive tile color", Config.INACTIVE);
        colorRow("Label text color", Config.TEXT);
        colorRow("Status text color", Config.SECOND);

        header("Style");
        toggle("Hide icon circle", Config.HIDE_CIRCLE);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(24), 0, 0);
        Button apply = new Button(this);
        apply.setText("Apply (restart SystemUI)");
        apply.setOnClickListener(v -> restartSystemUi());
        Button reset = new Button(this);
        reset.setText("Reset");
        reset.setOnClickListener(v -> {
            sp.edit().clear().apply();
            recreate();
        });
        buttons.addView(apply, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f));
        buttons.addView(reset, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(buttons);

        refreshPreview();
    }

    // ---------- preview ----------
    private void buildPreview() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, dp(8));
        for (int i = 0; i < 2; i++) {
            LinearLayout t = new LinearLayout(this);
            t.setOrientation(LinearLayout.HORIZONTAL);
            t.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);

            View dot = new View(this);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            dot.setBackground(d);
            t.addView(dot, new LinearLayout.LayoutParams(dp(24), dp(24)));

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            clp.setMarginStart(dp(12));
            TextView l = new TextView(this);
            l.setText(i == 0 ? "Wi-Fi" : "Bluetooth");
            l.setSingleLine(true);
            TextView s = new TextView(this);
            s.setText(i == 0 ? "Home" : "Off");
            s.setSingleLine(true);
            col.addView(l);
            col.addView(s);
            t.addView(col, clp);

            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, dp(72), 1f);
            tlp.setMargins(dp(4), 0, dp(4), 0);
            row.addView(t, tlp);

            pvTile[i] = t;
            pvLabel[i] = l;
            pvSecond[i] = s;
            pvDot[i] = dot;
        }
        root.addView(row);
    }

    private void refreshPreview() {
        for (int i = 0; i < 2; i++) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(geti(i == 0 ? Config.ACTIVE : Config.INACTIVE));
            g.setCornerRadius(dp(geti(Config.RADIUS)));
            pvTile[i].setBackground(g);

            ViewGroup.LayoutParams lp = pvTile[i].getLayoutParams();
            lp.height = dp(geti(Config.HEIGHT));
            pvTile[i].setLayoutParams(lp);

            int ph = dp(geti(Config.PAD));
            pvTile[i].setPadding(ph, 0, ph, 0);

            pvLabel[i].setTextColor(geti(Config.TEXT));
            pvLabel[i].setTextSize(TypedValue.COMPLEX_UNIT_SP, geti(Config.TEXT_SP));
            pvSecond[i].setTextColor(geti(Config.SECOND));
            pvSecond[i].setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(8, geti(Config.TEXT_SP) - 2));

            ((GradientDrawable) pvDot[i].getBackground()).setColor(geti(Config.TEXT));
            pvDot[i].setVisibility(View.VISIBLE);
        }
    }

    // ---------- UI builders ----------
    private void header(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        t.setPadding(0, dp(20), 0, dp(4));
        root.addView(t);
    }

    private void slider(final String label, final String key, final int min, int max, final String unit) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        final TextView t = new TextView(this);
        int val = geti(key);
        t.setText(label + ": " + val + unit);

        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(Math.max(0, val - min));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                int v = p + min;
                sp.edit().putInt(key, v).apply();
                t.setText(label + ": " + v + unit);
                refreshPreview();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        row.addView(t);
        row.addView(sb);
        root.addView(row);
    }

    private void toggle(String label, final String key) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setChecked(geti(key) == 1);
        sw.setPadding(0, dp(8), 0, dp(8));
        sw.setOnCheckedChangeListener((v, checked) -> {
            sp.edit().putInt(key, checked ? 1 : 0).apply();
            refreshPreview();
        });
        root.addView(sw);
    }

    private void colorRow(final String label, final String key) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        TextView t = new TextView(this);
        t.setText(label);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final View swatch = new View(this);
        setSwatch(swatch, geti(key));
        row.addView(swatch, new LinearLayout.LayoutParams(dp(56), dp(32)));

        row.setOnClickListener(v -> showColorDialog(label, key, swatch));
        root.addView(row);
    }

    private void setSwatch(View v, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(8));
        g.setStroke(dp(1), 0x88888888);
        v.setBackground(g);
    }

    private void showColorDialog(String label, final String key, final View swatch) {
        int c = geti(key);
        final int[] argb = {Color.alpha(c), Color.red(c), Color.green(c), Color.blue(c)};
        final boolean[] updating = {false};

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), 0);

        final View prev = new View(this);
        setSwatch(prev, c);
        box.addView(prev, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        final EditText hex = new EditText(this);
        hex.setSingleLine(true);
        hex.setText(String.format("#%08X", c));
        box.addView(hex);

        final SeekBar[] bars = new SeekBar[4];
        String[] names = {"Alpha", "Red", "Green", "Blue"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            TextView n = new TextView(this);
            n.setText(names[i]);
            box.addView(n);
            SeekBar sb = new SeekBar(this);
            sb.setMax(255);
            sb.setProgress(argb[i]);
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                    if (updating[0]) return;
                    argb[idx] = p;
                    int col = Color.argb(argb[0], argb[1], argb[2], argb[3]);
                    updating[0] = true;
                    hex.setText(String.format("#%08X", col));
                    updating[0] = false;
                    setSwatch(prev, col);
                }

                @Override
                public void onStartTrackingTouch(SeekBar s) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar s) {
                }
            });
            bars[i] = sb;
            box.addView(sb);
        }

        hex.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c2) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c2) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                if (updating[0]) return;
                try {
                    int col = Color.parseColor(e.toString().trim());
                    updating[0] = true;
                    argb[0] = Color.alpha(col);
                    argb[1] = Color.red(col);
                    argb[2] = Color.green(col);
                    argb[3] = Color.blue(col);
                    for (int i = 0; i < 4; i++) bars[i].setProgress(argb[i]);
                    updating[0] = false;
                    setSwatch(prev, col);
                } catch (Exception ignored) {
                    updating[0] = false;
                }
            }
        });

        ScrollView sc = new ScrollView(this);
        sc.addView(box);

        new AlertDialog.Builder(this)
                .setTitle(label)
                .setView(sc)
                .setPositiveButton("Save", (d, w) -> {
                    int col = Color.argb(argb[0], argb[1], argb[2], argb[3]);
                    sp.edit().putInt(key, col).apply();
                    setSwatch(swatch, col);
                    refreshPreview();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- actions ----------
    private void restartSystemUi() {
        Toast.makeText(this, "Restarting SystemUI…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "killall com.android.systemui"});
                int code = p.waitFor();
                if (code != 0) {
                    Runtime.getRuntime().exec(new String[]{"su", "-c", "am crash com.android.systemui"}).waitFor();
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Root not available. Restart SystemUI or reboot manually.", Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private int geti(String key) {
        return sp.getInt(key, Config.def(key));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
