package com.simonegiansante.screentime;

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class RulesTest {
    private static final Set<String> NO_BLACKLIST = Collections.emptySet();

    @Test
    public void inWindow_sameStartEnd_isAlwaysAllowed() {
        assertEquals(true, Rules.inWindow(480, 480, 0));
        assertEquals(true, Rules.inWindow(480, 480, 1200));
    }

    @Test
    public void inWindow_ordinaryWindow() {
        assertEquals(true, Rules.inWindow(480, 1200, 600));   // 08:00-20:00, at 10:00
        assertEquals(false, Rules.inWindow(480, 1200, 1260)); // at 21:00
        assertEquals(false, Rules.inWindow(480, 1200, 300));  // at 05:00
    }

    @Test
    public void inWindow_crossesMidnight() {
        assertEquals(true, Rules.inWindow(1320, 60, 30));    // 22:00-01:00, at 00:30
        assertEquals(false, Rules.inWindow(1320, 60, 600));  // at 10:00
    }

    @Test
    public void blacklist_winsOverEverythingElse() {
        Set<String> blacklist = new HashSet<>();
        blacklist.add("com.example.bad");
        Rules.Settings s = new Rules.Settings();
        Rules.Reason r = Rules.evaluate("com.example.bad", blacklist, false, 600, s, 0, true);
        assertEquals(Rules.Reason.APP_BLOCKED, r);
    }

    @Test
    public void override_liftsScheduleAndQuota_butNotBlacklist() {
        Rules.Settings s = new Rules.Settings();
        s.weekdayStart = 480;
        s.weekdayEnd = 1200;
        s.quotaMinutes = 30;
        Rules.Reason outsideNoOverride = Rules.evaluate("com.example.app", NO_BLACKLIST, false, 1260, s, 0, false);
        assertEquals(Rules.Reason.OUTSIDE_SCHEDULE, outsideNoOverride);
        Rules.Reason outsideWithOverride = Rules.evaluate("com.example.app", NO_BLACKLIST, false, 1260, s, 0, true);
        assertEquals(Rules.Reason.ALLOWED, outsideWithOverride);
    }

    @Test
    public void quota_blocksOnceSecondsReachTheLimit() {
        Rules.Settings s = new Rules.Settings();
        s.quotaMinutes = 30;
        Rules.Reason underQuota = Rules.evaluate("com.example.app", NO_BLACKLIST, false, 600, s, 29 * 60, false);
        assertEquals(Rules.Reason.ALLOWED, underQuota);
        Rules.Reason atQuota = Rules.evaluate("com.example.app", NO_BLACKLIST, false, 600, s, 30 * 60, false);
        assertEquals(Rules.Reason.QUOTA_EXCEEDED, atQuota);
    }

    @Test
    public void zeroQuota_meansNoLimit() {
        Rules.Settings s = new Rules.Settings();
        s.quotaMinutes = 0;
        Rules.Reason r = Rules.evaluate("com.example.app", NO_BLACKLIST, false, 600, s, 999_999, false);
        assertEquals(Rules.Reason.ALLOWED, r);
    }

    @Test
    public void weekendUsesItsOwnWindow() {
        Rules.Settings s = new Rules.Settings();
        s.weekdayStart = 480;
        s.weekdayEnd = 600;   // weekday window closed by 10:00
        s.weekendStart = 480;
        s.weekendEnd = 1200;  // weekend window open until 20:00
        Rules.Reason weekday = Rules.evaluate("com.example.app", NO_BLACKLIST, false, 900, s, 0, false);
        assertEquals(Rules.Reason.OUTSIDE_SCHEDULE, weekday);
        Rules.Reason weekend = Rules.evaluate("com.example.app", NO_BLACKLIST, true, 900, s, 0, false);
        assertEquals(Rules.Reason.ALLOWED, weekend);
    }
}
