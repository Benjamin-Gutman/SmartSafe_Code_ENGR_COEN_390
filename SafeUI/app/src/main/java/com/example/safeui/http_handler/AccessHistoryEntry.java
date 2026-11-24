package com.example.safeui.http_handler;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AccessHistoryEntry {
    private static final Pattern TIMESTAMP = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d{1,6}))?(Z|[+-]\\d{2}:\\d{2})$");

    public final Integer accessId;
    public final int userId;
    public final String username;
    public final String event;
    public final String timestamp;
    private final long timestampMicros;

    private AccessHistoryEntry(JsonObject value) {
        accessId = optionalInteger(value, "access_id");
        Integer actor = optionalInteger(value, "user_id");
        if (actor == null || actor < 0 || (accessId != null && accessId <= 0))
            throw new IllegalArgumentException("Invalid access-history ID");
        userId = actor;
        username = optionalText(value, "username");
        String eventName = optionalText(value, "event");
        // The current Pi schema has no event field. Do not invent a door-open
        // event for those older records or confuse changes_made with a door event.
        event = eventName == null ? "access" : eventName;
        timestamp = optionalText(value, "timestamp");
        timestampMicros = parseTimestamp(timestamp);
    }

    public static List<AccessHistoryEntry> parse(JsonElement payload) {
        if (payload == null || !payload.isJsonArray())
            throw new IllegalArgumentException("Missing or invalid access_history array");
        List<AccessHistoryEntry> result = new ArrayList<>();
        for (JsonElement value : payload.getAsJsonArray()) {
            if (!value.isJsonObject())
                throw new IllegalArgumentException("Invalid access-history entry");
            result.add(new AccessHistoryEntry(value.getAsJsonObject()));
        }
        result.sort(Comparator.comparingLong(AccessHistoryEntry::getTimestampMicros)
                .thenComparingInt(item -> item.accessId == null ? 0 : item.accessId).reversed());
        return Collections.unmodifiableList(result);
    }

    public long getTimestampMicros() {
        return timestampMicros;
    }

    public String getDisplayTime() {
        return new SimpleDateFormat("MMM d, yyyy, HH:mm:ss z", Locale.getDefault())
                .format(new Date(Math.floorDiv(timestampMicros, 1000L)));
    }

    public String getActorLabel(Map<Integer, String> users) {
        String name = username;
        if (name == null && users != null) name = users.get(userId);
        if (name != null && !name.trim().isEmpty()) return name + " (ID " + userId + ")";
        return userId == 0 ? "Unknown user" : "User #" + userId + " (name unavailable)";
    }

    public boolean isAlarm() {
        return "alarm".equals(event) || "unauthorized_open".equals(event);
    }

    public String getEventLabel() {
        switch (event) {
            case "unlocked": return "Safe unlocked";
            case "door_opened": return "Safe opened";
            case "door_closed": return "Safe closed";
            case "locked": return "Safe locked";
            case "alarm":
            case "unauthorized_open": return "Alarm: unauthorized opening";
            default: return "Safe access";
        }
    }

    private static Integer optionalInteger(JsonObject value, String field) {
        JsonElement item = value.get(field);
        if (item == null || item.isJsonNull()) return null;
        if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Invalid access-history " + field);
        try {
            return item.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException error) {
            throw new IllegalArgumentException("Invalid access-history " + field, error);
        }
    }

    private static String optionalText(JsonObject value, String field) {
        JsonElement item = value.get(field);
        if (item == null || item.isJsonNull()) return null;
        if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Invalid access-history " + field);
        String text = item.getAsString().trim();
        return text.isEmpty() ? null : text;
    }

    private static long parseTimestamp(String timestamp) {
        Matcher match = TIMESTAMP.matcher(timestamp == null ? "" : timestamp);
        if (!match.matches()) throw new IllegalArgumentException("Invalid access-history timestamp");
        String fraction = match.group(2) == null ? "" : match.group(2);
        String micros = (fraction + "000000").substring(0, 6);
        String value = match.group(1) + "." + micros.substring(0, 3) + match.group(3);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US);
        format.setLenient(false);
        ParsePosition position = new ParsePosition(0);
        Date date = format.parse(value, position);
        if (date == null || position.getIndex() != value.length())
            throw new IllegalArgumentException("Invalid access-history timestamp");
        return date.getTime() * 1000L + Integer.parseInt(micros.substring(3));
    }
}
