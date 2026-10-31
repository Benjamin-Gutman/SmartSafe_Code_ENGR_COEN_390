package com.example.safeui;

import android.content.ClipData;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

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
        public JsonElement data;


        // For single item responses
        public InventoryItem asInventoryItem()
        {
            if(data == null || data.isJsonNull())
            {
                return null;
            }

            return GSON.fromJson(data, InventoryItem.class) ;
        }

        // For multiple item responses
        public List<InventoryItem> asInventoryItems()
        {
            if (data == null || data.isJsonNull()) {
                return new java.util.ArrayList<>();
            }
            return GSON.fromJson(data, new TypeToken<List<InventoryItem>>() {}.getType());
        }
    }
}
