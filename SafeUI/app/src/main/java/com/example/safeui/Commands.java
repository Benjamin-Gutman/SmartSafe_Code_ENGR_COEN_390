package com.example.safeui;

// Generates string request commands for sending
public class Commands
{
    private Commands() {}


    // Overloaded create InventoryItem methods
    public static String createInventoryItem(String item_name, int quantity)
    {
        return "create --name " + "\"" + item_name + "\"" + " --quantity " + quantity;
    }
    public static String createInventoryItem(String item_name)
    {
        return "create --name " + "\"" + item_name + "\"";
    }
    public static String createInventoryItem(int quantity)
    {
        return "create --quantity " + quantity;
    }

    // Overloaded edit InventoryItem methods
    public static String editInventoryItem(int item_id, String item_name, int quantity)
    {
        return "update " + item_id + " --name \"" + item_name + "\" --quantity " + quantity;
    }
    public static String editInventoryItem(int item_id, String item_name)
    {
        return "update " + item_id + " --name \"" + item_name + "\"";
    }
    public static String editInventoryItem(int item_id, int quantity)
    {
        return "update " + item_id + " --quantity " + quantity;
    }

    public static String incrementInventoryItem(int item_id, int inc_count)
    {
        return "add " + item_id + " " + inc_count;
    }
    public static String decrementInventoryItem(int item_id, int inc_count)
    {
        return "remove " + item_id + " " + inc_count;
    }
    public static String GetByID(int item_id)
    {
        return "get " + item_id;
    }
    public static String deleteInventoryItem(int item_id)
    {
        return "delete " + item_id;
    }
}
