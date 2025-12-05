package com.fp.foodorderingsystem.services;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import com.fp.foodorderingsystem.config.SupabaseConfig;
import com.fp.foodorderingsystem.models.User;
import com.fp.foodorderingsystem.utils.NetworkUtil;
import com.fp.foodorderingsystem.utils.PreferenceUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;
import java.io.IOException;

public class AuthService {
    private static final String TAG = "AuthService";
    private SupabaseService supabaseService;
    private Context appContext;
    private PreferenceUtil preferenceUtil;
    private String accessToken;
    
    public AuthService(Context context) {
        this.supabaseService = SupabaseService.getInstance(context);
        this.preferenceUtil = new PreferenceUtil(context);
        this.appContext = context.getApplicationContext();
        // Load access token from preferences if available
        String savedToken = preferenceUtil.getAccessToken();
        if (savedToken != null && !savedToken.isEmpty()) {
            this.accessToken = savedToken;
        }
    }
    
    public interface AuthCallback {
        void onSuccess(User user, String token);
        void onError(String error);
    }

        public interface SimpleCallback {
            void onSuccess();
            void onError(String error);
        }

        public interface TokenCallback {
            void onSuccess(String accessToken);
            void onError(String error);
        }

    /**
     * Step 2: Email Verification (matches PHP verify-email.php flow)
     * 
     * Verification Process:
     * 1. User enters 6-digit OTP code
     * 2. Checks OTP match and expiry (Supabase handles expiry automatically)
     * 3. On success:
     *    - Updates user.is_verified = 1 (via createUserInDatabase)
     *    - Fetches user data and saves to preferences
     *    - Clears temporary signup data
     *    - Returns success callback
     * 4. On failure: Returns error message
     * 
     * OTP Expiry: Configured in Supabase Dashboard (Authentication → Email Templates)
     * Default: 60 seconds, recommended: 600 seconds (10 minutes) to match PHP flow
     * 
     * @param email User's email address
     * @param otpCode 6-digit OTP code entered by user
     * @param callback Success/error callback
     */
    public void verifyEmailOtp(String email, String otpCode, AuthCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(appContext)) {
            callback.onError("No internet connection");
            return;
        }
        new Thread(() -> {
            // Try both "signup" and "email" types since Supabase might use either
            String[] typesToTry = {"signup", "email"};
            String lastError = "Invalid or expired OTP";
            
            for (String type : typesToTry) {
                try {
                    JsonObject requestBody = new JsonObject();
                    requestBody.addProperty("type", type);
                    requestBody.addProperty("email", email);
                    requestBody.addProperty("token", otpCode);
                    
                    Log.d(TAG, "Verifying OTP: email=" + email + ", type=" + type + ", token=" + (otpCode.length() > 2 ? otpCode.substring(0, 2) + "****" : "****"));

                    Request request = new Request.Builder()
                        .url(SupabaseConfig.SUPABASE_URL + "/auth/v1/verify")
                        .post(RequestBody.create(
                            MediaType.parse("application/json"),
                            requestBody.toString()))
                        .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .build();

                    Response response = supabaseService.executeRequest(request);
                    String responseBody = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "OTP verification response (" + type + "): " + response.code() + " - " + responseBody);
                    
                    if (response.isSuccessful()) {
                        // OTP verified successfully - matches PHP: if ($verification['otp'] === $otp && strtotime($verification['expiry']) > time())
                        JsonObject json = supabaseService.getGson().fromJson(responseBody, JsonObject.class);
                        this.accessToken = json.get("access_token").getAsString();
                        // Save tokens to preferences for persistence
                        preferenceUtil.saveAccessToken(this.accessToken);
                        if (json.has("refresh_token")) {
                            preferenceUtil.saveRefreshToken(json.get("refresh_token").getAsString());
                        }
                        JsonObject userObj = json.getAsJsonObject("user");
                        String userId = userObj.get("id").getAsString();
                        String userEmail = userObj.has("email") ? userObj.get("email").getAsString() : email;
                        
                        // Save email temporarily for user creation if needed
                        // This is similar to PHP's $_SESSION['temp_email'] but stored in preferences
                        if (!userEmail.isEmpty()) {
                            preferenceUtil.saveUserData(userId, userEmail, "", ""); // Temporary save just for email
                        }
                        
                        // Fetch user data and update is_verified = 1 (handled in fetchUserData -> createUserInDatabase)
                        // This matches PHP: UPDATE users SET is_verified = 1 WHERE id = $user_id
                        fetchUserData(userId, callback);
                        return; // Success, exit
                    } else {
                        // Parse error for this attempt
                        try {
                            if (!responseBody.isEmpty()) {
                                JsonObject errorJson = supabaseService.getGson().fromJson(responseBody, JsonObject.class);
                                if (errorJson.has("msg")) {
                                    lastError = errorJson.get("msg").getAsString();
                                } else if (errorJson.has("error_description")) {
                                    lastError = errorJson.get("error_description").getAsString();
                                }
                                // Check for expired OTP - matches PHP: strtotime($verification['expiry']) > time()
                                if (errorJson.has("error_code") && "otp_expired".equals(errorJson.get("error_code").getAsString())) {
                                    lastError = "Invalid or expired OTP";
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to parse error response: " + responseBody);
                        }
                        
                        // If this is the last type to try, return error
                        if (type.equals(typesToTry[typesToTry.length - 1])) {
                            if (response.code() == 429) {
                                callback.onError("Too many requests. Please wait a minute and try again.");
                            } else if (response.code() == 403) {
                                callback.onError("Invalid or expired OTP");
                            } else {
                                callback.onError(lastError);
                            }
                            return;
                        }
                        // Continue to next type
                    }
                } catch (java.net.UnknownHostException e) {
                    Log.e(TAG, "Network DNS error", e);
                    callback.onError("Can't reach Supabase. Check internet/Supabase URL.");
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "OTP verification error for type " + type, e);
                    // If this is the last type, return error
                    if (type.equals(typesToTry[typesToTry.length - 1])) {
                        callback.onError("OTP verification failed: " + e.getMessage());
                        return;
                    }
                    // Otherwise continue to next type
                }
            }
        }).start();
    }

    public void sendSignupOtp(String email, SimpleCallback callback) {
        sendOtp(email, "signup", callback);
    }
    
    /**
     * Step 3: Resend OTP (matches PHP resend-otp.php flow)
     * 
     * Resend Process:
     * 1. Deletes old OTPs for the user (Supabase automatically invalidates old OTPs when sending new one)
     * 2. Generates new OTP with new expiry (10 minutes recommended, configured in Supabase Dashboard)
     * 3. Sends new email with OTP
     * 4. Redirects back to verification screen (handled by activity)
     * 
     * OTP Expiry: Configured in Supabase Dashboard
     * Recommended: 600 seconds (10 minutes) to match PHP: $otp_expiry = date('Y-m-d H:i:s', strtotime('+10 minutes'))
     * 
     * @param email User's email address
     * @param callback Success/error callback
     */
    public void sendSignupOtpForExistingUser(String email, SimpleCallback callback) {
        // Send OTP for a user that already exists (after signup)
        // Don't use create_user: true since user already exists
        // This matches PHP resend-otp.php: DELETE FROM email_verifications WHERE user_id = $user_id
        // (Supabase automatically invalidates old OTPs when sending new one)
        sendOtpForExistingUser(email, "signup", callback);
    }
    
    private void sendOtpForExistingUser(String email, String type, SimpleCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(appContext)) {
            callback.onError("No internet connection");
            return;
        }
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("email", email);
                body.addProperty("type", type);
                // Don't set create_user since user already exists
                // Supabase automatically invalidates old OTPs when sending new one
                // This matches PHP: DELETE FROM email_verifications WHERE user_id = $user_id
                
                String requestBody = body.toString();
                Log.d(TAG, "Resending OTP for existing user: " + requestBody);

                Request request = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/auth/v1/otp")
                    .post(RequestBody.create(MediaType.parse("application/json"), requestBody))
                    .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();

                Response response = supabaseService.executeRequest(request);
                String responseBody = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "OTP resend response: " + response.code() + " - " + responseBody);
                
                if (response.isSuccessful()) {
                    // New OTP generated and sent - matches PHP: INSERT INTO email_verifications with new expiry
                    // OTP expiry is configured in Supabase Dashboard (recommended: 10 minutes)
                    Log.d(TAG, "New OTP sent successfully to " + email);
                    callback.onSuccess();
                } else if (response.code() == 429) {
                    callback.onError("Too many requests. Please wait and try again.");
                } else {
                    Log.e(TAG, "Failed to resend OTP: " + responseBody);
                    callback.onError("Failed to send code: " + response.message());
                }
            } catch (java.net.UnknownHostException e) {
                Log.e(TAG, "Network error sending OTP", e);
                callback.onError("Can't reach Supabase. Check internet/Supabase URL.");
            } catch (Exception e) {
                Log.e(TAG, "Exception sending OTP", e);
                callback.onError("Failed to send code: " + e.getMessage());
            }
        }).start();
    }

    public void sendRecoveryOtp(String email, SimpleCallback callback) {
        sendOtp(email, "recovery", callback);
    }

    private void sendOtp(String email, String type, SimpleCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(appContext)) {
            callback.onError("No internet connection");
            return;
        }
        new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("email", email);
                body.addProperty("type", type); // signup | recovery | email_change
                if ("signup".equals(type)) {
                    body.addProperty("create_user", true);
                }

                Request request = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/auth/v1/otp")
                    .post(RequestBody.create(MediaType.parse("application/json"), body.toString()))
                    .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();

                Response response = supabaseService.executeRequest(request);
                if (response.isSuccessful()) {
                    callback.onSuccess();
                } else if (response.code() == 429) {
                    callback.onError("Too many requests. Please wait and try again.");
                } else {
                    callback.onError("Failed to send code: " + response.message());
                }
            } catch (java.net.UnknownHostException e) {
                callback.onError("Can't reach Supabase. Check internet/Supabase URL.");
            } catch (Exception e) {
                callback.onError("Failed to send code: " + e.getMessage());
            }
        }).start();
    }

    public void verifyRecoveryOtp(String email, String otpCode, TokenCallback callback) {
        new Thread(() -> {
            try {
                JsonObject jsonBody = new JsonObject();
                jsonBody.addProperty("type", "recovery");
                jsonBody.addProperty("email", email);
                jsonBody.addProperty("token", otpCode);

                RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.parse("application/json; charset=utf-8"));
                Request request = new Request.Builder()
                        .url(SupabaseConfig.SUPABASE_URL + "/auth/v1/verify")
                        .post(body)
                        .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .build();

                Response response = supabaseService.executeRequest(request);
                String responseBody = response.body() != null ? response.body().string() : "";
                
                if (response.isSuccessful()) {
                    JsonObject authResponse = supabaseService.getGson().fromJson(responseBody, JsonObject.class);
                    String recoveryAccessToken = authResponse.get("access_token").getAsString();
                    callback.onSuccess(recoveryAccessToken);
                } else {
                    String errorMsg = "OTP verification failed";
                    boolean isExpired = false;
                    
                    try {
                        if (!responseBody.isEmpty()) {
                            JsonObject errorJson = supabaseService.getGson().fromJson(responseBody, JsonObject.class);
                            if (errorJson.has("error_code")) {
                                String errorCode = errorJson.get("error_code").getAsString();
                                if ("otp_expired".equals(errorCode)) {
                                    isExpired = true;
                                    errorMsg = "Code has expired. Please request a new code.";
                                }
                            }
                            if (errorJson.has("msg")) {
                                errorMsg = errorJson.get("msg").getAsString();
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to parse error response: " + responseBody);
                    }
                    
                    if (response.code() == 429) {
                        callback.onError("Too many requests. Please wait and try again.");
                    } else if (response.code() == 403 || isExpired) {
                        callback.onError("Code expired or invalid. Please request a new code.");
                    } else {
                        callback.onError(errorMsg);
                    }
                }
            } catch (Exception e) {
                callback.onError("OTP verification failed: " + e.getMessage());
            }
        }).start();
    }

    public void updatePasswordWithToken(String accessTokenForRecovery, String newPassword, SimpleCallback callback) {
        new Thread(() -> {
            try {
                JsonObject bodyJson = new JsonObject();
                bodyJson.addProperty("password", newPassword);

                Request request = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/auth/v1/user")
                    .method("PUT", RequestBody.create(MediaType.parse("application/json"), bodyJson.toString()))
                    .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + accessTokenForRecovery)
                    .addHeader("Content-Type", "application/json")
                    .build();

                Response response = supabaseService.executeRequest(request);
                if (response.isSuccessful()) {
                    callback.onSuccess();
                } else {
                    callback.onError("Failed to update password: " + response.message());
                }
            } catch (Exception e) {
                callback.onError("Failed to update password: " + e.getMessage());
            }
        }).start();
    }
    
    public void login(String email, String password, AuthCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(appContext)) {
            callback.onError("No internet connection");
            return;
        }
        
        new Thread(() -> {
            try {
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("email", email);
                requestBody.addProperty("password", password);
                
                Request request = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/auth/v1/token?grant_type=password")
                    .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        requestBody.toString()))
                    .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();
                
                Response response = supabaseService.executeRequest(request);
                
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    JsonObject jsonResponse = supabaseService.getGson().fromJson(responseBody, JsonObject.class);
                    
                    this.accessToken = jsonResponse.get("access_token").getAsString();
                    // Save tokens to preferences for persistence
                    preferenceUtil.saveAccessToken(this.accessToken);
                    if (jsonResponse.has("refresh_token")) {
                        preferenceUtil.saveRefreshToken(jsonResponse.get("refresh_token").getAsString());
                    }
                    String userId = jsonResponse.getAsJsonObject("user").get("id").getAsString();
                    
                    // Fetch user data from users table
                    fetchUserData(userId, callback);
                } else {
                    if (response.code() == 429) {
                        callback.onError("Too many attempts. Please wait and try again.");
                    } else {
                        callback.onError("Invalid email or password");
                    }
                }
            } catch (java.net.UnknownHostException e) {
                Log.e(TAG, "Network DNS error", e);
                callback.onError("Can't reach Supabase. Check internet/Supabase URL.");
            } catch (Exception e) {
                Log.e(TAG, "Login error", e);
                callback.onError("Login failed: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * Step 1: Registration (matches PHP register.php flow)
     * 
     * Registration Process:
     * 1. User submits registration form (full name, email, phone, password, etc.)
     * 2. Validation: email, password format (handled by Supabase)
     * 3. Database insert:
     *    - User inserted into Supabase Auth with email confirmation enabled
     *    - User will be created in public.users table after OTP verification (is_verified = 0 initially)
     * 4. OTP generated and sent via email (Supabase handles this automatically)
     *    - OTP expiry: Configured in Supabase Dashboard (recommended: 10 minutes to match PHP)
     *    - Format: 6-digit numeric code
     * 5. Temporary data stored in preferences (similar to PHP $_SESSION['temp_user_id'], $_SESSION['temp_email'])
     * 6. Redirect to OTP verification screen
     * 
     * @param email User's email address
     * @param password User's password
     * @param fullName User's full name
     * @param phone User's phone number
     * @param address User's address
     * @param userType User type (customer/admin)
     * @param callback Success/error callback
     */
    public void signUp(String email, String password, String fullName, String phone, String address, String userType, AuthCallback callback) {
        if (!NetworkUtil.isNetworkAvailable(appContext)) {
            callback.onError("No internet connection");
            return;
        }
        
        new Thread(() -> {
            try {
                // Register user in Supabase Auth
                // This matches PHP: INSERT INTO users (email, password, is_verified) VALUES (?, ?, 0)
                JsonObject authBody = new JsonObject();
                authBody.addProperty("email", email);
                authBody.addProperty("password", password);

                Request authRequest = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/auth/v1/signup")
                    .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        authBody.toString()))
                    .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();

                Response authResponse = supabaseService.executeRequest(authRequest);
                String authResponseBody = authResponse.body() != null ? authResponse.body().string() : "";
                Log.d(TAG, "Signup response: " + authResponse.code() + " - " + authResponseBody);

                if (authResponse.isSuccessful()) {
                    // When email confirmation is enabled, Supabase does not return an access token.
                    // We proceed directly to OTP verification screen. The Auth user is already created
                    // in supabase/auth users, and the app will verify via 6-digit code.
                    // Supabase automatically sends OTP email (matches PHP: PHPMailer sends email with OTP)
                    // OTP expiry is configured in Supabase Dashboard (recommended: 10 minutes)
                    // This matches PHP: $otp_expiry = date('Y-m-d H:i:s', strtotime('+10 minutes'))
                    Log.d(TAG, "Signup successful, OTP should have been sent automatically");
                    callback.onSuccess(null, "");
                } else {
                    String friendlyMessage = authResponse.code() == 429
                        ? "You're sending too many signups. Please wait a minute and try again."
                        : "Registration failed";

                    if (!TextUtils.isEmpty(authResponseBody)) {
                        try {
                            JsonObject errorJson = supabaseService.getGson().fromJson(authResponseBody, JsonObject.class);
                            if (errorJson != null) {
                                if (errorJson.has("msg")) {
                                    friendlyMessage = errorJson.get("msg").getAsString();
                                } else if (errorJson.has("error_description")) {
                                    friendlyMessage = errorJson.get("error_description").getAsString();
                                }
                            }
                        } catch (Exception parseException) {
                            Log.w(TAG, "Unable to parse signup error: " + authResponseBody, parseException);
                        }
                    }

                    if (friendlyMessage != null && friendlyMessage.toLowerCase().contains("already")) {
                        friendlyMessage = "Account already exists";
                    }

                    callback.onError(friendlyMessage);
                }
            } catch (java.net.UnknownHostException e) {
                Log.e(TAG, "Network DNS error", e);
                callback.onError("Can't reach Supabase. Check internet/Supabase URL.");
            } catch (Exception e) {
                Log.e(TAG, "Sign up error", e);
                callback.onError("Sign up failed: " + e.getMessage());
            }
        }).start();
    }
    
    private void fetchUserData(String userId, AuthCallback callback) {
        try {
            Request request = supabaseService
                .createAuthenticatedRequest("users?id=eq." + userId, accessToken)
                .get()
                .build();
            
            Response response = supabaseService.executeRequest(request);
            
            if (response.isSuccessful()) {
                String responseBody = response.body().string();
                User[] users = supabaseService.getGson().fromJson(responseBody, User[].class);
                if (users.length > 0) {
                    User user = users[0];
                    JsonArray jsonArray = supabaseService.getGson().fromJson(responseBody, JsonArray.class);
                    JsonObject jsonUser = (jsonArray != null && jsonArray.size() > 0) ? jsonArray.get(0).getAsJsonObject() : null;

                    if (jsonUser != null) {
                        if ((user.getFullName() == null || user.getFullName().isEmpty()) && jsonUser.has("full_name") && !jsonUser.get("full_name").isJsonNull()) {
                            user.setFullName(jsonUser.get("full_name").getAsString());
                        }
                        if ((user.getPhone() == null || user.getPhone().isEmpty()) && jsonUser.has("phone") && !jsonUser.get("phone").isJsonNull()) {
                            user.setPhone(jsonUser.get("phone").getAsString());
                        }
                        if ((user.getAddress() == null || user.getAddress().isEmpty()) && jsonUser.has("address") && !jsonUser.get("address").isJsonNull()) {
                            user.setAddress(jsonUser.get("address").getAsString());
                        }
                        if ((user.getUserType() == null || user.getUserType().isEmpty()) && jsonUser.has("user_type") && !jsonUser.get("user_type").isJsonNull()) {
                            user.setUserType(jsonUser.get("user_type").getAsString());
                        }
                    }

                    String resolvedUserType = user.getUserType();
                    if (resolvedUserType == null || resolvedUserType.isEmpty()) {
                        // Check if user_type exists in JSON but wasn't parsed
                        if (jsonUser != null && jsonUser.has("user_type") && !jsonUser.get("user_type").isJsonNull()) {
                            resolvedUserType = jsonUser.get("user_type").getAsString();
                            user.setUserType(resolvedUserType);
                            Log.d(TAG, "Retrieved user_type from JSON: " + resolvedUserType);
                        } else {
                            resolvedUserType = "customer";
                            user.setUserType(resolvedUserType);
                            Log.w(TAG, "User type not found, defaulting to customer for user: " + userId);
                        }
                    } else {
                        Log.d(TAG, "User type from User object: " + resolvedUserType);
                    }

                    String resolvedName = user.getFullName();
                    if (resolvedName == null || resolvedName.isEmpty()) {
                        String email = user.getEmail() != null ? user.getEmail() : "";
                        resolvedName = email.contains("@") ? email.substring(0, email.indexOf('@')) : "User";
                        user.setFullName(resolvedName);
                    }

                    preferenceUtil.saveUserData(user.getId(), user.getEmail(), resolvedName, resolvedUserType);
                    // Clear temporary signup data after successful user creation/fetch
                    preferenceUtil.clearTempSignupData();
                    callback.onSuccess(user, accessToken);
                } else {
                    // User doesn't exist in users table - create it using temporary signup data
                    createUserInDatabase(userId, callback);
                }
            } else {
                callback.onError("Failed to fetch user data");
            }
        } catch (Exception e) {
            Log.e(TAG, "Fetch user error", e);
            callback.onError("Failed to fetch user data");
        }
    }
    
    private void createUserInDatabase(String userId, AuthCallback callback) {
        try {
            // Get temporary signup data
            String fullName = preferenceUtil.getTempFullName();
            String phone = preferenceUtil.getTempPhone();
            String address = preferenceUtil.getTempAddress();
            String userType = preferenceUtil.getTempUserType();
            
            // Get email from preferences (saved during OTP verification)
            String email = preferenceUtil.getUserEmail();
            
            // If no temp data, use defaults
            if (fullName == null || fullName.isEmpty()) {
                fullName = email.isEmpty() ? "User" : email.split("@")[0]; // Use email prefix as name
            }
            if (phone == null || phone.isEmpty()) {
                phone = "";
            }
            if (address == null || address.isEmpty()) {
                address = "";
            }
            if (userType == null || userType.isEmpty()) {
                userType = "customer";
            }
            
            // Create user in users table
            com.google.gson.JsonObject userJson = new com.google.gson.JsonObject();
            userJson.addProperty("id", userId);
            userJson.addProperty("email", email);
            userJson.addProperty("full_name", fullName);
            userJson.addProperty("phone", phone);
            userJson.addProperty("address", address);
            userJson.addProperty("user_type", userType);
            userJson.addProperty("is_verified", true);
            userJson.addProperty("cancellation_count", 0);
            
            Request createRequest = supabaseService
                .createAuthenticatedRequest("users", accessToken)
                .post(okhttp3.RequestBody.create(
                    okhttp3.MediaType.parse("application/json"),
                    userJson.toString()))
                .build();
            
            Response createResponse = supabaseService.executeRequest(createRequest);
            
            if (createResponse.isSuccessful()) {
                // User created successfully, now fetch it
                fetchUserData(userId, callback);
            } else {
                String errorBody = createResponse.body() != null ? createResponse.body().string() : "";
                Log.e(TAG, "Failed to create user: " + errorBody);
                // Try to fetch again in case user was created by trigger
                fetchUserData(userId, callback);
            }
        } catch (Exception e) {
            Log.e(TAG, "Create user error", e);
            // Try to fetch again in case user was created by trigger
            fetchUserData(userId, callback);
        }
    }
    
    public void logout() {
        this.accessToken = null;
        preferenceUtil.logout();
    }
    
    public String getAccessToken() {
        // First try to get from memory
        if (accessToken != null && !accessToken.isEmpty()) {
            return accessToken;
        }
        // If not in memory, try to get from preferences
        String savedToken = preferenceUtil.getAccessToken();
        if (savedToken != null && !savedToken.isEmpty()) {
            this.accessToken = savedToken; // Cache it in memory
            return savedToken;
        }
        return null;
    }
    
    /**
     * Refresh the access token using the stored refresh token
     * @param callback Success/error callback with new access token
     */
    public void refreshAccessToken(TokenCallback callback) {
        String refreshToken = preferenceUtil.getRefreshToken();
        if (refreshToken == null || refreshToken.isEmpty()) {
            callback.onError("No refresh token available. Please login again.");
            return;
        }
        
        if (!NetworkUtil.isNetworkAvailable(appContext)) {
            callback.onError("No internet connection");
            return;
        }
        
        new Thread(() -> {
            try {
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("refresh_token", refreshToken);
                
                Request request = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/auth/v1/token?grant_type=refresh_token")
                    .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        requestBody.toString()))
                    .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();
                
                Response response = supabaseService.executeRequest(request);
                String responseBody = response.body() != null ? response.body().string() : "";
                
                if (response.isSuccessful()) {
                    JsonObject jsonResponse = supabaseService.getGson().fromJson(responseBody, JsonObject.class);
                    String newAccessToken = jsonResponse.get("access_token").getAsString();
                    this.accessToken = newAccessToken;
                    preferenceUtil.saveAccessToken(newAccessToken);
                    
                    // Update refresh token if provided
                    if (jsonResponse.has("refresh_token")) {
                        preferenceUtil.saveRefreshToken(jsonResponse.get("refresh_token").getAsString());
                    }
                    
                    callback.onSuccess(newAccessToken);
                } else {
                    Log.e(TAG, "Token refresh failed: " + response.code() + " - " + responseBody);
                    callback.onError("Token refresh failed. Please login again.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Token refresh error", e);
                callback.onError("Token refresh error: " + e.getMessage());
            }
        }).start();
    }
}

