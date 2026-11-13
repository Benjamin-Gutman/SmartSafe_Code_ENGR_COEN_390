package com.example.safeui.http_handler;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.safeui.DatabaseHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.example.safeui.http_handler.HistoryTestData.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public class InventoryHistoryStoreTest
{
    private DatabaseHelper helper;
    private InventoryHistoryStore store;

    @Before
    public void setUp()
    {
        RuntimeEnvironment.getApplication().deleteDatabase("SafeInventory.db");
        helper = new DatabaseHelper(RuntimeEnvironment.getApplication());
        store = new InventoryHistoryStore(helper);
    }

    @After
    public void tearDown()
    {
        helper.close();
    }

    private List<Models.InventoryHistoryItem> snapshot()
    {
        return Arrays.asList(entry(1, 5, "add", "2026-11-11T18:00:00Z"),
                entry(2, 4, "add", "2026-11-11T18:01:00Z"),
                entry(1, 3, "remove", "2026-11-11T18:02:00Z"));
    }

    @Test
    public void refreshesDoNotDuplicateEventsAndReadsAreNewestFirst()
    {
        store.replaceAllHistory(snapshot());
        store.replaceAllHistory(snapshot());
        List<Models.InventoryHistoryItem> items = store.getAll();
        assertEquals(3, items.size());
        assertEquals("remove", items.get(0).action);
        assertEquals(Integer.valueOf(-2), items.get(0).quantity_change);
        assertEquals(Integer.valueOf(3), items.get(0).quantity);
    }

    @Test
    public void itemRefreshAndEmptyItemRefreshPreserveOtherItems()
    {
        store.replaceAllHistory(snapshot());
        store.replaceHistoryForItem(1, Collections.singletonList(
                entry(1, 9, "add", "2026-11-11T18:03:00Z")));
        assertEquals(2, store.getAll().size());
        assertEquals(Integer.valueOf(9), store.getForItem(1).get(0).quantity);
        store.replaceHistoryForItem(1, Collections.emptyList());
        assertTrue(store.getForItem(1).isEmpty());
        assertEquals(1, store.getForItem(2).size());
    }

    @Test
    public void fullEmptySnapshotClearsHistoryOnly()
    {
        store.replaceAllHistory(snapshot());
        helper.getWritableDatabase().execSQL("INSERT INTO inventory(item_id,item_name,quantity) VALUES(1,'Passport',5)");
        store.replaceAllHistory(Collections.emptyList());
        assertTrue(store.getAll().isEmpty());
        try (Cursor cursor = helper.getInventory()) { assertEquals(1, cursor.getCount()); }
    }

    @Test
    public void rejectedOrFailedReplacementKeepsThePreviousCache()
    {
        store.replaceAllHistory(snapshot());
        assertThrows(IllegalArgumentException.class, () -> store.replaceHistoryForItem(1,
                Collections.singletonList(entry(2, 9, "add", "2026-11-11T18:03:00Z"))));
        assertEquals(3, store.getAll().size());

        SQLiteDatabase db = helper.getWritableDatabase();
        db.execSQL("CREATE TRIGGER reject_history BEFORE INSERT ON inventory_history_cache "
                + "WHEN NEW.item_id = 2 "
                + "BEGIN SELECT RAISE(ABORT, 'simulated write failure'); END");
        assertThrows(RuntimeException.class, () -> store.replaceAllHistory(Arrays.asList(
                entry(1, 9, "add", "2026-11-11T18:03:00Z"),
                entry(2, 9, "add", "2026-11-11T18:04:00Z"))));
        assertEquals(3, store.getAll().size()); // The deletion was rolled back as well.
        assertEquals(Integer.valueOf(3), store.getForItem(1).get(0).quantity);
    }

    @Test
    public void supportsItemAndActionFiltersAndSurvivesInventoryDeletion()
    {
        helper.getWritableDatabase().execSQL("INSERT INTO inventory(item_id,item_name,quantity) VALUES(1,'Passport',5)");
        store.replaceAllHistory(snapshot());
        assertEquals(1, store.getHistory(1, "remove").size());
        assertEquals(2, store.getHistory(null, "add").size());
        assertEquals(1, helper.getWritableDatabase().delete("inventory", "item_id = ?", new String[]{"1"}));
        assertEquals(3, store.getAll().size());
    }

    @Test
    public void cachePersistsWhenTheDatabaseIsReopened()
    {
        store.replaceAllHistory(snapshot());
        helper.close();
        helper = new DatabaseHelper(RuntimeEnvironment.getApplication());
        store = new InventoryHistoryStore(helper);
        assertEquals(3, store.getAll().size());
    }

    @Test
    public void ordersByActualTimeAcrossOffsetsAndRetainsDistinctEvents()
    {
        store.replaceAllHistory(Arrays.asList(
                entry(1, 5, "add", "2026-11-11T18:00:00.123450Z"),
                entry(2, 4, "add", "2026-11-11T13:00:00.123455-05:00"),
                entry(1, 3, "remove", "2026-11-11T18:00:00.123450Z")));
        List<Models.InventoryHistoryItem> items = store.getAll();
        assertEquals(3, items.size());
        assertEquals(Integer.valueOf(2), items.get(0).item_id);
        assertEquals("remove", items.get(1).action);
        assertEquals(Integer.valueOf(-2), items.get(1).quantity_change);
    }

    @Test
    public void repeatedLargerSnapshotsRetainEveryEvent()
    {
        List<Models.InventoryHistoryItem> events = new java.util.ArrayList<>();
        for (int i = 0; i < 500; i++)
            events.add(entry(1, i + 1, "add", String.format(java.util.Locale.US,
                    "2026-11-11T18:00:00.%06dZ", i)));
        store.replaceAllHistory(events);
        store.replaceAllHistory(events);
        assertEquals(500, store.getAll().size());
        assertEquals(Integer.valueOf(500), store.getAll().get(0).quantity);
        assertEquals(Integer.valueOf(1), store.getAll().get(0).quantity_change);
    }

    @Test
    public void versionFourUpgradePreservesAccountsInventoryAndLegacyHistory()
    {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.execSQL("INSERT INTO users(username,password_hash,password_salt) VALUES('JJ','hash','salt')");
        db.execSQL("INSERT INTO inventory(item_id,item_name,quantity) VALUES(1,'Passport',5)");
        db.execSQL("INSERT INTO inventory_history(item_id,item_name,username,action,quantity_change,event_time) "
                + "VALUES(1,'Passport','JJ','add',5,'2026-11-11 18:00:00')");
        db.execSQL("DROP TABLE inventory_history_cache");
        db.setVersion(4);
        helper.close();

        helper = new DatabaseHelper(RuntimeEnvironment.getApplication());
        store = new InventoryHistoryStore(helper);
        assertEquals(5, helper.getReadableDatabase().getVersion());
        assertTrue(helper.hasUsers());
        try (Cursor cursor = helper.getInventory()) { assertEquals(1, cursor.getCount()); }
        try (Cursor cursor = helper.getReadableDatabase().rawQuery("SELECT * FROM inventory_history", null))
        { assertEquals(1, cursor.getCount()); }
        store.replaceAllHistory(snapshot());
        assertEquals(3, store.getAll().size());
    }
}
