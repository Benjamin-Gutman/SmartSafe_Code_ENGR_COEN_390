package com.example.safeui.http_handler;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.safeui.DatabaseHelper;

import java.util.ArrayList;
import java.util.List;

// Saves downloaded history and provides cached records to the UI.
public class InventoryHistoryStore implements DataSync.HistoryLocalStore
{
    private static final String TABLE =
            DatabaseHelper.INVENTORY_HISTORY_CACHE;

    private final DatabaseHelper helper;

    public InventoryHistoryStore(DatabaseHelper helper)
    {
        this.helper = helper;
    }

    @Override
    public void replaceAllHistory(List<Models.InventoryHistoryItem> items)
    {
        replaceHistory(items, null);
    }

    @Override
    public void replaceHistoryForItem(
            int itemId,
            List<Models.InventoryHistoryItem> items)
    {
        requireItemId(itemId);
        replaceHistory(items, itemId);
    }

    private void replaceHistory(
            List<Models.InventoryHistoryItem> items,
            Integer itemId)
    {
        List<Models.InventoryHistoryItem> prepared =
                Models.prepareHistory(items, itemId);

        SQLiteDatabase db = helper.getWritableDatabase();

        db.beginTransaction();
        try
        {
            // Replace the requested snapshot to prevent duplicate events.
            String selection = itemId == null ? null : "item_id = ?";
            String[] args = itemId == null
                    ? null
                    : new String[]{String.valueOf(itemId)};

            db.delete(TABLE, selection, args);

            int order = 0;

            for (Models.InventoryHistoryItem item : prepared)
            {
                ContentValues values = new ContentValues();
                values.put("item_id", item.item_id);
                values.put("item_name", item.item_name);
                values.put("quantity", item.quantity);
                values.put("action", item.action);
                values.put("timestamp", item.timestamp);
                values.put("timestamp_micros", item.getTimestampMicros());
                values.put("source_order", order++);
                values.put("quantity_change", item.quantity_change);

                db.insertOrThrow(TABLE, null, values);
            }

            db.setTransactionSuccessful();
        }
        finally
        {
            // A failed write rolls back the whole replacement.
            db.endTransaction();
        }
    }

    public List<Models.InventoryHistoryItem> getAll()
    {
        return getHistory(null, null);
    }

    public List<Models.InventoryHistoryItem> getForItem(int itemId)
    {
        return getHistory(itemId, null);
    }

    // Optional filters. Results are newest first and available offline.
    public List<Models.InventoryHistoryItem> getHistory(
            Integer itemId,
            String action)
    {
        if (itemId != null)
        {
            requireItemId(itemId);
        }

        if (action != null
                && !("add".equals(action)
                || "remove".equals(action)
                || "delete".equals(action)))
        {
            throw new IllegalArgumentException("Unknown history action");
        }

        String selection = null;
        List<String> arguments = new ArrayList<>();

        if (itemId != null)
        {
            selection = "item_id = ?";
            arguments.add(String.valueOf(itemId));
        }

        if (action != null)
        {
            selection = selection == null
                    ? "action = ?"
                    : selection + " AND action = ?";

            arguments.add(action);
        }

        List<Models.InventoryHistoryItem> items = new ArrayList<>();

        try (Cursor cursor = helper.getReadableDatabase().query(
                TABLE,
                new String[]{
                        "item_id",
                        "item_name",
                        "quantity",
                        "action",
                        "timestamp",
                        "quantity_change"
                },
                selection,
                arguments.toArray(new String[0]),
                null,
                null,
                "timestamp_micros DESC, source_order DESC, id DESC"))
        {
            while (cursor.moveToNext())
            {
                Models.InventoryHistoryItem item =
                        new Models.InventoryHistoryItem();

                item.item_id = cursor.getInt(0);
                item.item_name = cursor.getString(1);
                item.quantity = cursor.getInt(2);
                item.action = cursor.getString(3);
                item.timestamp = cursor.getString(4);
                item.quantity_change = cursor.isNull(5)
                        ? null
                        : cursor.getInt(5);

                items.add(item);
            }
        }

        return items;
    }

    private static void requireItemId(int itemId)
    {
        if (itemId <= 0)
        {
            throw new IllegalArgumentException("Item ID must be positive");
        }
    }
}