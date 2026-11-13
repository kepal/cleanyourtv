package com.simonegiansante.screentime;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/** Every launchable app with its own icon: OK ticks it to be always sent back to Home, Left/Right set its own daily limit. */
public class BlacklistActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFF0D1117);
        page.setPadding(Math.round(48 * dp), Math.round(32 * dp), Math.round(48 * dp), Math.round(24 * dp));

        TextView title = new TextView(this);
        title.setText(R.string.row_blacklist);
        title.setTextSize(24);
        title.setTextColor(0xFFFFFFFF);
        page.addView(title);
        TextView hint = new TextView(this);
        hint.setText(R.string.blacklist_hint);
        hint.setTextSize(13);
        hint.setTextColor(0xFF8B949E);
        hint.setPadding(0, Math.round(4 * dp), 0, Math.round(16 * dp));
        page.addView(hint);

        ScrollView scroll = new ScrollView(this);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Set<String> blacklist = Prefs.loadBlacklist(this);
        String self = getPackageName();
        boolean first = true;
        for (AppEntry entry : launchableApps()) {
            if (entry.packageName.equals(self) || entry.packageName.equals("com.gathin.tvhome")) continue;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int pv = Math.round(10 * dp), ph = Math.round(12 * dp);
            row.setPadding(ph, pv, ph, pv);
            row.setBackgroundResource(R.drawable.row_background);
            row.setFocusable(true);
            row.setClickable(true);

            ImageView icon = new ImageView(this);
            int iconSize = Math.round(40 * dp);
            icon.setImageDrawable(entry.icon);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            row.addView(icon, iconParams);

            TextView label = new TextView(this);
            label.setText(entry.label);
            label.setTextSize(16);
            label.setTextColor(0xFFFFFFFF);
            label.setPadding(Math.round(16 * dp), 0, 0, 0);
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView limit = new TextView(this);
            limit.setTextSize(14);
            limit.setTextColor(0xFFC9D1D9);
            limit.setGravity(Gravity.END);
            limit.setMinWidth(Math.round(120 * dp));
            limit.setPadding(0, 0, Math.round(16 * dp), 0);
            limit.setText(limitText(Prefs.appLimitMinutes(this, entry.packageName)));
            row.addView(limit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            CheckBox box = new CheckBox(this);
            box.setFocusable(false);
            box.setClickable(false);
            box.setChecked(blacklist.contains(entry.packageName));
            row.addView(box);

            row.setOnClickListener(v -> {
                boolean nowChecked = !box.isChecked();
                box.setChecked(nowChecked);
                Set<String> current = Prefs.loadBlacklist(this);
                if (nowChecked) current.add(entry.packageName); else current.remove(entry.packageName);
                Prefs.saveBlacklist(this, current);
            });

            row.setOnKeyListener((v, code, e) -> {
                if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                if (e.getAction() == KeyEvent.ACTION_DOWN) {
                    int step = code == KeyEvent.KEYCODE_DPAD_LEFT ? -Rules.QUOTA_STEP : Rules.QUOTA_STEP;
                    int next = Rules.clamp(Prefs.appLimitMinutes(this, entry.packageName) + step, 0, Rules.DAY_MINUTES);
                    Prefs.setAppLimitMinutes(this, entry.packageName, next);
                    limit.setText(limitText(next));
                }
                return true;
            });

            rows.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (first) {
                row.requestFocus();
                first = false;
            }
        }
        setContentView(page);
    }

    private String limitText(int minutes) {
        return minutes == 0 ? getString(R.string.app_limit_none) : getString(R.string.app_limit_minutes, minutes);
    }

    private static final class AppEntry {
        final String packageName;
        final String label;
        final Drawable icon;

        AppEntry(String packageName, String label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    private List<AppEntry> launchableApps() {
        PackageManager pm = getPackageManager();
        Intent leanback = new Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.LEANBACK_LAUNCHER");
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        TreeMap<String, AppEntry> byLabel = new TreeMap<>();
        Set<String> seen = new HashSet<>();
        for (Intent query : new Intent[]{leanback, launcher}) {
            for (ResolveInfo info : pm.queryIntentActivities(query, 0)) {
                ApplicationInfo app = info.activityInfo.applicationInfo;
                String pkg = app.packageName;
                if (!seen.add(pkg)) continue;
                String label = info.loadLabel(pm).toString();
                Drawable icon = info.loadIcon(pm);
                byLabel.put(label + "\u0000" + pkg, new AppEntry(pkg, label, icon));
            }
        }
        return new ArrayList<>(byLabel.values());
    }
}
