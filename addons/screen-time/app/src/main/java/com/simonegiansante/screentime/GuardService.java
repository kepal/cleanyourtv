package com.simonegiansante.screentime;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Watches which app is in front and enforces the schedule, the daily quota and the blacklist. A blocked
 * app is sent back to Home the moment it appears; the schedule and the quota are also re-checked every
 * TICK_SECONDS while an ordinary (non-exempt) app sits in front, so a window that was open before the
 * schedule ended or the quota ran out is caught too, not only a newly opened one.
 */
public class GuardService extends AccessibilityService {
    private static final int TICK_SECONDS = 20;
    /** Never evaluated: this app itself, and the launcher that Home must always be able to reach. */
    private static final Set<String> ALWAYS_ALLOWED = new HashSet<>();

    static {
        ALWAYS_ALLOWED.add("com.gathin.tvhome");
        ALWAYS_ALLOWED.add("com.android.systemui");
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private String foregroundPackage = "";
    private final Runnable tick = this::onTick;
    /** Set the moment the lock screen is launched, cleared once an allowed app is in front again:
        a blocked app that keeps trying to reclaim focus must not relaunch the lock screen on top
        of itself over and over. */
    private boolean locked = false;

    @Override
    protected void onServiceConnected() {
        ALWAYS_ALLOWED.add(getPackageName());
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;
        // Volume bar, panels and notifications draw over the app without replacing it, and raise no
        // event when they go away: letting one stand in as "the app in front" would stop the timer.
        if ("com.android.systemui".contentEquals(pkg)) return;
        foregroundPackage = pkg.toString();
        main.removeCallbacks(tick);
        check(foregroundPackage);
        if (!ALWAYS_ALLOWED.contains(foregroundPackage)) {
            main.postDelayed(tick, TICK_SECONDS * 1000L);
        }
    }

    private void onTick() {
        Prefs.addUsedSeconds(this, TICK_SECONDS);
        check(foregroundPackage);
        if (!ALWAYS_ALLOWED.contains(foregroundPackage)) {
            main.postDelayed(tick, TICK_SECONDS * 1000L);
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
                Prefs.appOverrideUntil(this, pkg) > now,
                Prefs.scheduleOverrideUntil(this) > now);
        if (!decision.blocked()) {
            locked = false;
            return;
        }
        if (locked) return;
        locked = true;

        main.removeCallbacks(tick);
        performGlobalAction(GLOBAL_ACTION_HOME);
        // GLOBAL_ACTION_HOME's own transition and this startActivity race on some firmware (seen on
        // this Sony/MediaTek board): Home's own window settling afterwards can cover the lock screen
        // that was already on top. A short delay lets Home finish first.
        main.postDelayed(() -> {
            Intent lock = new Intent(this, LockActivity.class);
            lock.putExtra(LockActivity.EXTRA_PACKAGE, pkg);
            lock.putExtra(LockActivity.EXTRA_APP_BLOCKED, decision.appBlocked);
            lock.putExtra(LockActivity.EXTRA_SCHEDULE_BLOCKED, decision.scheduleBlocked);
            lock.putExtra(LockActivity.EXTRA_QUOTA_BLOCKED, decision.quotaBlocked);
            lock.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(lock);
        }, 150);
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        main.removeCallbacks(tick);
        super.onDestroy();
    }
}
