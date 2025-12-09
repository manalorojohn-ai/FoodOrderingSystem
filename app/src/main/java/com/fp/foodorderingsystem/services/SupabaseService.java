package com.fp.foodorderingsystem.services;

import android.content.Context;
import com.fp.foodorderingsystem.config.SupabaseConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.*;
import android.content.pm.ApplicationInfo;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SupabaseService {
    private static SupabaseService instance;
    private OkHttpClient client;
    private Gson gson;
    private Context context;
    
    private SupabaseService(Context context) {
        this.context = context.getApplicationContext();
        okhttp3.OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true);

        // Add HTTP logging in debug builds
        boolean isDebuggable = (this.context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (isDebuggable) {
            okhttp3.logging.HttpLoggingInterceptor logging = new okhttp3.logging.HttpLoggingInterceptor(
                message -> android.util.Log.d("HTTP", message)
            );
            logging.setLevel(okhttp3.logging.HttpLoggingInterceptor.Level.BODY);
            builder.addInterceptor(logging);
        }

        this.client = builder.build();
        this.gson = new Gson();
    }
    
    public static synchronized SupabaseService getInstance(Context context) {
        if (instance == null) {
            instance = new SupabaseService(context);
        }
        return instance;
    }
    
    public Request.Builder createRequest(String endpoint) {
        return new Request.Builder()
            .url(SupabaseConfig.SUPABASE_URL + "/rest/v1/" + endpoint)
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=representation");
    }
    
    public Request.Builder createAuthenticatedRequest(String endpoint, String accessToken) {
        return createRequest(endpoint)
            .addHeader("Authorization", "Bearer " + accessToken);
    }
    
    public Response executeRequest(Request request) throws IOException {
        return client.newCall(request).execute();
    }

    public Response uploadFileToBucket(String bucket, String filePath, byte[] data, String mimeType, String accessToken) throws IOException {
        if (mimeType == null || mimeType.isEmpty()) {
            mimeType = "application/octet-stream";
        }
        RequestBody body = RequestBody.create(MediaType.parse(mimeType), data);
        
        // Use access token if provided, otherwise use anon key
        String authToken = (accessToken != null && !accessToken.isEmpty()) ? accessToken : SupabaseConfig.SUPABASE_ANON_KEY;
        
        Request request = new Request.Builder()
            .url(SupabaseConfig.SUPABASE_URL + "/storage/v1/object/" + bucket + "/" + filePath)
            .post(body)
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer " + authToken)
            .addHeader("Content-Type", mimeType)
            .addHeader("x-upsert", "true")
            .build();
        return client.newCall(request).execute();
    }
    
    public String getPublicUrl(String bucket, String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }
        
        // If it's already a full URL, return as is
        if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
            return filePath;
        }
        
        // Remove leading slash if present
        if (filePath.startsWith("/")) {
            filePath = filePath.substring(1);
        }
        
        // URL encode the file path to handle special characters
        try {
            // Split by slashes, encode each segment, then rejoin
            String[] pathParts = filePath.split("/");
            StringBuilder encodedPath = new StringBuilder();
            for (int i = 0; i < pathParts.length; i++) {
                if (i > 0) {
                    encodedPath.append("/");
                }
                // URL encode each path segment
                encodedPath.append(java.net.URLEncoder.encode(pathParts[i], "UTF-8")
                    .replace("+", "%20")); // Replace + with %20 for spaces
            }
            filePath = encodedPath.toString();
        } catch (Exception e) {
            // If encoding fails, use original path
            android.util.Log.w("SupabaseService", "Failed to encode file path: " + filePath, e);
        }
        
        String fullUrl = SupabaseConfig.SUPABASE_URL + "/storage/v1/object/public/" + bucket + "/" + filePath;
        android.util.Log.d("SupabaseService", "Generated public URL - Bucket: " + bucket + ", Path: " + filePath + ", Full URL: " + fullUrl);
        return fullUrl;
    }
    
    public String getAccessToken() {
        // This should be retrieved from auth service after login
        return ""; // Will be set by AuthService
    }
    
    public Gson getGson() {
        return gson;
    }
    
    // Helper methods for parsing JSON
    public <T> T parseObject(String json, Class<T> clazz) {
        try {
            return gson.fromJson(json, clazz);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public <T> List<T> parseArray(String json, Class<T> clazz) {
        List<T> list = new ArrayList<>();
        try {
            JsonArray jsonArray = gson.fromJson(json, JsonArray.class);
            for (JsonElement element : jsonArray) {
                list.add(gson.fromJson(element, clazz));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
}

