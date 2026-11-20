package com.example.safeui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.safeui.DatabaseHelper;
import com.example.safeui.R;
import com.example.safeui.http_handler.DataSync;
import com.example.safeui.http_handler.InventoryStore;
import com.example.safeui.http_handler.Models;
import com.example.safeui.http_handler.SafeStatusMonitor;

public class LoginActivity extends AppCompatActivity implements DataSync.Listener {
    private EditText editUsername;
    private EditText editPassword;
    private DataSync sync;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);


        editUsername = findViewById(R.id.editLoginUsername);
        editPassword = findViewById(R.id.editLoginPassword);
        Button buttonLogin = findViewById(R.id.buttonLogin);
        TextView textCreateAccount = findViewById(R.id.textCreateAccount);

        // Set up the login button.
        buttonLogin.setOnClickListener(v -> login());

        // Set up the create account option.
        textCreateAccount.setOnClickListener(v ->openCreateAccount());

        //set up datasync object to get info from database on RPI
         sync = new DataSync(this);

    }

    private void login() {
        String username = editUsername.getText().toString().trim();

        String password = editPassword.getText().toString();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Enter your username and password.", Toast.LENGTH_SHORT).show();
            return;
        }

        sync.verifyUser(username,password); // will call server database to verify user, check onReply to see how it responds.
    }

    private void openCreateAccount() {
        Intent intent = new Intent(LoginActivity.this, CreateAccountActivity.class);

        startActivity(intent);

    }

    @Override
    public void onReply(Models.Reply reply){
        if("verifyUser".equals(reply.command)){
            //if login successful, will go to main activity and feedback of successful login.
            // trasmits username to mainActivity
            if (!reply.message.contains("not")){//message will only contain not if user not found, so will only proceed if there isn't not in message
                String username = editUsername.getText().toString().trim();
                Toast.makeText(this, "Succesfully logged in, user: " + username , Toast.LENGTH_SHORT).show();

                // Start monitoring the safe after successful login.
               // SafeStatusMonitor.getInstance().start(getApplicationContext());

                Intent intent = new Intent( LoginActivity.this, MainActivity.class);
                intent.putExtra("username", username);
                startActivity(intent);
               // finish();
            } else{
                Toast.makeText(this, "Incorrect password, TRY AGAIN", Toast.LENGTH_SHORT).show();
            }
        }
    }
    // Databasehelper.py will return an error if an only if  user was not found.
    // Message should be "User {username} not found"
    // Only possible error message response in this activity.
    @Override
    public void onError(String message){
        Toast.makeText(this,  "ERROR:" + message, Toast.LENGTH_LONG).show();
    }




















}

