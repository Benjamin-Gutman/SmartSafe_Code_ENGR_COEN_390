package com.example.safeui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import android.app.AlertDialog;

import androidx.appcompat.app.AppCompatActivity;

import com.example.safeui.DatabaseHelper;
import com.example.safeui.R;

public class MainActivity extends AppCompatActivity {
    private String username;
    private DatabaseHelper databaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        username = getIntent().getStringExtra("username");

        if (username == null || username.isEmpty()) {
            username = "User";
        }

        databaseHelper = new DatabaseHelper(this);

        Button buttonInventory = findViewById(R.id.buttonInventory);
        Button buttonUserHistory = findViewById(R.id.buttonUserHistory);
        Button buttonOpenSafe = findViewById(R.id.buttonOpenSafe);
        Button buttonAccount = findViewById(R.id.buttonAccount);
        Button buttonManageUsers = findViewById(R.id.buttonManageUsers);

        // Only show Manage Users to administrators.
        boolean isAdmin = databaseHelper.isAdmin(username);

        buttonManageUsers.setVisibility(isAdmin ? android.view.View.VISIBLE : android.view.View.GONE);

        buttonInventory.setOnClickListener(v -> {Intent intent = new Intent(MainActivity.this, InventoryActivity.class);
            intent.putExtra("username", username);
            startActivity(intent);
        });

        buttonUserHistory.setOnClickListener(v -> Toast.makeText(this, "User history screen coming next.", Toast.LENGTH_SHORT).show());

        buttonOpenSafe.setOnClickListener(v -> {if (databaseHelper.isAdmin(username) || databaseHelper.hasSafeAccess(username)) {
                Toast.makeText(this, "Access authorized. Safe hardware " + "control will be added later.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "You have not been granted safe access.", Toast.LENGTH_LONG).show();
            }
        });

        buttonManageUsers.setOnClickListener(v -> {
            if (databaseHelper.isAdmin(username)) {
                Intent intent = new Intent(MainActivity.this, ManageUsersActivity.class);
                intent.putExtra("username", username);
                startActivity(intent);
            } else {
                Toast.makeText(this, "Administrator access required.", Toast.LENGTH_SHORT).show();
            }
        });
        buttonAccount.setOnClickListener(v -> showAccountDialog());
    }

    private void showAccountDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Account")
                .setMessage("Logged in as: " + username)
                .setPositiveButton("Log Out", (dialog, which) -> {Intent intent = new Intent(MainActivity.this, LoginActivity.class);

                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Close", null)
                .show();
    }
}


