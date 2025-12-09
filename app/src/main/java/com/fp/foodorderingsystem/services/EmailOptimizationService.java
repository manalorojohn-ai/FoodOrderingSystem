package com.fp.foodorderingsystem.services;

import com.fp.foodorderingsystem.config.SupabaseConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Lightweight helper to send and verify OTP emails quickly using Supabase Auth REST endpoints.
 * Uses a shared OkHttp client with pooling and short timeouts to keep email delivery snappy.
 */
public class EmailOptimizationService {
    private static final OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .connectionPool(new okhttp3.ConnectionPool(8, 5, TimeUnit.MINUTES))
        .build();

    private static final Gson gson = new Gson();
    private static final MediaType JSON = MediaType.parse("application/json");

    public interface Callback {
        void onSuccess();
        void onError(String error);
    }

    public interface TokenCallback {
        void onSuccess(String accessToken);
        void onError(String error);
    }

    public static void sendOtpOptimized(String email, String type, boolean createUser, Callback callback) {
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("email", email);
                body.addProperty("type", type);
                if (createUser) {
                    body.addProperty("create_user", true);
                }

                Request request = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/auth/v1/otp")
                    .post(RequestBody.create(JSON, body.toString()))
                    .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();

                try (Response response = client.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        callback.onSuccess();
                    } else if (response.code() == 429) {
                        callback.onError("Too many requests. Please wait and try again.");
                    } else {
                        callback.onError("Failed to send code: " + responseBody);
                    }
                }
            } catch (IOException e) {
                callback.onError("Network error sending code: " + e.getMessage());
            } catch (Exception e) {
                callback.onError("Unexpected error: " + e.getMessage());
            }
        }).start();
    }

    public static void resendOtpOptimized(String email, String type, Callback callback) {
        // Resend behaves like send without creating user; Supabase invalidates old OTP automatically.
        sendOtpOptimized(email, type, false, callback);
    }

    public static void verifyOtpOptimized(String email, String otpCode, String type, TokenCallback callback) {
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("email", email);
                body.addProperty("token", otpCode);
                body.addProperty("type", type); // signup | recovery

                Request request = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/auth/v1/verify")
                    .post(RequestBody.create(JSON, body.toString()))
                    .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();

                try (Response response = client.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        JsonObject json = gson.fromJson(responseBody, JsonObject.class);
                        if (json != null && json.has("access_token")) {
                            callback.onSuccess(json.get("access_token").getAsString());
                        } else if (json != null && json.has("session") && json.getAsJsonObject("session").has("access_token")) {
                            callback.onSuccess(json.getAsJsonObject("session").get("access_token").getAsString());
                        } else {
                            callback.onError("Verification succeeded but no access token returned");
                        }
                    } else if (response.code() == 400 || response.code() == 401) {
                        callback.onError("Invalid or expired code");
                    } else {
                        callback.onError("Failed to verify code: " + responseBody);
                    }
                }
            } catch (IOException e) {
                callback.onError("Network error verifying code: " + e.getMessage());
            } catch (Exception e) {
                callback.onError("Unexpected error: " + e.getMessage());
            }
        }).start();
    }
}

