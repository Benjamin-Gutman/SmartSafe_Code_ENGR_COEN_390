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

    public interface Listener
    {
        void onReply(Models.Reply reply);
        void onError(String message);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final Listener listener;
    private final LocalStore store;

    public DataSync(Listener listener)
    {
        this(listener, null);
    }

    public DataSync(Listener listener, LocalStore store)
    {
        this.listener = listener;
        this.store = store;
    }

    // ---- Commands (call from the UI; one at a time) ----

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