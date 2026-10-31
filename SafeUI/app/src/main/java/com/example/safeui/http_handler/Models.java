package com.example.safeui.http_handler;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

// Implements packages to store received JSON data into java objects. //
// Consider all forms of responses from the server //
public class Models
{
    private Models() {}

    private static final Gson GSON = new Gson();

    public static class InventoryItem
    {
        public int item_id;
        public String item_name;
        public int quantity;
    }

    public static class Reply
    {
        public String status;
        public String command;
        public String message;
        public JsonElement inventory;
        public JsonElement history;
        public Integer item_id;

        public boolean isOk()
        {
            return "ok".equals(status);
        }

        public InventoryItem asInventoryItem()
        {
            if (inventory == null || !inventory.isJsonObject())
            {
                return null;
            }
            return GSON.fromJson(inventory, InventoryItem.class);
        }

        public List<InventoryItem> asInventoryItems()
        {
            if (inventory == null || !inventory.isJsonArray())
            {
                return new ArrayList<>();
            }
            return GSON.fromJson(inventory, new TypeToken<List<InventoryItem>>(){}.getType());
        }
    }
}
