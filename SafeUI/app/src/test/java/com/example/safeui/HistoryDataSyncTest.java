package com.example.safeui.http_handler;

import android.os.Looper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.example.safeui.http_handler.HistoryTestData.*;
import static org.junit.Assert.*;
import static org.robolectric.shadows.ShadowLooper.idleMainLooper;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
public class HistoryDataSyncTest
{
    private FakeSender sender;
    private RecordingStore store;
    private RecordingListener listener;
    private DataSync sync;

    @Before
    public void setUp()
    {
        sender = new FakeSender();
        store = new RecordingStore();
        listener = new RecordingListener();
        sync = new DataSync(listener, null, store, sender);
    }

    @Test
    public void fullRefreshWritesTheStoreBeforeNotifyingTheUi()
    {
        sync.refreshHistory();
        assertEquals("history", sender.command);
        sender.callback.onReply(reply(Collections.singletonList(
                entry(1, 5, "add", "2026-11-11T18:00:00Z"))));
        assertEquals(1, store.fullWrites);
        assertEquals(0, listener.replies); // Listener runs through the main-thread queue.
        idleMainLooper();
        assertEquals(1, listener.replies);
        assertTrue(listener.onMainThread);
    }

    @Test
    public void emptyItemRefreshRetainsTheOriginalFilter()
    {
        sync.refreshHistoryForItem(42);
        assertEquals("history --item-id 42", sender.command);
        sender.callback.onReply(reply(Collections.emptyList()));
        idleMainLooper();
        assertEquals(Integer.valueOf(42), store.itemId);
        assertTrue(store.items.isEmpty());
        assertEquals(0, store.fullWrites);
        assertEquals(1, listener.replies);
    }

    @Test
    public void backgroundRepliesNotifyTheListenerOnTheMainThread() throws Exception
    {
        sync.refreshHistory();
        Thread serverThread = new Thread(() -> sender.callback.onReply(reply(Collections.emptyList())));
        serverThread.start();
        serverThread.join(5000);
        assertFalse(serverThread.isAlive());
        assertEquals(0, listener.replies);
        idleMainLooper();
        assertEquals(1, listener.replies);
        assertTrue(listener.onMainThread);
    }

    @Test
    public void malformedWrongScopeAndUnexpectedRepliesDoNotWriteTheCache()
    {
        for (Models.Reply response : new Models.Reply[]{
                json("{\"status\":\"ok\",\"command\":\"history\"}"),
                json("{\"status\":\"ok\",\"command\":\"list\",\"history\":[]}"),
                json("{\"status\":\"ok\",\"command\":\"history\",\"history\":[{\"item_id\":1,"
                        + "\"item_name\":\"Passport\",\"quantity\":1.5,\"action\":\"add\","
                        + "\"timestamp\":\"2026-11-11T18:00:00Z\"}]}"),
                reply(Collections.singletonList(entry(2, 1, "add", "2026-11-11T18:00:00Z")))})
        {
            sync.refreshHistoryForItem(1);
            sender.callback.onReply(response);
            idleMainLooper();
        }
        assertEquals(4, listener.errors.size());
        assertEquals(0, listener.replies);
        assertNull(store.items);
    }

    @Test
    public void serverAndNetworkErrorsDoNotWriteTheCache()
    {
        sync.refreshHistory();
        sender.callback.onReply(json("{\"status\":\"error\",\"command\":\"history\",\"message\":\"Unavailable\"}"));
        sync.refreshHistory();
        sender.callback.onFailure(new IOException("Offline"));
        idleMainLooper();
        assertEquals(2, listener.errors.size());
        assertEquals("Unavailable", listener.errors.get(0));
        assertEquals("Offline", listener.errors.get(1));
        assertEquals(0, listener.replies);
        assertNull(store.items);
    }

    @Test
    public void databaseFailuresNotifyErrorInsteadOfSuccess()
    {
        store.failWrites = true;
        sync.refreshHistory();
        sender.callback.onReply(reply(Collections.emptyList()));
        idleMainLooper();
        assertEquals(1, listener.errors.size());
        assertEquals(0, listener.replies);
    }

    @Test
    public void invalidItemIdsAreRejectedBeforeSending()
    {
        sync.refreshHistoryForItem(0);
        idleMainLooper();
        assertNull(sender.command);
        assertEquals(1, listener.errors.size());
    }

    @Test
    public void callersCanReceiveHistoryWithoutConnectingAStore()
    {
        sync = new DataSync(listener, null, null, sender);
        sync.refreshHistory();
        sender.callback.onReply(reply(Collections.emptyList()));
        idleMainLooper();
        assertEquals(1, listener.replies);
    }

    @Test
    public void existingInventoryRequestsStillUseTheInventoryStore()
    {
        final List<Models.InventoryItem> inventory = new ArrayList<>();
        DataSync.LocalStore inventoryStore = new DataSync.LocalStore()
        {
            public void upsert(Models.InventoryItem item)
            {
                inventory.removeIf(existing -> existing.item_id == item.item_id);
                inventory.add(item);
            }
            public void remove(int itemId) { inventory.removeIf(item -> item.item_id == itemId); }
            public void replaceAll(List<Models.InventoryItem> items) { inventory.clear(); inventory.addAll(items); }
        };
        sync = new DataSync(listener, inventoryStore, store, sender);
        sync.refreshAll();
        assertEquals("list", sender.command);
        sender.callback.onReply(json("{\"status\":\"ok\",\"command\":\"list\",\"inventory\":["
                + "{\"item_id\":1,\"item_name\":\"Passport\",\"quantity\":5}]}"));
        sync.decrement(1, 2);
        assertEquals("remove 1 2", sender.command);
        sender.callback.onReply(json("{\"status\":\"ok\",\"command\":\"remove\",\"inventory\":"
                + "{\"item_id\":1,\"item_name\":\"Passport\",\"quantity\":3}}"));
        assertEquals(3, inventory.get(0).quantity);
        sync.delete(1);
        assertEquals("delete 1", sender.command);
        sender.callback.onReply(json("{\"status\":\"ok\",\"command\":\"delete\",\"item_id\":1}"));
        idleMainLooper();
        assertTrue(inventory.isEmpty());
        assertEquals(3, listener.replies);
        assertEquals(0, store.fullWrites);
    }

    private static class FakeSender implements DataSync.CommandSender
    {
        String command;
        ClientSocket.ReplyCallback callback;
        public void send(String command, ClientSocket.ReplyCallback callback)
        {
            this.command = command;
            this.callback = callback;
        }
    }

    private static class RecordingStore implements DataSync.HistoryLocalStore
    {
        int fullWrites;
        Integer itemId;
        List<Models.InventoryHistoryItem> items;
        boolean failWrites;

        public void replaceAllHistory(List<Models.InventoryHistoryItem> items)
        {
            if (failWrites) throw new IllegalStateException("Database unavailable");
            fullWrites++;
            this.items = items;
        }

        public void replaceHistoryForItem(int itemId, List<Models.InventoryHistoryItem> items)
        {
            this.itemId = itemId;
            this.items = items;
        }
    }

    private static class RecordingListener implements DataSync.Listener
    {
        int replies;
        boolean onMainThread;
        final List<String> errors = new ArrayList<>();
        public void onReply(Models.Reply reply)
        {
            replies++;
            onMainThread = Looper.myLooper() == Looper.getMainLooper();
        }
        public void onError(String message) { errors.add(message); }
    }
}
