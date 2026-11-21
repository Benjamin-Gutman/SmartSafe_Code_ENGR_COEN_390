package com.example.safeui.activities;


import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.safeui.DatabaseHelper;
import com.example.safeui.R;
import com.example.safeui.http_handler.DataSync;
import com.example.safeui.http_handler.InventoryStore;
import com.example.safeui.http_handler.Models;
import com.example.safeui.http_handler.UserStore;
import java.util.ArrayList;
import java.util.List;

public class ManageUsersActivity extends AppCompatActivity implements DataSync.Listener {
    private DatabaseHelper databaseHelper;
    private String adminUsername;
    private LinearLayout userList;
    private UserStore store;
    private DataSync sync;
    private List<Models.User> serverUsers = new ArrayList<>();

    private CheckBox pendingPermissionCheckbox;
    private Boolean previousPermission;
    private Boolean permissionUpdatePending = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_users);

        adminUsername = getIntent().getStringExtra("username");
        databaseHelper = new DatabaseHelper(this);
        //set up datasync object to get info from database on RPI 
        store = new UserStore(databaseHelper);
        sync = new DataSync(this, null, null, store);

        userList = findViewById(R.id.userList);
        
        //check if user is admin or not
        sync.getUserByUsername(adminUsername);

        Button buttonBack = findViewById(R.id.buttonBackManageUsers);
        buttonBack.setOnClickListener(v -> finish());

    }
    //Ui should be a series of rows
    private void loadUsers() {
        userList.removeAllViews();

        try {

            for (Models.User user : serverUsers) {
                int userId = user.user_id;
                String username = user.username;
                String role = user.isAdmin ? "Admin" : "User";
                boolean canOpenSafe = user.hasOpenDoorPrivilege;

                // Do not list the current administrator.
                if (username.equalsIgnoreCase(adminUsername)) {
                    continue;
                }

 //--------------don't need to touch this, it's just creating the layout-------------
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(16, 12, 16, 12);
                row.setBackgroundColor(0xFFFFFFFF);

                LinearLayout.LayoutParams rowParams =
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        );

                rowParams.bottomMargin = 12;
                row.setLayoutParams(rowParams);

                TextView name = new TextView(this);
                name.setText(username);
                name.setTextSize(18);
                name.setTextColor(0xFF202020);
                name.setTypeface(
                        null,
                        android.graphics.Typeface.BOLD
                );
                row.addView(name);

                TextView roleLabel = new TextView(this);
                roleLabel.setText("Role: " + role);
                roleLabel.setTextSize(14);
                roleLabel.setTextColor(0xFF666666);
                row.addView(roleLabel);

                CheckBox permission = new CheckBox(this);
                permission.setText("Can open safe");
                permission.setTextColor(0xFF202020);
                permission.setChecked(canOpenSafe);
                row.addView(permission);

                CheckBox permissionAdmin = new CheckBox(this);
                permissionAdmin.setText("Give Admin permission");
                permissionAdmin.setTextColor(0xFF202020);
                permissionAdmin.setChecked(user.isAdmin);
                row.addView(permissionAdmin);

                Button deleteUser = new Button(this);
                deleteUser.setText("Delete User");
                deleteUser.setTextSize(12);
                LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 100 );
                deleteUser.setLayoutParams(buttonParams);
                deleteUser.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.RED));
                deleteUser.setTextColor(android.graphics.Color.WHITE);
                row.addView(deleteUser);
//-----------------------------------------------------------------

                //check mark button to give safe permissions
                permission.setOnCheckedChangeListener(
                    (button, checked) -> updatePermission((CheckBox) button,user,checked,false)
                );
                //userList.addView(row);

                //check mark button to give admin permission
                permissionAdmin.setOnCheckedChangeListener(
                        (button, checked) -> updatePermission((CheckBox) button,user,checked,true)
                );
                userList.addView(row);

                // Button to delete user, will ask for confirmation from admin.
                deleteUser.setOnClickListener(v -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Delete User")
                            .setMessage("Are you sure you want to delete the following user :  " + username + " ?")
                            .setPositiveButton("Delete", (dialog, which) -> {
                                sync.deleteUser(userId);
                            })
                            .setNegativeButton("Cancel", (dialog, which) -> {
                                dialog.dismiss();
                            })
                            .show();
                });
            }

        } catch (Exception e) {
            Toast.makeText(this, "Could not load user accounts.", Toast.LENGTH_LONG).show();
        }

        if (userList.getChildCount() == 0) {
            TextView emptyMessage = new TextView(this);
            emptyMessage.setText("No other user accounts have been created yet.");
//----------------------Layout --------------------------------------------------
            emptyMessage.setTextSize(16);
            emptyMessage.setTextColor(0xFF666666);
            emptyMessage.setPadding(16, 20, 16, 20);

            LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            emptyParams.setMargins(12, 0, 12, 12);
//-----------------------------------------------------------------------------
            userList.addView(emptyMessage, emptyParams);
        }
    }

    //functionality for checkbox to change permissions.
    private void updatePermission(CheckBox button, Models.User user, boolean checked, boolean isAdmin) {
        if (permissionUpdatePending) { // if functions gets called while waiting for server response.
            button.setChecked(!checked);
            return;
        }

        permissionUpdatePending = true;

        pendingPermissionCheckbox = button;
        previousPermission = !checked; // was going to use it in case server access is failed for some reason. but due to current configuration of onError, will not be used.

        button.setEnabled(false);

        boolean newAdminPermission = isAdmin ? checked : user.isAdmin;
        boolean newSafePermission = isAdmin ? user.hasOpenDoorPrivilege : checked;

        sync.updateUserPrivileges(user.user_id, newAdminPermission, newSafePermission);

    }

    @Override 
    public void onReply(Models.Reply reply){
        if("listUsers".equals(reply.command)){
           serverUsers = reply.asUsers();
           loadUsers(); // refresh the layout.
        }
        else if("updateUserPrivileges".equals(reply.command)){
            //will do the logic for the checkbox change be done here so that it actually has the response of the server to work with.
            permissionUpdatePending = false;

            if (pendingPermissionCheckbox != null) {
                pendingPermissionCheckbox.setEnabled(true);
            }
            Models.User user = reply.asUser();

            if (user != null) {
                Toast.makeText(this, "User permissions updated for " + user.username, Toast.LENGTH_SHORT).show();
            }
            else {
                Toast.makeText(this, "User permissions updated.", Toast.LENGTH_SHORT).show();
            }
            pendingPermissionCheckbox = null;
        }
        else if("getUserByUsername".equals(reply.command)){
            Models.User user = reply.asUser();
            if (user != null && user.isAdmin){
     
                sync.listUsers();
            }else{
                Toast.makeText(this, "Administrator access required.", Toast.LENGTH_LONG).show();
                finish();
            }
        } else if("deleteUser".equals(reply.command)){ // when deleting a user, will refresh page to have locally stored users match up as well as UI
            Toast.makeText(this, "User deleted successfully.", Toast.LENGTH_SHORT).show();
            // Request an updated list from the server
            sync.listUsers();
        }
    }
    
    @Override 
    public void onError(String message){
        permissionUpdatePending = false;
        if (pendingPermissionCheckbox != null) {
            pendingPermissionCheckbox.setEnabled(true);
            pendingPermissionCheckbox.setChecked(previousPermission);
            pendingPermissionCheckbox = null;
        }
        Toast.makeText(this,  message, Toast.LENGTH_SHORT).show();
    }













}
