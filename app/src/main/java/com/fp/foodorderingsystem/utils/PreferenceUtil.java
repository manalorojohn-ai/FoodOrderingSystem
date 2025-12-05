package com.fp.foodorderingsystem.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceUtil {
    private static final String PREFS_NAME = "FoodOrderingPrefs";
    
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_TYPE = "user_type";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_LAST_NOTIFICATION = "last_notification";
    private static final String KEY_LAST_NOTIFICATION_TIME = "last_notification_time";
    
    // Temporary signup data (cleared after user creation)
    private static final String KEY_TEMP_FULL_NAME = "temp_full_name";
    private static final String KEY_TEMP_PHONE = "temp_phone";
    private static final String KEY_TEMP_ADDRESS = "temp_address";
    private static final String KEY_TEMP_USER_TYPE = "temp_user_type";
    
    private SharedPreferences prefs;
    
    public PreferenceUtil(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    // User data
    public void saveUserData(String userId, String email, String name, String userType) {
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_TYPE, userType)
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .apply();
    }
    
    public String getUserId() {
        return prefs.getString(KEY_USER_ID, "");
    }
    
    public String getUserEmail() {
        return prefs.getString(KEY_USER_EMAIL, "");
    }
    
    public String getUserName() {
        return prefs.getString(KEY_USER_NAME, "");
    }
    
    public String getUserType() {
        return prefs.getString(KEY_USER_TYPE, "customer");
    }
    
    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }
    
    public void logout() {
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_TYPE)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .apply();
    }
    
    // Access token storage
    public void saveAccessToken(String accessToken) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .apply();
    }
    
    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, "");
    }
    
    public void saveRefreshToken(String refreshToken) {
        prefs.edit()
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply();
    }
    
    public String getRefreshToken() {
        return prefs.getString(KEY_REFRESH_TOKEN, "");
    }
    
    // Notification caching
    public void saveLastNotification(String notificationJson, long timestamp) {
        prefs.edit()
            .putString(KEY_LAST_NOTIFICATION, notificationJson)
            .putLong(KEY_LAST_NOTIFICATION_TIME, timestamp)
            .apply();
    }
    
    public String getLastNotification() {
        return prefs.getString(KEY_LAST_NOTIFICATION, "");
    }
    
    public long getLastNotificationTime() {
        return prefs.getLong(KEY_LAST_NOTIFICATION_TIME, 0);
    }
    
    public void clearLastNotification() {
        prefs.edit()
            .remove(KEY_LAST_NOTIFICATION)
            .remove(KEY_LAST_NOTIFICATION_TIME)
            .apply();
    }
    
    // Temporary signup data storage (for user creation after OTP verification)
    public void saveTempSignupData(String fullName, String phone, String address, String userType) {
        prefs.edit()
            .putString(KEY_TEMP_FULL_NAME, fullName)
            .putString(KEY_TEMP_PHONE, phone)
            .putString(KEY_TEMP_ADDRESS, address)
            .putString(KEY_TEMP_USER_TYPE, userType)
            .apply();
    }
    
    public String getTempFullName() {
        return prefs.getString(KEY_TEMP_FULL_NAME, "");
    }
    
    public String getTempPhone() {
        return prefs.getString(KEY_TEMP_PHONE, "");
    }
    
    public String getTempAddress() {
        return prefs.getString(KEY_TEMP_ADDRESS, "");
    }
    
    public String getTempUserType() {
        return prefs.getString(KEY_TEMP_USER_TYPE, "customer");
    }
    
    public void clearTempSignupData() {
        prefs.edit()
            .remove(KEY_TEMP_FULL_NAME)
            .remove(KEY_TEMP_PHONE)
            .remove(KEY_TEMP_ADDRESS)
            .remove(KEY_TEMP_USER_TYPE)
            .apply();
    }
}

