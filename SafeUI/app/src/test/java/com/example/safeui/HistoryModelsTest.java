package com.example.safeui.http_handler;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.example.safeui.http_handler.HistoryTestData.*;
import static org.junit.Assert.*;

public class HistoryModelsTest
{
    private static final String START = "2026-11-11T18:00:00Z";

    @Test
    public void readsTheExistingPiResponseWithoutAUsernameOrHistoryId()
    {
        Models.Reply response = json(
                "{\"status\":\"ok\",\"command\":\"history\",\"history\":["
                        + "{\"item_id\":3,\"item_name\":\"Passport\",\"quantity\":5,"
                        + "\"action\":\"add\",\"timestamp\":\"" + START + "\"}]}");

        Models.InventoryHistoryItem item =
                response.asInventoryHistoryItems().get(0);

        assertEquals(Integer.valueOf(3), item.item_id);
        assertEquals("Passport", item.item_name);
        assertEquals(Integer.valueOf(5), item.quantity);
        assertEquals(START, item.timestamp);
    }

    @Test
    public void calculatesSignedChangesWithoutChangingRemainingStock()
    {
        List<Models.InventoryHistoryItem> items = reply(Arrays.asList(
                entry(1, 5, "add", START),
                entry(1, 7, "add", "2026-11-11T18:01:00Z"),
                entry(2, 4, "add", "2026-11-11T18:01:30Z"),
                entry(1, 4, "remove", "2026-11-11T18:02:00Z")
        )).asInventoryHistoryItems();

        assertEquals(Integer.valueOf(5), items.get(0).quantity_change);
        assertEquals(Integer.valueOf(2), items.get(1).quantity_change);
        assertEquals(Integer.valueOf(4), items.get(2).quantity_change);
        assertEquals(Integer.valueOf(-3), items.get(3).quantity_change);

        // Remaining stock is separate from the quantity removed.
        assertEquals(Integer.valueOf(4), items.get(3).quantity);
    }

    @Test
    public void sortsSnapshotsBeforeCalculatingChanges()
    {
        List<Models.InventoryHistoryItem> items = reply(Arrays.asList(
                entry(1, 3, "remove", "2026-11-11T18:01:00Z"),
                entry(1, 5, "add", START)
        )).asInventoryHistoryItems();

        assertEquals(Integer.valueOf(-2), items.get(1).quantity_change);
    }

    @Test
    public void keepsUnknownChangesNullAndResetsAfterDeletion()
    {
        List<Models.InventoryHistoryItem> items = reply(Arrays.asList(
                entry(1, 3, "remove", START),
                entry(1, 3, "delete", "2026-11-11T18:01:00Z"),
                entry(1, 8, "add", "2026-11-11T18:02:00Z")
        )).asInventoryHistoryItems();

        assertNull(items.get(0).quantity_change);
        assertNull(items.get(1).quantity_change);
        assertEquals(Integer.valueOf(8), items.get(2).quantity_change);
    }

