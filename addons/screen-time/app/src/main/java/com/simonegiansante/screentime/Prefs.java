package com.simonegiansante.screentime;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
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
        s.weekdayStart = p.getInt("weekday_start", s.weekdayStart);
        s.weekdayEnd = p.getInt("weekday_end", s.weekdayEnd);
        s.weekendStart = p.getInt("weekend_start", s.weekendStart);
        s.weekendEnd = p.getInt("weekend_end", s.weekendEnd);
        s.quotaMinutes = p.getInt("quota_minutes", s.quotaMinutes);
        return s.clamped();
    }

    static void saveSettings(Context c, Rules.Settings s) {
        s.clamped();
        of(c).edit()
                .putInt("weekday_start", s.weekdayStart)
                .putInt("weekday_end", s.weekdayEnd)
                .putInt("weekend_start", s.weekendStart)
                .putInt("weekend_end", s.weekendEnd)
                .putInt("quota_minutes", s.quotaMinutes)
                .apply();
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

    /** True once a correct PIN has lifted the schedule/quota block for the rest of today. Never lifts the blacklist. */
    static boolean overrideActiveToday(Context c) {
        return today().equals(of(c).getString("override_day", ""));
    }

    static void setOverrideToday(Context c) {
        of(c).edit().putString("override_day", today()).apply();
    }
}
