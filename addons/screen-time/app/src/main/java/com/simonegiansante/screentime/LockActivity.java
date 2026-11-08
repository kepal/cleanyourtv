package com.simonegiansante.screentime;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Full screen, shown over Home whenever GuardService sends an app back. Always explains why and always
 * offers the PIN: a short unlock (15 minutes or 1 hour) or until the next natural reset at midnight. What
 * the PIN actually lifts follows what is blocking right now - the schedule and the quota together
 * (scheduleBlocked or quotaBlocked), the one package that triggered a blacklist hit (appBlocked), or both
 * at once, never more.
 */
public class LockActivity extends Activity {
    static final String EXTRA_PACKAGE = "package";
    static final String EXTRA_APP_BLOCKED = "app_blocked";
    static final String EXTRA_SCHEDULE_BLOCKED = "schedule_blocked";
    static final String EXTRA_QUOTA_BLOCKED = "quota_blocked";

    private boolean appBlocked, scheduleBlocked, quotaBlocked;
    private String blockedPackage;
    private TextView error;
    private PinPad pad;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        readExtras(getIntent());
        showLock();
    }

    /** singleTask delivers a repeat launch here instead of a fresh onCreate; redraw with whatever
        triggered it this time rather than leaving the first screen up unanswered. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readExtras(intent);
        showLock();
    }

    private void readExtras(Intent intent) {
        blockedPackage = intent.getStringExtra(EXTRA_PACKAGE);
        appBlocked = intent.getBooleanExtra(EXTRA_APP_BLOCKED, false);
        scheduleBlocked = intent.getBooleanExtra(EXTRA_SCHEDULE_BLOCKED, false);
        quotaBlocked = intent.getBooleanExtra(EXTRA_QUOTA_BLOCKED, false);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (pad != null && pad.handleKeyEvent(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    private void showLock() {
        pad = null;
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setBackgroundColor(0xFF0E1420);
        page.setPadding(0, 0, 0, Math.round(24 * dp));

        TextView title = new TextView(this);
        title.setTextSize(26);
        title.setTextColor(0xFFFFFFFF);
        title.setGravity(Gravity.CENTER);
        title.setText(titleFor());
        page.addView(title, matchWidthWrap());

        TextView detail = new TextView(this);
        detail.setTextSize(15);
        detail.setTextColor(0xFF8B949E);
        detail.setGravity(Gravity.CENTER);
        detail.setPadding(Math.round(32 * dp), Math.round(8 * dp), Math.round(32 * dp), Math.round(24 * dp));
        detail.setText(detailFor());
        page.addView(detail, matchWidthWrap());

        error = new TextView(this);
        error.setTextSize(14);
        error.setTextColor(0xFFE05555);
        error.setGravity(Gravity.CENTER);
        error.setPadding(0, 0, 0, Math.round(8 * dp));
        page.addView(error, matchWidthWrap());

        pad = new PinPad(this, this::onPinEntered);
        page.addView(pad, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(page);
    }

    private String titleFor() {
        List<Integer> parts = new ArrayList<>();
        if (appBlocked) parts.add(R.string.lock_title_app_blocked);
        if (scheduleBlocked) parts.add(R.string.lock_title_schedule);
        if (quotaBlocked) parts.add(R.string.lock_title_quota);
        if (parts.size() == 1) return getString(parts.get(0));
        return getString(R.string.lock_title_multiple);
    }

    private String detailFor() {
        List<String> lines = new ArrayList<>();
        if (appBlocked) lines.add(getString(R.string.lock_detail_app_blocked));
        if (scheduleBlocked) {
            boolean h24 = DateFormat.is24HourFormat(this);
            Rules.Settings s = Prefs.loadSettings(this);
            List<Rules.Window> windows = Prefs.isWeekendToday() ? s.weekendWindows : s.weekdayWindows;
            lines.add(getString(R.string.lock_detail_schedule, Rules.describeWindows(windows, h24)));
        }
        if (quotaBlocked) {
            Rules.Settings s = Prefs.loadSettings(this);
            lines.add(getString(R.string.lock_detail_quota, s.quotaMinutes));
        }
        lines.add(getString(R.string.lock_enter_pin_hint));
        return String.join("\n", lines);
    }

    private void onPinEntered(String pin) {
        if (Prefs.checkPin(this, pin)) {
            showDurationPicker();
        } else {
            error.setText(R.string.lock_wrong_pin);
            pad.reset();
        }
    }

    private void showDurationPicker() {
        pad = null;
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setBackgroundColor(0xFF0E1420);

        TextView title = new TextView(this);
        title.setTextSize(22);
        title.setTextColor(0xFFFFFFFF);
        title.setGravity(Gravity.CENTER);
        title.setText(R.string.lock_duration_title);
        page.addView(title, matchWidthWrap());

        page.addView(durationButton(R.string.lock_duration_15min, 15 * 60_000L), buttonParams(dp));
        page.addView(durationButton(R.string.lock_duration_1hour, 60 * 60_000L), buttonParams(dp));
        page.addView(durationButton(R.string.lock_duration_until_reset, -1), buttonParams(dp));

        setContentView(page);
        page.getChildAt(1).requestFocus();
    }

    private LinearLayout.LayoutParams buttonParams(float dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(12 * dp);
        return lp;
    }

    /** durationMillis -1 means "until the next natural reset", at the end of today. */
    private Button durationButton(int labelRes, long durationMillis) {
        Button b = new Button(this);
        b.setText(labelRes);
        b.setOnClickListener(v -> unlock(durationMillis));
        return b;
    }

    private void unlock(long durationMillis) {
        long until = durationMillis < 0 ? Prefs.endOfTodayMillis() : System.currentTimeMillis() + durationMillis;
        if (scheduleBlocked || quotaBlocked) {
            Prefs.setScheduleOverrideUntil(this, until);
        }
        if (appBlocked && blockedPackage != null) {
            Prefs.setAppOverrideUntil(this, blockedPackage, until);
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    private LinearLayout.LayoutParams matchWidthWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
