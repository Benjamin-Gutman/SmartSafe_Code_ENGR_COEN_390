package com.example.safeui.http_handler;

import android.content.Context;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.safeui.DatabaseHelper;
import com.example.safeui.activities.InventoryActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowToast;
import org.robolectric.util.ReflectionHelpers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static com.example.safeui.http_handler.HistoryTestData.*;
import static org.junit.Assert.*;
import static org.robolectric.shadows.ShadowLooper.idleMainLooper;

// Exercise the Activity's real callbacks and Android TextViews with controlled
// replies. XML inflation, navigation, and a live Pi are separate device checks.
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
public class InventoryHistoryUiTest
{
    private InventoryActivity activity;
    private DatabaseHelper helper;
    private InventoryHistoryStore store;
    private LinearLayout rows;
    private FakeSender sender;
    private DataSync sync;
    private ExecutorService reader;

    @Before
    public void setUp()
    {
        Context context = RuntimeEnvironment.getApplication();
        context.deleteDatabase("SafeInventory.db");
        helper = new DatabaseHelper(context);
        store = new InventoryHistoryStore(helper);

        // Keep resource inflation out of this callback test. The production
        // onCreate() is checked by building/running the complete Android app.
        activity = new InventoryActivity();
        ReflectionHelpers.setField(activity, "mBase", context);
        rows = new LinearLayout(context);
        ReflectionHelpers.setField(activity, "layoutInventoryItems", rows);
        ReflectionHelpers.setField(activity, "buttonAddItem", new Button(context));
        ReflectionHelpers.setField(activity, "textInventoryTitle", new TextView(context));
        ReflectionHelpers.setField(activity, "textInventorySubtitle", new TextView(context));
        ReflectionHelpers.setField(activity, "databaseHelper", helper);
        ReflectionHelpers.setField(activity, "historyStore", store);
        sender = new FakeSender();
        sync = new DataSync(activity, new InventoryStore(helper), store, sender);
        ReflectionHelpers.setField(activity, "sync", sync);
        reader = ReflectionHelpers.getField(activity, "historyReader");
        ShadowToast.reset();
    }

    @After
    public void tearDown() throws Exception
    {
        ReflectionHelpers.setField(activity, "destroyed", true);
        reader.shutdown();
        assertTrue(reader.awaitTermination(5, TimeUnit.SECONDS));
        helper.close();
    }

    private void startRefresh(boolean history)
    {
        ReflectionHelpers.setField(activity, "showingHistory", history);
        ReflectionHelpers.setField(activity, history
                ? "historyRefreshPending" : "inventoryRefreshPending", true);
        ReflectionHelpers.callInstanceMethod(activity, "sendNextRefresh");
    }

    private void drainUi() throws Exception
    {
        idleMainLooper();
        reader.submit(() -> {}).get(5, TimeUnit.SECONDS);
        idleMainLooper();
    }

    @Test
    public void startupRefreshesInventoryThenHistoryWithoutARefreshLoop() throws Exception
    {
        ReflectionHelpers.setField(activity, "inventoryRefreshPending", true);
        ReflectionHelpers.setField(activity, "historyRefreshPending", true);
        ReflectionHelpers.callInstanceMethod(activity, "sendNextRefresh");
        assertEquals(Collections.singletonList("list"), sender.commands);

        sender.callback.onReply(json(
                "{\"status\":\"ok\",\"command\":\"list\",\"inventory\":[]}"));
        drainUi();
        assertEquals(Arrays.asList("list", "history"), sender.commands);

        sender.callback.onReply(reply(Collections.emptyList()));
        drainUi();
        assertEquals(2, sender.commands.size());
        assertFalse(ReflectionHelpers.<Boolean>getField(activity, "requestInFlight"));
    }

    @Test
    public void switchingToHistoryQueuesItsRequestBehindTheCurrentCommand() throws Exception
    {
        startRefresh(false);
        startRefresh(true);
        assertEquals(Collections.singletonList("list"), sender.commands);

        sender.callback.onReply(json(
                "{\"status\":\"ok\",\"command\":\"list\",\"inventory\":[]}"));
        drainUi();
        assertEquals(Arrays.asList("list", "history"), sender.commands);
    }

    @Test
    public void historyShowsRemainingStockAndKeepsDeletedItems() throws Exception
    {
        startRefresh(true);
        sender.callback.onReply(reply(Arrays.asList(
                entry(1, 5, "add", "2026-11-11T18:00:00Z"),
                entry(1, 3, "remove", "2026-11-11T18:01:00Z"),
                entry(1, 3, "delete", "2026-11-11T18:02:00Z"))));
        drainUi();

        assertEquals(3, rows.getChildCount());
        String deletion = ((TextView) rows.getChildAt(0)).getText().toString();
        String removal = ((TextView) rows.getChildAt(1)).getText().toString();
        assertTrue(deletion.contains("Action: Deleted"));
        assertTrue(deletion.contains("Last recorded quantity: 3"));
        assertTrue(removal.contains("Action: Removed"));
        assertTrue(removal.contains("Remaining quantity: 3"));
        assertTrue(removal.contains("User: Unknown"));
        assertEquals(Collections.singletonList("history"), sender.commands);
    }

