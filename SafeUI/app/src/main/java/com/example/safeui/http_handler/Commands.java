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
    //Inventory commands
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

    // User commands
    public static String createUser(String username, String email)
    {
        
        return "createUser --name " + username + " --email " + email;
      
    }

    public static String getUserByID(int user_id)
    {
        return "getUser " + user_id;
    }

    public static String getUserByUsername(String username)
    {
        return "getUserByUsername " + username;
    }

    public static String listUsers()
    {
        return "listUsers";
    }

    public static String updateUserInfo(int user_id, String username, String email, boolean isAdmin, boolean hasOpenDoorprivilege, String password)
    {
        return "updateUserInfo --userID "+ user_id+ " --username " + username + " --email " + email + " --isAdmin " +isAdmin + " --hasOpenDoorprivilege " + hasOpenDoorprivilege + " --password " +password;
    }

    public static String updatePassword(int user_id, String new_password)
    {
        return "updateUserPassword --userID " + user_id+ " --newpassword " + new_password;
    }

    public static String updateUserPrivilege(int user_id, boolean isAdmin, boolean hasOpenDoorPrivilege)
    {
        return "updateUserPrivilege --userID " + user_id + " --isAdmin " + isAdmin + "--hasOpenDoorPrivilege " + hasOpenDoorPrivilege;
    }

    public static String deleteUser(int user_id)
    {
        return "deleteUser --userID " +user_id;
    }

    //Not exactly sure how the return process will work for this to verify login. 
    public static String verifyUser( int user_id, String password)
    {
        return "verifyUser --userID " + user_id + " --username " + username + " --password " + password;
    }

    // User Access History related commands

    public static String createAccess(int user_id, String changes_made, String timestamp)
    {
        return "createAccess --userID " + user_id + " --changes_made " + changes_made + " --timestamp " + timestamp;
    }

    public static String getAccessHistory(int access_id)
    {
        return "getAccessHistory --access_id " + access_id;
    }

    public static String listAccessHistory()
    {
        return "listAccessHistory";
    }

    public static String updateAccessHistory(int access_id, int user_id, String changes_made, String timestamp)
    {
        return "updateAccessHistory --access " + access_id + " --user_id " + user_id + " --changes_made "+ changes_made+ " --timestamp " + timestamp;
    }

    public static String getAccessHistoryByUser(int user_id)
    {
        return "getAccessHistoryByUser --userID " + user_id;
    }

    public static String deleteAccessHistory(int access_id)
    {
        return "deleteAccessHistory --userID " + access_id;
    }

    public static Static isLocked()
    {
        return "isLocked";
    }






}