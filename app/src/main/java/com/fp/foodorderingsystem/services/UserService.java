package com.fp.foodorderingsystem.services;

import android.content.Context;
import android.util.Log;
import com.fp.foodorderingsystem.models.User;
import com.fp.foodorderingsystem.utils.NetworkUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class UserService {
    private static final String TAG = "UserService";

    private final SupabaseService supabaseService;
    private final Context context;
    private final Gson gson;

    public UserService(Context context) {
        this.context = context.getApplicationContext();
        this.supabaseService = SupabaseService.getInstance(context);
        this.gson = new Gson();
    }

    public interface UserListCallback {
        void onSuccess(List<User> users);
        void onError(String error);
    }

    public interface UserCallback {
        void onSuccess(User user);
        void onError(String error);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String error);
    }
    
    public interface EmailCheckCallback {
        void onResult(boolean exists);
        void onError(String error);
    }
    
    /**
     * Check if email already exists in Supabase
     */
    public void checkEmailExists(String email, EmailCheckCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }
        
        if (email == null || email.trim().isEmpty()) {
            callback.onResult(false);
            return;
        }
        
        new Thread(() -> {
            try {
                String sanitizedEmail = email.trim().toLowerCase();
                // URL encode the email for the query parameter
                String encodedEmail = java.net.URLEncoder.encode(sanitizedEmail, "UTF-8");
                // Case-insensitive exact match using ilike
                String endpoint = "users?email=ilike." + encodedEmail + "&select=id";
                
                Request request = supabaseService.createRequest(endpoint).get().build();
                Response response = supabaseService.executeRequest(request);
                
                if (response.isSuccessful()) {
                    String responseBody = response.body() != null ? response.body().string() : "";

                    boolean exists = false;
                    try {
                        com.google.gson.JsonArray arr = gson.fromJson(responseBody, com.google.gson.JsonArray.class);
                        exists = arr != null && arr.size() > 0;
                    } catch (Exception ignore) {
                        // Fallback to string check
                        String trimmed = responseBody != null ? responseBody.trim() : "";
                        exists = !trimmed.equals("[]") && trimmed.length() > 2;
                    }

                    callback.onResult(exists);
                } else {
                    callback.onError("Unable to verify email right now. Please try again.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking email existence", e);
                callback.onError("Error checking email: " + e.getMessage());
            }
        }).start();
    }

    public void getAllUsers(UserListCallback callback) {
        getAllUsers(null, callback);
    }
    
    public void getAllUsers(String accessToken, UserListCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }

        new Thread(() -> {
            try {
                String endpoint = "users?select=*&order=created_at.desc";
                Log.d(TAG, "Fetching all users from: " + endpoint);
                
                Request.Builder requestBuilder;
                if (accessToken != null && !accessToken.isEmpty()) {
                    Log.d(TAG, "Using authenticated request");
                    requestBuilder = supabaseService.createAuthenticatedRequest(endpoint, accessToken);
                } else {
                    Log.d(TAG, "Using unauthenticated request");
                    requestBuilder = supabaseService.createRequest(endpoint);
                }
                
                Request request = requestBuilder.get().build();

                Response response = supabaseService.executeRequest(request);
                String responseBody = response.body() != null ? response.body().string() : "";
                
                Log.d(TAG, "getAllUsers response - Code: " + response.code() + ", Body length: " + responseBody.length());
                
                if (response.isSuccessful()) {
                    if (responseBody.isEmpty() || responseBody.trim().equals("[]")) {
                        Log.d(TAG, "No users found in database");
                        callback.onSuccess(new ArrayList<>());
                        return;
                    }
                    
                    Log.d(TAG, "Response body preview: " + (responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody));
                    
                    try {
                        User[] result = gson.fromJson(responseBody, User[].class);
                        List<User> users = new ArrayList<>();
                        if (result != null) {
                            Log.d(TAG, "Successfully parsed " + result.length + " users");
                            for (User user : result) {
                                if (user != null) {
                                    users.add(user);
                                }
                            }
                            Log.d(TAG, "Added " + users.size() + " valid users to list");
                        } else {
                            Log.w(TAG, "Parsed result is null - response body: " + responseBody);
                        }
                        callback.onSuccess(users);
                    } catch (Exception parseError) {
                        Log.e(TAG, "Error parsing users JSON", parseError);
                        Log.e(TAG, "Response body that failed to parse: " + responseBody);
                        callback.onError("Failed to parse user data: " + parseError.getMessage());
                    }
                } else {
                    String errorMsg = "Failed to load users: HTTP " + response.code();
                    if (!responseBody.isEmpty()) {
                        try {
                            JsonObject errorJson = gson.fromJson(responseBody, JsonObject.class);
                            if (errorJson != null && errorJson.has("message")) {
                                errorMsg = errorJson.get("message").getAsString();
                            } else if (errorJson != null && errorJson.has("error")) {
                                errorMsg = errorJson.get("error").getAsString();
                            }
                        } catch (Exception e) {
                            // Ignore parsing error
                        }
                        Log.e(TAG, "Error response body: " + responseBody);
                    }
                    Log.e(TAG, errorMsg);
                    callback.onError(errorMsg);
                }
            } catch (Exception e) {
                Log.e(TAG, "getAllUsers error", e);
                callback.onError("Error loading users: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Get all admin users
     */
    public void getAdminUsers(String accessToken, UserListCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }

        new Thread(() -> {
            try {
                String endpoint = "users?user_type=eq.admin&select=*";
                Log.d(TAG, "Fetching admin users from: " + endpoint);
                
                Request.Builder requestBuilder;
                if (accessToken != null && !accessToken.isEmpty()) {
                    requestBuilder = supabaseService.createAuthenticatedRequest(endpoint, accessToken);
                } else {
                    requestBuilder = supabaseService.createRequest(endpoint);
                }
                
                Request request = requestBuilder.get().build();
                Response response = supabaseService.executeRequest(request);
                String responseBody = response.body() != null ? response.body().string() : "";
                
                Log.d(TAG, "getAdminUsers response - Code: " + response.code() + ", Body length: " + responseBody.length());
                
                if (response.isSuccessful()) {
                    if (responseBody.isEmpty() || responseBody.trim().equals("[]")) {
                        Log.d(TAG, "No admin users found");
                        callback.onSuccess(new ArrayList<>());
                        return;
                    }
                    
                    try {
                        User[] result = gson.fromJson(responseBody, User[].class);
                        List<User> adminUsers = new ArrayList<>();
                        if (result != null) {
                            for (User user : result) {
                                if (user != null && user.isAdmin()) {
                                    adminUsers.add(user);
                                }
                            }
                            Log.d(TAG, "Found " + adminUsers.size() + " admin users");
                        }
                        callback.onSuccess(adminUsers);
                    } catch (Exception parseError) {
                        Log.e(TAG, "Error parsing admin users JSON", parseError);
                        callback.onError("Failed to parse admin user data: " + parseError.getMessage());
                    }
                } else {
                    String errorMsg = "Failed to load admin users: HTTP " + response.code();
                    Log.e(TAG, errorMsg);
                    callback.onError(errorMsg);
                }
            } catch (Exception e) {
                Log.e(TAG, "getAdminUsers error", e);
                callback.onError("Error loading admin users: " + e.getMessage());
            }
        }).start();
    }

    public void updateUserRole(String userId, String role, UserCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("user_type", role);
        patchUser(userId, body, callback);
    }

    public void updateVerification(String userId, boolean verified, UserCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("is_verified", verified);
        patchUser(userId, body, callback);
    }

    public void resetCancellationCount(String userId, UserCallback callback) {
        JsonObject body = new JsonObject();
        body.addProperty("cancellation_count", 0);
        patchUser(userId, body, callback);
    }

    public void getUserById(String userId, String accessToken, UserCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }

        new Thread(() -> {
            try {
                String endpoint = "users?id=eq." + userId + "&select=*";
                Log.d(TAG, "Fetching user by ID: " + userId);
                
                Request.Builder requestBuilder;
                if (accessToken != null && !accessToken.isEmpty()) {
                    requestBuilder = supabaseService.createAuthenticatedRequest(endpoint, accessToken);
                } else {
                    requestBuilder = supabaseService.createRequest(endpoint);
                }
                
                Request request = requestBuilder.get().build();
                Response response = supabaseService.executeRequest(request);
                
                if (response.isSuccessful()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    if (responseBody.isEmpty() || responseBody.trim().equals("[]")) {
                        callback.onError("User not found");
                        return;
                    }
                    
                    User[] users = gson.fromJson(responseBody, User[].class);
                    if (users != null && users.length > 0) {
                        callback.onSuccess(users[0]);
                    } else {
                        callback.onError("Failed to parse user data");
                    }
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    callback.onError("Failed to load user: " + response.code());
                }
            } catch (Exception e) {
                Log.e(TAG, "getUserById error", e);
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }

    public void updateUserProfile(String userId, String fullName, String phone, String address, String accessToken, UserCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }

        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                if (fullName != null && !fullName.trim().isEmpty()) {
                    body.addProperty("full_name", fullName.trim());
                }
                if (phone != null) {
                    body.addProperty("phone", phone.trim().isEmpty() ? null : phone.trim());
                }
                if (address != null) {
                    body.addProperty("address", address.trim().isEmpty() ? null : address.trim());
                }
                
                Log.d(TAG, "Updating user profile: " + body.toString());
                
                Request.Builder requestBuilder;
                if (accessToken != null && !accessToken.isEmpty()) {
                    requestBuilder = supabaseService.createAuthenticatedRequest("users?id=eq." + userId, accessToken);
                } else {
                    requestBuilder = supabaseService.createRequest("users?id=eq." + userId);
                }
                
                Request request = requestBuilder
                    .patch(RequestBody.create(
                        MediaType.parse("application/json"),
                        body.toString()
                    ))
                    .build();

                Response response = supabaseService.executeRequest(request);
                if (response.isSuccessful()) {
                    String json = response.body() != null ? response.body().string() : "";
                    User[] result = gson.fromJson(json, User[].class);
                    if (result != null && result.length > 0) {
                        Log.d(TAG, "User profile updated successfully");
                        callback.onSuccess(result[0]);
                    } else {
                        callback.onError("Failed to parse updated user");
                    }
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    Log.e(TAG, "Update profile failed: " + response.code() + " - " + errorBody);
                    callback.onError("Failed to update profile: " + response.code());
                }
            } catch (Exception e) {
                Log.e(TAG, "updateUserProfile error", e);
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }

    public void updateProfilePicture(String userId, String profilePicturePath, String accessToken, UserCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }

        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("profile_picture", profilePicturePath);
                
                // Also set the full URL for easier access
                String profilePictureUrl = supabaseService.getPublicUrl("profile-pictures", profilePicturePath);
                if (profilePictureUrl != null && !profilePictureUrl.isEmpty()) {
                    body.addProperty("profile_picture_url", profilePictureUrl);
                }
                
                Log.d(TAG, "Updating profile picture: " + profilePicturePath + ", URL: " + profilePictureUrl);
                
                Request.Builder requestBuilder;
                if (accessToken != null && !accessToken.isEmpty()) {
                    requestBuilder = supabaseService.createAuthenticatedRequest("users?id=eq." + userId, accessToken);
                } else {
                    requestBuilder = supabaseService.createRequest("users?id=eq." + userId);
                }
                
                Request request = requestBuilder
                    .patch(RequestBody.create(
                        MediaType.parse("application/json"),
                        body.toString()
                    ))
                    .build();

                Response response = supabaseService.executeRequest(request);
                if (response.isSuccessful()) {
                    String json = response.body() != null ? response.body().string() : "";
                    User[] result = gson.fromJson(json, User[].class);
                    if (result != null && result.length > 0) {
                        Log.d(TAG, "Profile picture updated successfully");
                        callback.onSuccess(result[0]);
                    } else {
                        callback.onError("Failed to parse updated user");
                    }
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    Log.e(TAG, "Update profile picture failed: " + response.code() + " - " + errorBody);
                    callback.onError("Failed to update profile picture: " + response.code());
                }
            } catch (Exception e) {
                Log.e(TAG, "updateProfilePicture error", e);
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }

    private void patchUser(String userId, JsonObject body, UserCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }

        new Thread(() -> {
            try {
                Request request = supabaseService.createRequest("users?id=eq." + userId)
                    .patch(RequestBody.create(
                        MediaType.parse("application/json"),
                        body.toString()
                    ))
                    .build();

                Response response = supabaseService.executeRequest(request);
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    User[] result = gson.fromJson(json, User[].class);
                    if (result != null && result.length > 0) {
                        callback.onSuccess(result[0]);
                    } else {
                        callback.onError("Failed to parse updated user");
                    }
                } else {
                    callback.onError("Failed to update user: " + response.code());
                }
            } catch (Exception e) {
                Log.e(TAG, "patchUser error", e);
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Delete user account from Supabase
     * This will delete the user record from the users table
     * Note: Related data (orders, cart items) may need to be handled separately
     * depending on database foreign key constraints
     */
    public void deleteUser(String userId, String accessToken, SimpleCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }

        new Thread(() -> {
            try {
                String endpoint = "users?id=eq." + userId;
                Log.d(TAG, "Deleting user: " + userId);
                
                Request.Builder requestBuilder;
                if (accessToken != null && !accessToken.isEmpty()) {
                    requestBuilder = supabaseService.createAuthenticatedRequest(endpoint, accessToken);
                } else {
                    requestBuilder = supabaseService.createRequest(endpoint);
                }
                
                Request request = requestBuilder.delete().build();
                Response response = supabaseService.executeRequest(request);
                
                if (response.isSuccessful()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "User deleted successfully: " + responseBody);
                    callback.onSuccess();
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    Log.e(TAG, "Delete user failed: " + response.code() + " - " + errorBody);
                    callback.onError("Failed to delete account: " + response.code());
                }
            } catch (Exception e) {
                Log.e(TAG, "deleteUser error", e);
                callback.onError("Error: " + e.getMessage());
            }
        }).start();
    }
}

