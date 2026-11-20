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

        buttonOpenSafe.setOnClickListener(v -> openSafe());

        buttonBackHome.setOnClickListener(v -> finish());

        if (userId == -1) {
            buttonOpenSafe.setEnabled(false);
            Toast.makeText(this, "Could not identify the logged-in user.", Toast.LENGTH_LONG).show();
        }
    }

    private void openSafe() {
        if (unlockPending) {return;}

        if (userId == -1) {
            Toast.makeText(this, "Invalid user.", Toast.LENGTH_SHORT).show();
            return;
        }

        unlockPending = true;
        buttonOpenSafe.setEnabled(false);
        buttonOpenSafe.setText("Sending request...");

        sync.unlockDoor(userId);
    }

    @Override
    public void onReply(Models.Reply reply) {
        if ("unlockDoor".equals(reply.command)) {

            unlockPending = false;
            buttonOpenSafe.setEnabled(true);
            buttonOpenSafe.setText("Open Safe");

            Toast.makeText(this, "Unlock command sent to the safe.", Toast.LENGTH_LONG).show();
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
