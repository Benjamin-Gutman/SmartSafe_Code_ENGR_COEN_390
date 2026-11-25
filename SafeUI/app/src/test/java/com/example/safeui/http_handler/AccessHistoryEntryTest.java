package com.example.safeui.http_handler;

import com.google.gson.JsonParser;
import com.google.gson.Gson;
import org.junit.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class AccessHistoryEntryTest {
    static String event(int userId, String time) {
        return "{\"user_id\":" + userId + ",\"changes_made\":[],\"timestamp\":\"" + time + "\"}";
    }

    private List<AccessHistoryEntry> parse(String json) {
        return AccessHistoryEntry.parse(JsonParser.parseString(json));
    }

    @Test public void acceptsCurrentPiSchemaWithoutAccessIdUsernameOrEvent() {
        AccessHistoryEntry item = parse("[" + event(4, "2026-11-22T18:00:00Z") + "]").get(0);
        assertNull(item.accessId);
        assertEquals("Safe access", item.getEventLabel());
        assertEquals("User #4 (name unavailable)", item.getActorLabel(Collections.emptyMap()));
    }

    @Test public void acceptsAResponseCapturedFromTheCurrentPiCommandHandler() throws Exception {
        try (InputStreamReader reader = new InputStreamReader(getClass().getResourceAsStream(
                "/access_history/pi_contract.json"), StandardCharsets.UTF_8)) {
            Models.Reply reply = new Gson().fromJson(reader, Models.Reply.class);
            assertEquals("getAccessList", reply.command);
            List<AccessHistoryEntry> items = AccessHistoryEntry.parse(reply.access_history);
            assertEquals(1, items.size());
            assertEquals(4, items.get(0).userId);
            assertEquals("Safe access", items.get(0).getEventLabel());
        }
    }

    @Test public void sortsByActualTimeIncludingOffsetsAndMicroseconds() {
        List<AccessHistoryEntry> items = parse("["
                + event(1,"2026-11-22T19:00:00.000001+01:00") + ","
                + event(2,"2026-11-22T18:00:00.000002Z") + ","
                + event(3,"2026-11-22T17:59:59Z") + "]");
        assertEquals(2, items.get(0).userId);
        assertEquals(1, items.get(1).userId);
        assertEquals(3, items.get(2).userId);
    }

    @Test public void usesRecordedActorBeforeCurrentUserNameLookup() {
        AccessHistoryEntry item = parse("[{\"access_id\":7,\"user_id\":4,\"username\":\"Alice\","
                + "\"timestamp\":\"2026-11-22T18:00:00Z\"}]").get(0);
        assertEquals("Alice (ID 4)", item.getActorLabel(Collections.singletonMap(4,"Renamed Alice")));
    }

    @Test public void resolvesActorByIdWithoutUsingTheViewer() {
        AccessHistoryEntry item = parse("[" + event(4,"2026-11-22T18:00:00Z") + "]").get(0);
        assertEquals("Alice (ID 4)", item.getActorLabel(Collections.singletonMap(4,"Alice")));
        assertEquals("User #4 (name unavailable)", item.getActorLabel(Collections.singletonMap(9,"JJ")));
    }

    @Test public void labelsExplicitAlarmsAndUnknownActors() {
        AccessHistoryEntry item = parse("[{\"user_id\":0,\"event\":\"alarm\","
                + "\"timestamp\":\"2026-11-22T18:00:00Z\"}]").get(0);
        assertTrue(item.isAlarm());
        assertEquals("Alarm: unauthorized opening", item.getEventLabel());
        assertEquals("Unknown user", item.getActorLabel(Collections.emptyMap()));
    }

    @Test public void doesNotInventAnAlarmOrPhysicalOpeningForLegacyEntries() {
        AccessHistoryEntry item = parse("[" + event(4,"2026-11-22T18:00:00Z") + "]").get(0);
        assertFalse(item.isAlarm());
        assertEquals("Safe access", item.getEventLabel());
    }

    @Test public void emptyArrayIsAValidEmptySnapshot() {
        assertTrue(parse("[]").isEmpty());
    }

    @Test public void missingOrWrongPayloadIsAnError() {
        assertThrows(IllegalArgumentException.class, () -> AccessHistoryEntry.parse(null));
        for (String value : new String[]{"null", "{}", "[null]", "[1]"})
            assertThrows(IllegalArgumentException.class, () -> parse(value));
    }

    @Test public void rejectsMalformedDatesAndMissingOffsets() {
        for (String time : new String[]{"2026-02-30T18:00:00Z", "2026-11-22T18:00:00", "garbage", ""})
            assertThrows(IllegalArgumentException.class, () -> parse("[" + event(4,time) + "]"));
    }

    @Test public void rejectsFractionalNegativeAndMissingUserIds() {
        for (String id : new String[]{"1.5", "-1", "null", "\"4\""})
            assertThrows(IllegalArgumentException.class, () -> parse("[{\"user_id\":" + id
                    + ",\"timestamp\":\"2026-11-22T18:00:00Z\"}]"));
    }

    @Test public void validatesEveryRowBeforeReturningASnapshot() {
        assertThrows(IllegalArgumentException.class, () -> parse("["
                + event(4,"2026-11-22T18:00:00Z") + ",{}]"));
    }

    @Test public void returnsAnImmutableSnapshot() {
        List<AccessHistoryEntry> items = parse("[" + event(4,"2026-11-22T18:00:00Z") + "]");
        assertThrows(UnsupportedOperationException.class, items::clear);
    }
}
