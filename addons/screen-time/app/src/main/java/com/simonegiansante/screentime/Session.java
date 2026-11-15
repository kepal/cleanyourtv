package com.simonegiansante.screentime;

import android.os.SystemClock;

/**
 * Whether the PIN was already entered during this visit to the app. The settings screens call started() and
 * stopped() from onStart and onStop; while at least one of them is on screen the visit goes on, and moving
 * between them (or Android recreating one) never asks for the PIN again. Once all of them are gone, that is
 * the person left the app (Home, another app, standby), the next opening asks again. Kept in memory only,
 * so it never survives the process.
 */
final class Session {
    /** Covers the instant between one screen stopping and the next starting (a recreated screen). */
    private static final long GRACE_MILLIS = 3000L;

    private static int visible = 0;
    private static boolean unlocked = false;
    private static long leftAt = 0L;

    private Session() {
    }

    static void started() {
        visible++;
    }

    static void stopped() {
        if (visible > 0) visible--;
        if (visible == 0) leftAt = SystemClock.elapsedRealtime();
    }

    static void unlock() {
        unlocked = true;
    }

    static boolean isUnlocked() {
        if (!unlocked) return false;
        if (visible > 0) return true;
        if (SystemClock.elapsedRealtime() - leftAt <= GRACE_MILLIS) return true;
        unlocked = false;
        return false;
    }
}
