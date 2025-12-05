package com.fp.foodorderingsystem.activities.customer;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.adapters.CartAdapter;
import com.fp.foodorderingsystem.models.CartItem;
import com.fp.foodorderingsystem.models.FoodItem;
import com.fp.foodorderingsystem.services.CartService;
import com.fp.foodorderingsystem.services.CartService.CartItemCallback;
import com.fp.foodorderingsystem.services.CartService.CartItemsCallback;
import com.fp.foodorderingsystem.services.CartService.SimpleCallback;
import com.fp.foodorderingsystem.services.SupabaseRealtimeClient;
import com.fp.foodorderingsystem.services.SupabaseRealtimeClient.RealtimeListener;
import com.fp.foodorderingsystem.ui.FoodLoaderView;
import com.fp.foodorderingsystem.utils.PreferenceUtil;
import com.fp.foodorderingsystem.utils.ToastUtil;
import com.fp.foodorderingsystem.utils.RealtimePayloadUtil;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonObject;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CartActivity extends AppCompatActivity implements CartAdapter.OnItemChangeListener {
    private static final double DELIVERY_FEE = 50.0;

    private RecyclerView rvCartItems;
    private TextView tvSubtotal;
    private TextView tvDeliveryFee;
    private TextView tvTotal;
    private View layoutEmptyCart;
    private View cardSummary;
    private MaterialButton btnCheckout;
    private MaterialButton btnBrowseMenu;
    private FoodLoaderView loaderView;

    private CartAdapter cartAdapter;
    private final List<CartItem> cartItems = new ArrayList<>();
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));

    private PreferenceUtil preferenceUtil;
    private CartService cartService;
    private Handler mainHandler;
    private String userId;
    private SupabaseRealtimeClient cartRealtimeClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);

        currencyFormat.setCurrency(java.util.Currency.getInstance("PHP"));

        preferenceUtil = new PreferenceUtil(this);
        cartService = new CartService(this);
        mainHandler = new Handler(Looper.getMainLooper());

        userId = preferenceUtil.getUserId();
        if (TextUtils.isEmpty(userId)) {
            ToastUtil.show(this, "Unable to identify user. Please login again.", Toast.LENGTH_LONG);
            finish();
            return;
        }

        initViews();
        setupRecyclerView();
        setupBottomNavigation();
        loadCart(true);
        subscribeToRealtimeCart();
    }

    private void initViews() {
        rvCartItems = findViewById(R.id.rvCartItems);
        tvSubtotal = findViewById(R.id.tvSubtotal);
        tvDeliveryFee = findViewById(R.id.tvDeliveryFee);
        tvTotal = findViewById(R.id.tvTotal);
        layoutEmptyCart = findViewById(R.id.layoutEmptyCart);
        cardSummary = findViewById(R.id.cardSummary);
        btnCheckout = findViewById(R.id.btnCheckout);
        btnBrowseMenu = findViewById(R.id.btnBrowseMenu);
        loaderView = findViewById(R.id.loaderView);

        // Setup toolbar back button
        findViewById(R.id.toolbar).setOnClickListener(v -> onBackPressed());

        btnCheckout.setOnClickListener(v -> {
            if (cartItems.isEmpty()) {
                ToastUtil.show(this, "Your cart is empty");
                return;
            }
            Intent intent = new Intent(this, CheckoutActivity.class);
            intent.putExtra("cart_items", new Gson().toJson(cartItems));
            startActivity(intent);
        });

        btnBrowseMenu.setOnClickListener(v -> {
            Intent intent = new Intent(this, MenuActivity.class);
            startActivity(intent);
            finish();
        });
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
        bottomNav.setSelectedItemId(R.id.menuCart);
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

    private void setupRecyclerView() {
        cartAdapter = new CartAdapter(cartItems);
        cartAdapter.setOnItemChangeListener(this);
        rvCartItems.setLayoutManager(new LinearLayoutManager(this));
        rvCartItems.setAdapter(cartAdapter);
    }

    private void loadCart(boolean showSpinner) {
        if (showSpinner) {
            showLoading(true);
        }

        cartService.getCartItems(userId, new CartItemsCallback() {
            @Override
            public void onSuccess(List<CartItem> items) {
                mainHandler.post(() -> {
                    cartItems.clear();
                    if (items != null) {
                        cartItems.addAll(items);
                    }
                    cartAdapter.notifyDataSetChanged();
                    updateUI();
                    updateSummary();
                    showLoading(false);
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    showLoading(false);
                    ToastUtil.show(CartActivity.this, error);
                    updateUI();
                });
            }
        });
    }

    private void subscribeToRealtimeCart() {
        cartRealtimeClient = new SupabaseRealtimeClient();
        cartRealtimeClient.subscribeToTable("public", "cart_items", new RealtimeListener() {
            @Override
            public void onOpen() { }

            @Override
            public void onChange(JsonObject payload) {
                if (isPayloadForCurrentUser(payload)) {
                    mainHandler.post(() -> loadCart(false));
                }
            }

            @Override
            public void onError(String error) {
                android.util.Log.e("CartActivity", "Realtime cart error: " + error);
            }
        });
    }

    private boolean isPayloadForCurrentUser(JsonObject payload) {
        JsonObject record = RealtimePayloadUtil.getRelevantRecord(payload);
        if (record != null && record.has("user_id")) {
            return userId != null && userId.equals(record.get("user_id").getAsString());
        }
        return false;
    }

    private void showLoading(boolean show) {
        if (loaderView != null) {
            if (show) {
                loaderView.showLoader();
            } else {
                loaderView.hideLoader();
            }
        }
        updateCheckoutButtonState(show);
    }

    private void updateUI() {
        boolean isEmpty = cartItems.isEmpty();
        layoutEmptyCart.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvCartItems.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        cardSummary.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        updateCheckoutButtonState(false);
    }

    private void updateCheckoutButtonState(boolean isLoading) {
        boolean hasItems = !cartItems.isEmpty();
        btnCheckout.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        btnCheckout.setEnabled(hasItems && !isLoading);
        btnCheckout.setAlpha(hasItems && !isLoading ? 1f : 0.5f);
    }

    private void updateSummary() {
        double subtotal = 0.0;
        for (CartItem item : cartItems) {
            subtotal += item.getSubtotal();
        }

        double total = subtotal + DELIVERY_FEE;

        tvSubtotal.setText(currencyFormat.format(subtotal));
        tvDeliveryFee.setText(currencyFormat.format(DELIVERY_FEE));
        tvTotal.setText(currencyFormat.format(total));
    }

    @Override
    public void onQuantityChanged(CartItem item, int quantity) {
        if (item == null || TextUtils.isEmpty(item.getId())) {
            return;
        }
        
        // Validate quantity limits
        if (quantity > 99) {
            ToastUtil.show(this, "Maximum quantity is 99");
            cartAdapter.notifyDataSetChanged();
            return;
        }
        
        // Validate stock availability
        FoodItem foodItem = item.getFoodItem();
        if (foodItem != null) {
            int availableStock = foodItem.getStock();
            if (quantity > availableStock) {
                ToastUtil.show(this, "Not enough stock. Only " + availableStock + " available");
                cartAdapter.notifyDataSetChanged();
                return;
            }
        }
        
        showLoading(true);
        cartService.updateQuantity(item.getId(), quantity, new CartItemCallback() {
            @Override
            public void onSuccess(CartItem updatedItem) {
                mainHandler.post(() -> {
                    CartItem localItem = findLocalItemById(item.getId());
                    if (localItem != null) {
                        localItem.setQuantity(quantity);
                        if (updatedItem != null) {
                            localItem.setUnitPrice(updatedItem.getUnitPrice());
                            localItem.setTotalPrice(updatedItem.getTotalPrice());
                        }
                    }
                    cartAdapter.notifyDataSetChanged();
                    updateSummary();
                    showLoading(false);
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    showLoading(false);
                    ToastUtil.show(CartActivity.this, error);
                });
            }
        });
    }

    @Override
    public void onItemRemoved(CartItem item) {
        if (item == null || TextUtils.isEmpty(item.getId())) {
            return;
        }
        showLoading(true);
        cartService.removeItem(item.getId(), new SimpleCallback() {
            @Override
            public void onSuccess() {
                mainHandler.post(() -> {
                    removeLocalItem(item.getId());
                    cartAdapter.notifyDataSetChanged();
                    updateUI();
                    updateSummary();
                    showLoading(false);
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    showLoading(false);
                    ToastUtil.show(CartActivity.this, error);
                });
            }
        });
    }

    private CartItem findLocalItemById(String id) {
        for (CartItem item : cartItems) {
            if (item != null && TextUtils.equals(item.getId(), id)) {
                return item;
            }
        }
        return null;
    }

    private void removeLocalItem(String cartItemId) {
        for (int i = 0; i < cartItems.size(); i++) {
            CartItem item = cartItems.get(i);
            if (item != null && TextUtils.equals(item.getId(), cartItemId)) {
                cartItems.remove(i);
                break;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cartRealtimeClient != null) {
            cartRealtimeClient.disconnect();
        }
    }
}


