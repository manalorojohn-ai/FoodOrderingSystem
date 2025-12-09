package com.fp.foodorderingsystem.activities.customer;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.activities.common.NotificationActivity;
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
import com.fp.foodorderingsystem.utils.ImageUtil;
import com.fp.foodorderingsystem.utils.NetworkUtil;
import com.fp.foodorderingsystem.utils.PreferenceUtil;
import com.fp.foodorderingsystem.utils.ToastUtil;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import com.google.gson.JsonObject;
import okhttp3.Request;
import okhttp3.Response;

public class CustomerDashboardActivity extends AppCompatActivity {
    private RecyclerView rvCategories;
    private RecyclerView rvPopularItems;
    private TextView tvGreeting;
    private TextView tvNotificationBadge;
    private ImageView ivAppLogo;
    private CategoryAdapter categoryAdapter;
    private FoodItemAdapter foodItemAdapter;
    private final List<Category> categories = new ArrayList<>();
    private final List<FoodItem> foodItems = new ArrayList<>();
    private final List<FoodItem> filteredFoodItems = new ArrayList<>();
    private final List<CartItem> cartItems = new ArrayList<>();
    private SupabaseService supabaseService;
    private PreferenceUtil preferenceUtil;
    private CartService cartService;
    private com.fp.foodorderingsystem.services.NotificationService notificationService;
    private Handler mainHandler;
    private String userId;
    private String selectedCategoryId = null;
    private SupabaseRealtimeClient categoryRealtimeClient;
    private SupabaseRealtimeClient menuRealtimeClient;
    private SupabaseRealtimeClient notificationRealtimeClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_dashboard);

        supabaseService = SupabaseService.getInstance(this);
        preferenceUtil = new PreferenceUtil(this);
        cartService = new CartService(this);
        notificationService = new com.fp.foodorderingsystem.services.NotificationService(this);
        mainHandler = new Handler(Looper.getMainLooper());
        userId = preferenceUtil.getUserId();

        if (!preferenceUtil.isLoggedIn() || TextUtils.isEmpty(userId)) {
            finish();
            return;
        }

        initViews();
        setupRecyclerViews();
        setupClickListeners();
        loadData();
        loadCartItems();
        loadNotifications();
        subscribeToRealtimeStreams();
    }

    private void initViews() {
        rvCategories = findViewById(R.id.rvCategories);
        rvPopularItems = findViewById(R.id.rvPopularItems);
        tvGreeting = findViewById(R.id.tvGreeting);
        tvNotificationBadge = findViewById(R.id.tvNotificationBadge);
        ivAppLogo = findViewById(R.id.ivAppLogo);
        setGreeting();
        loadAppLogo();
    }

    private void setGreeting() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour >= 5 && hour < 12) {
            greeting = "Good Morning";
        } else if (hour >= 12 && hour < 17) {
            greeting = "Good Afternoon";
        } else if (hour >= 17 && hour < 21) {
            greeting = "Good Evening";
        } else {
            greeting = "Good Night";
        }
        String userName = preferenceUtil.getUserName();
        if (!TextUtils.isEmpty(userName)) {
            greeting += ", " + userName;
        }
        tvGreeting.setText(greeting);
    }

    private void loadAppLogo() {
        String logoUrl = ImageUtil.getLogoUrl("logo.jpg");
        if (logoUrl != null && !logoUrl.isEmpty()) {
            Glide.with(this)
                .load(logoUrl)
                .placeholder(R.drawable.ic_food_banner)
                .error(R.drawable.ic_food_banner)
                .circleCrop()
                .into(ivAppLogo);
        } else {
            ivAppLogo.setImageResource(R.drawable.ic_food_banner);
        }
    }

    private void setupRecyclerViews() {
        categoryAdapter = new CategoryAdapter(categories);
        rvCategories.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvCategories.setAdapter(categoryAdapter);
        categoryAdapter.setOnItemClickListener(category -> {
            // Filter items by selected category
            selectedCategoryId = category != null ? String.valueOf(category.getId()) : null;
            filterFoodItems();
        });

        foodItemAdapter = new FoodItemAdapter(filteredFoodItems);
        rvPopularItems.setLayoutManager(new LinearLayoutManager(this));
        rvPopularItems.setAdapter(foodItemAdapter);
        foodItemAdapter.setOnItemClickListener(new FoodItemAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(FoodItem foodItem) {
                // placeholder for future detail screen
            }

            @Override
            public void onAddToCartClick(FoodItem foodItem) {
                addToCart(foodItem);
            }
        });
    }
    
    private void filterFoodItems() {
        filteredFoodItems.clear();
        if (selectedCategoryId == null || selectedCategoryId.isEmpty()) {
            // Show all items
            filteredFoodItems.addAll(foodItems);
        } else {
            // Filter by category
            try {
                int selectedId = Integer.parseInt(selectedCategoryId);
                for (FoodItem item : foodItems) {
                    if (item != null && item.getCategoryId() == selectedId) {
                        filteredFoodItems.add(item);
                    }
                }
            } catch (NumberFormatException e) {
                // If parsing fails, show all items
                filteredFoodItems.addAll(foodItems);
            }
        }
        foodItemAdapter.updateList(new ArrayList<>(filteredFoodItems));
    }

    private void setupClickListeners() {
        findViewById(R.id.btnNotifications).setOnClickListener(v ->
            startActivity(new Intent(this, NotificationActivity.class))
        );
        
        // Setup "See All" button for popular items
        TextView tvSeeAll = findViewById(R.id.tvSeeAll);
        if (tvSeeAll != null) {
            tvSeeAll.setOnClickListener(v -> {
                // Clear category filter to show all items
                selectedCategoryId = null;
                // Reset category adapter selection
                if (categoryAdapter != null) {
                    categoryAdapter.updateList(new ArrayList<>(categories));
                }
                filterFoodItems();
            });
        }

        ExtendedFloatingActionButton fabCart = findViewById(R.id.fabCart);
        if (fabCart != null) {
            fabCart.setOnClickListener(v -> {
                Intent intent = new Intent(this, CartActivity.class);
                startActivity(intent);
            });
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.menuHome);
            bottomNav.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.menuHome) {
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
                    startActivity(new Intent(this, ProfileActivity.class));
                    finish();
                    return true;
                }
                return false;
            });
        }
    }

    private void loadData() {
        if (!NetworkUtil.isNetworkAvailable(this)) {
            ToastUtil.show(this, "No internet connection");
            return;
        }
        loadCategories();
        loadFoodItems();
    }

    private void loadCategories() {
        new Thread(() -> {
            try {
                Request request = supabaseService.createRequest("categories?select=*&order=name")
                    .get().build();
                Response response = supabaseService.executeRequest(request);
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    android.util.Log.d("CustomerDashboard", "Categories JSON: " + json);
                    Category[] cats = supabaseService.getGson().fromJson(json, Category[].class);
                    categories.clear();
                    if (cats != null) {
                        for (Category cat : cats) {
                            android.util.Log.d("CustomerDashboard", "Category: " + cat.getName() + ", imageUrl: " + cat.getImageUrl());
                            categories.add(cat);
                        }
                    }
                    mainHandler.post(() -> categoryAdapter.updateList(new ArrayList<>(categories)));
                } else {
                    android.util.Log.e("CustomerDashboard", "Failed to load categories: " + response.code());
                }
            } catch (Exception e) {
                android.util.Log.e("CustomerDashboard", "Error loading categories", e);
                e.printStackTrace();
            }
        }).start();
    }

    private void loadFoodItems() {
        new Thread(() -> {
            try {
                Request request = supabaseService.createRequest("menu_items?status=eq.available")
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
                    mainHandler.post(() -> {
                        filterFoodItems(); // Apply current filter
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
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
                mainHandler.post(() ->
                    ToastUtil.show(CustomerDashboardActivity.this, error)
                );
            }
        });
    }

    private void addToCart(FoodItem foodItem) {
        if (foodItem == null) return;

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
                        ToastUtil.show(CustomerDashboardActivity.this, "Quantity updated");
                    });
                }

                @Override
                public void onError(String error) {
                    mainHandler.post(() ->
                        ToastUtil.show(CustomerDashboardActivity.this, error)
                    );
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
                    ToastUtil.show(CustomerDashboardActivity.this, "Added to cart");
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() ->
                    ToastUtil.show(CustomerDashboardActivity.this, error)
                );
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

    private void loadNotifications() {
        if (TextUtils.isEmpty(userId)) {
            if (tvNotificationBadge != null) {
                tvNotificationBadge.setVisibility(TextView.GONE);
            }
            return;
        }
        
        notificationService.getNotifications(userId, new com.fp.foodorderingsystem.services.NotificationService.NotificationCallback() {
            @Override
            public void onSuccess(List<com.fp.foodorderingsystem.models.Notification> notifications) {
                mainHandler.post(() -> {
                    int unreadCount = 0;
                    if (notifications != null) {
                        for (com.fp.foodorderingsystem.models.Notification notification : notifications) {
                            if (notification != null && !notification.isRead()) {
                                unreadCount++;
                            }
                        }
                    }
                    updateNotificationBadge(unreadCount);
                });
            }
            
            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    if (tvNotificationBadge != null) {
                        tvNotificationBadge.setVisibility(TextView.GONE);
                    }
                });
            }
        });
    }
    
    private void updateNotificationBadge(int count) {
        if (tvNotificationBadge != null) {
            if (count > 0) {
                tvNotificationBadge.setVisibility(TextView.VISIBLE);
                tvNotificationBadge.setText(count > 99 ? "99+" : String.valueOf(count));
            } else {
            tvNotificationBadge.setVisibility(TextView.GONE);
            }
        }
    }

    private void subscribeToRealtimeStreams() {
        categoryRealtimeClient = new SupabaseRealtimeClient();
        menuRealtimeClient = new SupabaseRealtimeClient();
        notificationRealtimeClient = new SupabaseRealtimeClient();

        RealtimeListener reloadCategoriesListener = new RealtimeListener() {
            @Override
            public void onOpen() { }

            @Override
            public void onChange(JsonObject payload) {
                mainHandler.post(CustomerDashboardActivity.this::loadCategories);
            }

            @Override
            public void onError(String error) {
                android.util.Log.e("CustomerDashboard", "Category realtime error: " + error);
            }
        };

        RealtimeListener reloadMenuListener = new RealtimeListener() {
            @Override
            public void onOpen() { }

            @Override
            public void onChange(JsonObject payload) {
                mainHandler.post(CustomerDashboardActivity.this::loadFoodItems);
            }

            @Override
            public void onError(String error) {
                android.util.Log.e("CustomerDashboard", "Menu realtime error: " + error);
            }
        };

        RealtimeListener notificationListener = new RealtimeListener() {
            @Override
            public void onOpen() { }

            @Override
            public void onChange(JsonObject payload) {
                mainHandler.post(CustomerDashboardActivity.this::loadNotifications);
            }

            @Override
            public void onError(String error) {
                android.util.Log.e("CustomerDashboard", "Notification realtime error: " + error);
            }
        };

        categoryRealtimeClient.subscribeToTable("public", "categories", reloadCategoriesListener);
        menuRealtimeClient.subscribeToTable("public", "menu_items", reloadMenuListener);
        notificationRealtimeClient.subscribeToTable("public", "notifications", notificationListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (categoryRealtimeClient != null) {
            categoryRealtimeClient.disconnect();
        }
        if (menuRealtimeClient != null) {
            menuRealtimeClient.disconnect();
        }
        if (notificationRealtimeClient != null) {
            notificationRealtimeClient.disconnect();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!preferenceUtil.isLoggedIn() || TextUtils.isEmpty(userId)) {
            finish();
            return;
        }
        loadNotifications();
    }
}


