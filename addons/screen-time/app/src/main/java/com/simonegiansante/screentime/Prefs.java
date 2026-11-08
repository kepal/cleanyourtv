package com.simonegiansante.screentime;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Everything in the app's own storage. GuardService reads it fresh on every check: nothing to restart. */
final class Prefs {
    private static final String FILE = "screen_time";
    private static final SimpleDateFormat DAY = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);

    private Prefs() {
    }

    static SharedPreferences of(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // --- schedule / quota ---

    static Rules.Settings loadSettings(Context c) {
        SharedPreferences p = of(c);
        Rules.Settings s = new Rules.Settings();
        if (p.contains("weekday_count") || p.contains("weekend_count")) {
            loadWindows(p, "weekday", s.weekdayWindows);
            loadWindows(p, "weekend", s.weekendWindows);
        } else {
            // First run: one sensible default window per day type, same as before multiple windows existed.
            s.weekdayWindows.add(new Rules.Window(8 * 60, 20 * 60));
            s.weekendWindows.add(new Rules.Window(9 * 60, 21 * 60));
        }
        s.quotaMinutes = p.getInt("quota_minutes", s.quotaMinutes);
        return s.clamped();
    }

    private static void loadWindows(SharedPreferences p, String prefix, List<Rules.Window> into) {
        int count = Rules.clamp(p.getInt(prefix + "_count", 0), 0, Rules.MAX_WINDOWS);
        for (int i = 0; i < count; i++) {
            int start = p.getInt(prefix + "_start_" + i, 8 * 60);
            int end = p.getInt(prefix + "_end_" + i, 20 * 60);
            into.add(new Rules.Window(start, end));
        }
    }

    static void saveSettings(Context c, Rules.Settings s) {
        s.clamped();
        SharedPreferences.Editor e = of(c).edit().putInt("quota_minutes", s.quotaMinutes);
        saveWindows(e, "weekday", s.weekdayWindows);
        saveWindows(e, "weekend", s.weekendWindows);
        e.apply();
    }

    private static void saveWindows(SharedPreferences.Editor e, String prefix, List<Rules.Window> windows) {
        e.putInt(prefix + "_count", windows.size());
        for (int i = 0; i < windows.size(); i++) {
            e.putInt(prefix + "_start_" + i, windows.get(i).start);
            e.putInt(prefix + "_end_" + i, windows.get(i).end);
        }
    }

    // --- blacklist ---

    static Set<String> loadBlacklist(Context c) {
        return new HashSet<>(of(c).getStringSet("blacklist", new HashSet<>()));
    }

    static void saveBlacklist(Context c, Set<String> packages) {
        of(c).edit().putStringSet("blacklist", packages).apply();
    }

    // --- PIN ---

    static boolean hasPin(Context c) {
        return of(c).contains("pin_hash");
    }

    static void setPin(Context c, String pin) {
        String salt = PinUtil.randomSaltBase64();
        String hash = PinUtil.hash(pin, salt);
        of(c).edit().putString("pin_salt", salt).putString("pin_hash", hash).apply();
    }

    static boolean checkPin(Context c, String pin) {
        SharedPreferences p = of(c);
        return PinUtil.matches(pin, p.getString("pin_salt", null), p.getString("pin_hash", null));
    }

    // --- today's usage and the day's override ---

    private static String today() {
        return DAY.format(new Date());
    }

    static boolean isWeekendToday() {
        int day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        return day == Calendar.SATURDAY || day == Calendar.SUNDAY;
    }

    static int nowMinutes() {
        Calendar now = Calendar.getInstance();
        return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
    }

    /** Seconds counted today, 0 once the calendar day has changed since the last call. */
    static int usedSecondsToday(Context c) {
        SharedPreferences p = of(c);
        if (!today().equals(p.getString("usage_day", ""))) return 0;
        return p.getInt("usage_seconds", 0);
    }

    static void addUsedSeconds(Context c, int seconds) {
        SharedPreferences p = of(c);
        String day = today();
        int current = day.equals(p.getString("usage_day", "")) ? p.getInt("usage_seconds", 0) : 0;
        p.edit().putString("usage_day", day).putInt("usage_seconds", current + seconds).apply();
    }

    /** A correct PIN lifts the schedule and the quota together, until this moment (epoch millis, 0 = none active). */
    static long scheduleOverrideUntil(Context c) {
        return of(c).getLong("schedule_override_until", 0L);
    }

    static void setScheduleOverrideUntil(Context c, long untilMillis) {
        of(c).edit().putLong("schedule_override_until", untilMillis).apply();
    }

    /** The same, but for one blacklisted package only; never lifts the schedule or the quota. */
    static long appOverrideUntil(Context c, String packageName) {
        return of(c).getLong("app_override_" + packageName, 0L);
    }

    static void setAppOverrideUntil(Context c, String packageName, long untilMillis) {
        of(c).edit().putLong("app_override_" + packageName, untilMillis).apply();
    }

    /** Midnight at the end of the current calendar day, for the "until the next block" choice. */
    static long endOfTodayMillis() {
        Calendar end = Calendar.getInstance();
        end.set(Calendar.HOUR_OF_DAY, 23);
        end.set(Calendar.MINUTE, 59);
        end.set(Calendar.SECOND, 59);
        end.set(Calendar.MILLISECOND, 999);
        return end.getTimeInMillis();
    }
}
