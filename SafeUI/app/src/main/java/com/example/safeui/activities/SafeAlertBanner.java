package com.example.safeui.activities;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import com.example.safeui.R;
import com.example.safeui.http_handler.SafeStatusMonitor;

public class SafeAlertBanner implements SafeStatusMonitor.StatusListener {
    private final Activity activity;
    private final SafeStatusMonitor monitor;

    private final LinearLayout banner;
    private final Button buttonGoToSafe;
    private final Button buttonDismiss;

    private String username;
    private int userId;

    public SafeAlertBanner(Activity activity, String username, int userId) {

        this.activity = activity;
        this.username = username;
        this.userId = userId;

        monitor = SafeStatusMonitor.getInstance();

        banner = activity.findViewById(R.id.safeAlertBanner);
        buttonGoToSafe = activity.findViewById(R.id.buttonGoToSafe);
        buttonDismiss = activity.findViewById(R.id.buttonDismissSafeAlert);

        buttonDismiss.setOnClickListener(v -> monitor.dismissBanner());

        buttonGoToSafe.setOnClickListener(v -> {

            if (userId == -1 || username == null || username.isEmpty()) {
                return;
            }

            Intent intent = new Intent(activity, SafeActivity.class);
            intent.putExtra("username", username);
            intent.putExtra("user_id", userId);

            activity.startActivity(intent);
        });

        // No need to navigate to SafeActivity if already there.
        if (activity instanceof SafeActivity) {
            buttonGoToSafe.setVisibility(View.GONE);
        }
    }

    public void updateUserInfo(String username, int userId) {
        this.username = username;
        this.userId = userId;
    }

    public void onResume() {
        monitor.registerListener(this);
    }

    public void onPause() {
        monitor.unregisterListener(this);
    }

    @Override
    public void onAlarmStatusChanged(boolean alarmActive, boolean dismissed) {
        if (banner == null) {
            return;
        }

        if (alarmActive && !dismissed) {
            banner.setVisibility(View.VISIBLE);
        } else {
            banner.setVisibility(View.GONE);
        }
    }
}