    @Test
    public void acceptsAnEmptyArrayButRejectsMissingOrNonArrayHistory()
    {
        assertTrue(reply(Collections.emptyList())
                .asInventoryHistoryItems().isEmpty());

        for (String payload : Arrays.asList(
                "{}",
                "{\"history\":null}",
                "{\"history\":{}}"))
        {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> json(payload).asInventoryHistoryItems());
        }
    }

    @Test
    public void rejectsIncompleteOrInvalidEntries()
    {
        Models.InventoryHistoryItem missingQuantity =
                entry(1, 1, "add", START);

        missingQuantity.quantity = null;

        for (Models.InventoryHistoryItem item : Arrays.asList(
                null,
                missingQuantity,
                entry(0, 1, "add", START),
                entry(1, -1, "remove", START),
                entry(1, 1, "unknown", START)))
        {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> reply(Collections.singletonList(item))
                            .asInventoryHistoryItems());
        }
    }

    @Test
    public void parsesOffsetsAndPreservesMicrosecondPrecision()
    {
        Models.InventoryHistoryItem utc =
                entry(1, 1, "add", "2026-11-11T18:00:00.123456Z");

        Models.InventoryHistoryItem offset =
                entry(1, 1, "add", "2026-11-11T13:00:00.123456-05:00");

        assertEquals(
                utc.getTimestampMicros(),
                offset.getTimestampMicros());

        assertEquals(
                456L,
                Math.floorMod(utc.getTimestampMicros(), 1000L));

        assertEquals(
                123L,
                Math.floorMod(utc.getTimestampMillis(), 1000L));

        assertEquals(
                100000L,
                entry(1, 1, "add", "2026-11-11T18:00:00.1Z")
                        .getTimestampMicros()
                        - entry(1, 1, "add", START).getTimestampMicros());
    }

    @Test
    public void rejectsInvalidDatesInsteadOfReplacingThemWithTheCurrentTime()
    {
        for (String timestamp : Arrays.asList(
                "2026-02-30T18:00:00Z",
                "yesterday",
                "2026-11-11 18:00:00",
                null))
        {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> entry(1, 1, "add", timestamp)
                            .getTimestampMicros());
        }
    }

    @Test
    public void historyCommandsKeepTheServerSyntax()
    {
        assertEquals("history", Commands.history());
        assertEquals("history --item-id 3", Commands.historyForItem(3));
    }

    @Test
    public void aRemovalToZeroStillPreservesTheEventAndRemainingValue()
    {
        List<Models.InventoryHistoryItem> items = reply(Arrays.asList(
                entry(1, 5, "add", START),
                entry(1, 0, "remove", "2026-11-11T18:01:00Z"),
                entry(1, 0, "delete", "2026-11-11T18:02:00Z")
        )).asInventoryHistoryItems();

        assertEquals(3, items.size());
        assertEquals(Integer.valueOf(0), items.get(1).quantity);
        assertEquals(Integer.valueOf(-5), items.get(1).quantity_change);
        assertEquals("delete", items.get(2).action);
    }

    @Test
    public void rejectsFractionalQuantitiesInsteadOfTruncatingHistory()
    {
        Models.Reply response = json(
                "{\"status\":\"ok\",\"command\":\"history\",\"history\":["
                        + "{\"item_id\":1,\"item_name\":\"Passport\",\"quantity\":1.5,"
                        + "\"action\":\"add\",\"timestamp\":\"" + START + "\"}]}");

        assertThrows(
                RuntimeException.class,
                response::asInventoryHistoryItems);
    }

    @Test
    public void historyKeepsAnActorWhenTheServerSuppliesOne()
    {
        Models.InventoryHistoryItem event = entry(1, 5, "add", START);
        event.username = "Alice";
        assertEquals("Alice", reply(Collections.singletonList(event))
                .asInventoryHistoryItems().get(0).username);
    }

    @Test
    public void missingNullAndBlankActorsRemainUnknown()
    {
        assertNull(reply(Collections.singletonList(entry(1, 5, "add", START)))
                .asInventoryHistoryItems().get(0).username);
        String template = "{\"history\":[{\"item_id\":1,\"item_name\":\"Passport\","
                + "\"quantity\":5,\"action\":\"add\",\"timestamp\":\"" + START
                + "\",\"username\":%s}]}";
        for (String actor : Arrays.asList("null", "\"\"", "\"   \""))
            assertNull(json(String.format(template, actor))
                    .asInventoryHistoryItems().get(0).username);
    }

    @Test
    public void malformedActorIsRejectedInsteadOfTurnedIntoAName()
    {
        String template = "{\"history\":[{\"item_id\":1,\"item_name\":\"Passport\","
                + "\"quantity\":5,\"action\":\"add\",\"timestamp\":\"" + START
                + "\",\"username\":%s}]}";
        for (String actor : Arrays.asList("7", "true", "[]", "{}"))
            assertThrows(IllegalArgumentException.class,
                    () -> json(String.format(template, actor)).asInventoryHistoryItems());
    }

    @Test
    public void rejectsWrongFieldTypesAndOutOfRangeNumbers()
    {
        String template =
                "{\"history\":[{\"item_id\":1,\"item_name\":\"Passport\","
                        + "\"quantity\":5,\"action\":\"add\","
                        + "\"timestamp\":\"" + START + "\"}]}";

        for (String bad : Arrays.asList(
                template.replace(
                        "\"quantity\":5", "\"quantity\":2147483648"),
                template.replace(
                        "\"item_id\":1", "\"item_id\":1.5"),
                template.replace(
                        "\"quantity\":5", "\"quantity\":\"5\""),
                template.replace(
                        "\"item_name\":\"Passport\"", "\"item_name\":123"),
                template.replace(
                        "\"action\":\"add\"", "\"action\":true")))
        {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> json(bad).asInventoryHistoryItems());
        }
    }
}