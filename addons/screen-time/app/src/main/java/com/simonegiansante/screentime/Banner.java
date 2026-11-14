package com.simonegiansante.screentime;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * A short message drawn over whatever app is in front. An accessibility service may add this window type
 * without any overlay permission; it takes no focus and no touches, so the app underneath keeps running
 * and keeps the remote.
 */
final class Banner {
    private static final long SHOW_MILLIS = 10_000L;

    private final Context context;
    private final WindowManager windows;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideTask = this::hide;
    private TextView view;

    Banner(Context context) {
        this.context = context;
        this.windows = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    void show(String text) {
        hide();
        float dp = context.getResources().getDisplayMetrics().density;
        TextView v = new TextView(context);
        v.setText(text);
        v.setTextColor(0xFFFFFFFF);
        v.setTextSize(20);
        v.setMaxWidth(Math.round(560 * dp));
        v.setBackgroundResource(R.drawable.banner_background);
        v.setPadding(Math.round(24 * dp), Math.round(16 * dp), Math.round(24 * dp), Math.round(16 * dp));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = Math.round(48 * dp);
        lp.y = Math.round(32 * dp);
        try {
            windows.addView(v, lp);
            view = v;
            handler.postDelayed(hideTask, SHOW_MILLIS);
        } catch (RuntimeException e) {
            view = null;
        }
    }

    void hide() {
        handler.removeCallbacks(hideTask);
        if (view == null) return;
        try {
            windows.removeView(view);
        } catch (RuntimeException ignored) {
        }
        view = null;
    }
}
