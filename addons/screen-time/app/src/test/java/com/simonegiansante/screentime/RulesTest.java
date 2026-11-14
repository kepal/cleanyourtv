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
    public void inWindow_sameStartEnd_isWholeDay() {
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
    public void noWindows_meansNoScheduleRestriction() {
        Rules.Settings s = new Rules.Settings(); // both window lists start empty
        Rules.Decision d = Rules.evaluate("com.example.app", NO_BLACKLIST, false, 0, s, 0, false, false);
        assertFalse(d.blocked());
    }

    @Test
    public void twoWindows_allowedInEither_blockedBetween() {
        Rules.Settings s = new Rules.Settings();
        s.weekdayWindows.add(new Rules.Window(8 * 60, 9 * 60));
        s.weekdayWindows.add(new Rules.Window(16 * 60, 20 * 60));
        assertFalse(Rules.evaluate("com.example.app", NO_BLACKLIST, false, 8 * 60 + 30, s, 0, false, false).scheduleBlocked);
        assertFalse(Rules.evaluate("com.example.app", NO_BLACKLIST, false, 18 * 60, s, 0, false, false).scheduleBlocked);
        assertTrue(Rules.evaluate("com.example.app", NO_BLACKLIST, false, 12 * 60, s, 0, false, false).scheduleBlocked);
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
        s.weekdayWindows.add(new Rules.Window(480, 600)); // closed by 10:00
        Rules.Decision d = Rules.evaluate("com.example.bad", blacklist, false, 900, s, 0, true, false);
        assertFalse(d.appBlocked);
        assertTrue(d.scheduleBlocked);
    }

    @Test
    public void scheduleOverride_liftsScheduleAndQuota_notTheBlacklist() {
        Set<String> blacklist = new HashSet<>();
        blacklist.add("com.example.bad");
        Rules.Settings s = new Rules.Settings();
        s.weekdayWindows.add(new Rules.Window(480, 600));
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
    public void weekendUsesItsOwnWindows() {
        Rules.Settings s = new Rules.Settings();
        s.weekdayWindows.add(new Rules.Window(480, 600));   // weekday window closed by 10:00
        s.weekendWindows.add(new Rules.Window(480, 1200));  // weekend window open until 20:00
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
        s.weekdayWindows.add(new Rules.Window(480, 600));
        Rules.Decision d = Rules.evaluate("com.example.bad", blacklist, false, 900, s, 0, false, false);
        assertEquals(true, d.appBlocked);
        assertEquals(true, d.scheduleBlocked);
    }

    @Test
    public void describeWindows_joinsEachRangeWithAComma() {
        java.util.List<Rules.Window> windows = new java.util.ArrayList<>();
        windows.add(new Rules.Window(8 * 60, 9 * 60));
        windows.add(new Rules.Window(16 * 60, 20 * 60));
        assertEquals("08:00-09:00, 16:00-20:00", Rules.describeWindows(windows, true));
    }

    @Test
    public void appLimit_blocksOnlyThatAppOnceItsOwnTimeIsUsed() {
        Rules.Settings s = new Rules.Settings();
        assertFalse(Rules.evaluate("com.example.app", NO_BLACKLIST, false, 600, s, 0, 30, 29 * 60, false, false).blocked());
        Rules.Decision d = Rules.evaluate("com.example.app", NO_BLACKLIST, false, 600, s, 0, 30, 30 * 60, false, false);
        assertTrue(d.appLimitBlocked);
        assertFalse(d.appBlocked);
        assertFalse(d.scheduleBlocked);
    }

    @Test
    public void appLimit_zeroMeansNone_andOverrideLiftsIt() {
        Rules.Settings s = new Rules.Settings();
        assertFalse(Rules.evaluate("com.example.app", NO_BLACKLIST, false, 600, s, 0, 0, 999_999, false, false).blocked());
        assertFalse(Rules.evaluate("com.example.app", NO_BLACKLIST, false, 600, s, 0, 30, 999_999, true, false).blocked());
    }

    @Test
    public void appLimit_isIndependentOfTheScheduleOverride() {
        Rules.Settings s = new Rules.Settings();
        Rules.Decision d = Rules.evaluate("com.example.app", NO_BLACKLIST, false, 600, s, 0, 30, 31 * 60, false, true);
        assertTrue(d.appLimitBlocked);
    }

    @Test
    public void minutesUntilScheduleBlock_countsToTheEndOfTheWindow() {
        java.util.List<Rules.Window> w = new java.util.ArrayList<>();
        w.add(new Rules.Window(9 * 60, 12 * 60));
        assertEquals(5, Rules.minutesUntilScheduleBlock(w, 11 * 60 + 55, 5));
        assertEquals(-1, Rules.minutesUntilScheduleBlock(w, 11 * 60 + 40, 5));
        assertEquals(-1, Rules.minutesUntilScheduleBlock(w, 13 * 60, 5));
    }

    @Test
    public void minutesUntilScheduleBlock_adjacentWindowsAreNotAnEnd() {
        java.util.List<Rules.Window> w = new java.util.ArrayList<>();
        w.add(new Rules.Window(8 * 60, 9 * 60));
        w.add(new Rules.Window(9 * 60, 10 * 60));
        assertEquals(-1, Rules.minutesUntilScheduleBlock(w, 8 * 60 + 57, 5));
        assertEquals(3, Rules.minutesUntilScheduleBlock(w, 9 * 60 + 57, 5));
    }

    @Test
    public void minutesUntilScheduleBlock_noWindowsMeansNeverBlocked() {
        assertEquals(-1, Rules.minutesUntilScheduleBlock(new java.util.ArrayList<>(), 600, 5));
    }

    @Test
    public void nextWindowEnd_isTheNextOneStillToStartToday() {
        java.util.List<Rules.Window> today = new java.util.ArrayList<>();
        today.add(new Rules.Window(7 * 60, 7 * 60 + 55));
        today.add(new Rules.Window(9 * 60, 12 * 60));
        assertEquals(4 * 60 + 3, Rules.minutesUntilNextWindowEnd(today, new java.util.ArrayList<>(), 7 * 60 + 57));
    }

    @Test
    public void nextWindowEnd_aWindowPastMidnightEndsTheNextMorning() {
        java.util.List<Rules.Window> today = new java.util.ArrayList<>();
        today.add(new Rules.Window(9 * 60, 2 * 60));
        // at 08:00: 1 h to the start, then 17 h until 02:00 the next day
        assertEquals(18 * 60, Rules.minutesUntilNextWindowEnd(today, new java.util.ArrayList<>(), 8 * 60));
    }

    @Test
    public void nextWindowEnd_usesTomorrowWhenTodayIsOver() {
        java.util.List<Rules.Window> tomorrow = new java.util.ArrayList<>();
        tomorrow.add(new Rules.Window(7 * 60, 8 * 60));
        // at 22:00: 2 h to midnight, 7 h to the start, 1 h long
        assertEquals(10 * 60, Rules.minutesUntilNextWindowEnd(new java.util.ArrayList<>(), tomorrow, 22 * 60));
        assertEquals(-1, Rules.minutesUntilNextWindowEnd(new java.util.ArrayList<>(), new java.util.ArrayList<>(), 22 * 60));
    }
}
