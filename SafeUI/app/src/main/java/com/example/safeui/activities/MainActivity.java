package com.example.safeui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import android.app.AlertDialog;

import androidx.appcompat.app.AppCompatActivity;

import com.example.safeui.DatabaseHelper;
import com.example.safeui.R;
import com.example.safeui.http_handler.DataSync;
import com.example.safeui.http_handler.InventoryStore;
import com.example.safeui.http_handler.Models;
import com.example.safeui.http_handler.UserStore;

public class MainActivity extends AppCompatActivity implements DataSync.Listener {
    private String username;
    private DatabaseHelper databaseHelper;
    private DataSync sync;
    private Button buttonManageUsers;
    private boolean isAdminFlag = false;
    private boolean hasOpenDoorPrivilege = false;
    private boolean userInfoLoaded = false;
    private int currentUserId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        username = getIntent().getStringExtra("username");


        if (username == null || username.isEmpty()) {
            username = "User";
        }

        databaseHelper = new DatabaseHelper(this);
        InventoryStore inventoryStore = new InventoryStore(databaseHelper);
        UserStore userStore = new UserStore(databaseHelper);

        sync = new DataSync(this, inventoryStore, null, userStore);
        sync.getUserByUsername(username);

        Button buttonInventory = findViewById(R.id.buttonInventory);
        Button buttonUserHistory = findViewById(R.id.buttonUserHistory);
        Button buttonOpenSafe = findViewById(R.id.buttonOpenSafe);
        Button buttonAccount = findViewById(R.id.buttonAccount);

        buttonManageUsers = findViewById(R.id.buttonManageUsers);
        buttonManageUsers.setVisibility(android.view.View.GONE);

        // Only show Manage Users to administrators., will check with server and then in the OnReply will set the visibility of the button.


        //boolean isAdmin = databaseHelper.isAdmin(username);
       // buttonManageUsers.setVisibility(isAdmin ? android.view.View.VISIBLE : android.view.View.GONE);

        //Inventory button
        buttonInventory.setOnClickListener(v -> {Intent intent = new Intent(MainActivity.this, InventoryActivity.class);
            intent.putExtra("username", username);
            startActivity(intent);
        });

        buttonUserHistory.setOnClickListener(v -> Toast.makeText(this, "User history screen coming next.", Toast.LENGTH_SHORT).show());

        //Open safe button
        buttonOpenSafe.setOnClickListener(v -> {
            if (!userInfoLoaded) {
                Toast.makeText(this, "User information is still loading.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (hasOpenDoorPrivilege) {
                Intent intent = new Intent(MainActivity.this, SafeActivity.class);
                intent.putExtra("username", username);
                intent.putExtra("user_id", currentUserId);
                startActivity(intent);

            } else {
                Toast.makeText(this, "You have not been granted safe access.", Toast.LENGTH_LONG).show();
            }
        });

        //Manage user button
        buttonManageUsers.setOnClickListener(v -> {
            if (isAdminFlag) {
                Intent intent = new Intent(MainActivity.this, ManageUsersActivity.class);
                intent.putExtra("username", username);
                startActivity(intent);
            } else {
                Toast.makeText(this, "Administrator access required.", Toast.LENGTH_SHORT).show();
            }
        });
        buttonAccount.setOnClickListener(v -> showAccountDialog());
    }

    //account button + sign out dialog
    private void showAccountDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Account")
                .setMessage("Logged in as: " + username)
                .setPositiveButton("Log Out", (dialog, which) -> {Intent intent = new Intent(MainActivity.this, LoginActivity.class);

                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

                    startActivity(intent);
                    finish();
                })
                .setNeutralButton("Close", null)
                .show();
    }

    @Override 
    public void onReply(Models.Reply reply){
        if("getUserByUsername".equals(reply.command)) {
            // will only show the button for manage users if user is an admin...
            Models.User user = reply.asUser();

            if (user != null) {
                currentUserId = user.user_id;
                isAdminFlag = user.isAdmin;
                hasOpenDoorPrivilege = user.hasOpenDoorPrivilege;
                userInfoLoaded = true;
                buttonManageUsers.setVisibility(isAdminFlag ? android.view.View.VISIBLE : android.view.View.GONE);

            }
        }
    }
    
    @Override 
    public void onError(String message){
        Toast.makeText(this,   "ERROR: "+message, Toast.LENGTH_SHORT).show();
    }





}


