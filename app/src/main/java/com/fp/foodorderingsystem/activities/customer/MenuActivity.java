package com.fp.foodorderingsystem.activities.customer;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.adapters.CategoryAdapter;
import com.fp.foodorderingsystem.adapters.FoodItemAdapter;
import com.fp.foodorderingsystem.models.CartItem;
import com.fp.foodorderingsystem.models.Category;
import com.fp.foodorderingsystem.models.FoodItem;
import com.fp.foodorderingsystem.services.CartService;
import com.fp.foodorderingsystem.services.CartService.CartItemCallback;
import com.fp.foodorderingsystem.services.CartService.CartItemsCallback;
import com.fp.foodorderingsystem.services.SupabaseRealtimeClient;
import com.fp.foodorderingsystem.services.SupabaseRealtimeClient.RealtimeListener;
import com.fp.foodorderingsystem.services.SupabaseService;
import com.fp.foodorderingsystem.utils.NetworkUtil;
import com.fp.foodorderingsystem.utils.PreferenceUtil;
import com.fp.foodorderingsystem.utils.ToastUtil;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Request;
import okhttp3.Response;

public class MenuActivity extends AppCompatActivity {
    private RecyclerView rvCategories, rvMenuItems;
    private CategoryAdapter categoryAdapter;
    private FoodItemAdapter foodItemAdapter;
    private final List<FoodItem> foodItems = new ArrayList<>();
    private final List<Category> categories = new ArrayList<>();
    private SupabaseService supabaseService;
    private PreferenceUtil preferenceUtil;
    private CartService cartService;
    private Handler mainHandler;
    private String selectedCategoryId = null;
    private String userId;
    private final List<CartItem> cartItems = new ArrayList<>();
    private SupabaseRealtimeClient menuRealtimeClient;
    private SupabaseRealtimeClient categoryRealtimeClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Use a dedicated menu layout that contains rvCategories and rvMenuItems
        setContentView(R.layout.activity_customer_menu);

        supabaseService = SupabaseService.getInstance(this);
        preferenceUtil = new PreferenceUtil(this);
        cartService = new CartService(this);
        mainHandler = new Handler(Looper.getMainLooper());
        userId = preferenceUtil.getUserId();

        if (!preferenceUtil.isLoggedIn() || TextUtils.isEmpty(userId)) {
            finish();
            return;
        }

