package com.fp.foodorderingsystem.services;

import android.content.Context;
import android.util.Log;
import com.fp.foodorderingsystem.config.PayMongoConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import android.content.pm.ApplicationInfo;

/**
 * PayMongo Service
 * Handles payment processing through PayMongo API for GCash and Maya
 */
public class PayMongoService {
    private static final String TAG = "PayMongoService";
    private static PayMongoService instance;
    private OkHttpClient client;
    private Gson gson;
    private Context context;
    
    private PayMongoService(Context context) {
        this.context = context.getApplicationContext();
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true);
        
        // Add HTTP logging in debug builds
        boolean isDebuggable = (this.context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (isDebuggable) {
            okhttp3.logging.HttpLoggingInterceptor logging = new okhttp3.logging.HttpLoggingInterceptor(
                message -> Log.d("PayMongo", message)
            );
            logging.setLevel(okhttp3.logging.HttpLoggingInterceptor.Level.BODY);
            builder.addInterceptor(logging);
        }
        
        this.client = builder.build();
        this.gson = new Gson();
    }
    
    public static synchronized PayMongoService getInstance(Context context) {
        if (instance == null) {
            instance = new PayMongoService(context);
        }
        return instance;
    }
    
    /**
     * Callback interface for payment intent creation
     */
    public interface PaymentIntentCallback {
        void onSuccess(String paymentIntentId, String checkoutUrl);
        void onError(String error);
    }
    
    /**
     * Callback interface for payment status check
     */
    public interface PaymentStatusCallback {
        void onSuccess(String status, String paymentIntentId);
        void onError(String error);
    }
    
