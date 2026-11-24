package com.example.safeui.activities;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.safeui.DatabaseHelper;
import com.example.safeui.R;
import com.example.safeui.http_handler.DataSync;
import com.example.safeui.http_handler.AccessHistoryController;
import com.example.safeui.http_handler.AccessHistoryEntry;
import com.example.safeui.http_handler.ClientSocket;

import java.util.List;
import java.util.Map;
import com.example.safeui.http_handler.InventoryStore;
import com.example.safeui.http_handler.Models;
import com.example.safeui.http_handler.UserStore;

public class SafeActivity extends AppCompatActivity implements DataSync.Listener {
    private String username;
    private int userId;

    private DatabaseHelper databaseHelper;
    private DataSync sync;
    private Button buttonOpenSafe;
    private Button buttonBackHome;

    private SafeAlertBanner safeAlertBanner;
    private boolean unlockPending = false;

    private static final long HISTORY_REFRESH_MS = 15_000;
    private final Handler historyHandler = new Handler(Looper.getMainLooper());
    private ClientSocket historyConnection;
    private AccessHistoryController historyController;
    private Button buttonRefreshHistory;
    private LinearLayout historySection;
    private LinearLayout historyRows;
    private TextView historyStatus;
    private ProgressBar historyProgress;
    private List<AccessHistoryEntry> displayedHistory;
    private Map<Integer, String> displayedUsers;

    private final Runnable refreshHistoryTask = new Runnable() {
        @Override
        public void run() {
            if (historyController == null) return;
            historyController.refresh();
            historyHandler.postDelayed(this, HISTORY_REFRESH_MS);
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_safe);

        username = getIntent().getStringExtra("username");
        userId = getIntent().getIntExtra("user_id", -1);
        safeAlertBanner = new SafeAlertBanner(this, username, userId);

        databaseHelper = new DatabaseHelper(this);

        InventoryStore inventoryStore = new InventoryStore(databaseHelper);
        UserStore userStore = new UserStore(databaseHelper);

        sync = new DataSync(this, inventoryStore, null, userStore);

        buttonOpenSafe = findViewById(R.id.buttonOpenSafe);
        buttonBackHome = findViewById(R.id.buttonBackHome);
        buttonRefreshHistory = findViewById(R.id.buttonRefreshSafeHistory);
        historySection = findViewById(R.id.layoutSafeHistorySection);
        historyRows = findViewById(R.id.layoutSafeHistory);
        historyStatus = findViewById(R.id.textSafeHistoryStatus);
        historyProgress = findViewById(R.id.progressSafeHistory);
        buttonRefreshHistory.setOnClickListener(view -> {
            if (historyController != null) historyController.refresh();
        });

        buttonOpenSafe.setOnClickListener(v -> sync.getStatus());

        buttonBackHome.setOnClickListener(v -> finish());

        if (userId == -1) {
            buttonOpenSafe.setEnabled(false);
            Toast.makeText(this, "Could not identify the logged-in user.", Toast.LENGTH_LONG).show();
        }
    }

