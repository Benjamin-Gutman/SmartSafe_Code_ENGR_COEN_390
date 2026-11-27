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
    public static String createInventoryItem(String item_name, int quantity, String item_barcode, int user_id)
    {
        return "create --name " + quote(item_name) + " --quantity " + quantity + " --barcode " + item_barcode + " --user_id " + user_id;
    }

    public static String editInventoryItem(int item_id, String item_name, int quantity, String item_barcode, int user_id)
    {
        return "update " + item_id + " --name " + quote(item_name) + " --quantity " + quantity + " --barcode " + item_barcode + " --user_id " + user_id;
    }

    public static String incrementInventoryItem(int item_id, int inc_count, int user_id)
    {
        return "add " + item_id + " " + inc_count+ " "  + user_id;
    }

    public static String decrementInventoryItem(int item_id, int dec_count, int user_id)
    {
        return "remove " + item_id + " " + dec_count + " " + user_id;
    }

    public static String getByID(int item_id)
    {
        return "get " + item_id;
    }

    public static String getItemByBarcode(String item_barcode)
    {
        return "getItemByBarcode " + item_barcode;
    }

    public static String deleteInventoryItem(int item_id, int user_id)
    {
        return "delete " + item_id + "" + user_id;
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

    // ---------------------------User commands------------------------------
   public static String createUser(String username, String email,  String password)
   {
      // will by default give no admin nor door privilege
       //CHANGE HERE TO TRUE FOR TESTING IF YOU WANT
        return "createUser --name " + username + " --email " + email + " --password " + password + " --isAdmin false --hasOpenDoorPrivilege false" ;
       
   }

    public static String getUserByID(int user_id)
    {
        return "getUserByID " + user_id;
    }

    public static String getUserByUsername(String username)
    {
        return "getUserByUsername --username " + username;
    }

    public static String listUsers()
    {
        return "listUsers";
    }

    public static String updateUserInfo(int user_id, String username, String email, boolean isAdmin, boolean hasOpenDoorprivilege, String password)
    {
        return "updateUserInfo --user_id "+ user_id+ " --username " + username + " --email " + email + " --isAdmin " +isAdmin + " --hasOpenDoorprivilege " + hasOpenDoorprivilege + " --password " +password;
    }

    public static String updatePassword(int user_id, String new_password)
    {
        return "updatePassword --user_id " + user_id+ " --newpassword " + new_password;
    }

    public static String updateUserPrivileges(int user_id, boolean isAdmin, boolean hasOpenDoorPrivilege)
    {
        return "updateUserPrivileges --user_id " + user_id + " --isAdmin " + isAdmin + " --hasOpenDoorPrivilege " + hasOpenDoorPrivilege;
    }

    public static String deleteUser(int user_id)
    {
        return "deleteUser --user_id " +user_id;
    }

    
   public static String verifyUser( String username, String password)
   {
       return "verifyUser --username " + username + " --password " + password;
   }

    // User Access History related commands

    public static String createAccess(int user_id, String changes_made, String timestamp)
    {
        return "createAccess --user_id " + user_id + " --changes_made " + changes_made + " --timestamp " + timestamp;
    }

    public static String getAccessHistory(int access_id)
    {
        return "getAccessHistory --access_id " + access_id;
    }

    public static String listAccessHistory()
    {
        return "getAccessList";
    }

    public static String updateAccessHistory(int access_id, int user_id, String changes_made, String timestamp)
    {
        return "updateAccessHistory --access_id " + access_id + " --user_id " + user_id + " --changes_made "+ changes_made+ " --timestamp " + timestamp;
    }

    public static String getAccessHistoryByUser(int user_id)
    {
        return "getAccessHistoryByUser --user_id " + user_id;
    }

    public static String deleteAccessHistory(int access_id)
    {
        return "deleteAccessHistory --access_id " + access_id;
    }

    public static String isLocked()
    {
        return "isLocked";
    }

//-----------Safe Commands ------------------
    public static String unlockDoor(int user_id){
        return "unlockDoor --user_id " + user_id;
    }

    public static String getStatus() {
        return "getStatus";
    }
}
