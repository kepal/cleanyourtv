package com.simonegiansante.screentime;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Watches which app is in front and enforces the schedule, the daily quota and the blacklist. A blocked
 * app is sent back to Home the moment it appears; the schedule and the quota are also re-checked every
 * TICK_SECONDS while an ordinary (non-exempt) app sits in front, so a window that was open before the
 * schedule ended or the quota ran out is caught too, not only a newly opened one.
 */
public class GuardService extends AccessibilityService {
    private static final int TICK_SECONDS = 20;
    private static final int WARN_MINUTES = 5;
    /** Never evaluated: this app itself, and the launcher that Home must always be able to reach. */
    private static final Set<String> ALWAYS_ALLOWED = new HashSet<>();

    static {
        ALWAYS_ALLOWED.add("com.gathin.tvhome");
        ALWAYS_ALLOWED.add("com.android.systemui");
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private String foregroundPackage = "";
    private final Runnable tick = this::onTick;
    private boolean tickPending = false;
    /** Set the moment the lock screen is launched, cleared once an allowed app is in front again:
        a blocked app that keeps trying to reclaim focus must not relaunch the lock screen on top
        of itself over and over. */
    private boolean locked = false;
    /** Warnings already shown, so each one appears once rather than on every tick. */
    private final Set<String> warned = new HashSet<>();
    private Banner banner;

    @Override
    protected void onServiceConnected() {
        ALWAYS_ALLOWED.add(getPackageName());
        banner = new Banner(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;
        // Volume bar, panels and notifications draw over the app without replacing it, and raise no
        // event when they go away: letting one stand in as "the app in front" would stop the timer.
        if ("com.android.systemui".contentEquals(pkg)) return;
        String previous = foregroundPackage;
        foregroundPackage = pkg.toString();
        if (!previous.equals(foregroundPackage)) Prefs.setLastEvent(this, foregroundPackage);
        check(foregroundPackage);
        if (ALWAYS_ALLOWED.contains(foregroundPackage)) {
            main.removeCallbacks(tick);
            tickPending = false;
            return;
        }
        // Same app raising more window events (a player, a dialog, a splash): leave the running timer
        // alone. Restarting it on every event would keep it from ever reaching TICK_SECONDS, and the
        // time used would never be counted.
        if (tickPending && previous.equals(foregroundPackage)) return;
        main.removeCallbacks(tick);
        main.postDelayed(tick, TICK_SECONDS * 1000L);
        tickPending = true;
    }

    private void onTick() {
        tickPending = false;
        Prefs.addUsedSeconds(this, TICK_SECONDS);
        if (!ALWAYS_ALLOWED.contains(foregroundPackage)) Prefs.addAppUsedSeconds(this, foregroundPackage, TICK_SECONDS);
        Prefs.setLastTick(this, foregroundPackage);
        check(foregroundPackage);
        if (!locked) warnIfClose(foregroundPackage);
        if (!ALWAYS_ALLOWED.contains(foregroundPackage)) {
            main.postDelayed(tick, TICK_SECONDS * 1000L);
            tickPending = true;
        }
    }

    private void check(String pkg) {
        if (ALWAYS_ALLOWED.contains(pkg)) {
            locked = false;
            return;
        }
        long now = System.currentTimeMillis();
        Rules.Settings s = Prefs.loadSettings(this);
        Rules.Decision decision = Rules.evaluate(
                pkg,
                Prefs.loadBlacklist(this),
                Prefs.isWeekendToday(),
                Prefs.nowMinutes(),
                s,
                Prefs.usedSecondsToday(this),
                Prefs.appLimitMinutes(this, pkg),
                Prefs.appUsedSecondsToday(this, pkg),
                Prefs.appOverrideUntil(this, pkg) > now,
                Prefs.scheduleOverrideUntil(this) > now);
        if (!decision.blocked()) {
            locked = false;
            return;
        }
        if (locked) return;
        locked = true;

        main.removeCallbacks(tick);
        tickPending = false;
        if (banner != null) banner.hide();
        // No trip through Home first: the lock screen goes straight over the blocked app, which saves
        // Home's own transition. LockActivity sends the person to Home itself when it is dismissed.
        Intent lock = new Intent(this, LockActivity.class);
        lock.putExtra(LockActivity.EXTRA_PACKAGE, pkg);
        lock.putExtra(LockActivity.EXTRA_APP_BLOCKED, decision.appBlocked);
        lock.putExtra(LockActivity.EXTRA_SCHEDULE_BLOCKED, decision.scheduleBlocked);
        lock.putExtra(LockActivity.EXTRA_QUOTA_BLOCKED, decision.quotaBlocked);
        lock.putExtra(LockActivity.EXTRA_APP_LIMIT_BLOCKED, decision.appLimitBlocked);
        lock.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(lock);
    }

    /** A banner over the app, never taking focus, when the schedule, the day's limit or this app's limit
        is within WARN_MINUTES of blocking, so the block does not land in the middle of a video. */
    private void warnIfClose(String pkg) {
        if (ALWAYS_ALLOWED.contains(pkg) || banner == null) return;
        long now = System.currentTimeMillis();
        Rules.Settings s = Prefs.loadSettings(this);
        String day = Prefs.todayKey();
        if (Prefs.scheduleOverrideUntil(this) <= now) {
            List<Rules.Window> windows = Prefs.isWeekendToday() ? s.weekendWindows : s.weekdayWindows;
            int minutes = Rules.minutesUntilScheduleBlock(windows, Prefs.nowMinutes(), WARN_MINUTES);
            if (minutes > 0) warnOnce("schedule:" + day + ":" + (Prefs.nowMinutes() + minutes), R.string.banner_schedule, minutes);
            int quotaLeft = s.quotaMinutes * 60 - Prefs.usedSecondsToday(this);
            if (s.quotaMinutes > 0 && quotaLeft > 0 && quotaLeft <= WARN_MINUTES * 60) {
                warnOnce("quota:" + day, R.string.banner_quota, ceilMinutes(quotaLeft));
            }
        }
        int limit = Prefs.appLimitMinutes(this, pkg);
        if (limit > 0 && Prefs.appOverrideUntil(this, pkg) <= now) {
            int left = limit * 60 - Prefs.appUsedSecondsToday(this, pkg);
            if (left > 0 && left <= WARN_MINUTES * 60) warnOnce("app:" + pkg + ":" + day, R.string.banner_app_limit, ceilMinutes(left));
        }
    }

    private void warnOnce(String key, int messageRes, int minutes) {
        if (warned.add(key)) banner.show(getString(messageRes, minutes));
    }

    private static int ceilMinutes(int seconds) {
        return (seconds + 59) / 60;
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        main.removeCallbacks(tick);
        if (banner != null) banner.hide();
        super.onDestroy();
    }
}
