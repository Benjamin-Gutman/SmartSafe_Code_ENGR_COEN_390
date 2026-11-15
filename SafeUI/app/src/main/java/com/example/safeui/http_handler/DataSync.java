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

    // Kept separate because inventory items and history events have different models.
    public interface HistoryLocalStore
    {
        void replaceAllHistory(List<Models.InventoryHistoryItem> items);
        void replaceHistoryForItem(int itemId, List<Models.InventoryHistoryItem> items);
    }

    public interface UserLocalStore {
        void upsert(Models.User user);
        void remove(int userID);
        void replaceAll(List<Models.User> users);
    }

    // Allows tests to supply replies without connecting to the team's Pi.
    interface CommandSender
    {
        void send(String command, ClientSocket.ReplyCallback callback);
    }

    public interface Listener
    {
        void onReply(Models.Reply reply);
        void onError(String message);
    }


    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final Listener listener;
    private final LocalStore store;
    private final HistoryLocalStore historyStore;
    private final CommandSender sender;
    private final UserLocalStore userStore;

    //will forward these constructors to the main one with all of args. 
    public DataSync(Listener listener)
    {
        this(listener, null, null);
    }

    public DataSync(Listener listener, LocalStore store)
    {
        this(listener, store, null);
    }

    public DataSync(Listener listener, LocalStore store, HistoryLocalStore historyStore)
    {
        this(listener, store, historyStore, null);
    }

    //second constructor for userStore
    public DataSync (Listener listener, LocalStore store, HistoryLocalStore historyStore, UserLocalStore userStore){
        this(listener, store, historyStore, userStore, ClientSocket.get()::send);
    }

    DataSync(Listener listener, LocalStore store, HistoryLocalStore historyStore, UserLocalStore userStore, CommandSender sender)
    {
        if (listener == null || sender == null)
            throw new IllegalArgumentException("A listener and command sender are required");
        this.listener = listener;
        this.store = store;
        this.historyStore = historyStore;
        this.userStore = userStore;
        this.sender = sender;
    }


    // ---- Commands (call from the UI; one at a time) ----
    
    // Inventory

    public void refreshAll()
    {
        run(Commands.listInventory());
    }

    public void refreshHistory()
    {
        run(Commands.history(), true, null);
    }

    public void refreshHistoryForItem(int itemId)
    {
        if (itemId <= 0)
        {
            MAIN.post(() -> listener.onError("Item ID must be positive"));
            return;
        }
        run(Commands.historyForItem(itemId), true, itemId);
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

    public void createUser(String username, String email, String password)
    {
        run(Commands.createUser(username,email, password));
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

    public void verifyUser(String username, String password)
    {
        run(Commands.verifyUser(username,password));
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
        run(commandLine, false, null);
    }

    private void run(String commandLine, boolean historyRequest, Integer historyItemId)
    {
        android.util.Log.d("SafeUI", "Sending: " + commandLine);

        sender.send(commandLine, new ClientSocket.ReplyCallback()
        {
            @Override
            public void onReply(Models.Reply reply)
            {
                if (reply == null || !reply.isOk())
                {
                    String msg = reply != null && reply.message != null ? reply.message : "Server error";
                    MAIN.post(() -> listener.onError(msg));
                    return;
                }
                try
                {
                    if (historyRequest)
                        applyHistory(reply, historyItemId);
                    else
                        apply(reply);
                }
                catch (RuntimeException error)
                {
                    android.util.Log.e("SafeUI", "Could not save server reply", error);
                    MAIN.post(() -> listener.onError("Could not update local data: " + error.getMessage()));
                    return;
                }
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

    private void applyHistory(Models.Reply reply, Integer itemId)
    {
        if (!"history".equals(reply.command))
            throw new IllegalArgumentException("Unexpected reply to history request");
        List<Models.InventoryHistoryItem> items = Models.prepareHistory(reply.asInventoryHistoryItems(), itemId);
        if (historyStore == null) return; // A caller may choose to use reply data without caching.

        // Keep the original request's item ID: an empty filtered reply has no entries
        // from which to recover it, and the server does not echo the filter.
        if (itemId == null)
            historyStore.replaceAllHistory(items);
        else
            historyStore.replaceHistoryForItem(itemId, items);
    }

    // Forwards a successful reply to LocalStore, if one is connected.
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
            case "updateUserPrivileges":
            case "getUserByUsername":
            case "updatePassword":
            case "getUserByID":
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
