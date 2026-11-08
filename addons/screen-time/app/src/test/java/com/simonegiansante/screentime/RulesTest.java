package com.simonegiansante.screentime;

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RulesTest {
    private static final Set<String> NO_BLACKLIST = Collections.emptySet();

    @Test
    public void inWindow_sameStartEnd_isAlwaysAllowed() {
        assertTrue(Rules.inWindow(480, 480, 0));
        assertTrue(Rules.inWindow(480, 480, 1200));
    }

    @Test
    public void inWindow_ordinaryWindow() {
        assertTrue(Rules.inWindow(480, 1200, 600));    // 08:00-20:00, at 10:00
        assertFalse(Rules.inWindow(480, 1200, 1260));  // at 21:00
        assertFalse(Rules.inWindow(480, 1200, 300));   // at 05:00
    }

    @Test
    public void inWindow_crossesMidnight() {
        assertTrue(Rules.inWindow(1320, 60, 30));     // 22:00-01:00, at 00:30
        assertFalse(Rules.inWindow(1320, 60, 600));   // at 10:00
    }

    @Test
    public void blacklist_blocksEvenInsideTheSchedule() {
        Set<String> blacklist = new HashSet<>();
        blacklist.add("com.example.bad");
        Rules.Settings s = new Rules.Settings();
        Rules.Decision d = Rules.evaluate("com.example.bad", blacklist, false, 600, s, 0, false, false);
        assertTrue(d.appBlocked);
        assertFalse(d.scheduleBlocked);
        assertFalse(d.quotaBlocked);
        assertTrue(d.blocked());
    }

    @Test
    public void appOverride_liftsOnlyTheBlacklist_notTheSchedule() {
        Set<String> blacklist = new HashSet<>();
        blacklist.add("com.example.bad");
        Rules.Settings s = new Rules.Settings();
        s.weekdayStart = 480;
        s.weekdayEnd = 600; // closed by 10:00
        Rules.Decision d = Rules.evaluate("com.example.bad", blacklist, false, 900, s, 0, true, false);
        assertFalse(d.appBlocked);
        assertTrue(d.scheduleBlocked);
    }

    @Test
    public void scheduleOverride_liftsScheduleAndQuota_notTheBlacklist() {
        Set<String> blacklist = new HashSet<>();
        blacklist.add("com.example.bad");
        Rules.Settings s = new Rules.Settings();
        s.weekdayStart = 480;
        s.weekdayEnd = 600;
        s.quotaMinutes = 30;
        Rules.Decision d = Rules.evaluate("com.example.bad", blacklist, false, 900, s, 999_999, false, true);
        assertTrue(d.appBlocked);
        assertFalse(d.scheduleBlocked);
        assertFalse(d.quotaBlocked);
    }

    @Test
    public void quota_blocksOnceSecondsReachTheLimit() {
        Rules.Settings s = new Rules.Settings();
        s.quotaMinutes = 30;
        Rules.Decision underQuota = Rules.evaluate("com.example.app", NO_BLACKLIST, false, 600, s, 29 * 60, false, false);
        assertFalse(underQuota.blocked());
        Rules.Decision atQuota = Rules.evaluate("com.example.app", NO_BLACKLIST, false, 600, s, 30 * 60, false, false);
        assertTrue(atQuota.quotaBlocked);
    }

    @Test
    public void zeroQuota_meansNoLimit() {
        Rules.Settings s = new Rules.Settings();
        s.quotaMinutes = 0;
        Rules.Decision d = Rules.evaluate("com.example.app", NO_BLACKLIST, false, 600, s, 999_999, false, false);
        assertFalse(d.blocked());
    }

    @Test
    public void weekendUsesItsOwnWindow() {
        Rules.Settings s = new Rules.Settings();
        s.weekdayStart = 480;
        s.weekdayEnd = 600;   // weekday window closed by 10:00
        s.weekendStart = 480;
        s.weekendEnd = 1200;  // weekend window open until 20:00
        Rules.Decision weekday = Rules.evaluate("com.example.app", NO_BLACKLIST, false, 900, s, 0, false, false);
        assertTrue(weekday.scheduleBlocked);
        Rules.Decision weekend = Rules.evaluate("com.example.app", NO_BLACKLIST, true, 900, s, 0, false, false);
        assertFalse(weekend.blocked());
    }

    @Test
    public void bothRestrictionsCanApplyAtOnce() {
        Set<String> blacklist = new HashSet<>();
        blacklist.add("com.example.bad");
        Rules.Settings s = new Rules.Settings();
        s.weekdayStart = 480;
        s.weekdayEnd = 600;
        Rules.Decision d = Rules.evaluate("com.example.bad", blacklist, false, 900, s, 0, false, false);
        assertEquals(true, d.appBlocked);
        assertEquals(true, d.scheduleBlocked);
    }
}
