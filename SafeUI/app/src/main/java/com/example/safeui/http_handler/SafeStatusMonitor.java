
package com.example.safeui.http_handler;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

public class SafeStatusMonitor implements DataSync.Listener {

    private static final long POLL_INTERVAL = 3000;

    private static SafeStatusMonitor instance;

    public interface StatusListener {
        void onAlarmStatusChanged(boolean alarmActive, boolean dismissed);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DataSync sync;
    private final Runnable pollTask;

    private final List<StatusListener> listeners = new ArrayList<>();

    private boolean running = false;
    private boolean alarmActive = false;
    private boolean bannerDismissed = false;

    private Models.SafeStatus latestStatus;

    private SafeStatusMonitor() {

        sync = new DataSync(this);

        pollTask = () -> {
            if (running) {
                sync.getStatus();
            }
        };
    }

    public static synchronized SafeStatusMonitor getInstance() {
        if (instance == null) {
            instance = new SafeStatusMonitor();
        }

        return instance;
    }

    public void start(Context context) {
        if (running) {
            return;
        }

        running = true;
        handler.removeCallbacks(pollTask);
        handler.post(pollTask);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(pollTask);

        alarmActive = false;
        bannerDismissed = false;
        latestStatus = null;

        notifyListeners();
    }

    public void registerListener(StatusListener listener) {
        if (listener == null || listeners.contains(listener)) {
            return;
        }

        listeners.add(listener);
        listener.onAlarmStatusChanged(alarmActive, bannerDismissed);
    }

    public void unregisterListener(StatusListener listener) {
        listeners.remove(listener);
    }

    public void dismissBanner() {
        if (!alarmActive) {
            return;
        }
        bannerDismissed = true;
        notifyListeners();
    }

    public Models.SafeStatus getLatestStatus() {
        return latestStatus;
    }

    private boolean isAlarmState(Models.SafeStatus status) {
        return status != null
                && Boolean.TRUE.equals(status.isLocked)
                && Boolean.TRUE.equals(status.isOpen)
                && Boolean.TRUE.equals(status.alarm);
    }

    private void updateAlarmStatus(Models.SafeStatus status) {
        boolean newAlarmState = isAlarmState(status);

        if (!newAlarmState) {
            // When the alarm clears, allow a future alarm to show the banner again.
            bannerDismissed = false;
        }

        if (alarmActive != newAlarmState || !newAlarmState) {
            alarmActive = newAlarmState;
            notifyListeners();
        }
    }

    private void notifyListeners() {
        for (StatusListener listener : new ArrayList<>(listeners)) {
            listener.onAlarmStatusChanged(alarmActive, bannerDismissed);
        }
    }

    @Override
    public void onReply(Models.Reply reply) {
        if (!running) {
            return;
        }

        if ("getStatus".equals(reply.command)) {

            Models.SafeStatus newStatus = reply.asSafeStatus();

            if (newStatus != null) {
                latestStatus = newStatus;
                updateAlarmStatus(newStatus);
            }
        }

        handler.removeCallbacks(pollTask);
        handler.postDelayed(pollTask, POLL_INTERVAL);
    }

    @Override
    public void onError(String message) {
        if (!running) {
            return;
        }

        handler.removeCallbacks(pollTask);
        handler.postDelayed(pollTask, POLL_INTERVAL);
    }
}
