package com.example.safeui.http_handler;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import com.example.safeui.DatabaseHelper;

import java.util.List;

// Connects DataSync to the existing SQLite database.
// Assumes the inventory table has been changed to use the server's field names:
//   item_id   INTEGER PRIMARY KEY   (the server's id, NOT autoincremented locally)
//   item_name TEXT NOT NULL
//   quantity  INTEGER NOT NULL
// Any other columns (e.g. description) are left untouched on updates.
// Methods run on a background thread.

public class InventoryStore implements DataSync.LocalStore
{
    private static final String TABLE = "inventory";

    private final DatabaseHelper helper;

    public InventoryStore(DatabaseHelper helper)
    {
        this.helper = helper;
    }

    @Override
    public void upsert(Models.InventoryItem item)
    {
        writeItem(helper.getWritableDatabase(), item);
    }

    @Override
    public void remove(int itemId)
    {
        helper.getWritableDatabase().delete(
                TABLE, "item_id = ?", new String[]{ String.valueOf(itemId) });
    }

    @Override
    public void replaceAll(List<Models.InventoryItem> items)
    {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try
        {
            db.delete(TABLE, null, null);
            for (Models.InventoryItem item : items)
            {
                writeItem(db, item);
            }
            db.setTransactionSuccessful();
        }
        finally
        {
            db.endTransaction();
        }
    }

    // Update the row if it exists, otherwise insert it (keeps columns the server doesn't send).
    private void writeItem(SQLiteDatabase db, Models.InventoryItem item)
    {
        ContentValues cv = new ContentValues();
        cv.put("item_name", item.item_name);
        cv.put("quantity", item.quantity);

        int rows = db.update(TABLE, cv, "item_id = ?",
                new String[]{ String.valueOf(item.item_id) });

        if (rows == 0)
        {
            cv.put("item_id", item.item_id);
            db.insertOrThrow(TABLE, null, cv);
        }
    }
}