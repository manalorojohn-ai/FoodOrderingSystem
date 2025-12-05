package com.fp.foodorderingsystem.services;

import android.content.Context;
import android.util.Log;
import com.fp.foodorderingsystem.models.Category;
import com.fp.foodorderingsystem.utils.NetworkUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CategoryService {
    private static final String TAG = "CategoryService";

    private final SupabaseService supabaseService;
    private final Context context;
    private final Gson gson;

    public CategoryService(Context context) {
        this.context = context.getApplicationContext();
        this.supabaseService = SupabaseService.getInstance(context);
        this.gson = new Gson();
    }

    public interface CategoryListCallback {
        void onSuccess(List<Category> categories);
        void onError(String error);
    }

    public interface CategoryCallback {
        void onSuccess(Category category);
        void onError(String error);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String error);
    }

    public void getAllCategories(CategoryListCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }

        new Thread(() -> {
            try {
                Request request = supabaseService.createRequest("categories?select=*&order=created_at.desc")
                    .get()
                    .build();

                Response response = supabaseService.executeRequest(request);
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    Category[] result = gson.fromJson(json, Category[].class);
                    List<Category> categories = new ArrayList<>();
                    if (result != null) {
                        for (Category category : result) {
                            categories.add(category);
                        }
                    }
                    callback.onSuccess(categories);
                } else {
                    callback.onError("Failed to load categories: " + response.code());
                }
            } catch (Exception e) {
                Log.e(TAG, "getAllCategories error", e);
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }

    public void createCategory(Category category, CategoryCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }

        new Thread(() -> {
            try {
                JsonObject body = buildBodyFromCategory(category);

                Request request = supabaseService.createRequest("categories")
                    .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        body.toString()
                    ))
                    .build();

                Response response = supabaseService.executeRequest(request);
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    Category[] result = gson.fromJson(json, Category[].class);
                    if (result != null && result.length > 0) {
                        callback.onSuccess(result[0]);
                    } else {
                        callback.onError("Failed to parse created category");
                    }
                } else {
                    callback.onError("Failed to create category: " + response.code());
                }
            } catch (Exception e) {
                Log.e(TAG, "createCategory error", e);
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }

    public void updateCategory(Category category, CategoryCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }

        new Thread(() -> {
            try {
                JsonObject body = buildBodyFromCategory(category);

                Request request = supabaseService.createRequest("categories?id=eq." + category.getId())
                    .patch(RequestBody.create(
                        MediaType.parse("application/json"),
                        body.toString()
                    ))
                    .build();

                Response response = supabaseService.executeRequest(request);
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    Category[] result = gson.fromJson(json, Category[].class);
                    if (result != null && result.length > 0) {
                        callback.onSuccess(result[0]);
                    } else {
                        callback.onError("Failed to parse updated category");
                    }
                } else {
                    callback.onError("Failed to update category: " + response.code());
                }
            } catch (Exception e) {
                Log.e(TAG, "updateCategory error", e);
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }

    public void deleteCategory(int categoryId, SimpleCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }

        new Thread(() -> {
            try {
                Request request = supabaseService.createRequest("categories?id=eq." + categoryId)
                    .delete()
                    .build();

                Response response = supabaseService.executeRequest(request);
                if (response.isSuccessful()) {
                    callback.onSuccess();
                } else {
                    callback.onError("Failed to delete category: " + response.code());
                }
            } catch (Exception e) {
                Log.e(TAG, "deleteCategory error", e);
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }

    private JsonObject buildBodyFromCategory(Category category) {
        JsonObject body = new JsonObject();
        body.addProperty("name", category.getName());
        String description = category.getDescription();
        if (description != null && !description.trim().isEmpty()) {
            body.addProperty("description", description);
        } else {
            body.add("description", com.google.gson.JsonNull.INSTANCE);
        }
        return body;
    }
}