    private void handleSafeStatus(Models.SafeStatus safeStatus) {
        if (safeStatus == null
                || safeStatus.isLocked == null
                || safeStatus.isOpen == null
                || safeStatus.alarm == null) {

            Toast.makeText(this, "Could not retrieve the safe status.", Toast.LENGTH_LONG).show();
            return;
        }

        // Case 1: Locked, closed, alarm off
        if (safeStatus.isLocked && !safeStatus.isOpen && !safeStatus.alarm) {
            sync.unlockDoor(userId);
        }

        // Case 2: Unlocked, open, alarm off
        else if (!safeStatus.isLocked && safeStatus.isOpen && !safeStatus.alarm) {
            Toast.makeText(this, "The safe is already open.", Toast.LENGTH_LONG).show();
        }

        // Case 3: Locked, open, alarm on
        //this will be changed when we eventually get a check status regularly
        else if (safeStatus.isLocked && safeStatus.isOpen && safeStatus.alarm) {
            Toast.makeText(this, "Safe was opened without permission, clearing the alarm now.", Toast.LENGTH_LONG).show();
            sync.unlockDoor(userId);
        }

        else {
            Toast.makeText(this, "The safe is in an unexpected state.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onReply(Models.Reply reply) {
        if ("getStatus".equals(reply.command)) {
            Models.SafeStatus safeStatus = reply.asSafeStatus();
            handleSafeStatus(safeStatus);
        }

        else if ("unlockDoor".equals(reply.command)) {
            Toast.makeText(this, "Unlock command sent to the safe.", Toast.LENGTH_LONG).show();
            // Fetch server records; an unlock acknowledgement is not a physical access record.
            historyHandler.removeCallbacks(refreshHistoryTask);
            historyHandler.postDelayed(refreshHistoryTask, 1000);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (safeAlertBanner != null) {
            safeAlertBanner.onResume();
        }
        startAccessHistory();
    }

    @Override
    protected void onPause() {
        historyHandler.removeCallbacks(refreshHistoryTask);
        if (historyController != null) historyController.stop();
        if (historyConnection != null) historyConnection.close();
        historyController = null;
        historyConnection = null;
        if (safeAlertBanner != null) {
            safeAlertBanner.onPause();
        }

        super.onPause();
    }

    private void startAccessHistory() {
        historyController = createAccessHistoryController();
        displayedHistory = null;
        displayedUsers = null;
        historyController.refresh();
        historyHandler.postDelayed(refreshHistoryTask, HISTORY_REFRESH_MS);
    }

    protected AccessHistoryController createAccessHistoryController() {
        // History has its own socket so it cannot take replies intended for the
        // existing door controls or the background alarm monitor.
        historyConnection = ClientSocket.newConnection();
        ClientSocket screenConnection = historyConnection;
        return new AccessHistoryController(username, userId, (command, callback) ->
                screenConnection.send(command, new ClientSocket.ReplyCallback() {
                    @Override
                    public void onReply(Models.Reply reply) {
                        historyHandler.post(() -> callback.onReply(reply));
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        historyHandler.post(() -> callback.onError(error.getMessage()));
                    }
                }), this::renderAccessHistory);
    }

    protected void renderAccessHistory() {
        if (historyController == null) return;
        boolean allowed = historyController.canViewHistory();
        buttonRefreshHistory.setEnabled(!historyController.isBusy());
        buttonRefreshHistory.setVisibility(allowed || !historyController.hasPermissions()
                ? View.VISIBLE : View.GONE);
        historyProgress.setVisibility(historyController.isBusy() ? View.VISIBLE : View.GONE);
        historyStatus.setText(historyController.getHistoryStatus());
        historySection.setVisibility(allowed ? View.VISIBLE : View.GONE);
        findViewById(R.id.spaceSafeHistory).setVisibility(allowed ? View.GONE : View.VISIBLE);
        if (!allowed) {
            historyRows.removeAllViews();
            displayedHistory = null;
            displayedUsers = null;
            return;
        }
        if (displayedHistory == historyController.getHistory()
                && displayedUsers == historyController.getUsers()) return;
        displayedHistory = historyController.getHistory();
        displayedUsers = historyController.getUsers();
        historyRows.removeAllViews();
        for (AccessHistoryEntry entry : displayedHistory) {
            View row = getLayoutInflater().inflate(R.layout.row_access_history, historyRows, false);
            TextView event = row.findViewById(R.id.textAccessEvent);
            TextView actor = row.findViewById(R.id.textAccessUser);
            TextView time = row.findViewById(R.id.textAccessTime);
            event.setText(entry.getEventLabel());
            actor.setText(getString(R.string.access_history_user, entry.getActorLabel(displayedUsers)));
            time.setText(entry.getDisplayTime());
            if (entry.isAlarm()) {
                row.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFF0F0")));
                event.setTextColor(Color.parseColor("#A31621"));
            }
            historyRows.addView(row);
        }
    }

    @Override
    public void onError(String message) {
        unlockPending = false;
        buttonOpenSafe.setEnabled(true);
        buttonOpenSafe.setText("Open Safe");

        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
