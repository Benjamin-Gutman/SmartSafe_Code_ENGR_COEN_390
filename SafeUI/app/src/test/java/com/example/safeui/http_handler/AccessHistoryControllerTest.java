package com.example.safeui.http_handler;

import com.google.gson.Gson;
import org.junit.Before;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class AccessHistoryControllerTest {
    private final Gson gson = new Gson();
    private FakeSender sender;
    private RecordingListener listener;
    private AccessHistoryController controller;

    @Before public void setUp() {
        sender = new FakeSender();
        listener = new RecordingListener();
        controller = new AccessHistoryController("JJ", 9, sender, listener);
    }

    private void reply(String json) {
        sender.callback.onReply(gson.fromJson(json, Models.Reply.class));
    }

    private void user(boolean admin, boolean canOpen) {
        reply("{\"status\":\"ok\",\"command\":\"getUserByUsername\",\"user\":{"
                + "\"user_id\":9,\"username\":\"JJ\",\"isAdmin\":" + admin
                + ",\"hasOpenDoorPrivilege\":" + canOpen + "}}");
    }

    private void users() {
        reply("{\"status\":\"ok\",\"command\":\"listUsers\",\"users\":["
                + "{\"user_id\":4,\"username\":\"Alice\"},{\"user_id\":9,\"username\":\"JJ\"}]}");
    }

    private void history(String data) {
        reply("{\"status\":\"ok\",\"command\":\"getAccessList\",\"access_history\":" + data + "}");
    }

    private void loadAdminHistory() {
        controller.refresh(); user(true,true); users();
        history("[" + AccessHistoryEntryTest.event(4,"2026-11-22T18:00:00Z") + "]");
    }

    @Test public void checksRoleThenLoadsNamesThenTheCorrectHistoryCommand() {
        loadAdminHistory();
        assertEquals(Arrays.asList("getUserByUsername --username JJ", "listUsers", "getAccessList"), sender.commands);
        assertTrue(controller.canViewHistory());
        assertFalse(controller.isBusy());
        assertEquals("Alice (ID 4)", controller.getHistory().get(0).getActorLabel(controller.getUsers()));
    }

    @Test public void ordinaryUsersNeverRequestNamesOrHistory() {
        controller.refresh(); user(false,true);
        assertEquals(1,sender.commands.size());
        assertFalse(controller.canViewHistory());
    }

    @Test public void adminWithoutDoorPrivilegeCanStillViewHistory() {
        controller.refresh(); user(true,false); users(); history("[]");
        assertTrue(controller.canViewHistory());
        assertEquals(3,sender.commands.size());
    }

    @Test public void missingUserIdentitySendsNoCommands() {
        controller = new AccessHistoryController(null,-1,sender,listener);
        controller.refresh();
        assertTrue(sender.commands.isEmpty());
        assertFalse(controller.canViewHistory());
    }

    @Test public void mismatchedUserIdentityCannotGrantAdminAccess() {
        controller.refresh();
        reply("{\"status\":\"ok\",\"command\":\"getUserByUsername\",\"user\":{"
                + "\"user_id\":4,\"username\":\"Alice\",\"isAdmin\":true}}");
        assertFalse(controller.canViewHistory());
        assertFalse(controller.isBusy());
        assertEquals(1,sender.commands.size());
    }

    @Test public void failedNameLookupStillShowsHistoryWithActorIds() {
        controller.refresh(); user(true,true);
        sender.callback.onError("offline");
        assertEquals("getAccessList",sender.commands.get(2));
        history("[" + AccessHistoryEntryTest.event(4,"2026-11-22T18:00:00Z") + "]");
        assertEquals("User #4 (name unavailable)",controller.getHistory().get(0).getActorLabel(controller.getUsers()));
    }

    @Test public void malformedSnapshotPreservesPreviouslyLoadedRecords() {
        loadAdminHistory();
        List<AccessHistoryEntry> before = controller.getHistory();
        controller.refresh(); user(true,true); users();
        history("[{}]");
        assertSame(before,controller.getHistory());
        assertTrue(controller.getHistoryStatus().contains("previously loaded"));
        assertFalse(controller.isBusy());
    }

    @Test public void networkFailurePreservesPreviouslyLoadedRecords() {
        loadAdminHistory();
        controller.refresh(); user(true,true); users();
        sender.callback.onError("offline");
        assertEquals(1,controller.getHistory().size());
        assertFalse(controller.isBusy());
    }

    @Test public void emptyHistoryIsDifferentFromARequestFailure() {
        controller.refresh(); user(true,true); users(); history("[]");
        assertTrue(controller.getHistory().isEmpty());
        assertTrue(controller.getHistoryStatus().contains("No access history"));
    }

    @Test public void disconnectedPermissionRecheckPreservesAlreadyVisibleRecords() {
        loadAdminHistory();
        controller.refresh();
        sender.callback.onError("offline");
        assertEquals(1,controller.getHistory().size());
        assertTrue(controller.canViewHistory());
        assertTrue(controller.getHistoryStatus().contains("previously loaded"));
        assertFalse(controller.isBusy());
        assertEquals(4,sender.commands.size());
    }

    @Test public void serverAccountRejectionClearsPreviouslyLoadedRecords() {
        loadAdminHistory();
        controller.refresh();
        reply("{\"status\":\"error\",\"command\":\"getUserByUsername\",\"message\":\"User not found\"}");
        assertFalse(controller.canViewHistory());
        assertTrue(controller.getHistory().isEmpty());
    }

    @Test public void historyRefreshNeverSendsWritesOrDoorCommands() {
        loadAdminHistory();
        for (String command : sender.commands) {
            assertTrue(command.startsWith("getUserByUsername ")
                    || command.equals("listUsers") || command.equals("getAccessList"));
        }
    }

    @Test public void refreshesAreQueuedWithoutOverlappingCommands() {
        controller.refresh(); controller.refresh();
        assertEquals(1,sender.commands.size());
        user(true,true); users(); history("[]");
        assertEquals(4,sender.commands.size());
        assertEquals("getUserByUsername --username JJ", sender.commands.get(3));
    }

    @Test public void roleRevocationClearsThePreviousAdminView() {
        loadAdminHistory();
        controller.refresh(); user(false,true);
        assertTrue(controller.getHistory().isEmpty());
        assertFalse(controller.canViewHistory());
        assertEquals(4,sender.commands.size());
    }

    @Test public void stoppingTheScreenIgnoresLateReplies() {
        controller.refresh(); controller.stop(); user(true,true);
        assertEquals(1,sender.commands.size());
        assertFalse(controller.canViewHistory());
    }

    @Test public void unexpectedRepliesFailWithoutRequestingHistory() {
        controller.refresh();
        reply("{\"status\":\"ok\",\"command\":\"getStatus\",\"doorStatus\":{}}");
        assertFalse(controller.canViewHistory());
        assertFalse(controller.isBusy());
        assertEquals(1,sender.commands.size());
    }

    @Test public void duplicateCallbacksCannotSendDuplicateRequests() {
        controller.refresh();
        AccessHistoryController.Callback previous = sender.callback;
        user(true,true);
        previous.onReply(gson.fromJson("{\"status\":\"ok\",\"command\":\"getUserByUsername\"}",Models.Reply.class));
        assertEquals(2,sender.commands.size());
    }

    private static class FakeSender implements AccessHistoryController.Sender {
        final List<String> commands = new ArrayList<>();
        AccessHistoryController.Callback callback;
        public void send(String command, AccessHistoryController.Callback callback) {
            commands.add(command);
            this.callback = callback;
        }
    }

    private static class RecordingListener implements AccessHistoryController.Listener {
        public void onChanged() {}
    }
}
