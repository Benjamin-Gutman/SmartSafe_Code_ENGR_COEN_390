package com.example.safeui.http_handler;

import android.os.Handler;
import android.os.Looper;

import java.util.List;

public class DataSync
{
    public interface LocalStore
    {
        void upsert(Models.InventoryItem item);            // create, add, remove, update, get
        void remove(int itemId);                           // delete
        void replaceAll(List<Models.InventoryItem> items); // list
    }

    public interface UserLocalStore
    {
        void upsert(Models.User user);                  // create, add, remove, update, get
        void remove(int userId);                        // delete
        void replaceAll(List<Models.User> users);       // list
    }

    public interface Listener
    {
        void onReply(Models.Reply reply);
        void onError(String message);
    }


    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final Listener listener;
    private final LocalStore store;
    private final UserLocalStore userStore;

    public DataSync(Listener listener, LocalStore store, UserLocalStore userStore)
    {
        this.listener = listener;
        this.store = store;
        this.userStore = userStore;
    }


    //will forward these constructors to the main one with all of args. 
    public DataSync(Listener listener)
    {
        this(listener, null, null);
    }

    public DataSync(Listener listener, LocalStore store)
    {
        this(listener,store,null);
    }


    // ---- Commands (call from the UI; one at a time) ----
    
    // Inventory

    public void refreshAll()
    {
        run(Commands.listInventory());
    }

    public void create(String name, int quantity)
    {
        run(Commands.createInventoryItem(name, quantity));
    }

    public void update(int itemId, String name, int quantity)
    {
        run(Commands.editInventoryItem(itemId, name, quantity));
    }

    public void increment(int itemId, int count)
    {
        run(Commands.incrementInventoryItem(itemId, count));
    }

    public void decrement(int itemId, int count)
    {
        run(Commands.decrementInventoryItem(itemId, count));
    }

    public void fetch(int itemId)
    {
        run(Commands.getByID(itemId));
    }

    public void delete(int itemId)
    {
        run(Commands.deleteInventoryItem(itemId));
    }

    //User, will always have USER in it.

    public void createUser(String username, String email)
    {
        run(Commands.createUser(username,email));
    }

    public void getUserByID(int user_id)
    {
        run(Commands.getUserByID(user_id));
    }

    public void getUserByUsername(String username)
    {
        run(Commands.getUserByUsername(username));
    }

    public void listUsers()
    {
        run(Commands.listUsers());
    }

    public void updateUserInfo(int user_id, String username, String email, boolean isAdmin, boolean hasOpenDoorPrivilege, String password)
    {
        run(Commands.updateUserInfo(user_id,username,email,isAdmin, hasOpenDoorPrivilege,password));
    }

    public void updatePassword(int user_id, String new_password)
    {
        run(Commands.updatePassword(user_id, new_password));
    }

    public void updateUserPrivileges(int user_id, boolean isAdmin, boolean hasOpenDoorPrivilege)
    {
        run(Commands.updateUserPrivileges(user_id,isAdmin,hasOpenDoorPrivilege));
    }

    public void verifyUser(int user_id, String password)
    {
        run(Commands.verifyUser(user_id,password));
    }

    // User Access History related commands

    public void getAccessHistory(int access_id)
    {
        run(Commands.getAccessHistory(access_id));
    }

    public void listAccessHistory()
    {
        run(Commands.listAccessHistory());
    }

    public void getAccessHistoryByUser(int user_id)
    {
        run(Commands.getAccessHistoryByUser(user_id));
    }

    // ---- Internals ----

    private void run(String commandLine)
    {
        android.util.Log.d("SafeUI", "Sending: " + commandLine);

        ClientSocket.get().send(commandLine, new ClientSocket.ReplyCallback()
        {
            @Override
            public void onReply(Models.Reply reply)
            {
                if (!reply.isOk())
                {
                    String msg = reply.message != null ? reply.message : "Server error";
                    MAIN.post(() -> listener.onError(msg));
                    return;
                }
                apply(reply);
                MAIN.post(() -> listener.onReply(reply));
            }

            @Override
            public void onFailure(Throwable error)
            {
                android.util.Log.e("SafeUI", "Command failed: " + commandLine, error);
                String detail = error.getMessage() != null ? error.getMessage() : "Could not reach server";
                MAIN.post(() -> listener.onError(detail));
            }
        });
    }

    // Forwards a successful reply to LocalStore/LocalUserStore, if one is connected.
    private void apply(Models.Reply reply)
    {
        String command = reply.command == null ? "" : reply.command;
        switch (command)
        {
            //inventory related stuff
            case "list":
                if (store != null){
                    store.replaceAll(reply.asInventoryItems());
                }
                break;
            case "create":
            case "add":
            case "remove":
            case "update":
            case "get":
                if (store != null){
                    Models.InventoryItem item = reply.asInventoryItem();
                    if (item != null) store.upsert(item);
                }
                 break;
            case "delete":
                    if (store !=null && reply.item_id != null) 
                    {
                        {store.remove(reply.item_id);}
                    }
                break;
            //users related stuff  
            case "listUsers":
                    if( userStore !=null){
                        userStore.replaceAll(reply.asUsers());
                    }
                break;

            case "createUser":
            case "updateUserInfo":
            case "getUser":
                if( userStore !=null){
                    Models.User user = reply.asUser();
                    if (user != null)
                    {
                        userStore.upsert(user);
                    }
                }
                break;

            case "deleteUser":

                if (userStore !=null && reply.user_id != null)
                {
                    userStore.remove(reply.user_id);
                }
                    
                break;

            default:
                break;
        }
    }

}