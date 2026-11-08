package com.simonegiansante.screentime;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/** Every launchable app, ticked for the ones GuardService always sends back to Home. */
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
        for (AppEntry entry : launchableApps()) {
            if (entry.packageName.equals(self) || entry.packageName.equals("com.gathin.tvhome")) continue;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Math.round(8 * dp), Math.round(8 * dp), Math.round(8 * dp), Math.round(8 * dp));
            CheckBox box = new CheckBox(this);
            box.setChecked(blacklist.contains(entry.packageName));
            box.setOnCheckedChangeListener((btn, checked) -> {
                Set<String> current = Prefs.loadBlacklist(this);
                if (checked) current.add(entry.packageName); else current.remove(entry.packageName);
                Prefs.saveBlacklist(this, current);
            });
            row.addView(box);
            TextView label = new TextView(this);
            label.setText(entry.label);
            label.setTextSize(16);
            label.setTextColor(0xFFFFFFFF);
            label.setPadding(Math.round(12 * dp), 0, 0, 0);
            row.addView(label);
            rows.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        setContentView(page);
    }

    private static final class AppEntry {
        final String packageName;
        final String label;

        AppEntry(String packageName, String label) {
            this.packageName = packageName;
            this.label = label;
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
                byLabel.put(label + "\u0000" + pkg, new AppEntry(pkg, label));
            }
        }
        return new ArrayList<>(byLabel.values());
    }
}
