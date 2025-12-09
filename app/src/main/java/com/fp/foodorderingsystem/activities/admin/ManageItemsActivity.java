package com.fp.foodorderingsystem.activities.admin;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.adapters.AdminMenuItemAdapter;
import com.fp.foodorderingsystem.models.Category;
import com.fp.foodorderingsystem.models.FoodItem;
import com.fp.foodorderingsystem.services.AuthService;
import com.fp.foodorderingsystem.services.MenuItemService;
import com.fp.foodorderingsystem.services.MenuItemService.MenuItemCallback;
import com.fp.foodorderingsystem.services.MenuItemService.MenuItemsCallback;
import com.fp.foodorderingsystem.services.MenuItemService.SimpleCallback;
import com.fp.foodorderingsystem.services.SupabaseRealtimeClient;
import com.fp.foodorderingsystem.services.SupabaseRealtimeClient.RealtimeListener;
import com.fp.foodorderingsystem.services.SupabaseService;
import com.fp.foodorderingsystem.utils.ImageUtil;
import com.fp.foodorderingsystem.utils.PreferenceUtil;
import com.fp.foodorderingsystem.utils.ToastUtil;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.Request;
import okhttp3.Response;

public class ManageItemsActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener {
    private TextInputLayout inputNameLayout;
    private TextInputLayout inputDescriptionLayout;
    private TextInputLayout inputPriceLayout;
    private TextInputLayout inputStockLayout;
    private TextInputLayout inputCategoryLayout;
    private TextInputLayout inputImageLayout;
    private TextInputEditText inputName;
    private TextInputEditText inputDescription;
    private TextInputEditText inputPrice;
    private TextInputEditText inputStock;
    private TextInputEditText inputImagePath;
    private MaterialAutoCompleteTextView inputCategory;
    private SwitchMaterial switchAvailability;
    private MaterialButton btnSaveItem;
    private MaterialButton btnClearForm;
    private MaterialButton btnUploadImage;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView rvMenuItems;
    private ProgressBar progressBar;
    private TextView tvEmptyState;
    
    private ArrayAdapter<String> categoryAdapter;
    private final List<Category> categories = new ArrayList<>();
    private final Map<Integer, String> categoryLookup = new HashMap<>();
    private final List<FoodItem> menuItems = new ArrayList<>();
    private final Gson gson = new Gson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private MenuItemService menuItemService;
    private SupabaseService supabaseService;
    private SupabaseRealtimeClient realtimeClient;
    private AuthService authService;
    private PreferenceUtil preferenceUtil;
    private AdminMenuItemAdapter adapter;
    private ActivityResultLauncher<String> imagePickerLauncher;
    
