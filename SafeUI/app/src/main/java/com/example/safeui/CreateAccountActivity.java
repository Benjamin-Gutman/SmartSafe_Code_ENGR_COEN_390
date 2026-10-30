package com.example.safeui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class CreateAccountActivity extends AppCompatActivity {
    private EditText editUsername;
    private EditText editPassword;
    private EditText editConfirmPassword;
    private DatabaseHelper databaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_account);

        databaseHelper = new DatabaseHelper(this);

        editUsername = findViewById(R.id.editUsername);
        editPassword = findViewById(R.id.editPassword);
        editConfirmPassword = findViewById(R.id.editConfirmPassword);

        Button buttonCreateAccount = findViewById(R.id.buttonCreateAccount);

        buttonCreateAccount.setOnClickListener(v -> createAccount());
    }

    private void createAccount() {
        String username = editUsername.getText().toString().trim();
        String password = editPassword.getText().toString();
        String confirmPassword = editConfirmPassword.getText().toString();

        if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (username.length() < 3) {
            editUsername.setError("Username must be at least 3 characters.");
            return;
        }

        if (password.length() < 8) {
            editPassword.setError("Password must be at least 8 characters.");
            return;
        }

        if (!password.equals(confirmPassword)) {
            editConfirmPassword.setError("Passwords do not match.");
            return;
        }

        boolean success = databaseHelper.createUser(username, password);

        if (success) {
            Toast.makeText(this, "Account created. Please log in.", Toast.LENGTH_LONG).show();

            finish();
        } else {
            Toast.makeText(this, "Account could not be created. " + "The username may already exist.", Toast.LENGTH_LONG).show();
        }
    }
}

