package com.example.safeui.http_handler;

import com.google.gson.Gson;

import java.util.List;

final class HistoryTestData
{
    private static final Gson GSON = new Gson();

    private HistoryTestData() {}

    static Models.InventoryHistoryItem entry(int itemId, int quantity, String action, String timestamp)
    {
        Models.InventoryHistoryItem item = new Models.InventoryHistoryItem();
        item.item_id = itemId;
        item.item_name = "Item " + itemId;
        item.quantity = quantity;
        item.action = action;
        item.timestamp = timestamp;
        return item;
    }

    static Models.Reply reply(List<Models.InventoryHistoryItem> entries)
    {
        Models.Reply reply = new Models.Reply();
        reply.status = "ok";
        reply.command = "history";
        reply.history = GSON.toJsonTree(entries);
        return reply;
    }

    static Models.Reply json(String json)
    {
        return GSON.fromJson(json, Models.Reply.class);
    }
}
