package com.simonegiansante.screentime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The settings and the decision, with no Android types, so the JVM tests cover all of it: whether a package
 * may be in front right now, given the day's windows, today's quota and the blacklist. The launcher
 * (com.gathin.tvhome) and this app's own package are never evaluated at all: see GuardService.ALWAYS_ALLOWED.
 * The three restrictions are independent, so a moment can be blocked by more than one at once (an app that
 * is both blacklisted and outside every window): the caller unlocks exactly the ones that applied.
 */
final class Rules {
    static final int DAY_MINUTES = 24 * 60;
    static final int TIME_STEP = 5;
    static final int QUOTA_STEP = 5;
    static final int MAX_WINDOWS = 3;

    /** One allowed time-of-day range. start == end means the whole day (used only as a fresh window's default). */
    static final class Window {
        int start;
        int end;

        Window(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    static final class Settings {
        final List<Window> weekdayWindows = new ArrayList<>();
        final List<Window> weekendWindows = new ArrayList<>();
        /** Minutes of screen time allowed per day; 0 means no quota. */
        int quotaMinutes = 0;

        Settings clamped() {
            for (Window w : weekdayWindows) clampWindow(w);
            for (Window w : weekendWindows) clampWindow(w);
            quotaMinutes = clamp(round(quotaMinutes, QUOTA_STEP), 0, DAY_MINUTES);
            return this;
        }
    }

    /** Which restrictions apply right now; none of the three imply each other. */
    static final class Decision {
        final boolean appBlocked;
        final boolean scheduleBlocked;
        final boolean quotaBlocked;

        Decision(boolean appBlocked, boolean scheduleBlocked, boolean quotaBlocked) {
            this.appBlocked = appBlocked;
            this.scheduleBlocked = scheduleBlocked;
            this.quotaBlocked = quotaBlocked;
        }

        boolean blocked() {
            return appBlocked || scheduleBlocked || quotaBlocked;
        }
    }

    private Rules() {
    }

    /** True from start up to, not including, end; a window may run past midnight. start == end means the whole day. */
    static boolean inWindow(int start, int end, int minute) {
        if (start == end) return true;
        return start < end ? minute >= start && minute < end : minute >= start || minute < end;
    }

    /** No windows at all for that day type means no schedule restriction that day. */
    static boolean allowedByWindows(List<Window> windows, int nowMinutes) {
        if (windows.isEmpty()) return true;
        for (Window w : windows) {
            if (inWindow(w.start, w.end, nowMinutes)) return true;
        }
        return false;
    }

    /**
     * appOverridden: a standing PIN unlock for this exact package lifts the blacklist for it.
     * scheduleOverridden: a standing PIN unlock lifts both the schedule windows and the quota.
     */
    static Decision evaluate(String foregroundPackage, Set<String> blacklist, boolean isWeekend, int nowMinutes,
                              Settings s, int usedSecondsToday, boolean appOverridden, boolean scheduleOverridden) {
        boolean appBlocked = blacklist.contains(foregroundPackage) && !appOverridden;
        boolean scheduleBlocked = false;
        boolean quotaBlocked = false;
        if (!scheduleOverridden) {
            List<Window> windows = isWeekend ? s.weekendWindows : s.weekdayWindows;
            if (!allowedByWindows(windows, nowMinutes)) {
                scheduleBlocked = true;
            } else if (s.quotaMinutes > 0 && usedSecondsToday >= s.quotaMinutes * 60) {
                quotaBlocked = true;
            }
        }
        return new Decision(appBlocked, scheduleBlocked, quotaBlocked);
    }

    static String clock(int minute, boolean h24) {
        int h = minute / 60, m = minute % 60;
        if (h24) return String.format(Locale.ROOT, "%02d:%02d", h, m);
        return String.format(Locale.ROOT, "%d:%02d %s", h % 12 == 0 ? 12 : h % 12, m, h < 12 ? "AM" : "PM");
    }

    /** "08:00-09:00, 16:00-20:00", for the lock screen; empty windows reads as "no restriction". */
    static String describeWindows(List<Window> windows, boolean h24) {
        StringBuilder sb = new StringBuilder();
        for (Window w : windows) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(clock(w.start, h24)).append('-').append(clock(w.end, h24));
        }
        return sb.toString();
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static int wrap(int minute) {
        return ((minute % DAY_MINUTES) + DAY_MINUTES) % DAY_MINUTES;
    }

    private static void clampWindow(Window w) {
        w.start = wrap(round(w.start, TIME_STEP));
        w.end = wrap(round(w.end, TIME_STEP));
    }

    private static int round(int value, int step) {
        return Math.round(value / (float) step) * step;
    }
}
