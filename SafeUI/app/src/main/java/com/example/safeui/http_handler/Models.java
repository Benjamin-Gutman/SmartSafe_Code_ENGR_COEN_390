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

    public static class User
    {
        public String username;
        public String email;
        public boolean isAdmin;
        public boolean hasOpenDoorPrivilege;
        public int user_id;
        public String hashed_password; // not sure if i need to include this here
    }


    // For response received from server.
    public static class Reply
    {
        public String status;
        public String command;
        public String message; // For error messages
        public JsonElement inventory;
        public JsonElement history;
        public JsonElement users;
        public Integer item_id; // Not sure why its here, but will keep just in case for now
    

        public boolean isOk()
        {
            return "ok".equals(status);
        }

        // For when inventory JSON contains only one item
        public InventoryItem asInventoryItem()
        {
            if (inventory == null || !inventory.isJsonObject())
            {
                return null;
            }
            return GSON.fromJson(inventory, InventoryItem.class);
        }

        // For inventory JSON containing list of items
        public List<InventoryItem> asInventoryItems()
        {
            if (inventory == null || !inventory.isJsonArray())
            {
                return new ArrayList<>();
            }
            return GSON.fromJson(inventory, new TypeToken<List<InventoryItem>>(){}.getType());
        }

        //Will do like inventory where option to return 1 user and then multiple users
        public User asUser()
        {
            if (user == null || !user.isJsonObject())
            {
                return null;
            }
            return GSON.fromJson(user, user.class);



        }

        public List<User> asUsers()
        {
            if(users = null ||!users.isJsonArray())
            {
                return new ArrayList();
            }
            return GSON.fromJson(users, new TypeToken<List<User>>(){}.getType());
        }



    }

    






}
