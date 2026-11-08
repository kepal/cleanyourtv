package com.simonegiansante.screentime;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The tile and module card open this. A PIN gate comes first every time (set one the first time there is
 * none); past it, one list of rows worked with the remote, same shape as the other add-ons here. Left and
 * Right change a value, OK opens a sub-screen.
 */
public class MainActivity extends Activity {
    private Rules.Settings s;
    private String pendingPin;
    private Row weekdayStartRow, weekdayEndRow, weekendStartRow, weekendEndRow, quotaRow, blacklistRow, pinRow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showGate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (s != null) refreshSettings();
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
        LinearLayout.LayoutParams padParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        padParams.topMargin = Math.round(16 * dp);
        page.addView(padHolder[0], padParams);

        setContentView(page);
    }

    // --- settings ---

    private void showSettings() {
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
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        TextView hint = new TextView(this);
        hint.setText(R.string.settings_hint);
        hint.setTextSize(13);
        hint.setTextColor(0xFF8B949E);
        hint.setPadding(Math.round(16 * dp), Math.round(12 * dp), 0, 0);
        page.addView(hint);

        weekdayStartRow = add(rows, new Row(this, getString(R.string.row_weekday_start), null, null, d -> {
            s.weekdayStart += d * Rules.TIME_STEP;
            changed();
        }));
        weekdayEndRow = add(rows, new Row(this, getString(R.string.row_weekday_end), null, null, d -> {
            s.weekdayEnd += d * Rules.TIME_STEP;
            changed();
        }));
        weekendStartRow = add(rows, new Row(this, getString(R.string.row_weekend_start), null, null, d -> {
            s.weekendStart += d * Rules.TIME_STEP;
            changed();
        }));
        weekendEndRow = add(rows, new Row(this, getString(R.string.row_weekend_end), null, null, d -> {
            s.weekendEnd += d * Rules.TIME_STEP;
            changed();
        }));
        quotaRow = add(rows, new Row(this, getString(R.string.row_quota), getString(R.string.row_quota_detail), null, d -> {
            s.quotaMinutes += d * Rules.QUOTA_STEP;
            changed();
        }));
        blacklistRow = add(rows, new Row(this, getString(R.string.row_blacklist), getString(R.string.row_blacklist_detail),
                () -> startActivity(new Intent(this, BlacklistActivity.class)), null));
        pinRow = add(rows, new Row(this, getString(R.string.row_change_pin), null, () -> {
            showPinScreen(R.string.gate_create_pin, null, this::onCreateFirstDigitsEntered);
        }, null));

        setContentView(page);
        weekdayStartRow.requestFocus();
        refreshSettings();
    }

    private Row add(LinearLayout rows, Row r) {
        rows.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    private void changed() {
        Prefs.saveSettings(this, s);
        refreshSettings();
    }

    private void refreshSettings() {
        boolean h24 = DateFormat.is24HourFormat(this);
        weekdayStartRow.setValue(Rules.clock(s.weekdayStart, h24));
        weekdayEndRow.setValue(Rules.clock(s.weekdayEnd, h24));
        weekendStartRow.setValue(Rules.clock(s.weekendStart, h24));
        weekendEndRow.setValue(Rules.clock(s.weekendEnd, h24));
        quotaRow.setValue(s.quotaMinutes == 0 ? getString(R.string.quota_unlimited) : s.quotaMinutes + " min");
        int blocked = Prefs.loadBlacklist(this).size();
        blacklistRow.setValue(String.valueOf(blocked));
        boolean guardOn = isGuardServiceEnabled();
        pinRow.setDetail(guardOn ? null : getString(R.string.guard_disabled_warning));
    }

    private boolean isGuardServiceEnabled() {
        String enabled = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        ComponentName mine = new ComponentName(this, GuardService.class);
        return enabled != null && enabled.contains(mine.flattenToString());
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWidthWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
