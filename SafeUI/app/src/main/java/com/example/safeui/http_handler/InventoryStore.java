package com.example.safeui.http_handler;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import com.example.safeui.DatabaseHelper;

import java.util.List;

// Saves current inventory items in the phone's SQLite database.
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
                TABLE,
                "item_id = ?",
                new String[]{String.valueOf(itemId)});
    }

    @Override
    public void replaceAll(List<Models.InventoryItem> items)
    {
        SQLiteDatabase db = helper.getWritableDatabase();

        // Replace the inventory within one transaction.
        // If a write fails, the previous inventory is preserved.
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

    private void writeItem(SQLiteDatabase db, Models.InventoryItem item)
    {
        ContentValues values = new ContentValues();
        values.put("item_name", item.item_name);
        values.put("quantity", item.quantity);

        // Update the item if it already exists.
        int rows = db.update(
                TABLE,
                values,
                "item_id = ?",
                new String[]{String.valueOf(item.item_id)});

        // Otherwise, insert it using the Pi's item ID.
        if (rows == 0)
        {
            values.put("item_id", item.item_id);
            db.insertOrThrow(TABLE, null, values);
        }
    }
}