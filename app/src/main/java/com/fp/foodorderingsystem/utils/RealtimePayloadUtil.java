package com.fp.foodorderingsystem.utils;

import com.google.gson.JsonObject;

/**
 * Helper methods for parsing Supabase realtime payloads.
 */
public final class RealtimePayloadUtil {
    private RealtimePayloadUtil() {}

    public static String getEventType(JsonObject payload) {
        if (payload == null) {
            return "";
        }
        if (payload.has("eventType")) {
            return safeString(payload.get("eventType").getAsString());
        }
        if (payload.has("type")) {
            return safeString(payload.get("type").getAsString());
        }
        if (payload.has("event")) {
            return safeString(payload.get("event").getAsString());
        }
        return "";
    }

    public static JsonObject getNewRecord(JsonObject payload) {
        if (payload == null) return null;
        if (payload.has("record")) {
            return payload.getAsJsonObject("record");
        }
        if (payload.has("new")) {
            return payload.getAsJsonObject("new");
        }
        if (payload.has("new_record")) {
            return payload.getAsJsonObject("new_record");
        }
        return null;
    }

    public static JsonObject getOldRecord(JsonObject payload) {
        if (payload == null) return null;
        if (payload.has("old")) {
            return payload.getAsJsonObject("old");
        }
        if (payload.has("old_record")) {
            return payload.getAsJsonObject("old_record");
        }
        return null;
    }

    public static JsonObject getRelevantRecord(JsonObject payload) {
        JsonObject record = getNewRecord(payload);
        if (record != null) {
            return record;
        }
        return getOldRecord(payload);
    }

    private static String safeString(String value) {
        return value != null ? value : "";
    }
}


