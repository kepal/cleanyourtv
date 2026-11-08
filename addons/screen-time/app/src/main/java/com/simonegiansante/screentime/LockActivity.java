package com.simonegiansante.screentime;

import android.app.Activity;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Full screen, shown over Home whenever GuardService sends an app back. A blacklisted app has no PIN
 * field here at all: the only way past it is removing the app from the blacklist in Settings, which
 * needs the PIN anyway. Outside the schedule or past today's quota, the PIN lifts both for the rest of
 * the calendar day.
 */
public class LockActivity extends Activity {
    static final String EXTRA_REASON = "reason";

    private TextView error;
    private PinPad pad;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Rules.Reason reason;
        try {
            reason = Rules.Reason.valueOf(getIntent().getStringExtra(EXTRA_REASON));
        } catch (IllegalArgumentException | NullPointerException e) {
            reason = Rules.Reason.OUTSIDE_SCHEDULE;
        }
        setContentView(build(reason));
    }

    private ViewGroup build(Rules.Reason reason) {
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
        title.setText(reason == Rules.Reason.APP_BLOCKED ? R.string.lock_title_app_blocked
                : reason == Rules.Reason.QUOTA_EXCEEDED ? R.string.lock_title_quota
                : R.string.lock_title_schedule);
        page.addView(title, matchWidthWrap());

        TextView detail = new TextView(this);
        detail.setTextSize(15);
        detail.setTextColor(0xFF8B949E);
        detail.setGravity(Gravity.CENTER);
        detail.setPadding(Math.round(32 * dp), Math.round(8 * dp), Math.round(32 * dp), Math.round(24 * dp));
        detail.setText(detailFor(reason));
        page.addView(detail, matchWidthWrap());

        if (reason == Rules.Reason.APP_BLOCKED) {
            Button home = new Button(this);
            home.setText(R.string.lock_back_to_home);
            home.setOnClickListener(v -> finish());
            page.addView(home, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            home.requestFocus();
        } else {
            error = new TextView(this);
            error.setTextSize(14);
            error.setTextColor(0xFFE05555);
            error.setGravity(Gravity.CENTER);
            error.setPadding(0, 0, 0, Math.round(8 * dp));
            page.addView(error, matchWidthWrap());

            pad = new PinPad(this, this::onPinEntered);
            page.addView(pad, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return page;
    }

    private String detailFor(Rules.Reason reason) {
        boolean h24 = DateFormat.is24HourFormat(this);
        Rules.Settings s = Prefs.loadSettings(this);
        int end = Prefs.isWeekendToday() ? s.weekendEnd : s.weekdayEnd;
        int start = Prefs.isWeekendToday() ? s.weekendStart : s.weekdayStart;
        switch (reason) {
            case APP_BLOCKED:
                return getString(R.string.lock_detail_app_blocked);
            case QUOTA_EXCEEDED:
                return getString(R.string.lock_detail_quota, s.quotaMinutes);
            default:
                return getString(R.string.lock_detail_schedule, Rules.clock(start, h24), Rules.clock(end, h24));
        }
    }

    private void onPinEntered(String pin) {
        if (Prefs.checkPin(this, pin)) {
            Prefs.setOverrideToday(this);
            finish();
        } else {
            error.setText(R.string.lock_wrong_pin);
            pad.reset();
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    private LinearLayout.LayoutParams matchWidthWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
