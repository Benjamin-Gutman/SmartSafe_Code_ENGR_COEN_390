package com.example.safeui;

import android.database.Cursor;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ManageUsersActivity extends AppCompatActivity {
    private DatabaseHelper databaseHelper;
    private String adminUsername;
    private LinearLayout userList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_users);

        adminUsername = getIntent().getStringExtra("username");
        databaseHelper = new DatabaseHelper(this);

        // Only administrators may access this screen.
        if (adminUsername == null
                || !databaseHelper.isAdmin(adminUsername)) {

            Toast.makeText(
                    this,
                    "Administrator access required.",
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        userList = findViewById(R.id.userList);

        Button buttonBack = findViewById(R.id.buttonBackManageUsers);
        buttonBack.setOnClickListener(v -> finish());

        loadUsers();
    }

    private void loadUsers() {
        userList.removeAllViews();

        try (Cursor cursor = databaseHelper.getAllUsers()) {

            while (cursor != null && cursor.moveToNext()) {
                int userId = cursor.getInt(0);
                String username = cursor.getString(1);
                String role = cursor.getString(2);
                boolean canOpenSafe = cursor.getInt(3) == 1;

                // Do not list the current administrator.
                if (username.equalsIgnoreCase(adminUsername)) {
                    continue;
                }

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

                permission.setOnCheckedChangeListener(
                        (button, checked) -> {
                            button.setEnabled(false);

                            boolean success =
                                    databaseHelper.setSafeAccess(
                                            userId,
                                            checked
                                    );

                            if (!success) {
                                // Temporarily remove the listener so
                                // reverting the checkbox doesn't retry
                                // the database update.
                                button.setOnCheckedChangeListener(null);
                                button.setChecked(!checked);

                                Toast.makeText(
                                        this,
                                        "Could not update permission.",
                                        Toast.LENGTH_SHORT
                                ).show();

                                button.setOnCheckedChangeListener(
                                        (changedButton, newChecked) ->
                                                updatePermission((CheckBox)changedButton, userId, newChecked)
                                );
                            } else {
                                Toast.makeText(
                                        this,
                                        checked
                                                ? "Safe access granted."
                                                : "Safe access revoked.",
                                        Toast.LENGTH_SHORT
                                ).show();
                            }

                            button.setEnabled(true);
                        }
                );

                userList.addView(row);
            }

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Could not load user accounts.",
                    Toast.LENGTH_LONG
            ).show();
        }

        if (userList.getChildCount() == 0) {
            TextView emptyMessage = new TextView(this);
            emptyMessage.setText(
                    "No other user accounts have been created yet."
            );
            emptyMessage.setTextSize(16);
            emptyMessage.setTextColor(0xFF666666);
            emptyMessage.setPadding(16, 20, 16, 20);

            LinearLayout.LayoutParams emptyParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            emptyParams.setMargins(12, 0, 12, 12);

            userList.addView(emptyMessage, emptyParams);
        }
    }

    private void updatePermission(
            CheckBox button,
            int userId,
            boolean checked
    ) {
        button.setEnabled(false);

        boolean success = databaseHelper.setSafeAccess(
                userId,
                checked
        );

        if (!success) {
            button.setOnCheckedChangeListener(null);
            button.setChecked(!checked);

            Toast.makeText(
                    this,
                    "Could not update permission.",
                    Toast.LENGTH_SHORT
            ).show();

            button.setOnCheckedChangeListener(
                    (changedButton, newChecked) ->
                            updatePermission(
                                    (CheckBox)changedButton,
                                    userId,
                                    newChecked
                            )
            );
        } else {
            Toast.makeText(
                    this,
                    checked
                            ? "Safe access granted."
                            : "Safe access revoked.",
                    Toast.LENGTH_SHORT
            ).show();
        }

        button.setEnabled(true);
    }
}
