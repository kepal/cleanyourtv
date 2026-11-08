package com.simonegiansante.screentime;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A 4-digit PIN pad built for the remote: a row of dots shows how many digits are in, 0-9 plus a
 * backspace in a D-pad-navigable grid. Submits itself as soon as the 4th digit goes in.
 */
final class PinPad extends LinearLayout {
    static final int LENGTH = 4;

    interface Listener {
        void onPinEntered(String pin);
    }

    private final StringBuilder entered = new StringBuilder();
    private final TextView[] dots = new TextView[LENGTH];
    private final Listener listener;

    PinPad(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        float dp = context.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout dotsRow = new LinearLayout(context);
        dotsRow.setOrientation(HORIZONTAL);
        dotsRow.setGravity(Gravity.CENTER_HORIZONTAL);
        for (int i = 0; i < LENGTH; i++) {
            TextView dot = new TextView(context);
            dot.setText("○");
            dot.setTextSize(28);
            dot.setTextColor(0xFF8B949E);
            LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = lp.rightMargin = Math.round(8 * dp);
            dotsRow.addView(dot, lp);
            dots[i] = dot;
        }
        addView(dotsRow, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        GridLayout grid = new GridLayout(context);
        grid.setColumnCount(3);
        grid.setRowCount(4);
        int keySize = Math.round(64 * dp);
        int margin = Math.round(6 * dp);
        String[] labels = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫"};
        for (String label : labels) {
            if (label.isEmpty()) {
                View spacer = new View(context);
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = keySize;
                lp.height = keySize;
                grid.addView(spacer, lp);
                continue;
            }
            Button key = new Button(context);
            key.setText(label);
            key.setTextSize(22);
            key.setTypeface(Typeface.DEFAULT_BOLD);
            key.setTextColor(Color.WHITE);
            key.setBackgroundResource(R.drawable.key_background);
            key.setAllCaps(false);
            if (label.equals("⌫")) {
                key.setOnClickListener(v -> backspace());
            } else {
                key.setOnClickListener(v -> digit(label));
            }
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = keySize;
            lp.height = keySize;
            lp.setMargins(margin, margin, margin, margin);
            grid.addView(key, lp);
        }
        LayoutParams gridParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gridParams.topMargin = Math.round(20 * dp);
        addView(grid, gridParams);
    }

    private void digit(String d) {
        if (entered.length() >= LENGTH) return;
        entered.append(d);
        refresh();
        if (entered.length() == LENGTH) {
            String pin = entered.toString();
            listener.onPinEntered(pin);
        }
    }

    private void backspace() {
        if (entered.length() == 0) return;
        entered.deleteCharAt(entered.length() - 1);
        refresh();
    }

    /** Clears the dots after a wrong PIN, so the next attempt starts fresh. */
    void reset() {
        entered.setLength(0);
        refresh();
    }

    /**
     * Lets a digit key (0-9) or delete on the remote itself work, not only moving focus to the grid and
     * pressing OK. The hosting Activity forwards key-down events here from its own dispatchKeyEvent.
     * True means it was a PIN key and was used; the caller sends anything else on as usual.
     */
    boolean handleKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        int code = event.getKeyCode();
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
            digit(String.valueOf(code - KeyEvent.KEYCODE_0));
            return true;
        }
        if (code == KeyEvent.KEYCODE_DEL || code == KeyEvent.KEYCODE_FORWARD_DEL) {
            backspace();
            return true;
        }
        return false;
    }

    private void refresh() {
        for (int i = 0; i < LENGTH; i++) {
            boolean filled = i < entered.length();
            dots[i].setText(filled ? "●" : "○");
            dots[i].setTextColor(filled ? 0xFF3D7EFF : 0xFF8B949E);
        }
    }
}
