package com.example.safeui.http_handler;

import android.os.Handler;
import android.os.Looper;

import java.util.List;


public class UserStore implements DataSync.LocalUsers {

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
    public void remove (int userId)
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
        db.beginTransaction();

         try
        {
            for (Models.User user : users)
            {
                writeUser(db, user);
            }

            // Remove users that no longer exist on the server.
            if (users.isEmpty())
            {
                db.delete(TABLE, null, null);
            }
            else
            {
                //needs to do it like this so that SQLiteDatabase can properly delete users. 
                // NOT IN(users that are actually still in server) --> will delete those that are NOT IN those basically. 
                StringBuilder selection = new StringBuilder("id NOT IN (");
                String[] args = new String[users.size()];
                for (int i = 0; i < users.size(); i++)
                {
                    if (i > 0){
                        selection.append(",");
                    }
                    selection.append("?");
                    args[i] = String.valueOf(users.get(i).user_id);
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

     private void writeUser(SQLiteDatabase db, Models.User user)
    {
        if (user == null) return;

        ContentValues cv = new ContentValues();

        cv.put("username", user.username);
        cv.put("role", user.isAdmin ? "admin" : "user");
        cv.put("can_open_safe",
                user.hasOpenDoorPrivilege ? 1 : 0);

        int rows = db.update(
                TABLE,
                cv,
                "id = ?",
                new String[]{String.valueOf(user.user_id)}
        );

        if (rows == 0)
        {
            // Pre-existing info should not be replaced by this. 
            cv.put("id", user.user_id);
            cv.put("password_hash", "");
            cv.put("password_salt", "");

            db.insertOrThrow(TABLE, null, cv);
        }
    }
    
}