    private FoodItem editingItem = null;
    private int selectedCategoryId = -1;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_items);
        
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        menuItemService = new MenuItemService(this);
        supabaseService = SupabaseService.getInstance(this);
        authService = new AuthService(this);
        preferenceUtil = new PreferenceUtil(this);
        realtimeClient = new SupabaseRealtimeClient();
        
        initViews();
        setupRecyclerView();
        setupListeners();
        setupImagePicker();
        
        loadCategories();
        loadMenuItems(true);
        subscribeToRealtimeUpdates();
    }
    
    private void initViews() {
        inputNameLayout = findViewById(R.id.inputNameLayout);
        inputDescriptionLayout = findViewById(R.id.inputDescriptionLayout);
        inputPriceLayout = findViewById(R.id.inputPriceLayout);
        inputStockLayout = findViewById(R.id.inputStockLayout);
        inputCategoryLayout = findViewById(R.id.inputCategoryLayout);
        inputImageLayout = findViewById(R.id.inputImageLayout);
        
        inputName = findViewById(R.id.inputName);
        inputDescription = findViewById(R.id.inputDescription);
        inputPrice = findViewById(R.id.inputPrice);
        inputStock = findViewById(R.id.inputStock);
        inputImagePath = findViewById(R.id.inputImagePath);
        inputCategory = findViewById(R.id.inputCategory);
        switchAvailability = findViewById(R.id.switchAvailability);
        btnSaveItem = findViewById(R.id.btnSaveItem);
        btnClearForm = findViewById(R.id.btnClearForm);
        btnUploadImage = findViewById(R.id.btnUploadImage);
        swipeRefreshLayout = findViewById(R.id.swipeRefresh);
        rvMenuItems = findViewById(R.id.rvMenuItems);
        progressBar = findViewById(R.id.progressBar);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        
        switchAvailability.setChecked(true);
    }
    
    private void setupRecyclerView() {
        adapter = new AdminMenuItemAdapter();
        adapter.setOnItemActionListener(new AdminMenuItemAdapter.OnItemActionListener() {
            @Override
            public void onToggleStatus(FoodItem item) {
                toggleItemStatus(item);
            }
            
            @Override
            public void onEdit(FoodItem item) {
                populateFormForEdit(item);
            }
            
            @Override
            public void onDelete(FoodItem item) {
                confirmDelete(item);
            }
        });
        
        rvMenuItems.setLayoutManager(new LinearLayoutManager(this));
        rvMenuItems.setAdapter(adapter);
    }
    
    private void setupListeners() {
        swipeRefreshLayout.setOnRefreshListener(this);
        inputCategory.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < categories.size()) {
                selectedCategoryId = categories.get(position).getId();
                inputCategoryLayout.setError(null);
            }
        });
        inputCategory.setOnClickListener(v -> inputCategory.showDropDown());
        inputCategory.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                inputCategory.showDropDown();
            }
        });
        
        btnSaveItem.setOnClickListener(v -> saveItem());
        btnClearForm.setOnClickListener(v -> clearForm());
        btnUploadImage.setOnClickListener(v -> selectImageFromDevice());
    }

    private void setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                uploadImageToStorage(uri);
            }
        });
    }

    private void selectImageFromDevice() {
        if (imagePickerLauncher != null) {
            imagePickerLauncher.launch("image/*");
        }
    }
    
    private void loadCategories() {
        new Thread(() -> {
            try {
                Request request = supabaseService.createRequest("categories?select=id,name&order=name")
                    .get().build();
                Response response = supabaseService.executeRequest(request);
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    Category[] fetched = gson.fromJson(json, Category[].class);
                    categories.clear();
                    categoryLookup.clear();
                    List<String> categoryNames = new ArrayList<>();
                    if (fetched != null) {
                        Collections.addAll(categories, fetched);
                        for (Category category : categories) {
                            categoryLookup.put(category.getId(), category.getName());
                            categoryNames.add(category.getName());
                        }
                    }
                    
                    mainHandler.post(() -> {
                        adapter.setCategoryLookup(categoryLookup);
                        categoryAdapter = new ArrayAdapter<>(ManageItemsActivity.this, android.R.layout.simple_list_item_1, categoryNames);
                        inputCategory.setAdapter(categoryAdapter);
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> ToastUtil.show(ManageItemsActivity.this, "Failed to load categories"));
            }
        }).start();
    }
    
    private void loadMenuItems(boolean showSpinner) {
        if (showSpinner) {
            showLoading(true);
        }
        
        menuItemService.getAllMenuItems(new MenuItemsCallback() {
            @Override
            public void onSuccess(List<FoodItem> items) {
                mainHandler.post(() -> {
                    menuItems.clear();
                    if (items != null) {
                        menuItems.addAll(items);
                    }
                    sortMenuItems();
                    adapter.setItems(new ArrayList<>(menuItems));
                    updateEmptyState();
                    showLoading(false);
                    swipeRefreshLayout.setRefreshing(false);
                });
            }
            
            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    ToastUtil.show(ManageItemsActivity.this, error);
                    showLoading(false);
                    swipeRefreshLayout.setRefreshing(false);
                });
            }
        });
    }
    
    private void saveItem() {
        clearErrors();
        String name = getTextValue(inputName);
        String description = getTextValue(inputDescription);
        String priceValue = getTextValue(inputPrice);
        String stockValue = getTextValue(inputStock);
        String imageValue = getTextValue(inputImagePath);
        String imagePublicUrl = TextUtils.isEmpty(imageValue)
                ? null
                : supabaseService.getPublicUrl(ImageUtil.BUCKET_FOOD_ITEMS, imageValue);
        
        boolean hasError = false;
        if (TextUtils.isEmpty(name)) {
            inputNameLayout.setError("Name is required");
            hasError = true;
        }
        
        double price = 0.0;
        if (TextUtils.isEmpty(priceValue)) {
            inputPriceLayout.setError("Price is required");
            hasError = true;
        } else {
            try {
                price = Double.parseDouble(priceValue);
                if (price <= 0) {
                    inputPriceLayout.setError("Enter a price greater than 0");
                    hasError = true;
                }
            } catch (NumberFormatException e) {
                inputPriceLayout.setError("Invalid price");
                hasError = true;
            }
        }
        
        if (selectedCategoryId < 0) {
            inputCategoryLayout.setError("Select a category");
            hasError = true;
        }

        int stock = 0;
        if (TextUtils.isEmpty(stockValue)) {
            inputStockLayout.setError("Stock is required");
            hasError = true;
        } else {
            try {
                stock = Integer.parseInt(stockValue);
                if (stock < 0) {
                    inputStockLayout.setError("Stock cannot be negative");
                    hasError = true;
                }
            } catch (NumberFormatException e) {
                inputStockLayout.setError("Invalid stock");
                hasError = true;
            }
        }
        
        if (hasError) {
            return;
        }
        
        FoodItem item = editingItem != null ? editingItem : new FoodItem();
        item.setName(name);
        item.setDescription(description);
        item.setPrice(price);
        item.setCategoryId(selectedCategoryId);
        item.setStatus(switchAvailability.isChecked() ? "available" : "unavailable");
        item.setStock(stock);
        item.setImagePath(TextUtils.isEmpty(imageValue) ? null : imageValue);
        item.setImageUrl(imagePublicUrl);
        
        showLoading(true);
        if (editingItem == null) {
            menuItemService.createMenuItem(item, new MenuItemCallback() {
                @Override
                public void onSuccess(FoodItem created) {
                    mainHandler.post(() -> {
                        ToastUtil.show(ManageItemsActivity.this, "Item created");
                        upsertLocalItem(created);
                        clearForm();
                        showLoading(false);
                    });
                }
                
                @Override
                public void onError(String error) {
                    mainHandler.post(() -> {
                        ToastUtil.show(ManageItemsActivity.this, error);
                        showLoading(false);
                    });
                }
            });
        } else {
            menuItemService.updateMenuItem(item, new MenuItemCallback() {
                @Override
                public void onSuccess(FoodItem updated) {
                    mainHandler.post(() -> {
                        ToastUtil.show(ManageItemsActivity.this, "Item updated");
                        upsertLocalItem(updated);
                        clearForm();
                        showLoading(false);
                    });
                }
                
                @Override
                public void onError(String error) {
                    mainHandler.post(() -> {
                        ToastUtil.show(ManageItemsActivity.this, error);
                        showLoading(false);
                    });
                }
            });
        }
    }
    
    private void toggleItemStatus(FoodItem item) {
        if (item == null) {
            return;
        }
        String newStatus = "available".equalsIgnoreCase(item.getStatus()) ? "unavailable" : "available";
        showLoading(true);
        menuItemService.updateStatus(item.getId(), newStatus, new MenuItemCallback() {
            @Override
            public void onSuccess(FoodItem updated) {
                mainHandler.post(() -> {
                    upsertLocalItem(updated);
                    showLoading(false);
                });
            }
            
            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    ToastUtil.show(ManageItemsActivity.this, error);
                    showLoading(false);
                });
            }
        });
    }
    
    private void confirmDelete(FoodItem item) {
        if (item == null) {
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Delete item")
            .setMessage("Remove \"" + item.getName() + "\" from the menu?")
            .setPositiveButton("Delete", (dialog, which) -> deleteItem(item))
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void deleteItem(FoodItem item) {
        showLoading(true);
        menuItemService.deleteMenuItem(item.getId(), new SimpleCallback() {
            @Override
            public void onSuccess() {
                mainHandler.post(() -> {
                    removeLocalItem(item.getId());
                    ToastUtil.show(ManageItemsActivity.this, "Item deleted");
                    showLoading(false);
                });
            }
            
            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    ToastUtil.show(ManageItemsActivity.this, error);
                    showLoading(false);
                });
            }
        });
    }
    
    private void populateFormForEdit(FoodItem item) {
        editingItem = item;
        btnSaveItem.setText("Update item");
        btnClearForm.setText("Cancel");
        
        inputName.setText(item.getName());
        inputDescription.setText(item.getDescription());
        inputPrice.setText(String.valueOf(item.getPrice()));
        if (item.getImagePath() != null) {
            inputImagePath.setText(item.getImagePath());
        } else if (item.getImageUrl() != null) {
            inputImagePath.setText(item.getImageUrl());
        } else {
            inputImagePath.setText("");
        }
        inputStock.setText(String.valueOf(item.getStock()));
        switchAvailability.setChecked("available".equalsIgnoreCase(item.getStatus()));
        selectedCategoryId = item.getCategoryId();
        String categoryName = categoryLookup.getOrDefault(item.getCategoryId(), "");
        inputCategory.setText(categoryName, false);
    }
    
    private void clearForm() {
        editingItem = null;
        btnSaveItem.setText("Save item");
        btnClearForm.setText("Clear");
        inputName.setText("");
        inputDescription.setText("");
        inputPrice.setText("");
        inputStock.setText("");
        inputImagePath.setText("");
        inputCategory.setText("", false);
        selectedCategoryId = -1;
        switchAvailability.setChecked(true);
        clearErrors();
    }
    
    private void clearErrors() {
        inputNameLayout.setError(null);
        inputDescriptionLayout.setError(null);
        inputPriceLayout.setError(null);
        inputStockLayout.setError(null);
        inputCategoryLayout.setError(null);
        inputImageLayout.setError(null);
    }
    
    private void upsertLocalItem(FoodItem item) {
        if (item == null) {
            return;
        }
        boolean found = false;
        for (int i = 0; i < menuItems.size(); i++) {
            if (menuItems.get(i).getId() == item.getId()) {
                menuItems.set(i, item);
                found = true;
                break;
            }
        }
        if (!found) {
            menuItems.add(0, item);
        }
        sortMenuItems();
        adapter.setItems(new ArrayList<>(menuItems));
        updateEmptyState();
    }
    
    private void removeLocalItem(int itemId) {
        for (int i = 0; i < menuItems.size(); i++) {
            if (menuItems.get(i).getId() == itemId) {
                menuItems.remove(i);
                break;
            }
        }
        sortMenuItems();
        adapter.setItems(new ArrayList<>(menuItems));
        updateEmptyState();
    }
    
    private void updateEmptyState() {
        boolean isEmpty = menuItems.isEmpty();
        tvEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvMenuItems.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }
    
    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    private void uploadImageToStorage(Uri uri) {
        showLoading(true);
        new Thread(() -> {
            try {
                byte[] bytes = readBytesFromUri(uri);
                if (bytes == null || bytes.length == 0) {
                    throw new IllegalStateException("Unable to read file");
                }
                String mimeType = getContentResolver().getType(uri);
                if (mimeType == null) {
                    mimeType = "image/jpeg";
                }
                String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                if (extension == null) {
                    extension = "jpg";
                }
                String fileName = "menu_" + System.currentTimeMillis() + "." + extension;
                String accessToken = authService.getAccessToken();
                if (TextUtils.isEmpty(accessToken)) {
                    accessToken = preferenceUtil.getAccessToken();
                }
                Response response = supabaseService.uploadFileToBucket(ImageUtil.BUCKET_FOOD_ITEMS, fileName, bytes, mimeType, accessToken);
                if (response.isSuccessful()) {
                    mainHandler.post(() -> {
                        inputImagePath.setText(fileName);
                        inputImageLayout.setError(null);
                        ToastUtil.show(ManageItemsActivity.this, "Image uploaded");
                        showLoading(false);
                    });
                } else {
                    String error = "Upload failed: " + response.code();
                    mainHandler.post(() -> {
                    ToastUtil.show(ManageItemsActivity.this, error);
                        showLoading(false);
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    ToastUtil.show(ManageItemsActivity.this, "Upload error: " + e.getMessage());
                    showLoading(false);
                });
            }
        }).start();
    }

    private byte[] readBytesFromUri(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) return null;
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[4096];
            int n;
            while ((n = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, n);
            }
            inputStream.close();
            return buffer.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
    
    private void sortMenuItems() {
        Collections.sort(menuItems, (a, b) -> {
            String updatedA = a != null ? a.getUpdatedAt() : null;
            String updatedB = b != null ? b.getUpdatedAt() : null;
            if (updatedA == null && updatedB == null) {
                return 0;
            }
            if (updatedA == null) {
                return 1;
            }
            if (updatedB == null) {
                return -1;
            }
            return updatedB.compareTo(updatedA);
        });
    }
    
    private void subscribeToRealtimeUpdates() {
        realtimeClient.subscribeToTable("public", "menu_items", new RealtimeListener() {
            @Override
            public void onOpen() {
                // No-op
            }
            
            @Override
            public void onChange(JsonObject payload) {
                handleRealtimePayload(payload);
            }
            
            @Override
            public void onError(String error) {
                mainHandler.post(() -> ToastUtil.show(ManageItemsActivity.this, "Realtime error: " + error));
            }
        });
    }
    
    private void handleRealtimePayload(JsonObject payload) {
        if (payload == null) {
            return;
        }
        String eventType = "";
        if (payload.has("eventType")) {
            eventType = payload.get("eventType").getAsString();
        } else if (payload.has("type")) {
            eventType = payload.get("type").getAsString();
        }
        
        JsonObject newRecord = null;
        if (payload.has("new")) {
            newRecord = payload.getAsJsonObject("new");
        } else if (payload.has("new_record")) {
            newRecord = payload.getAsJsonObject("new_record");
        }
        
        JsonObject oldRecord = null;
        if (payload.has("old")) {
            oldRecord = payload.getAsJsonObject("old");
        } else if (payload.has("old_record")) {
            oldRecord = payload.getAsJsonObject("old_record");
        }
        
        switch (eventType.toUpperCase()) {
            case "INSERT":
            case "UPDATE":
                if (newRecord != null) {
                    FoodItem updated = gson.fromJson(newRecord, FoodItem.class);
                    mainHandler.post(() -> upsertLocalItem(updated));
                }
                break;
            case "DELETE":
                if (oldRecord != null && oldRecord.has("id")) {
                    int id = oldRecord.get("id").getAsInt();
                    mainHandler.post(() -> removeLocalItem(id));
                }
                break;
            default:
                // Ignore other events
        }
    }
    
    @Override
    public void onRefresh() {
        loadMenuItems(false);
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (realtimeClient != null) {
            realtimeClient.disconnect();
        }
    }
    
    private String getTextValue(TextInputEditText editText) {
        return editText != null && editText.getText() != null
            ? editText.getText().toString().trim()
            : "";
    }
}

