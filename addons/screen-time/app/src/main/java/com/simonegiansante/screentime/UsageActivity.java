package com.simonegiansante.screentime;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Today's time per app, as the guard counted it, with each app's own limit beside it. Also the last window
 * event and the last count the service made, so a limit that never fires can be told from a service that
 * never ran or one that saw a different package name than the one the limit was set on.
 */
public class UsageActivity extends Activity {
    @Override
    protected void onStart() {
        super.onStart();
        Session.started();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Session.stopped();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFF0D1117);
        page.setPadding(Math.round(48 * dp), Math.round(32 * dp), Math.round(48 * dp), Math.round(24 * dp));

        Map<String, Integer> usage = Prefs.appUsageToday(this);
        Map<String, Integer> limits = Prefs.appLimits(this);

        TextView title = new TextView(this);
        title.setText(R.string.row_usage);
        title.setTextSize(24);
        title.setTextColor(0xFFFFFFFF);
        page.addView(title);

        TextView total = new TextView(this);
        total.setText(getString(R.string.usage_total, format(Prefs.usedSecondsToday(this))));
        total.setTextSize(16);
        total.setTextColor(0xFF3D7EFF);
        total.setPadding(0, Math.round(4 * dp), 0, 0);
        page.addView(total);

        TextView seen = new TextView(this);
        seen.setText(seenText());
        seen.setTextSize(12);
        seen.setTextColor(0xFF8B949E);
        seen.setPadding(0, Math.round(4 * dp), 0, Math.round(12 * dp));
        page.addView(seen);

        ScrollView scroll = new ScrollView(this);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Set<String> packages = new HashSet<>(usage.keySet());
        packages.addAll(limits.keySet());
        List<String> ordered = new ArrayList<>(packages);
        Collections.sort(ordered, (a, b) -> {
            int byUsed = Integer.compare(used(usage, b), used(usage, a));
            return byUsed != 0 ? byUsed : a.compareTo(b);
        });

        if (ordered.isEmpty()) {
            TextView none = new TextView(this);
            none.setText(R.string.usage_none);
            none.setTextSize(16);
            none.setTextColor(0xFFC9D1D9);
            rows.addView(none);
        }
        boolean first = true;
        for (String pkg : ordered) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int pv = Math.round(10 * dp), ph = Math.round(12 * dp);
            row.setPadding(ph, pv, ph, pv);
            row.setBackgroundResource(R.drawable.row_background);
            row.setFocusable(true);

            LinearLayout names = new LinearLayout(this);
            names.setOrientation(LinearLayout.VERTICAL);
            TextView label = new TextView(this);
            label.setText(labelOf(pkg));
            label.setTextSize(16);
            label.setTextColor(0xFFFFFFFF);
            names.addView(label);
            TextView id = new TextView(this);
            id.setText(pkg);
            id.setTextSize(11);
            id.setTextColor(0xFF8B949E);
            names.addView(id);
            row.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView time = new TextView(this);
            time.setText(format(used(usage, pkg)));
            time.setTextSize(16);
            time.setTextColor(0xFFFFFFFF);
            time.setGravity(Gravity.END);
            time.setMinWidth(Math.round(120 * dp));
            row.addView(time);

            TextView limit = new TextView(this);
            Integer l = limits.get(pkg);
            limit.setText(l == null ? getString(R.string.app_limit_none) : getString(R.string.app_limit_minutes, l));
            limit.setTextSize(14);
            limit.setTextColor(0xFFC9D1D9);
            limit.setGravity(Gravity.END);
            limit.setMinWidth(Math.round(120 * dp));
            row.addView(limit);

            rows.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (first) {
                row.requestFocus();
                first = false;
            }
        }
        setContentView(page);
    }

    private static int used(Map<String, Integer> usage, String pkg) {
        Integer v = usage.get(pkg);
        return v == null ? 0 : v;
    }

    private String labelOf(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private String seenText() {
        android.content.SharedPreferences p = Prefs.of(this);
        return getString(R.string.usage_seen,
                orDash(p.getString("last_event_pkg", null)), clock(p.getLong("last_event_at", 0L)),
                orDash(p.getString("last_tick_pkg", null)), clock(p.getLong("last_tick_at", 0L)));
    }

    private static String orDash(String s) {
        return s == null ? "-" : s;
    }

    private String clock(long millis) {
        if (millis == 0L) return "-";
        return DateFormat.format("HH:mm:ss", new Date(millis)).toString();
    }

    /** "1 h 05 min", "5 min 20 s" or "40 s": whole seconds as people read them. */
    private static String format(int seconds) {
        int h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        if (h > 0) return h + " h " + (m < 10 ? "0" : "") + m + " min";
        if (m > 0) return m + " min " + (s < 10 ? "0" : "") + s + " s";
        return s + " s";
    }
}