    /**
     * Create a payment intent for GCash or Maya
     * @param amount Amount in cents (e.g., 10000 = ₱100.00)
     * @param paymentMethod "gcash" or "paymaya"
     * @param description Description of the payment
     * @param callback Callback for result
     */
    public void createPaymentIntent(double amount, String paymentMethod, String description, PaymentIntentCallback callback) {
        // Convert amount to cents (PayMongo uses cents)
        int amountInCents = (int) Math.round(amount * 100);
        
        // Validate payment method
        if (!PayMongoConfig.PAYMENT_METHOD_GCASH.equals(paymentMethod) && 
            !PayMongoConfig.PAYMENT_METHOD_MAYA.equals(paymentMethod)) {
            callback.onError("Invalid payment method. Use 'gcash' or 'paymaya'");
            return;
        }
        
        // Create request body
        // PayMongo requires payment_method_allowed as an array of payment method types
        com.google.gson.JsonArray paymentMethodAllowed = new com.google.gson.JsonArray();
        paymentMethodAllowed.add(paymentMethod);
        
        JsonObject attributes = new JsonObject();
        attributes.addProperty("amount", amountInCents);
        attributes.addProperty("currency", "PHP");
        attributes.addProperty("description", description);
        attributes.add("payment_method_allowed", paymentMethodAllowed);
        
        JsonObject data = new JsonObject();
        data.addProperty("type", "payment_intent");
        data.add("attributes", attributes);
        
        JsonObject requestBody = new JsonObject();
        requestBody.add("data", data);
        
        // Create request
        String jsonBody = gson.toJson(requestBody);
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(jsonBody, mediaType);
        
        String authHeader = "Basic " + android.util.Base64.encodeToString(
            (PayMongoConfig.PAYMONGO_SECRET_KEY + ":").getBytes(),
            android.util.Base64.NO_WRAP
        );
        
        Request request = new Request.Builder()
            .url(PayMongoConfig.PAYMONGO_API_URL + "/payment_intents")
            .post(body)
            .addHeader("Authorization", authHeader)
            .addHeader("Content-Type", "application/json")
            .build();
        
        // Execute request asynchronously
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Payment intent creation failed", e);
                callback.onError("Network error: " + e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    Log.e(TAG, "Payment intent creation failed: " + response.code() + " - " + errorBody);
                    callback.onError("Failed to create payment intent: " + response.code());
                    return;
                }
                
                String responseBody = response.body() != null ? response.body().string() : "{}";
                Log.d(TAG, "Payment intent response: " + responseBody);
                
                try {
                    JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                    JsonObject data = jsonResponse.getAsJsonObject("data");
                    String paymentIntentId = data.get("id").getAsString();
                    
                    JsonObject attributes = data.getAsJsonObject("attributes");
                    
                    // Check if next_action exists and is not null
                    String checkoutUrl = null;
                    if (attributes.has("next_action") && !attributes.get("next_action").isJsonNull()) {
                        JsonObject nextAction = attributes.getAsJsonObject("next_action");
                        if (nextAction != null && nextAction.has("url")) {
                            checkoutUrl = nextAction.get("url").getAsString();
                        }
                    }
                    
                    // If status is "awaiting_payment_method" or no checkout URL, create payment method and attach
                    String status = attributes.has("status") ? attributes.get("status").getAsString() : "";
                    if (checkoutUrl == null || checkoutUrl.isEmpty() || 
                        PayMongoConfig.STATUS_AWAITING_PAYMENT_METHOD.equals(status)) {
                        // Create payment method and attach to get checkout URL
                        createPaymentMethodAndAttach(paymentIntentId, paymentMethod, callback);
                    } else {
                        callback.onSuccess(paymentIntentId, checkoutUrl);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing payment intent response", e);
                    callback.onError("Failed to parse response: " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * Create and attach a payment method to a payment intent
     */
    private void createPaymentMethodAndAttach(String paymentIntentId, String paymentMethod, PaymentIntentCallback callback) {
        // Create payment method
        JsonObject paymentMethodAttributes = new JsonObject();
        paymentMethodAttributes.addProperty("type", paymentMethod);
        
        JsonObject paymentMethodData = new JsonObject();
        paymentMethodData.addProperty("type", "payment_method");
        paymentMethodData.add("attributes", paymentMethodAttributes);
        
        JsonObject requestBody = new JsonObject();
        requestBody.add("data", paymentMethodData);
        
        String jsonBody = gson.toJson(requestBody);
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(jsonBody, mediaType);
        
        String authHeader = "Basic " + android.util.Base64.encodeToString(
            (PayMongoConfig.PAYMONGO_SECRET_KEY + ":").getBytes(),
            android.util.Base64.NO_WRAP
        );
        
        Request request = new Request.Builder()
            .url(PayMongoConfig.PAYMONGO_API_URL + "/payment_methods")
            .post(body)
            .addHeader("Authorization", authHeader)
            .addHeader("Content-Type", "application/json")
            .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Failed to create payment method: " + e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError("Failed to create payment method: " + response.code());
                    return;
                }
                
                String responseBody = response.body() != null ? response.body().string() : "{}";
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                JsonObject data = jsonResponse.getAsJsonObject("data");
                String paymentMethodId = data.get("id").getAsString();
                
                // Attach payment method to payment intent
                attachPaymentMethod(paymentIntentId, paymentMethodId, callback);
            }
        });
    }
    
