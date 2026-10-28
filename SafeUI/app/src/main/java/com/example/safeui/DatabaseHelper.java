package com.example.safeui;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "SafeInventory.db";
    private static final int DATABASE_VERSION = 3;

    private static final String USERS_TABLE = "users";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createUsersTable =
                "CREATE TABLE " + USERS_TABLE + " (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "username TEXT NOT NULL COLLATE NOCASE UNIQUE, " +
                        "password_hash TEXT NOT NULL, " +
                        "password_salt TEXT NOT NULL, " +
                        "role TEXT NOT NULL DEFAULT 'user', " +
                        "can_open_safe INTEGER NOT NULL DEFAULT 0) ";

        db.execSQL(createUsersTable);

        db.execSQL(
                "CREATE TABLE inventory (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "name TEXT NOT NULL COLLATE NOCASE UNIQUE, " +
                        "description TEXT NOT NULL DEFAULT '', " +
                        "quantity INTEGER NOT NULL DEFAULT 0 CHECK(quantity >= 0))"
        );

        db.execSQL(
                "CREATE TABLE inventory_history (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "item_id INTEGER, " +
                        "item_name TEXT NOT NULL, " +
                        "username TEXT NOT NULL, " +
                        "action TEXT NOT NULL, " +
                        "quantity_change INTEGER NOT NULL, " +
                        "event_time TEXT NOT NULL)"
        );

    }

    //added a default user on version 2
    //added histories on version 3

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        if (oldVersion < 2) {
            db.execSQL(
                    "ALTER TABLE users ADD COLUMN " +
                            "can_open_safe INTEGER NOT NULL DEFAULT 0"
            );
        }

        if (oldVersion < 3) {
            db.execSQL(
                    "CREATE TABLE inventory (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "name TEXT NOT NULL COLLATE NOCASE UNIQUE, " +
                            "description TEXT NOT NULL DEFAULT '', " +
                            "quantity INTEGER NOT NULL DEFAULT 0 CHECK(quantity >= 0))"
            );

            db.execSQL(
                    "CREATE TABLE inventory_history (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "item_id INTEGER, " +
                            "item_name TEXT NOT NULL, " +
                            "username TEXT NOT NULL, " +
                            "action TEXT NOT NULL, " +
                            "quantity_change INTEGER NOT NULL, " +
                            "event_time TEXT NOT NULL)"
            );
        }
    }


    public boolean hasUsers() {
        SQLiteDatabase db = getReadableDatabase();

        try (Cursor cursor = db.rawQuery(
                "SELECT id FROM " + USERS_TABLE + " LIMIT 1",
                null)) {
            return cursor.moveToFirst();
        }
    }

    public boolean createUser(String username, String password) {
        username = username.trim();

        if (username.isEmpty() || password.isEmpty()) {
            return false;
        }

        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);

        String saltString = bytesToHex(salt);
        String passwordHash = hashPassword(password, salt);

        if (passwordHash == null) {
            return false;
        }

        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put("username", username);
        values.put("password_hash", passwordHash);
        values.put("password_salt", saltString);

        // The first account becomes the administrator.
        values.put("role", hasUsers() ? "user" : "admin");

        long result = db.insert(USERS_TABLE, null, values);

        return result != -1;
    }

    public boolean validateUser(String username, String password) {
        SQLiteDatabase db = getReadableDatabase();

        try (Cursor cursor = db.query(
                USERS_TABLE,
                new String[]{"password_hash", "password_salt"},
                "username = ?",
                new String[]{username.trim()},
                null, null, null)) {

            if (!cursor.moveToFirst()) {
                return false;
            }

            String storedHash = cursor.getString(0);
            String storedSalt = cursor.getString(1);

            byte[] salt = hexToBytes(storedSalt);
            String enteredHash = hashPassword(password, salt);

            return enteredHash != null &&
                    storedHash.equals(enteredHash);
        }
    }

    private String hashPassword(String password, byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(
                    password.toCharArray(),
                    salt,
                    120000,
                    256
            );

            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

            byte[] hash = factory.generateSecret(spec).getEncoded();

            return bytesToHex(hash);

        } catch (Exception e) {
            return null;
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();

        for (byte b : bytes) {
            result.append(String.format("%02x", b & 0xff));
        }

        return result.toString();
    }

    private byte[] hexToBytes(String hex) {
        byte[] result = new byte[hex.length() / 2];

        for (int i = 0; i < result.length; i++) {
            int index = i * 2;
            result[i] = (byte) Integer.parseInt(
                    hex.substring(index, index + 2), 16);
        }

        return result;
    }


    public boolean isAdmin(String username) {
        SQLiteDatabase db = getReadableDatabase();

        try (Cursor cursor = db.query(
                "users",
                new String[]{"role"},
                "username = ?",
                new String[]{username},
                null, null, null)) {

            return cursor.moveToFirst()
                    && "admin".equals(cursor.getString(0));
        }
    }

    public boolean hasSafeAccess(String username) {
        SQLiteDatabase db = getReadableDatabase();

        try (Cursor cursor = db.query(
                "users",
                new String[]{"can_open_safe"},
                "username = ?",
                new String[]{username},
                null, null, null)) {

            return cursor.moveToFirst() && cursor.getInt(0) == 1;
        }
    }

    public boolean setSafeAccess(int userId, boolean allowed) {
        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("can_open_safe", allowed ? 1 : 0);

        int rowsUpdated = db.update(
                "users",
                values,
                "id = ? AND role != ?",
                new String[]{
                        String.valueOf(userId),
                        "admin"
                }
        );

        return rowsUpdated == 1;
    }

    public Cursor getAllUsers() {
        SQLiteDatabase db = getReadableDatabase();

        return db.query(
                "users",
                new String[]{"id", "username",
                        "role", "can_open_safe"},
                null,
                null,
                null,
                null,
                "username COLLATE NOCASE ASC"
        );
    }


    private String getCurrentTimestamp() {
        return new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()
        ).format(new Date());
    }

    public Cursor getInventory() {
        SQLiteDatabase db = getReadableDatabase();

        return db.query(
                "inventory",
                new String[]{"id", "name", "description", "quantity"},
                null, null, null, null,
                "name COLLATE NOCASE ASC"
        );
    }

    public boolean addInventoryItem(
            String name,
            String description,
            int quantity,
            String username) {

        name = name.trim();
        description = description.trim();

        if (name.isEmpty() || quantity <= 0 ||
                username == null || username.trim().isEmpty()) {
            return false;
        }

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();

        try {
            Cursor cursor = db.query(
                    "inventory",
                    new String[]{"id", "quantity"},
                    "name = ?",
                    new String[]{name},
                    null, null, null
            );

            long itemId;
            String action;

            try {
                if (cursor.moveToFirst()) {
                    itemId = cursor.getLong(0);
                    int oldQuantity = cursor.getInt(1);

                    if (oldQuantity > Integer.MAX_VALUE - quantity) {
                        return false;
                    }

                    ContentValues update = new ContentValues();
                    update.put("quantity", oldQuantity + quantity);

                    // Update the description if a new one was supplied.
                    if (!description.isEmpty()) {
                        update.put("description", description);
                    }

                    db.update(
                            "inventory",
                            update,
                            "id = ?",
                            new String[]{String.valueOf(itemId)}
                    );

                    action = "ADDED";
                } else {
                    ContentValues values = new ContentValues();
                    values.put("name", name);
                    values.put("description", description);
                    values.put("quantity", quantity);

                    itemId = db.insert("inventory", null, values);

                    if (itemId == -1) {
                        return false;
                    }

                    action = "ADDED";
                }
            } finally {
                cursor.close();
            }

            recordInventoryChange(
                    db, itemId, name, username,
                    action, quantity
            );

            db.setTransactionSuccessful();
            return true;

        } catch (Exception e) {
            return false;
        } finally {
            db.endTransaction();
        }
    }

    public boolean removeInventoryItem(
            long itemId, int quantity, String username) {

        if (quantity <= 0 ||
                username == null || username.trim().isEmpty()) {
            return false;
        }

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();

        try {
            Cursor cursor = db.query(
                    "inventory",
                    new String[]{"name", "quantity"},
                    "id = ?",
                    new String[]{String.valueOf(itemId)},
                    null, null, null
            );

            String itemName;
            int oldQuantity;

            try {
                if (!cursor.moveToFirst()) {
                    return false;
                }

                itemName = cursor.getString(0);
                oldQuantity = cursor.getInt(1);
            } finally {
                cursor.close();
            }

            if (quantity > oldQuantity) {
                return false;
            }

            ContentValues values = new ContentValues();
            values.put("quantity", oldQuantity - quantity);

            int rowsUpdated = db.update(
                    "inventory",
                    values,
                    "id = ? AND quantity >= ?",
                    new String[]{
                            String.valueOf(itemId),
                            String.valueOf(quantity)
                    }
            );

            if (rowsUpdated != 1) {
                return false;
            }

            recordInventoryChange(
                    db, itemId, itemName, username,
                    "REMOVED", -quantity
            );

            db.setTransactionSuccessful();
            return true;

        } catch (Exception e) {
            return false;
        } finally {
            db.endTransaction();
        }
    }

    private void recordInventoryChange(
            SQLiteDatabase db,
            long itemId,
            String itemName,
            String username,
            String action,
            int quantityChange) {

        ContentValues history = new ContentValues();
        history.put("item_id", itemId);
        history.put("item_name", itemName);
        history.put("username", username);
        history.put("action", action);
        history.put("quantity_change", quantityChange);
        history.put("event_time", getCurrentTimestamp());

        long result = db.insert(
                "inventory_history", null, history
        );

        if (result == -1) {
            throw new IllegalStateException(
                    "Could not record inventory history."
            );
        }
    }


}