        initViews();
        setupRecyclerViews();
        setupBottomNavigation();
        loadData();
        loadCartItems();
        subscribeToRealtimeStreams();
    }

    private void initViews() {
        rvCategories = findViewById(R.id.rvCategories);
        rvMenuItems = findViewById(R.id.rvMenuItems);
        
        // Setup toolbar back button
        findViewById(R.id.toolbar).setOnClickListener(v -> onBackPressed());
        
        // Setup "See all" button for categories
        TextView tvSeeAllCategories = findViewById(R.id.tvSeeAllCategories);
        if (tvSeeAllCategories != null) {
            tvSeeAllCategories.setOnClickListener(v -> {
                // Clear category filter to show all items
                selectedCategoryId = null;
                // Update category adapter to show no selection
                if (categoryAdapter != null) {
                    categoryAdapter.updateList(new ArrayList<>(categories));
                }
                filterMenuItems();
            });
        }
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
        bottomNav.setSelectedItemId(R.id.menuMenu);
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menuHome) {
                startActivity(new Intent(this, CustomerDashboardActivity.class));
                finish();
                return true;
            } else if (itemId == R.id.menuMenu) {
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
                startActivity(new Intent(this, ProfileActivity.class));
                finish();
                return true;
            }
            return false;
        });
    }

    private void setupRecyclerViews() {
        categoryAdapter = new CategoryAdapter(categories);
        rvCategories.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvCategories.setAdapter(categoryAdapter);
        categoryAdapter.setOnItemClickListener(category -> {
            selectedCategoryId = String.valueOf(category.getId());
            filterMenuItems();
        });

        foodItemAdapter = new FoodItemAdapter(foodItems);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 2);
        gridLayoutManager.setOrientation(GridLayoutManager.VERTICAL);
        rvMenuItems.setLayoutManager(gridLayoutManager);
        rvMenuItems.setAdapter(foodItemAdapter);
        foodItemAdapter.setOnItemClickListener(new FoodItemAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(FoodItem foodItem) {
                // Future detail screen
            }

            @Override
            public void onAddToCartClick(FoodItem foodItem) {
                addToCart(foodItem);
            }
        });
    }

    private void loadData() {
        if (!NetworkUtil.isNetworkAvailable(this)) {
            ToastUtil.show(this, "No internet connection");
            return;
        }

        loadCategories();
        loadMenuItems();
    }

    private void loadCategories() {
        new Thread(() -> {
            try {
                Request request = supabaseService.createRequest("categories?select=*&order=name")
                    .get().build();
                Response response = supabaseService.executeRequest(request);

                if (response.isSuccessful()) {
                    String json = response.body().string();
                    android.util.Log.d("MenuActivity", "Categories JSON: " + json);
                    Category[] cats = supabaseService.getGson().fromJson(json, Category[].class);
                    categories.clear();
                    if (cats != null) {
                        for (Category cat : cats) {
                            android.util.Log.d("MenuActivity", "Category: " + cat.getName() + ", imageUrl: " + cat.getImageUrl());
                            categories.add(cat);
                        }
                    }
                    runOnUiThread(() -> categoryAdapter.updateList(new ArrayList<>(categories)));
                } else {
                    android.util.Log.e("MenuActivity", "Failed to load categories: " + response.code());
                }
            } catch (Exception e) {
                android.util.Log.e("MenuActivity", "Error loading categories", e);
                e.printStackTrace();
            }
        }).start();
    }

    private void loadMenuItems() {
        new Thread(() -> {
            try {
                String url = selectedCategoryId != null
                    ? "menu_items?status=eq.available&category_id=eq." + selectedCategoryId
                    : "menu_items?status=eq.available";

                Request request = supabaseService.createRequest(url)
                    .get().build();
                Response response = supabaseService.executeRequest(request);

                if (response.isSuccessful()) {
                    String json = response.body().string();
                    FoodItem[] items = supabaseService.getGson().fromJson(json, FoodItem[].class);
                    foodItems.clear();
                    if (items != null) {
                        for (FoodItem item : items) {
                            foodItems.add(item);
                        }
                    }
                    runOnUiThread(() -> {
                        foodItemAdapter.updateList(new ArrayList<>(foodItems));
                        updateEmptyState();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void filterMenuItems() {
        loadMenuItems();
    }

    private void updateEmptyState() {
        TextView tvEmpty = findViewById(R.id.tvEmpty);
        if (tvEmpty != null) {
            tvEmpty.setVisibility(foodItems.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void loadCartItems() {
        cartService.getCartItems(userId, new CartItemsCallback() {
            @Override
            public void onSuccess(List<CartItem> items) {
                mainHandler.post(() -> {
                    cartItems.clear();
                    if (items != null) {
                        cartItems.addAll(items);
                    }
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> ToastUtil.show(MenuActivity.this, error));
            }
        });
    }

    private void subscribeToRealtimeStreams() {
        menuRealtimeClient = new SupabaseRealtimeClient();
        categoryRealtimeClient = new SupabaseRealtimeClient();

        RealtimeListener menuListener = new RealtimeListener() {
            @Override
            public void onOpen() { }

            @Override
            public void onChange(JsonObject payload) {
                mainHandler.post(MenuActivity.this::loadMenuItems);
            }

            @Override
            public void onError(String error) {
                android.util.Log.e("MenuActivity", "Menu realtime error: " + error);
            }
        };

        RealtimeListener categoryListener = new RealtimeListener() {
            @Override
            public void onOpen() { }

            @Override
            public void onChange(JsonObject payload) {
                mainHandler.post(MenuActivity.this::loadCategories);
            }

            @Override
            public void onError(String error) {
                android.util.Log.e("MenuActivity", "Category realtime error: " + error);
            }
        };

        menuRealtimeClient.subscribeToTable("public", "menu_items", menuListener);
        categoryRealtimeClient.subscribeToTable("public", "categories", categoryListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (menuRealtimeClient != null) {
            menuRealtimeClient.disconnect();
        }
        if (categoryRealtimeClient != null) {
            categoryRealtimeClient.disconnect();
        }
    }

    private void addToCart(FoodItem foodItem) {
        if (foodItem == null) {
            return;
        }

        CartItem existing = findCartItemByMenuId(foodItem.getId());
        if (existing != null && !TextUtils.isEmpty(existing.getId())) {
            cartService.updateQuantity(existing.getId(), existing.getQuantity() + 1, new CartItemCallback() {
                @Override
                public void onSuccess(CartItem updatedItem) {
                    mainHandler.post(() -> {
                        if (updatedItem != null) {
                            existing.setQuantity(updatedItem.getQuantity());
                            existing.setUnitPrice(updatedItem.getUnitPrice());
                            existing.setTotalPrice(updatedItem.getTotalPrice());
                        } else {
                            existing.setQuantity(existing.getQuantity() + 1);
                        }
                        ToastUtil.show(MenuActivity.this, "Quantity updated");
                    });
                }

                @Override
                public void onError(String error) {
                    mainHandler.post(() -> ToastUtil.show(MenuActivity.this, error));
                }
            });
            return;
        }

        cartService.addOrUpdateItem(userId, foodItem.getId(), 1, foodItem.getPrice(), new CartItemCallback() {
            @Override
            public void onSuccess(CartItem newItem) {
                mainHandler.post(() -> {
                    if (newItem != null) {
                        if (newItem.getFoodItem() == null) {
                            newItem.setFoodItem(foodItem);
                        }
                        cartItems.add(newItem);
                    } else {
                        cartItems.add(new CartItem(foodItem, 1));
                    }
                    ToastUtil.show(MenuActivity.this, "Added to cart");
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> ToastUtil.show(MenuActivity.this, error));
            }
        });
    }

    private CartItem findCartItemByMenuId(int menuItemId) {
        for (CartItem cartItem : cartItems) {
            if (cartItem != null && cartItem.getMenuItemId() == menuItemId) {
                return cartItem;
            }
        }
        return null;
    }
}


