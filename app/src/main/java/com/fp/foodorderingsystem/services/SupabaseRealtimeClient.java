package com.fp.foodorderingsystem.services;

import android.util.Log;
import com.fp.foodorderingsystem.config.SupabaseConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * Lightweight Supabase Realtime client using OkHttp WebSockets.
 * Listens for Postgres changes and forwards them to the provided listener.
 */
public class SupabaseRealtimeClient {
    private static final String TAG = "SupabaseRealtimeClient";
    private static final long HEARTBEAT_SECONDS = 20;
    
    public interface RealtimeListener {
        void onOpen();
        void onChange(JsonObject payload);
        void onError(String error);
    }
    
    private final OkHttpClient okHttpClient;
    private final Gson gson;
    private final AtomicInteger refCounter = new AtomicInteger(1);
    private ScheduledExecutorService heartbeatExecutor;
    private WebSocket webSocket;
    private RealtimeListener listener;
    private String currentTopic;
    
    public SupabaseRealtimeClient() {
        this.okHttpClient = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build();
        this.gson = new Gson();
    }
    
    public void subscribeToTable(String schema, String table, RealtimeListener listener) {
        disconnect();
        this.listener = listener;
        this.currentTopic = "realtime:" + schema + ":" + table;
        
        HttpUrl baseUrl = HttpUrl.parse(SupabaseConfig.SUPABASE_URL);
        if (baseUrl == null) {
            Log.e(TAG, "Invalid Supabase URL: " + SupabaseConfig.SUPABASE_URL);
            if (listener != null) {
                listener.onError("Invalid Supabase URL configuration.");
            }
            return;
        }
        
        HttpUrl url = baseUrl.newBuilder()
            .addPathSegments("realtime/v1/websocket")
            .addQueryParameter("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addQueryParameter("vsn", "1.0.0")
            .build();
        
        String websocketUrl = url.toString()
            .replaceFirst("^http", "ws");
        
        Request request = new Request.Builder()
            .url(websocketUrl)
            .addHeader("User-Agent", "okhttp/4.12.0")
            .addHeader("Accept", "application/json")
            .build();
        
        webSocket = okHttpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                sendJoinMessage(schema, table);
                startHeartbeat();
                if (listener != null) {
                    listener.onOpen();
                }
            }
            
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handleMessage(text);
            }
            
            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                // Not expecting binary messages, but handle gracefully
                handleMessage(bytes.utf8());
            }
            
            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "Realtime connection error", t);
                stopHeartbeat();
                if (listener != null) {
                    listener.onError(t.getMessage());
                }
            }
            
            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                stopHeartbeat();
            }
        });
    }
    
    private void handleMessage(String text) {
        try {
            JsonObject message = gson.fromJson(text, JsonObject.class);
            if (message == null || !message.has("event")) {
                return;
            }
            
            String event = message.get("event").getAsString();
            if ("postgres_changes".equals(event) && listener != null) {
                JsonObject payload = message.getAsJsonObject("payload");
                if (payload != null) {
                    listener.onChange(payload);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse realtime message: " + text, e);
        }
    }
    
    private void sendJoinMessage(String schema, String table) {
        JsonObject changeConfig = new JsonObject();
        changeConfig.addProperty("event", "*");
        changeConfig.addProperty("schema", schema);
        changeConfig.addProperty("table", table);
        
        JsonArray changes = new JsonArray();
        changes.add(changeConfig);
        
        JsonObject config = new JsonObject();
        config.add("postgres_changes", changes);
        
        JsonObject payload = new JsonObject();
        payload.add("config", config);
        
        JsonObject message = new JsonObject();
        message.addProperty("topic", currentTopic);
        message.addProperty("event", "phx_join");
        message.add("payload", payload);
        message.addProperty("ref", String.valueOf(refCounter.getAndIncrement()));
        
        sendMessage(message);
    }
    
    private void sendHeartbeat() {
        JsonObject message = new JsonObject();
        message.addProperty("topic", "phoenix");
        message.addProperty("event", "heartbeat");
        message.add("payload", new JsonObject());
        message.addProperty("ref", String.valueOf(refCounter.getAndIncrement()));
        sendMessage(message);
    }
    
    private void sendMessage(JsonObject message) {
        if (webSocket != null) {
            webSocket.send(message.toString());
        }
    }
    
    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        heartbeatExecutor.scheduleAtFixedRate(
            this::sendHeartbeat,
            HEARTBEAT_SECONDS,
            HEARTBEAT_SECONDS,
            TimeUnit.SECONDS
        );
    }
    
    private void stopHeartbeat() {
        if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) {
            heartbeatExecutor.shutdownNow();
        }
        heartbeatExecutor = null;
    }
    
    public void disconnect() {
        stopHeartbeat();
        if (webSocket != null) {
            try {
                webSocket.close(1000, "Client closing");
            } catch (Exception e) {
                Log.w(TAG, "Error closing realtime socket", e);
            }
        }
        webSocket = null;
    }
}

