package com.simonegiansante.screentime;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * The tile and module card open this. A PIN gate comes first every time (set one the first time there is
 * none); past it, one list of rows worked with the remote, same shape as the other add-ons here. Left and
 * Right change a value, OK opens a sub-screen or, for a window's own rows, adds or removes it - the row
 * count changes with the schedule, so the whole list is rebuilt after every edit rather than kept in
 * fields.
 */
public class MainActivity extends Activity {
    private Rules.Settings s;
    private String pendingPin;
    private PinPad currentPad;
    private LinearLayout rows;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showGate();
    }

    /** Lets the remote's own number keys type a PIN, not only moving focus to the grid. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (currentPad != null && currentPad.handleKeyEvent(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (s != null) {
            rebuildRows();
            if (rows.getChildCount() > 0) rows.getChildAt(0).requestFocus();
        }
    }

    // --- PIN gate ---

    private void showGate() {
        pendingPin = null;
        if (Prefs.hasPin(this)) {
            showPinScreen(R.string.gate_enter_pin, null, this::onGatePinEntered);
        } else {
            showPinScreen(R.string.gate_create_pin, null, this::onCreateFirstDigitsEntered);
        }
    }

    private void onGatePinEntered(String pin, TextView error, PinPad pad) {
        if (Prefs.checkPin(this, pin)) {
            showSettings();
        } else {
            error.setText(R.string.lock_wrong_pin);
            pad.reset();
        }
    }

    private void onCreateFirstDigitsEntered(String pin, TextView error, PinPad pad) {
        pendingPin = pin;
        showPinScreen(R.string.gate_confirm_pin, null, this::onCreateConfirmEntered);
    }

    private void onCreateConfirmEntered(String pin, TextView error, PinPad pad) {
        if (pin.equals(pendingPin)) {
            Prefs.setPin(this, pin);
            pendingPin = null;
            showSettings();
        } else {
            pendingPin = null;
            showPinScreen(R.string.gate_create_pin, getString(R.string.gate_pins_differ), this::onCreateFirstDigitsEntered);
        }
    }

    private interface PinStep {
        void onEntered(String pin, TextView error, PinPad pad);
    }

    private void showPinScreen(int titleRes, String detail, PinStep step) {
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setBackgroundColor(0xFF0E1420);

        TextView title = new TextView(this);
        title.setTextSize(24);
        title.setTextColor(0xFFFFFFFF);
        title.setGravity(Gravity.CENTER);
        title.setText(titleRes);
        page.addView(title, wrap());

        TextView error = new TextView(this);
        error.setTextSize(14);
        error.setTextColor(0xFFE05555);
        error.setGravity(Gravity.CENTER);
        error.setPadding(0, Math.round(8 * dp), 0, Math.round(8 * dp));
        if (detail != null) error.setText(detail);
        page.addView(error, matchWidthWrap());

        PinPad[] padHolder = new PinPad[1];
        padHolder[0] = new PinPad(this, pin -> step.onEntered(pin, error, padHolder[0]));
        currentPad = padHolder[0];
        LinearLayout.LayoutParams padParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        padParams.topMargin = Math.round(16 * dp);
        page.addView(padHolder[0], padParams);

        setContentView(page);
    }

    // --- settings ---

    private void showSettings() {
        currentPad = null;
        s = Prefs.loadSettings(this);
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFF0D1117);
        page.setPadding(Math.round(48 * dp), Math.round(32 * dp), Math.round(48 * dp), Math.round(24 * dp));
        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(28);
        title.setTextColor(0xFFFFFFFF);
        page.addView(title);

        ScrollView scroll = new ScrollView(this);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        TextView hint = new TextView(this);
        hint.setText(R.string.settings_hint);
        hint.setTextSize(13);
        hint.setTextColor(0xFF8B949E);
        hint.setPadding(Math.round(16 * dp), Math.round(12 * dp), 0, 0);
        page.addView(hint);

        setContentView(page);
        rebuildRows();
        if (rows.getChildCount() > 0) rows.getChildAt(0).requestFocus();
    }

    private void saveAndRebuild() {
        int focusIndex = focusedRowIndex();
        Prefs.saveSettings(this, s);
        rebuildRows();
        int restoreTo = Rules.clamp(focusIndex, 0, rows.getChildCount() - 1);
        if (restoreTo >= 0) rows.getChildAt(restoreTo).requestFocus();
    }

    private int focusedRowIndex() {
        for (int i = 0; i < rows.getChildCount(); i++) {
            if (rows.getChildAt(i).isFocused()) return i;
        }
        return 0;
    }

    private void rebuildRows() {
        rows.removeAllViews();
        addSection(getString(R.string.section_weekday), s.weekdayWindows);
        addSection(getString(R.string.section_weekend), s.weekendWindows);

        Row quotaRow = add(new Row(this, getString(R.string.row_quota), getString(R.string.row_quota_detail), null, d -> {
            s.quotaMinutes += d * Rules.QUOTA_STEP;
            saveAndRebuild();
        }));
        quotaRow.setValue(s.quotaMinutes == 0 ? getString(R.string.quota_unlimited) : s.quotaMinutes + " min");

        Row blacklistRow = add(new Row(this, getString(R.string.row_blacklist), getString(R.string.row_blacklist_detail),
                () -> startActivity(new Intent(this, BlacklistActivity.class)), null));
        blacklistRow.setValue(String.valueOf(Prefs.loadBlacklist(this).size()));

        Row pinRow = add(new Row(this, getString(R.string.row_change_pin), null,
                () -> showPinScreen(R.string.gate_create_pin, null, this::onCreateFirstDigitsEntered), null));
        pinRow.setDetail(isGuardServiceEnabled() ? null : getString(R.string.guard_disabled_warning));
    }

    private void addSection(String label, List<Rules.Window> windows) {
        boolean h24 = DateFormat.is24HourFormat(this);
        TextView header = new TextView(this);
        header.setText(label);
        header.setTextSize(14);
        header.setTextColor(0xFF8B949E);
        float dp = getResources().getDisplayMetrics().density;
        header.setPadding(Math.round(16 * dp), Math.round(12 * dp), 0, Math.round(4 * dp));
        rows.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < windows.size(); i++) {
            Rules.Window w = windows.get(i);
            int position = i;
            Row startRow = add(new Row(this, getString(R.string.row_window_start, position + 1), null, null, d -> {
                w.start += d * Rules.TIME_STEP;
                saveAndRebuild();
            }));
            startRow.setValue(Rules.clock(w.start, h24));

            Row endRow = add(new Row(this, getString(R.string.row_window_end, position + 1), null, null, d -> {
                w.end += d * Rules.TIME_STEP;
                saveAndRebuild();
            }));
            endRow.setValue(Rules.clock(w.end, h24));

            add(new Row(this, getString(R.string.row_window_remove, position + 1), null, () -> {
                windows.remove(position);
                saveAndRebuild();
            }, null));
        }

        if (windows.size() < Rules.MAX_WINDOWS) {
            add(new Row(this, getString(R.string.row_window_add), null, () -> {
                int defaultStart = windows.isEmpty() ? 8 * 60 : windows.get(windows.size() - 1).end;
                int defaultEnd = Rules.clamp(defaultStart + 60, 0, Rules.DAY_MINUTES - Rules.TIME_STEP);
                windows.add(new Rules.Window(defaultStart, defaultEnd));
                saveAndRebuild();
            }, null));
        }
    }

    private Row add(Row r) {
        rows.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    private boolean isGuardServiceEnabled() {
        String enabled = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        // The setting is free-form text: some writers use the full class name, others the manifest's
        // own shorthand ("pkg/.GuardService"), so both must be checked.
        ComponentName full = new ComponentName(this, GuardService.class);
        String shorthand = getPackageName() + "/." + GuardService.class.getSimpleName();
        return enabled.contains(full.flattenToString()) || enabled.contains(shorthand);
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWidthWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
