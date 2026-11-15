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
        buttonManageUsers = findViewById(R.id.buttonManageUsers);

        // Only show Manage Users to administrators., will check with server and then in the OnReply will set the visibility of the button. 
        sync = new DataSync(this, new InventoryStore(databaseHelper), new UserStore(databaseHelper));
        sync.getUserByUsername(username);


        //boolean isAdmin = databaseHelper.isAdmin(username);
       // buttonManageUsers.setVisibility(isAdmin ? android.view.View.VISIBLE : android.view.View.GONE);

        //Inventory button
        buttonInventory.setOnClickListener(v -> {Intent intent = new Intent(MainActivity.this, InventoryActivity.class);
            intent.putExtra("username", username);
            startActivity(intent);
        });

        buttonUserHistory.setOnClickListener(v -> Toast.makeText(this, "User history screen coming next.", Toast.LENGTH_SHORT).show());

        //Open safe button
        buttonOpenSafe.setOnClickListener(v -> {if (databaseHelper.isAdmin(username) || databaseHelper.hasSafeAccess(username)) {
                Toast.makeText(this, "Access authorized. Safe hardware " + "control will be added later.", Toast.LENGTH_LONG).show();
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
        if("getUserByUsername".equals(reply.command)){
            // will only show the button for manage users if user is an admin...
                Models.User user = reply.asUser();
            //Toast.makeText(this,  "Received User" + user.username, Toast.LENGTH_SHORT).show();
                if (user == null){
                    Toast.makeText(this,  "no user returned", Toast.LENGTH_SHORT).show();
                }
                if (user != null && user.isAdmin){
                    isAdminFlag= true;
                    buttonManageUsers.setVisibility(android.view.View.VISIBLE);
                }else{
                    buttonManageUsers.setVisibility(android.view.View.GONE);
                   // buttonManageUsers.setVisibility(android.view.View.VISIBLE );
                }
            }
    }
    
    @Override 
    public void onError(String message){
        Toast.makeText(this,   "ERROR: "+message, Toast.LENGTH_SHORT).show();
    }





}