    /**
     * Attach payment method to payment intent
     */
    private void attachPaymentMethod(String paymentIntentId, String paymentMethodId, PaymentIntentCallback callback) {
        JsonObject attributes = new JsonObject();
        attributes.addProperty("payment_method", paymentMethodId);
        // PayMongo requires return_url for GCash and Maya payment methods
        // PayMongo requires a full HTTP/HTTPS URL (not custom URL schemes)
        // In production, use your website URL that can redirect to your app's deep link
        attributes.addProperty("return_url", PayMongoConfig.PAYMENT_RETURN_URL);
        
        JsonObject data = new JsonObject();
        data.addProperty("type", "payment_intent");
        data.add("attributes", attributes);
        
        JsonObject requestBody = new JsonObject();
        requestBody.add("data", data);
        
        String jsonBody = gson.toJson(requestBody);
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(jsonBody, mediaType);
        
        String authHeader = "Basic " + android.util.Base64.encodeToString(
            (PayMongoConfig.PAYMONGO_SECRET_KEY + ":").getBytes(),
            android.util.Base64.NO_WRAP
        );
        
        Request request = new Request.Builder()
            .url(PayMongoConfig.PAYMONGO_API_URL + "/payment_intents/" + paymentIntentId + "/attach")
            .post(body)
            .addHeader("Authorization", authHeader)
            .addHeader("Content-Type", "application/json")
            .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Failed to attach payment method: " + e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    Log.e(TAG, "Attach payment method failed: " + response.code() + " - " + errorBody);
                    
                    // Check if it's a return_url format error
                    if (errorBody.contains("return_url") && errorBody.contains("format")) {
                        Log.e(TAG, "PayMongo rejected return_url format. Using fallback HTTPS URL.");
                        // Could retry with HTTPS URL here if needed
                    }
                    
                    callback.onError("Failed to attach payment method: " + response.code());
                    return;
                }
                
                String responseBody = response.body() != null ? response.body().string() : "{}";
                Log.d(TAG, "Attach payment method response: " + responseBody);
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                JsonObject data = jsonResponse.getAsJsonObject("data");
                String paymentIntentId = data.get("id").getAsString();
                
                JsonObject attributes = data.getAsJsonObject("attributes");
                
                // Check if next_action exists and is not null
                String checkoutUrl = null;
                if (attributes.has("next_action") && !attributes.get("next_action").isJsonNull()) {
                    JsonObject nextAction = attributes.getAsJsonObject("next_action");
                    if (nextAction != null) {
                        // Check for redirect type with url field
                        if (nextAction.has("redirect") && !nextAction.get("redirect").isJsonNull()) {
                            JsonObject redirect = nextAction.getAsJsonObject("redirect");
                            if (redirect != null && redirect.has("url")) {
                                checkoutUrl = redirect.get("url").getAsString();
                            }
                        }
                        // Fallback: check for direct url field (older API format)
                        if ((checkoutUrl == null || checkoutUrl.isEmpty()) && nextAction.has("url")) {
                            checkoutUrl = nextAction.get("url").getAsString();
                        }
                    }
                }
                
                if (checkoutUrl == null || checkoutUrl.isEmpty()) {
                    // If still no checkout URL, check status
                    String status = attributes.has("status") ? attributes.get("status").getAsString() : "";
                    callback.onError("No checkout URL received from PayMongo. Status: " + status);
                } else {
                    // Successfully got checkout URL
                    callback.onSuccess(paymentIntentId, checkoutUrl);
                }
            }
        });
    }
    
    /**
     * Check payment intent status
     * @param paymentIntentId The payment intent ID
     * @param callback Callback for result
     */
    public void checkPaymentStatus(String paymentIntentId, PaymentStatusCallback callback) {
        String authHeader = "Basic " + android.util.Base64.encodeToString(
            (PayMongoConfig.PAYMONGO_SECRET_KEY + ":").getBytes(),
            android.util.Base64.NO_WRAP
        );
        
        Request request = new Request.Builder()
            .url(PayMongoConfig.PAYMONGO_API_URL + "/payment_intents/" + paymentIntentId)
            .get()
            .addHeader("Authorization", authHeader)
            .addHeader("Content-Type", "application/json")
            .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Payment status check failed", e);
                callback.onError("Network error: " + e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError("Failed to check payment status: " + response.code());
                    return;
                }
                
                String responseBody = response.body() != null ? response.body().string() : "{}";
                try {
                    JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                    JsonObject data = jsonResponse.getAsJsonObject("data");
                    JsonObject attributes = data.getAsJsonObject("attributes");
                    String status = attributes.get("status").getAsString();
                    String id = data.get("id").getAsString();
                    
                    callback.onSuccess(status, id);
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing payment status response", e);
                    callback.onError("Failed to parse response: " + e.getMessage());
                }
            }
        });
    }
}

