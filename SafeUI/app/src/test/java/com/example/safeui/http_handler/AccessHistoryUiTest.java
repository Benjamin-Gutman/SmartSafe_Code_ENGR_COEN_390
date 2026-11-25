package com.example.safeui.http_handler;

import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.safeui.R;
import com.example.safeui.activities.SafeActivity;
import com.google.gson.Gson;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
public class AccessHistoryUiTest {
    private ActivityController<TestSafeActivity> lifecycle;
    private TestSafeActivity activity;
    private LinearLayout rows;

    @Before public void setUp() {
        Intent intent = new Intent().putExtra("username", "JJ").putExtra("user_id", 9);
        lifecycle = Robolectric.buildActivity(TestSafeActivity.class, intent).setup();
        activity = lifecycle.get();
        rows = activity.findViewById(R.id.layoutSafeHistory);
    }

    @After public void tearDown() {
        if (lifecycle != null) lifecycle.pause().stop().destroy();
    }

    private void reply(String json) {
        activity.sender.callback.onReply(new Gson().fromJson(json, Models.Reply.class));
    }

    private void permissions(boolean admin) {
        reply("{\"status\":\"ok\",\"command\":\"getUserByUsername\",\"user\":{"
                + "\"user_id\":9,\"username\":\"JJ\",\"isAdmin\":" + admin + "}}");
    }

    private void names() {
        reply("{\"status\":\"ok\",\"command\":\"listUsers\",\"users\":["
                + "{\"user_id\":4,\"username\":\"Alice\"}]}");
    }

    private void history(String records) {
        reply("{\"status\":\"ok\",\"command\":\"getAccessList\",\"access_history\":" + records + "}");
    }

    @Test public void inflatesTheRealManageSafeLayoutAndDisplaysNewestRecordsFirst() {
        permissions(true); names();
        history("[" + AccessHistoryEntryTest.event(4,"2026-11-22T18:00:00Z") + ","
                + AccessHistoryEntryTest.event(5,"2026-11-22T19:00:00Z") + "]");
        assertEquals(View.VISIBLE, activity.findViewById(R.id.layoutSafeHistorySection).getVisibility());
        assertEquals(2,rows.getChildCount());
        assertEquals("User: User #5 (name unavailable)",
                ((TextView)rows.getChildAt(0).findViewById(R.id.textAccessUser)).getText().toString());
        assertEquals("User: Alice (ID 4)",
                ((TextView)rows.getChildAt(1).findViewById(R.id.textAccessUser)).getText().toString());
        assertNotNull(activity.findViewById(R.id.buttonOpenSafe));
        assertNotNull(activity.findViewById(R.id.safeAlertBanner));
    }

    @Test public void ordinaryUsersSeeNoHistoryAndSendOnlyAnAccountLookup() {
        permissions(false);
        assertEquals(View.GONE,activity.findViewById(R.id.layoutSafeHistorySection).getVisibility());
        assertEquals(0,rows.getChildCount());
        assertEquals(Arrays.asList("getUserByUsername --username JJ"),activity.sender.commands);
    }

    @Test public void emptyHistoryShowsAnExplicitEmptyState() {
        permissions(true); names(); history("[]");
        assertEquals(0,rows.getChildCount());
        assertTrue(((TextView)activity.findViewById(R.id.textSafeHistoryStatus)).getText()
                .toString().contains("No access history"));
        assertTrue(((Button)activity.findViewById(R.id.buttonRefreshSafeHistory)).isEnabled());
    }

    @Test public void refreshFailureKeepsTheExistingVisibleRecord() {
        permissions(true); names();
        history("[" + AccessHistoryEntryTest.event(4,"2026-11-22T18:00:00Z") + "]");
        activity.findViewById(R.id.buttonRefreshSafeHistory).performClick();
        permissions(true); names();
        activity.sender.callback.onError("offline");
        assertEquals(1,rows.getChildCount());
        assertTrue(((TextView)activity.findViewById(R.id.textSafeHistoryStatus)).getText()
                .toString().contains("previously loaded"));
    }

    @Test public void leavingDuringLoadingIgnoresLateReplies() {
        AccessHistoryController.Callback pending = activity.sender.callback;
        lifecycle.pause();
        pending.onReply(new Gson().fromJson("{\"status\":\"ok\",\"command\":\"getUserByUsername\","
                + "\"user\":{\"user_id\":9,\"username\":\"JJ\",\"isAdmin\":true}}",Models.Reply.class));
        assertEquals(1,activity.sender.commands.size());
        assertEquals(0,rows.getChildCount());
        lifecycle.resume();
    }

    public static class TestSafeActivity extends SafeActivity {
        final FakeSender sender = new FakeSender();

        @Override protected AccessHistoryController createAccessHistoryController() {
            return new AccessHistoryController(getIntent().getStringExtra("username"),
                    getIntent().getIntExtra("user_id",-1),sender,this::renderAccessHistory);
        }
    }

    private static class FakeSender implements AccessHistoryController.Sender {
        final List<String> commands = new ArrayList<>();
        AccessHistoryController.Callback callback;
        @Override public void send(String command, AccessHistoryController.Callback callback) {
            commands.add(command);
            this.callback = callback;
        }
    }
}
