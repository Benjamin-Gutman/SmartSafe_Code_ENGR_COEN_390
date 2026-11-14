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

    public DataSync(Listener listener)
    {
        this(listener, null);
    }

    public DataSync(Listener listener, LocalStore store)
    {
        this(listener, store, null);
    }

    public DataSync(Listener listener, LocalStore store, HistoryLocalStore historyStore)
    {
        this(listener, store, historyStore, ClientSocket.get()::send);
    }

    DataSync(Listener listener, LocalStore store, HistoryLocalStore historyStore, CommandSender sender)
    {
        if (listener == null || sender == null)
            throw new IllegalArgumentException("A listener and command sender are required");
        this.listener = listener;
        this.store = store;
        this.historyStore = historyStore;
        this.sender = sender;
    }

    // ---- Commands (call from the UI; one at a time) ----

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
        if (store == null) return;

        String command = reply.command == null ? "" : reply.command;
        switch (command)
        {
            case "list":
                store.replaceAll(reply.asInventoryItems());
                break;

            case "create":
            case "add":
            case "remove":
            case "update":
            case "get":
                Models.InventoryItem item = reply.asInventoryItem();
                if (item != null) store.upsert(item);
                break;

            case "delete":
                if (reply.item_id != null) store.remove(reply.item_id);
                break;

            default:
                break;
        }
    }
}
