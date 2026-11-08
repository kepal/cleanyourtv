package com.simonegiansante.screentime;

import java.util.Locale;
import java.util.Set;

/**
 * The settings and the decision, with no Android types, so the JVM tests cover all of it: whether a package
 * may be in front right now, given the day's window, today's quota and the blacklist. The launcher
 * (com.gathin.tvhome) and this app's own package are never evaluated at all: see GuardService.ALWAYS_ALLOWED.
 */
final class Rules {
    static final int DAY_MINUTES = 24 * 60;
    static final int TIME_STEP = 15;
    static final int QUOTA_STEP = 15;

    enum Reason { ALLOWED, APP_BLOCKED, OUTSIDE_SCHEDULE, QUOTA_EXCEEDED }

    static final class Settings {
        int weekdayStart = 8 * 60;
        int weekdayEnd = 20 * 60;
        int weekendStart = 9 * 60;
        int weekendEnd = 21 * 60;
        /** Minutes of screen time allowed per day; 0 means no quota. */
        int quotaMinutes = 0;

        Settings clamped() {
            weekdayStart = wrap(round(weekdayStart, TIME_STEP));
            weekdayEnd = wrap(round(weekdayEnd, TIME_STEP));
            weekendStart = wrap(round(weekendStart, TIME_STEP));
            weekendEnd = wrap(round(weekendEnd, TIME_STEP));
            quotaMinutes = clamp(round(quotaMinutes, QUOTA_STEP), 0, DAY_MINUTES);
            return this;
        }
    }

    private Rules() {
    }

    /** True from start up to, not including, end; a window may run past midnight. start == end means no window: always allowed. */
    static boolean inWindow(int start, int end, int minute) {
        if (start == end) return true;
        return start < end ? minute >= start && minute < end : minute >= start || minute < end;
    }

    static Reason evaluate(String foregroundPackage, Set<String> blacklist, boolean isWeekend, int nowMinutes,
                            Settings s, int usedSecondsToday, boolean overrideActive) {
        if (blacklist.contains(foregroundPackage)) return Reason.APP_BLOCKED;
        if (overrideActive) return Reason.ALLOWED;
        int start = isWeekend ? s.weekendStart : s.weekdayStart;
        int end = isWeekend ? s.weekendEnd : s.weekdayEnd;
        if (!inWindow(start, end, nowMinutes)) return Reason.OUTSIDE_SCHEDULE;
        if (s.quotaMinutes > 0 && usedSecondsToday >= s.quotaMinutes * 60) return Reason.QUOTA_EXCEEDED;
        return Reason.ALLOWED;
    }

    static String clock(int minute, boolean h24) {
        int h = minute / 60, m = minute % 60;
        if (h24) return String.format(Locale.ROOT, "%02d:%02d", h, m);
        return String.format(Locale.ROOT, "%d:%02d %s", h % 12 == 0 ? 12 : h % 12, m, h < 12 ? "AM" : "PM");
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static int wrap(int minute) {
        return ((minute % DAY_MINUTES) + DAY_MINUTES) % DAY_MINUTES;
    }

    private static int round(int value, int step) {
        return Math.round(value / (float) step) * step;
    }
}
