package com.example.safeui.http_handler;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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


    // Field names match the Pi's inventory-history JSON.
    public static class InventoryHistoryItem
    {
        public Integer item_id;
        public String item_name;
        public Integer quantity; // Stock remaining after the event, NOT the amount added/removed.
        public String action;
        public String timestamp;

        // Optional: the current Pi response has no actor. Never substitute the
        // person viewing the screen for the person who performed an old event.
        public String username;

        // Calculated locally from complete history. Null means the change is unknown.
        // A "delete" event deletes an inventory record; it is not a measured stock change.
        public transient Integer quantity_change;

        private static final Pattern TIMESTAMP = Pattern.compile(
                "^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d{1,6}))?(Z|[+-]\\d{2}:\\d{2})$");

        public long getTimestampMillis()
        {
            return Math.floorDiv(getTimestampMicros(), 1000L);
        }

        // Python sends UTC timestamps with up to six decimal places. Keep that precision
        // for sorting, and accept an explicit offset without depending on Android API 26.
        public long getTimestampMicros()
        {
            Matcher match = TIMESTAMP.matcher(timestamp == null ? "" : timestamp);
            if (!match.matches()) throw new IllegalArgumentException("Invalid history timestamp");

            String fraction = match.group(2) == null ? "" : match.group(2);
            String micros = (fraction + "000000").substring(0, 6);
            String value = match.group(1) + "." + micros.substring(0, 3) + match.group(3);
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US);
            format.setLenient(false);
            ParsePosition position = new ParsePosition(0);
            Date date = format.parse(value, position);
            if (date == null || position.getIndex() != value.length())
                throw new IllegalArgumentException("Invalid history timestamp");

            return date.getTime() * 1000L + Integer.parseInt(micros.substring(3));
        }
    }

    // Both history endpoints currently return complete snapshots, oldest first. Sorting
    // also supports callers supplying the same complete snapshot in a different order.
    static List<InventoryHistoryItem> prepareHistory(List<InventoryHistoryItem> items, Integer itemId)
    {
        if (items == null) throw new IllegalArgumentException("Missing history list");
        List<InventoryHistoryItem> result = new ArrayList<>(items);
        for (InventoryHistoryItem item : result)
        {
            if (item == null || item.item_id == null || item.item_id <= 0
                    || item.item_name == null || item.item_name.trim().isEmpty()
                    || item.quantity == null || item.quantity < 0
                    || !("add".equals(item.action) || "remove".equals(item.action) || "delete".equals(item.action)))
                throw new IllegalArgumentException("Invalid inventory history entry");
            if (itemId != null && !itemId.equals(item.item_id))
                throw new IllegalArgumentException("History contains an unexpected item ID");
            item.getTimestampMicros(); // Validate before any cached rows can be replaced.
        }
        result.sort(Comparator.comparingLong(InventoryHistoryItem::getTimestampMicros));

        Map<Integer, Integer> previousQuantities = new HashMap<>();
        for (InventoryHistoryItem item : result)
        {
            Integer previous = previousQuantities.get(item.item_id);
            item.quantity_change = null;
            if ("delete".equals(item.action))
            {
                // The Pi may reuse an item ID after deleting its inventory record.
                previousQuantities.remove(item.item_id);
                continue;
            }
            if (previous == null && "add".equals(item.action)) previous = 0;
            if (previous != null)
            {
                int change = item.quantity - previous;
                if (("add".equals(item.action) && change >= 0)
                        || ("remove".equals(item.action) && change <= 0))
                    item.quantity_change = change;
            }
            previousQuantities.put(item.item_id, item.quantity);
        }
        return result;
    }

    private static int historyInteger(JsonObject object, String field)
    {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Missing or invalid history " + field);
        try
        {
            // Gson's JsonElement integer conversion can truncate fractions. An event
            // count must be an exact integer in the supported Java/SQLite model range.
            return value.getAsBigDecimal().intValueExact();
        }
        catch (ArithmeticException | NumberFormatException error)
        {
            throw new IllegalArgumentException("Invalid history integer: " + field, error);
        }
    }

    private static String historyText(JsonObject object, String field)
    {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Missing or invalid history " + field);
        return value.getAsString();
    }

    private static String optionalHistoryText(JsonObject object, String field)
    {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) return null;
        String text = historyText(object, field).trim();
        return text.isEmpty() ? null : text;
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
//        public User asUser()
//        {
//            if (user == null || !user.isJsonObject())
//            {
//                return null;
//            }
//            return GSON.fromJson(user, user.class);
//
//
//
//        }
//
//        public List<User> asUsers()
//        {
//            if(users = null ||!users.isJsonArray())
//            {
//                return new ArrayList();
//            }
//            return GSON.fromJson(users, new TypeToken<List<User>>(){}.getType());
//        }
//



        public List<InventoryHistoryItem> asInventoryHistoryItems()
        {
            // An empty array is valid. A missing or malformed field is an error, so it
            // must not be mistaken for an empty snapshot that clears the local cache.
            if (history == null || !history.isJsonArray())
                throw new IllegalArgumentException("Missing or invalid history array");
            List<InventoryHistoryItem> items = new ArrayList<>();
            for (JsonElement value : history.getAsJsonArray())
            {
                if (!value.isJsonObject()) throw new IllegalArgumentException("Invalid history entry");
                JsonObject object = value.getAsJsonObject();
                InventoryHistoryItem item = new InventoryHistoryItem();
                item.item_id = historyInteger(object, "item_id");
                item.item_name = historyText(object, "item_name");
                item.quantity = historyInteger(object, "quantity");
                item.action = historyText(object, "action");
                item.timestamp = historyText(object, "timestamp");
                item.username = optionalHistoryText(object, "username");
                items.add(item);
            }
            return prepareHistory(items, null);
        }
    }








}