package com.fp.foodorderingsystem.activities.customer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.activities.auth.LoginActivity;
import com.fp.foodorderingsystem.config.SupabaseConfig;
import com.fp.foodorderingsystem.models.User;
import com.fp.foodorderingsystem.services.AuthService;
import com.fp.foodorderingsystem.services.SupabaseRealtimeClient;
import com.fp.foodorderingsystem.services.SupabaseService;
import com.fp.foodorderingsystem.services.UserService;
import com.fp.foodorderingsystem.ui.FoodLoaderView;
import com.fp.foodorderingsystem.utils.PreferenceUtil;
import com.fp.foodorderingsystem.utils.ToastUtil;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ProfileActivity extends AppCompatActivity {
    private TextView tvName, tvEmail, tvPhone, tvAddress, tvUserType;
    private ImageView ivProfilePicture;
    private PreferenceUtil preferenceUtil;
    private AuthService authService;
    private UserService userService;
    private SupabaseService supabaseService;
    private User currentUser;
    private FoodLoaderView loaderView;
    private SupabaseRealtimeClient realtimeClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String BUCKET_NAME = "profile-pictures";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_profile);

        preferenceUtil = new PreferenceUtil(this);
        authService = new AuthService(this);
        userService = new UserService(this);
        supabaseService = SupabaseService.getInstance(this);

        if (!preferenceUtil.isLoggedIn()) {
            finish();
            return;
        }

        initViews();
        loadUserData();
        setupClickListeners();
        setupBottomNavigation();
        subscribeToRealtimeUpdates();
    }

    private void initViews() {
        tvName = findViewById(R.id.tvName);
        tvEmail = findViewById(R.id.tvEmail);
        tvPhone = findViewById(R.id.tvPhone);
        tvAddress = findViewById(R.id.tvAddress);
        tvUserType = findViewById(R.id.tvUserType);
        ivProfilePicture = findViewById(R.id.ivProfilePicture);
        loaderView = findViewById(R.id.loaderView);

        findViewById(R.id.toolbar).setOnClickListener(v -> onBackPressed());
        
        // Set up profile picture click listener
        ivProfilePicture.setOnClickListener(v -> showImagePickerDialog());
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Intent intent = new Intent(this, CustomerDashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.menuProfile);
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menuHome) {
                startActivity(new Intent(this, CustomerDashboardActivity.class));
                finish();
                return true;
            } else if (itemId == R.id.menuMenu) {
                startActivity(new Intent(this, MenuActivity.class));
                finish();
                return true;
            } else if (itemId == R.id.menuCart) {
                startActivity(new Intent(this, CartActivity.class));
                finish();
                return true;
            } else if (itemId == R.id.menuOrders) {
                startActivity(new Intent(this, OrderHistoryActivity.class));
                finish();
                return true;
            } else if (itemId == R.id.menuProfile) {
                return true;
            }
            return false;
        });
    }

    private void loadUserData() {
        // Show loading
        if (loaderView != null) {
            loaderView.showLoader();
        }
        
        // Load basic data from preferences first
        String prefName = preferenceUtil.getUserName();
        String prefEmail = preferenceUtil.getUserEmail();
        String prefUserType = preferenceUtil.getUserType();
        
        // Set initial values with fallbacks
        tvName.setText(!TextUtils.isEmpty(prefName) ? prefName : "User");
        tvEmail.setText(!TextUtils.isEmpty(prefEmail) ? prefEmail : "No email");
        tvUserType.setText(!TextUtils.isEmpty(prefUserType) ? prefUserType : "Customer");
        
        // Fetch full user data from Supabase
        String userId = preferenceUtil.getUserId();
        String accessToken = authService.getAccessToken();
        if (TextUtils.isEmpty(accessToken)) {
            accessToken = preferenceUtil.getAccessToken();
        }
        
        if (!TextUtils.isEmpty(userId)) {
            loadUserDataWithToken(userId, accessToken);
        } else {
            if (loaderView != null) {
                loaderView.hideLoader();
            }
        }
    }
    
    private void loadUserDataWithToken(String userId, String accessToken) {
            userService.getUserById(userId, accessToken, new UserService.UserCallback() {
                @Override
                public void onSuccess(User user) {
                    mainHandler.post(() -> {
                        currentUser = user;
                        updateUI(user);
                        if (loaderView != null) {
                            loaderView.hideLoader();
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                // Check if it's a 401 error (authentication failed)
                if (error != null && error.contains("401")) {
                    android.util.Log.d("ProfileActivity", "401 error detected, attempting to refresh token...");
                    // Try to refresh the token and retry
                    authService.refreshAccessToken(new AuthService.TokenCallback() {
                        @Override
                        public void onSuccess(String newAccessToken) {
                            android.util.Log.d("ProfileActivity", "Token refreshed successfully, retrying user data load...");
                            // Retry with new token
                            loadUserDataWithToken(userId, newAccessToken);
                        }
                        
                        @Override
                        public void onError(String refreshError) {
                            android.util.Log.e("ProfileActivity", "Token refresh failed: " + refreshError);
                    mainHandler.post(() -> {
                        // Still show basic data from preferences
                        if (currentUser != null) {
                            tvPhone.setText(TextUtils.isEmpty(currentUser.getPhone()) ? "Not set" : currentUser.getPhone());
                            tvAddress.setText(TextUtils.isEmpty(currentUser.getAddress()) ? "Not set" : currentUser.getAddress());
                                    // Try to load profile picture from cached user data
                                    loadProfilePicture(currentUser);
                        } else {
                            tvPhone.setText("Not set");
                            tvAddress.setText("Not set");
                        }
                        if (loaderView != null) {
                            loaderView.hideLoader();
                        }
                        android.util.Log.e("ProfileActivity", "Failed to load user data: " + error);
                    });
                }
            });
        } else {
                    mainHandler.post(() -> {
                        // Still show basic data from preferences
                        if (currentUser != null) {
                            tvPhone.setText(TextUtils.isEmpty(currentUser.getPhone()) ? "Not set" : currentUser.getPhone());
                            tvAddress.setText(TextUtils.isEmpty(currentUser.getAddress()) ? "Not set" : currentUser.getAddress());
                            // Try to load profile picture from cached user data
                            loadProfilePicture(currentUser);
                        } else {
                            tvPhone.setText("Not set");
                            tvAddress.setText("Not set");
                        }
            if (loaderView != null) {
                loaderView.hideLoader();
            }
                        android.util.Log.e("ProfileActivity", "Failed to load user data: " + error);
                    });
        }
            }
        });
    }
    
    private void updateUI(User user) {
        if (user == null) return;
        
        // Get name with multiple fallbacks
        String displayName = user.getFullName();
        if (TextUtils.isEmpty(displayName)) {
            displayName = preferenceUtil.getUserName();
        }
        if (TextUtils.isEmpty(displayName)) {
            String email = user.getEmail();
            if (!TextUtils.isEmpty(email) && email.contains("@")) {
                displayName = email.substring(0, email.indexOf("@"));
            } else {
                displayName = "User";
            }
        }
        tvName.setText(displayName);
        
        // Get email with fallback
        String displayEmail = user.getEmail();
        if (TextUtils.isEmpty(displayEmail)) {
            displayEmail = preferenceUtil.getUserEmail();
        }
        if (TextUtils.isEmpty(displayEmail)) {
            displayEmail = "No email";
        }
        tvEmail.setText(displayEmail);
        
        // Get user type with fallback
        String displayUserType = user.getUserType();
        if (TextUtils.isEmpty(displayUserType)) {
            displayUserType = preferenceUtil.getUserType();
        }
        if (TextUtils.isEmpty(displayUserType)) {
            displayUserType = "Customer";
        }
        tvUserType.setText(displayUserType);
        
        // Phone and address
        tvPhone.setText(TextUtils.isEmpty(user.getPhone()) ? "Not set" : user.getPhone());
        tvAddress.setText(TextUtils.isEmpty(user.getAddress()) ? "Not set" : user.getAddress());
        
        // Load profile picture
        loadProfilePicture(user);
    }
    
    private void loadProfilePicture(User user) {
        if (ivProfilePicture == null) return;
        
        // If user is null, try to use currentUser as fallback
        if (user == null) {
            user = currentUser;
        }
        
        if (user == null) {
            android.util.Log.d("ProfileActivity", "No user data available, using default avatar");
            ivProfilePicture.setImageResource(R.drawable.ic_food_banner);
            return;
        }
        
        String imageUrl = null;
        
        // First, try to use profile_picture_url if it exists (full URL)
        String profilePictureUrl = user.getProfilePictureUrl();
        if (!TextUtils.isEmpty(profilePictureUrl)) {
            imageUrl = profilePictureUrl;
            android.util.Log.d("ProfileActivity", "Using profile_picture_url from database: " + imageUrl);
        } else {
            // Fall back to constructing URL from profile_picture path
            String profilePicture = user.getProfilePicture();
        if (!TextUtils.isEmpty(profilePicture)) {
                // If it's already a full URL, use it directly
                if (profilePicture.startsWith("http://") || profilePicture.startsWith("https://")) {
                    imageUrl = profilePicture;
                    android.util.Log.d("ProfileActivity", "Profile picture is already a full URL: " + imageUrl);
                } else {
            // Construct the public URL for the profile picture
                    imageUrl = supabaseService.getPublicUrl(BUCKET_NAME, profilePicture);
                    android.util.Log.d("ProfileActivity", "Constructed profile picture URL from path: " + imageUrl);
                }
            }
        }
        
        if (!TextUtils.isEmpty(imageUrl)) {
            // Create a final copy for use in inner class
            final String finalImageUrl = imageUrl;
            android.util.Log.d("ProfileActivity", "Loading profile picture from URL: " + finalImageUrl);
            
            Glide.with(this)
                .load(finalImageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_food_banner)
                .error(R.drawable.ic_food_banner)
                .circleCrop()
                .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                    @Override
                    public boolean onLoadFailed(@androidx.annotation.Nullable com.bumptech.glide.load.engine.GlideException e, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                        android.util.Log.e("ProfileActivity", "Failed to load profile picture: " + (e != null ? e.getMessage() : "Unknown error") + ", URL: " + finalImageUrl);
                        return false;
                    }
                    
                    @Override
                    public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                        android.util.Log.d("ProfileActivity", "Profile picture loaded successfully");
                        return false;
                    }
                })
                .into(ivProfilePicture);
        } else {
            android.util.Log.d("ProfileActivity", "No profile picture path or URL, using default avatar");
            // Use default avatar
            ivProfilePicture.setImageResource(R.drawable.ic_food_banner);
        }
    }

    private void setupClickListeners() {
        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            authService.logout();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.btnEditProfile).setOnClickListener(v -> showEditProfileDialog());
        
        findViewById(R.id.btnDeleteAccount).setOnClickListener(v -> showDeleteAccountDialog());
    }
    
    private void showImagePickerDialog() {
        // Camera option removed - requires privacy policy for Google Play
        // Can be re-enabled after adding privacy policy URL in Google Play Console
        String[] options = {"Choose from Gallery", "Cancel"};
        new AlertDialog.Builder(this)
            .setTitle("Select Profile Picture")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    // Choose from Gallery
                    if (checkStoragePermission()) {
                        openGallery();
                    } else {
                        requestStoragePermission();
                    }
                }
            })
            .show();
    }
    
    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }
    
    private boolean checkStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE);
    }
    
    private void requestStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_IMAGES}, PERMISSION_REQUEST_CODE);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
        }
    }
    
    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Bundle extras = result.getData().getExtras();
                if (extras != null) {
                    Bitmap imageBitmap = (Bitmap) extras.get("data");
                    if (imageBitmap != null) {
                        uploadImage(imageBitmap);
                    }
                }
            }
        }
    );
    
    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri imageUri = result.getData().getData();
                if (imageUri != null) {
                    try {
                        Bitmap imageBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                        uploadImage(imageBitmap);
                    } catch (IOException e) {
                        android.util.Log.e("ProfileActivity", "Error loading image from gallery", e);
                        ToastUtil.show(this, "Failed to load image");
                    }
                }
            }
        }
    );
    
    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            cameraLauncher.launch(takePictureIntent);
        } else {
            ToastUtil.show(this, "Camera not available");
        }
    }
    
    private void openGallery() {
        Intent pickPhoto = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(pickPhoto);
    }
    
    private void uploadImage(Bitmap bitmap) {
        String userId = preferenceUtil.getUserId();
        if (TextUtils.isEmpty(userId)) {
            ToastUtil.show(this, "User ID not found. Please login again.");
            return;
        }
        
        // If currentUser is null, try to load it but don't block the upload
        if (currentUser == null) {
            // Try to load user data in background
            loadUserData();
        }
        
        String accessToken = authService.getAccessToken();
        if (TextUtils.isEmpty(accessToken)) {
            accessToken = preferenceUtil.getAccessToken();
        }
        
        if (TextUtils.isEmpty(accessToken)) {
            ToastUtil.show(this, "Authentication token not found. Please login again.");
            return;
        }
        
        uploadImageWithToken(bitmap, accessToken);
    }
    
    private void uploadImageWithToken(Bitmap bitmap, String accessToken) {
        String userId = preferenceUtil.getUserId();
        
        // Show loading
        if (loaderView != null) {
            loaderView.showLoader();
        }
        
        // Compress and convert bitmap to byte array
        final String finalUserId = userId;
        final String finalAccessToken = accessToken;
        new Thread(() -> {
            try {
                // Resize image to max 800x800 to save storage
                Bitmap resizedBitmap = resizeBitmap(bitmap, 800, 800);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
                byte[] imageData = baos.toByteArray();
                
                // Generate unique filename
                String fileName = "profile_" + finalUserId + "_" + System.currentTimeMillis() + ".jpg";
                
                // Upload to Supabase Storage
                okhttp3.Response response = supabaseService.uploadFileToBucket(
                    BUCKET_NAME,
                    fileName,
                    imageData,
                    "image/jpeg",
                    finalAccessToken
                );
                
                if (response.isSuccessful()) {
                    // Update user record with profile picture path
                    updateProfilePictureWithToken(finalUserId, fileName, finalAccessToken);
                } else {
                    // Check if it's a 401 error
                    if (response.code() == 401) {
                        android.util.Log.d("ProfileActivity", "401 error on upload, refreshing token...");
                        authService.refreshAccessToken(new AuthService.TokenCallback() {
                        @Override
                            public void onSuccess(String newAccessToken) {
                                android.util.Log.d("ProfileActivity", "Token refreshed, retrying upload...");
                                // Retry upload with new token
                                uploadImageWithToken(bitmap, newAccessToken);
                        }
                        
                        @Override
                            public void onError(String refreshError) {
                            mainHandler.post(() -> {
                                if (loaderView != null) {
                                    loaderView.hideLoader();
                                }
                                    ToastUtil.show(ProfileActivity.this, "Authentication failed. Please login again.");
                            });
                        }
                    });
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    mainHandler.post(() -> {
                        if (loaderView != null) {
                            loaderView.hideLoader();
                        }
                        android.util.Log.e("ProfileActivity", "Upload failed: " + response.code() + " - " + errorBody);
                        ToastUtil.show(ProfileActivity.this, "Failed to upload image: " + response.code());
                    });
                    }
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (loaderView != null) {
                        loaderView.hideLoader();
                    }
                    android.util.Log.e("ProfileActivity", "Error uploading image", e);
                    ToastUtil.show(ProfileActivity.this, "Error uploading image: " + e.getMessage());
                });
            }
        }).start();
    }
    
    private Bitmap resizeBitmap(Bitmap bitmap, int maxWidth, int maxHeight) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        if (width <= maxWidth && height <= maxHeight) {
            return bitmap;
        }
        
        float scale = Math.min((float) maxWidth / width, (float) maxHeight / height);
        int newWidth = Math.round(width * scale);
        int newHeight = Math.round(height * scale);
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
    }
    
    private void updateProfilePictureWithToken(String userId, String fileName, String accessToken) {
        userService.updateProfilePicture(userId, fileName, accessToken, new UserService.UserCallback() {
            @Override
            public void onSuccess(User updatedUser) {
                mainHandler.post(() -> {
                    currentUser = updatedUser;
                    loadProfilePicture(updatedUser);
                    if (loaderView != null) {
                        loaderView.hideLoader();
                    }
                    ToastUtil.show(ProfileActivity.this, "Profile picture updated successfully");
                });
            }
            
            @Override
            public void onError(String error) {
                // Check if it's a 401 error
                if (error != null && error.contains("401")) {
                    android.util.Log.d("ProfileActivity", "401 error on update profile picture, refreshing token...");
                    authService.refreshAccessToken(new AuthService.TokenCallback() {
                        @Override
                        public void onSuccess(String newAccessToken) {
                            android.util.Log.d("ProfileActivity", "Token refreshed, retrying update profile picture...");
                            // Retry with new token
                            updateProfilePictureWithToken(userId, fileName, newAccessToken);
                        }
                        
                        @Override
                        public void onError(String refreshError) {
                            mainHandler.post(() -> {
                                if (loaderView != null) {
                                    loaderView.hideLoader();
                                }
                                ToastUtil.show(ProfileActivity.this, "Authentication failed. Please login again.");
                            });
                        }
                    });
                } else {
                    mainHandler.post(() -> {
                        if (loaderView != null) {
                            loaderView.hideLoader();
                        }
                        ToastUtil.show(ProfileActivity.this, "Failed to update profile picture: " + error);
                    });
                }
            }
        });
    }
    
    private void subscribeToRealtimeUpdates() {
        String userId = preferenceUtil.getUserId();
        if (TextUtils.isEmpty(userId)) {
            return;
        }
        
        realtimeClient = new SupabaseRealtimeClient();
        realtimeClient.subscribeToTable("public", "users", new SupabaseRealtimeClient.RealtimeListener() {
            @Override
            public void onOpen() {
                android.util.Log.d("ProfileActivity", "Realtime connection established");
            }
            
            @Override
            public void onChange(JsonObject payload) {
                handleRealtimePayload(payload);
            }
            
            @Override
            public void onError(String error) {
                android.util.Log.e("ProfileActivity", "Realtime error: " + error);
            }
        });
    }
    
    private void handleRealtimePayload(JsonObject payload) {
        if (payload == null) {
            return;
        }
        
        String userId = preferenceUtil.getUserId();
        if (TextUtils.isEmpty(userId)) {
            return;
        }
        
        String eventType = "";
        if (payload.has("eventType")) {
            eventType = payload.get("eventType").getAsString();
        } else if (payload.has("type")) {
            eventType = payload.get("type").getAsString();
        }
        
        JsonObject newRecord = null;
        JsonObject oldRecord = null;
        
        if (payload.has("new")) {
            newRecord = payload.getAsJsonObject("new");
        } else if (payload.has("new_record")) {
            newRecord = payload.getAsJsonObject("new_record");
        }
        
        if (payload.has("old")) {
            oldRecord = payload.getAsJsonObject("old");
        } else if (payload.has("old_record")) {
            oldRecord = payload.getAsJsonObject("old_record");
        }
        
        String recordUserId = null;
        if (newRecord != null && newRecord.has("id")) {
            recordUserId = newRecord.get("id").getAsString();
        } else if (oldRecord != null && oldRecord.has("id")) {
            recordUserId = oldRecord.get("id").getAsString();
        }
        
        // Only process if this is the current user
        if (!userId.equals(recordUserId)) {
            return;
        }
        
        if ("UPDATE".equalsIgnoreCase(eventType) && newRecord != null) {
            // Handle user update
            try {
                com.google.gson.Gson gson = new com.google.gson.Gson();
                User updatedUser = gson.fromJson(newRecord, User.class);
                if (updatedUser != null) {
                    mainHandler.post(() -> {
                        currentUser = updatedUser;
                        updateUI(updatedUser);
                        
                        // Update preferences with latest data
                        preferenceUtil.saveUserData(
                            updatedUser.getId(),
                            updatedUser.getEmail(),
                            updatedUser.getFullName(),
                            updatedUser.getUserType()
                        );
                        
                        android.util.Log.d("ProfileActivity", "Profile updated via realtime");
                    });
                }
            } catch (Exception e) {
                android.util.Log.e("ProfileActivity", "Error parsing user from realtime payload", e);
                // Fallback: reload user data
                mainHandler.post(() -> loadUserData());
            }
        } else if ("INSERT".equalsIgnoreCase(eventType) && newRecord != null) {
            // Handle new user creation (shouldn't happen for existing user, but handle it)
            try {
                com.google.gson.Gson gson = new com.google.gson.Gson();
                User newUser = gson.fromJson(newRecord, User.class);
                if (newUser != null) {
                    mainHandler.post(() -> {
                        currentUser = newUser;
                        updateUI(newUser);
                    });
                }
            } catch (Exception e) {
                android.util.Log.e("ProfileActivity", "Error parsing new user from realtime payload", e);
            }
        } else if ("DELETE".equalsIgnoreCase(eventType)) {
            // Handle user deletion
            mainHandler.post(() -> {
                android.util.Log.d("ProfileActivity", "User account deleted via realtime");
                // Clear data and logout
                preferenceUtil.clearAll();
                authService.logout();
                Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, show dialog again
                showImagePickerDialog();
            } else {
                ToastUtil.show(this, "Permission denied");
            }
        }
    }

    private void showEditProfileDialog() {
        // If currentUser is null, try to load it but don't block the dialog
        if (currentUser == null) {
            loadUserData();
        }
        
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile, null);
        
        TextInputEditText etFullName = dialogView.findViewById(R.id.etFullName);
        TextInputEditText etEmail = dialogView.findViewById(R.id.etEmail);
        TextInputEditText etPhone = dialogView.findViewById(R.id.etPhone);
        TextInputEditText etAddress = dialogView.findViewById(R.id.etAddress);
        
        // Populate fields from currentUser if available, otherwise use preferences or UI values
        if (currentUser != null) {
        etFullName.setText(currentUser.getFullName() != null ? currentUser.getFullName() : "");
        etEmail.setText(currentUser.getEmail() != null ? currentUser.getEmail() : "");
        etPhone.setText(currentUser.getPhone() != null ? currentUser.getPhone() : "");
        etAddress.setText(currentUser.getAddress() != null ? currentUser.getAddress() : "");
        } else {
            // Use data from preferences or UI
            etFullName.setText(tvName.getText().toString());
            etEmail.setText(tvEmail.getText().toString());
            etPhone.setText(tvPhone.getText().toString().equals("Not set") ? "" : tvPhone.getText().toString());
            etAddress.setText(tvAddress.getText().toString().equals("Not set") ? "" : tvAddress.getText().toString());
        }
        
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setView(dialogView)
            .setCancelable(true)
            .create();
        
        // Make dialog fill more of the screen and look modern
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.9),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
        
        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnSave).setOnClickListener(v -> {
            String fullName = etFullName.getText() != null ? etFullName.getText().toString().trim() : "";
            String phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
            String address = etAddress.getText() != null ? etAddress.getText().toString().trim() : "";
            
            if (TextUtils.isEmpty(fullName)) {
                ToastUtil.show(this, "Full name is required");
                return;
            }
            
            saveProfile(fullName, phone, address, dialog);
        });
        
        dialog.show();
    }
    
    private void saveProfile(String fullName, String phone, String address, AlertDialog dialog) {
        String userId = preferenceUtil.getUserId();
        String accessToken = authService.getAccessToken();
        if (TextUtils.isEmpty(accessToken)) {
            accessToken = preferenceUtil.getAccessToken();
        }
        
        if (TextUtils.isEmpty(userId)) {
            ToastUtil.show(this, "User ID not found");
            return;
        }
        
        // Show loading
        if (loaderView != null) {
            loaderView.showLoader();
        }
        dialog.dismiss();
        
        saveProfileWithToken(userId, fullName, phone, address, accessToken);
    }
    
    private void saveProfileWithToken(String userId, String fullName, String phone, String address, String accessToken) {
        userService.updateUserProfile(userId, fullName, phone, address, accessToken, new UserService.UserCallback() {
            @Override
            public void onSuccess(User updatedUser) {
                mainHandler.post(() -> {
                    currentUser = updatedUser;
                    
                    // Update preferences
                    preferenceUtil.saveUserData(
                        updatedUser.getId(),
                        updatedUser.getEmail(),
                        updatedUser.getFullName(),
                        updatedUser.getUserType()
                    );
                    
                    // Update UI
                    updateUI(updatedUser);
                    
                    if (loaderView != null) {
                        loaderView.hideLoader();
                    }
                    
                    ToastUtil.show(ProfileActivity.this, "Profile updated successfully");
                });
            }
            
            @Override
            public void onError(String error) {
                // Check if it's a 401 error
                if (error != null && error.contains("401")) {
                    android.util.Log.d("ProfileActivity", "401 error on save profile, refreshing token...");
                    authService.refreshAccessToken(new AuthService.TokenCallback() {
                        @Override
                        public void onSuccess(String newAccessToken) {
                            android.util.Log.d("ProfileActivity", "Token refreshed, retrying save profile...");
                            // Retry with new token
                            saveProfileWithToken(userId, fullName, phone, address, newAccessToken);
                        }
                        
                        @Override
                        public void onError(String refreshError) {
                            mainHandler.post(() -> {
                                if (loaderView != null) {
                                    loaderView.hideLoader();
                                }
                                ToastUtil.show(ProfileActivity.this, "Authentication failed. Please login again.");
                            });
                        }
                    });
                } else {
                mainHandler.post(() -> {
                    if (loaderView != null) {
                        loaderView.hideLoader();
                    }
                    ToastUtil.show(ProfileActivity.this, "Failed to update profile: " + error);
                });
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!preferenceUtil.isLoggedIn()) {
            finish();
            return;
        }
        // Reload user data to ensure we have the latest
        loadUserData();
        // Reconnect realtime if needed
        if (realtimeClient == null) {
            subscribeToRealtimeUpdates();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Keep realtime connection alive, but we can disconnect if needed
        // Realtime will reconnect automatically when activity resumes
    }
    
    private void showDeleteAccountDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Delete Account")
            .setMessage("Are you sure you want to delete your account? This action cannot be undone. All your data including orders, profile information, and preferences will be permanently deleted.")
            .setPositiveButton("Delete", (dialog, which) -> {
                deleteAccount();
            })
            .setNegativeButton("Cancel", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
    }
    
    private void deleteAccount() {
        String userId = preferenceUtil.getUserId();
        String accessToken = authService.getAccessToken();
        if (TextUtils.isEmpty(accessToken)) {
            accessToken = preferenceUtil.getAccessToken();
        }
        
        if (TextUtils.isEmpty(userId)) {
            ToastUtil.show(this, "User ID not found");
            return;
        }
        
        // Show loading
        if (loaderView != null) {
            loaderView.showLoader();
        }
        
        // Disconnect realtime client before deletion
        if (realtimeClient != null) {
            realtimeClient.disconnect();
        }
        
        userService.deleteUser(userId, accessToken, new UserService.SimpleCallback() {
            @Override
            public void onSuccess() {
                mainHandler.post(() -> {
                    // Clear all local data
                    preferenceUtil.clearAll();
                    
                    // Logout
                    authService.logout();
                    
                    if (loaderView != null) {
                        loaderView.hideLoader();
                    }
                    
                    ToastUtil.show(ProfileActivity.this, "Account deleted successfully");
                    
                    // Redirect to login
                    Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
            }
            
            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    if (loaderView != null) {
                        loaderView.hideLoader();
                    }
                    ToastUtil.show(ProfileActivity.this, "Failed to delete account: " + error);
                    android.util.Log.e("ProfileActivity", "Delete account error: " + error);
                });
            }
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (realtimeClient != null) {
            realtimeClient.disconnect();
        }
    }
}


