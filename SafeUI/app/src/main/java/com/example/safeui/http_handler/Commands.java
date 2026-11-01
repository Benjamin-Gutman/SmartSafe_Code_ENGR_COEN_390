package com.example.safeui.http_handler;

// Generates string request commands for sending.
// The server parses these with shlex.split + argparse, so the syntax must match exactly.
public class Commands
{
    private Commands() {}

    private static String quote(String s)
    {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public static String createInventoryItem(String item_name, int quantity)
    {
        return "create --name " + quote(item_name) + " --quantity " + quantity;
    }

    public static String editInventoryItem(int item_id, String item_name, int quantity)
    {
        return "update " + item_id + " --name " + quote(item_name) + " --quantity " + quantity;
    }

    public static String incrementInventoryItem(int item_id, int inc_count)
    {
        return "add " + item_id + " " + inc_count;
    }

    public static String decrementInventoryItem(int item_id, int dec_count)
    {
        return "remove " + item_id + " " + dec_count;
    }

    public static String getByID(int item_id)
    {
        return "get " + item_id;
    }

    public static String deleteInventoryItem(int item_id)
    {
        return "delete " + item_id;
    }

    public static String listInventory()
    {
        return "list";
    }

    public static String history()
    {
        return "history";
    }

    public static String historyForItem(int item_id)
    {
        return "history --item-id " + item_id;
    }
}