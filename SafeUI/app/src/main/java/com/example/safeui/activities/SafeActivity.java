package com.example.safeui.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.safeui.DatabaseHelper;
import com.example.safeui.R;
import com.example.safeui.http_handler.DataSync;
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

    private boolean unlockPending = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_safe);

        username = getIntent().getStringExtra("username");
        userId = getIntent().getIntExtra("user_id", -1);

        databaseHelper = new DatabaseHelper(this);

        InventoryStore inventoryStore = new InventoryStore(databaseHelper);
        UserStore userStore = new UserStore(databaseHelper);

        sync = new DataSync(this, inventoryStore, null, userStore);

        buttonOpenSafe = findViewById(R.id.buttonOpenSafe);
        buttonBackHome = findViewById(R.id.buttonBackHome);

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
            Toast.makeText(this,
                    "The safe is already open.",
                    Toast.LENGTH_LONG).show();
        }

        // Case 3: Locked, open, alarm on
        else if (safeStatus.isLocked && safeStatus.isOpen && safeStatus.alarm) {
            Toast.makeText(this, "Unauthorized opening detected! Click Unlock to clear the alarm.", Toast.LENGTH_LONG).show();
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
            Toast.makeText(this,
                    "Unlock command sent to the safe.",
                    Toast.LENGTH_LONG).show();
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