    @Test
    public void failedHistoryRefreshStillDisplaysTheSavedRecords() throws Exception
    {
        store.replaceAllHistory(Collections.singletonList(
                entry(1, 3, "remove", "2026-11-11T18:01:00Z")));
        startRefresh(true);
        sender.callback.onFailure(new IOException("Offline"));
        drainUi();

        assertEquals(1, store.getAll().size());
        assertEquals(1, rows.getChildCount());
        assertTrue(((TextView) rows.getChildAt(0)).getText().toString()
                .contains("Remaining quantity: 3"));
        assertEquals("Offline", ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void deletionConfirmsSuccessOnlyAfterTheReplyAndThenRefreshesHistory() throws Exception
    {
        Boolean accepted = ReflectionHelpers.callInstanceMethod(
                activity, "beginInventoryRequest",
                ReflectionHelpers.ClassParameter.from(String.class, "Passport permanently deleted."));
        assertTrue(accepted);
        sync.delete(1);
        assertNull(ShadowToast.getTextOfLatestToast());

        sender.callback.onReply(json(
                "{\"status\":\"ok\",\"command\":\"delete\",\"item_id\":1}"));
        drainUi();

        assertEquals("Passport permanently deleted.", ShadowToast.getTextOfLatestToast());
        assertEquals(Arrays.asList("delete 1", "history"), sender.commands);
    }

    @Test
    public void historyDisplaysTheRecordedActorInsteadOfTheViewer() throws Exception
    {
        activity.setIntent(new android.content.Intent().putExtra("username", "JJ"));
        Models.InventoryHistoryItem event = entry(1, 3, "remove", "2026-11-11T18:01:00Z");
        event.username = "Alice";
        startRefresh(true);
        sender.callback.onReply(reply(Collections.singletonList(event)));
        drainUi();
        String text = ((TextView) rows.getChildAt(0)).getText().toString();
        assertTrue(text.contains("User: Alice"));
        assertFalse(text.contains("User: JJ"));
    }

    @Test
    public void legacyHistoryRemainsVisibleWithItsUserAndOriginalQuantityMeaning() throws Exception
    {
        helper.getWritableDatabase().execSQL("INSERT INTO inventory_history "
                + "(item_id,item_name,username,action,quantity_change,event_time) "
                + "VALUES(1,'Passport','JJ','REMOVED',2,'2026-11-11 18:00:00')");
        startRefresh(true);
        sender.callback.onReply(reply(Collections.emptyList()));
        drainUi();
        assertEquals(2, rows.getChildCount());
        assertEquals("Previous records on this phone",
                ((TextView) rows.getChildAt(0)).getText().toString());
        String text = ((TextView) rows.getChildAt(1)).getText().toString();
        assertTrue(text.contains("User: JJ"));
        assertTrue(text.contains("Action: Removed"));
        assertTrue(text.contains("Recorded amount: 2"));
        assertTrue(text.contains("Remaining quantity: Unknown"));
        assertFalse(text.contains("Remaining quantity: 2"));
    }

    @Test
    public void addingQuantityUpdatesTheExistingItemAndThenRefreshesItsHistory() throws Exception
    {
        helper.getWritableDatabase().execSQL(
                "INSERT INTO inventory(item_id,item_name,quantity) VALUES(1,'Passport',5)");
        ReflectionHelpers.callInstanceMethod(activity, "beginInventoryRequest",
                ReflectionHelpers.ClassParameter.from(String.class, "Quantity added."));
        sync.increment(1, 2);
        assertEquals(Collections.singletonList("add 1 2"), sender.commands);

        sender.callback.onReply(json(
                "{\"status\":\"ok\",\"command\":\"add\",\"inventory\":"
                        + "{\"item_id\":1,\"item_name\":\"Passport\",\"quantity\":7}}"));
        drainUi();
        assertEquals(Arrays.asList("add 1 2", "history"), sender.commands);
        try (android.database.Cursor cursor = helper.getInventory())
        {
            assertTrue(cursor.moveToFirst());
            assertEquals(7, cursor.getInt(cursor.getColumnIndexOrThrow("quantity")));
        }

        sender.callback.onReply(reply(Arrays.asList(
                entry(1, 5, "add", "2026-11-11T18:00:00Z"),
                entry(1, 7, "add", "2026-11-11T18:01:00Z"))));
        drainUi();
        assertEquals(2, store.getAll().size());
        assertEquals(Integer.valueOf(7), store.getAll().get(0).quantity);
        assertEquals(Integer.valueOf(2), store.getAll().get(0).quantity_change);
        assertEquals(2, sender.commands.size());
    }

    private static class FakeSender implements DataSync.CommandSender
    {
        final List<String> commands = new ArrayList<>();
        ClientSocket.ReplyCallback callback;

        public void send(String command, ClientSocket.ReplyCallback callback)
        {
            commands.add(command);
            this.callback = callback;
        }
    }
}