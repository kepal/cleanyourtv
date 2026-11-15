package com.simonegiansante.screentime;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
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
    /** Clock time (elapsedRealtime) up to which usage is already counted; 0 while no counted app is in front. */
    private long accountedAt = 0L;
    private static final int MAX_GAP_SECONDS = 120;
    /** Set the moment the lock screen is launched, cleared once an allowed app is in front again:
        a blocked app that keeps trying to reclaim focus must not relaunch the lock screen on top
        of itself over and over. */
    private boolean locked = false;
    /** Warnings already shown, so each one appears once rather than on every tick. */
    private final Set<String> warned = new HashSet<>();
    private Banner banner;

    private PowerManager.WakeLock awake;
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                // The TV went to standby: that time is not spent in the app.
                account();
                accountedAt = 0L;
                main.removeCallbacks(tick);
                tickPending = false;
                holdAwake(false);
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())
                    && !foregroundPackage.isEmpty() && !ALWAYS_ALLOWED.contains(foregroundPackage) && !locked) {
                accountedAt = SystemClock.elapsedRealtime();
                holdAwake(true);
                if (!tickPending) {
                    main.postDelayed(tick, TICK_SECONDS * 1000L);
                    tickPending = true;
                }
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        ALWAYS_ALLOWED.add(getPackageName());
        banner = new Banner(this);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        awake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "screentime:guard");
        awake.setReferenceCounted(false);
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screen, f);
    }

    /** While a counted app is in front the CPU is kept awake, so the tick keeps firing even when the
        picture is static (an app's home screen): a timer on a sleeping CPU stops and time goes uncounted.
        The timeout makes sure it can never be left held. */
    private void holdAwake(boolean on) {
        if (awake == null) return;
        try {
            if (on) awake.acquire(2 * 60 * 1000L);
            else if (awake.isHeld()) awake.release();
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;
        // Volume bar, panels and notifications draw over the app without replacing it, and raise no
        // event when they go away: letting one stand in as "the app in front" would stop the timer.
        if ("com.android.systemui".contentEquals(pkg)) return;
        // Our own warning banner is a window of this app too, but not a screen the person went to:
        // taking it for one would stop the counting for the app underneath. Only this app's own
        // activities (the settings, the lock screen) count as being in front.
        if (getPackageName().contentEquals(pkg)) {
            CharSequence cls = event.getClassName();
            if (cls == null || !cls.toString().startsWith(getPackageName())) return;
        }
        // The time up to this event belongs to the app that was in front until now.
        account();
        String previous = foregroundPackage;
        foregroundPackage = pkg.toString();
        if (!previous.equals(foregroundPackage)) Prefs.setLastEvent(this, foregroundPackage);
        if (ALWAYS_ALLOWED.contains(foregroundPackage)) {
            accountedAt = 0L;
            main.removeCallbacks(tick);
            tickPending = false;
            holdAwake(false);
            check(foregroundPackage);
            return;
        }
        if (accountedAt == 0L) accountedAt = SystemClock.elapsedRealtime();
        holdAwake(true);
        check(foregroundPackage);
        // The tick runs by itself and is never restarted by an event: an app that keeps raising window
        // events (or swapping between its own packages) would otherwise keep it from ever firing.
        if (!tickPending && !locked) {
            main.postDelayed(tick, TICK_SECONDS * 1000L);
            tickPending = true;
        }
    }

    /** Adds the real time elapsed since the last accounting to today's totals and to the app in front.
        Measured on the clock, not counted in ticks, so how often the tick fires does not change it. */
    private void account() {
        long now = SystemClock.elapsedRealtime();
        if (accountedAt == 0L || ALWAYS_ALLOWED.contains(foregroundPackage) || foregroundPackage.isEmpty()) {
            return;
        }
        long seconds = (now - accountedAt) / 1000L;
        if (seconds <= 0) return;
        accountedAt += seconds * 1000L;
        // A long gap means the TV slept: that was not time spent in the app.
        int counted = (int) Math.min(seconds, MAX_GAP_SECONDS);
        Prefs.addUsedSeconds(this, counted);
        Prefs.addAppUsedSeconds(this, foregroundPackage, counted);
    }

    private void onTick() {
        tickPending = false;
        account();
        Prefs.setLastTick(this, foregroundPackage);
        check(foregroundPackage);
        if (!locked) warnIfClose(foregroundPackage);
        if (!locked && !ALWAYS_ALLOWED.contains(foregroundPackage)) {
            holdAwake(true);
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
        accountedAt = 0L;
        holdAwake(false);
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
        holdAwake(false);
        try {
            unregisterReceiver(screen);
        } catch (RuntimeException ignored) {
        }
        if (banner != null) banner.hide();
        super.onDestroy();
    }
}
