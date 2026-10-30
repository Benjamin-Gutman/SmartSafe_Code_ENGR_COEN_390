package com.example.safeui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {
    private EditText editUsername;
    private EditText editPassword;
    private DatabaseHelper databaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        databaseHelper = new DatabaseHelper(this);

        editUsername = findViewById(R.id.editLoginUsername);
        editPassword = findViewById(R.id.editLoginPassword);

        Button buttonLogin = findViewById(R.id.buttonLogin);
        TextView textCreateAccount =
                findViewById(R.id.textCreateAccount);

        // Set up the login button.
        buttonLogin.setOnClickListener(v -> login());

        // Set up the create account option.
        textCreateAccount.setOnClickListener(v ->
                openCreateAccount());

        // If no accounts exist, open account creation.
        if (!databaseHelper.hasUsers()) {
            openCreateAccount();
        }
    }

    private void login() {
        String username =
                editUsername.getText().toString().trim();

        String password =
                editPassword.getText().toString();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this,
                    "Enter your username and password.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (databaseHelper.validateUser(username, password)) {

            Intent intent = new Intent(
                    LoginActivity.this,
                    MainActivity.class
            );

            intent.putExtra("username", username);

            startActivity(intent);
            finish();

        } else {
            Toast.makeText(this,
                    "Incorrect username or password.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void openCreateAccount() {
        Intent intent = new Intent(
                LoginActivity.this,
                CreateAccountActivity.class
        );

        startActivity(intent);
    }
}

