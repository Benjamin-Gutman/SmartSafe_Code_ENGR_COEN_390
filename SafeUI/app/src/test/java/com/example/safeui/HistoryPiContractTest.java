package com.example.safeui.http_handler;

import com.example.safeui.DatabaseHelper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static com.example.safeui.http_handler.HistoryTestData.entry;
import static org.junit.Assert.*;
import static org.robolectric.shadows.ShadowLooper.idleMainLooper;

// These responses were captured from the unmodified main-branch Pi server over
// a real local WebSocket, using a temporary database. No live Pi is needed here.
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
public class HistoryPiContractTest
{
    private DatabaseHelper helper;
    private InventoryHistoryStore store;
    private DataSync sync;
    private int replies;

    @Before
    public void setUp() throws Exception
    {
        RuntimeEnvironment.getApplication().deleteDatabase("SafeInventory.db");
        helper = new DatabaseHelper(RuntimeEnvironment.getApplication());
        store = new InventoryHistoryStore(helper);
        JsonObject fixture;
        try (InputStreamReader reader = new InputStreamReader(
                getClass().getResourceAsStream("/inventory_history/pi_contract.json"), StandardCharsets.UTF_8))
        {
            fixture = JsonParser.parseReader(reader).getAsJsonObject();
        }
        DataSync.Listener listener = new DataSync.Listener()
        {
            public void onReply(Models.Reply reply) { replies++; }
            public void onError(String message) { fail(message); }
        };
        sync = new DataSync(listener, null, store, (command, callback) -> {
            String key;
            if ("history".equals(command)) key = "all_history";
            else if ("history --item-id 1".equals(command)) key = "item_history";
            else if ("history --item-id 2".equals(command)) key = "zero_item_history";
            else if ("history --item-id 9999".equals(command)) key = "empty_history";
            else throw new IllegalArgumentException("Unexpected command: " + command);
            callback.onReply(new Gson().fromJson(fixture.get(key), Models.Reply.class));
        });
    }

    @After
    public void tearDown()
    {
        helper.close();
    }

    @Test
    public void piResponsesPassThroughDataSyncAndPersistWithoutDuplicatesOrDataLoss()
    {
        sync.refreshHistory();
        assertEquals(8, store.getAll().size());
        sync.refreshHistory();
        assertEquals(8, store.getAll().size());
        sync.refreshHistoryForItem(1);
        assertEquals(8, store.getAll().size());
        assertEquals(3, store.getForItem(2).size());

        List<Models.InventoryHistoryItem> itemHistory = store.getForItem(1);
        assertEquals(5, itemHistory.size());
        assertEquals("delete", itemHistory.get(0).action);
        assertNull(itemHistory.get(0).quantity_change);
        assertEquals(Integer.valueOf(2), itemHistory.get(1).quantity_change);
        assertEquals(Integer.valueOf(-3), itemHistory.get(2).quantity_change);
        assertEquals(Integer.valueOf(4), itemHistory.get(2).quantity);

        // Simulate an older cached entry that the next authoritative reply removes.
        store.replaceHistoryForItem(9999, Collections.singletonList(
                entry(9999, 1, "add", "2026-11-11T00:00:00Z")));
        sync.refreshHistoryForItem(9999);
        assertTrue(store.getForItem(9999).isEmpty());
        assertEquals(8, store.getAll().size());
        idleMainLooper();
        assertEquals(4, replies);
    }

    @Test
    public void completePiHistoryIncludesZeroStockRemovalAndDeletion()
    {
        sync.refreshHistory();
        sync.refreshHistoryForItem(2);
        List<Models.InventoryHistoryItem> events = store.getForItem(2);
        assertEquals(3, events.size());
        assertEquals("delete", events.get(0).action);
        assertEquals("remove", events.get(1).action);
        assertEquals(Integer.valueOf(0), events.get(1).quantity);
        assertEquals(Integer.valueOf(-4), events.get(1).quantity_change);
        assertEquals("Coins", events.get(1).item_name);
        assertNotNull(events.get(1).timestamp);
        assertEquals(5, store.getForItem(1).size());
        idleMainLooper();
        assertEquals(2, replies);
    }
}
