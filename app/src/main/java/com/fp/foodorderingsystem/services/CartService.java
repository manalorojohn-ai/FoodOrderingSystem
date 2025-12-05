package com.fp.foodorderingsystem.services;

import android.content.Context;
import android.util.Log;
import com.fp.foodorderingsystem.models.CartItem;
import com.fp.foodorderingsystem.utils.NetworkUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CartService {
    private static final String CART_SELECT = "id,user_id,menu_item_id,quantity,unit_price,total_price,menu_items(*)";
    private static final String TAG = "CartService";

    private final SupabaseService supabaseService;
    private final Context context;
    private final Gson gson;

    public CartService(Context context) {
        this.context = context.getApplicationContext();
        this.supabaseService = SupabaseService.getInstance(context);
        this.gson = new Gson();
    }

    public interface CartItemsCallback {
        void onSuccess(List<CartItem> items);
        void onError(String error);
    }

    public interface CartItemCallback {
        void onSuccess(CartItem item);
        void onError(String error);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String error);
    }

    public void getCartItems(String userId, CartItemsCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }

        new Thread(() -> {
            try {
                String endpoint = String.format(
                    java.util.Locale.US,
                    "cart_items?select=%s&user_id=eq.%s&order=updated_at.desc",
                    CART_SELECT,
                    userId
                );
                Request request = supabaseService.createRequest(endpoint)
                    .get()
                    .build();

                try (Response response = supabaseService.executeRequest(request)) {
                    if (response.isSuccessful()) {
                        String json = response.body() != null ? response.body().string() : "[]";
                        CartItem[] items = gson.fromJson(json, CartItem[].class);
                        List<CartItem> cartItems = new ArrayList<>();
                        if (items != null) {
                            for (CartItem item : items) {
                                if (item != null && item.getFoodItem() == null && item.getMenuItemId() != 0) {
                                    // No-op: menu item data may be absent only when join fails.
                                }
                                cartItems.add(item);
                            }
                        }
                        callback.onSuccess(cartItems);
                    } else {
                        String errorBody = response.body() != null ? response.body().string() : "";
                        Log.e(TAG, "Failed to load cart: " + response.code() + " - " + errorBody);
                        callback.onError("Failed to load cart: " + response.code());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "getCartItems error", e);
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }

    public void addOrUpdateItem(String userId, int menuItemId, int quantity, double unitPrice, CartItemCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }

        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("user_id", userId);
                body.addProperty("menu_item_id", menuItemId);
                body.addProperty("quantity", quantity);
                body.addProperty("unit_price", unitPrice);

                Request request = supabaseService.createRequest("cart_items?select=" + CART_SELECT + "&on_conflict=user_id,menu_item_id")
                    .header("Prefer", "resolution=merge-duplicates,return=representation")
                    .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        body.toString()
                    ))
                    .build();

                try (Response response = supabaseService.executeRequest(request)) {
                    String json = response.body() != null ? response.body().string() : "[]";
                    if (response.isSuccessful()) {
                        CartItem[] items = gson.fromJson(json, CartItem[].class);
                        if (items != null && items.length > 0) {
                            callback.onSuccess(items[0]);
                        } else {
                            callback.onError("Failed to parse cart item");
                        }
                    } else {
                        Log.e(TAG, "Failed to add item: " + response.code() + " - " + json);
                        callback.onError("Failed to add item: " + response.code());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "addOrUpdateItem error", e);
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }

    public void updateQuantity(String cartItemId, int quantity, CartItemCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }

        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("quantity", quantity);

                Request request = supabaseService.createRequest("cart_items?id=eq." + cartItemId + "&select=" + CART_SELECT)
                    .header("Prefer", "return=representation")
                    .patch(RequestBody.create(
                        MediaType.parse("application/json"),
                        body.toString()
                    ))
                    .build();

                try (Response response = supabaseService.executeRequest(request)) {
                    String json = response.body() != null ? response.body().string() : "[]";
                    if (response.isSuccessful()) {
                        CartItem[] items = gson.fromJson(json, CartItem[].class);
                        if (items != null && items.length > 0) {
                            callback.onSuccess(items[0]);
                        } else {
                            callback.onError("Failed to parse updated item");
                        }
                    } else {
                        Log.e(TAG, "Failed to update quantity: " + response.code() + " - " + json);
                        callback.onError("Failed to update quantity: " + response.code());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "updateQuantity error", e);
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }

    public void removeItem(String cartItemId, SimpleCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }

        new Thread(() -> {
            try {
                Request request = supabaseService.createRequest("cart_items?id=eq." + cartItemId)
                    .delete()
                    .build();

                try (Response response = supabaseService.executeRequest(request)) {
                    if (response.isSuccessful()) {
                        callback.onSuccess();
                    } else {
                        callback.onError("Failed to remove item: " + response.code());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "removeItem error", e);
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }

    public void clearCart(String userId, SimpleCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }

        new Thread(() -> {
            try {
                Request request = supabaseService.createRequest("cart_items?user_id=eq." + userId)
                    .delete()
                    .build();

                try (Response response = supabaseService.executeRequest(request)) {
                    if (response.isSuccessful()) {
                        callback.onSuccess();
                    } else {
                        callback.onError("Failed to clear cart: " + response.code());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "clearCart error", e);
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }
}

