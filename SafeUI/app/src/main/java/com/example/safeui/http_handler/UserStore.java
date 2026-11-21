package com.example.safeui.http_handler;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import com.example.safeui.DatabaseHelper;
import java.util.List;


public class UserStore implements DataSync.UserLocalStore {

    private static final String TABLE = "users";

    private final DatabaseHelper helper;

    public UserStore(DatabaseHelper helper)
    {
        this.helper = helper;
    }

    // upload/insert user into table
    @Override 
    public void upsert(Models.User user)
    {
        if (user == null){
            return;
        }
        writeUser(helper.getWritableDatabase(), user);
    }
    
    @Override
    public void remove (int userID)
    {
        helper.getWritableDatabase().delete(
            TABLE,
            "id = ?",
            new String[]{String.valueOf(userID)}
        );
    }

    //Sync local users with server list
    @Override
    public void replaceAll(List<Models.User> users)
    {
        SQLiteDatabase db = helper.getWritableDatabase();
        if (users == null){
            return; //dont know why this would happen, but just in case so it wont crash.
        }
        db.beginTransaction();

         try
        {
            for (Models.User user : users)
            {
                if(user != null){
                    writeUser(db, user);
                }
            }
                //needs to do it like this so that SQLiteDatabase can properly delete users. 
                // NOT IN(users that are actually still in server) --> will delete those that are NOT IN those basically.
                List<Models.User> validUsers = new java.util.ArrayList<>();

                for(Models.User user : users){
                    if (user != null)
                        validUsers.add(user);
                }
                // Remove users that no longer exist on the server
                if(validUsers.isEmpty()){
                    db.delete(TABLE, null,null);
                }
                else {
                    StringBuilder selection = new StringBuilder("id NOT IN (");
                    String[] args = new String [validUsers.size()];

                    for (int i =0; i<validUsers.size(); i++){
                        if (i>0)
                            selection.append(",");
                        selection.append("?");
                        args[i] = String.valueOf(validUsers.get(i).user_id);
                    }
                selection.append(")");
                db.delete(TABLE, selection.toString(), args);
            }
            db.setTransactionSuccessful();
        }
        finally
        {
            db.endTransaction();
        }
    }


    private void writeUser(SQLiteDatabase db, Models.User user) {
        if (user == null) return;

        ContentValues cv = new ContentValues();

        cv.put("username", user.username);
        cv.put("role", user.isAdmin ? "admin" : "user");
        cv.put("can_open_safe", user.hasOpenDoorPrivilege ? 1 : 0);

        // First, update the user using their username.
        // Username is unique in the local database.
        int rows = db.update(
                TABLE,
                cv,
                "username = ? COLLATE NOCASE",
                new String[]{user.username}
        );

        // The user already exists locally, so we are finished.
        if (rows > 0) {
            return;
        }

        // Otherwise, insert the user as a new local record.
        // Keep the server ID so existing server-ID-based operations
        // continue to work for newly synchronized users.
        cv.put("id", user.user_id);
        cv.put("password_hash", "");
        cv.put("password_salt", "");

        db.insertOrThrow(TABLE, null, cv);
    }


}
