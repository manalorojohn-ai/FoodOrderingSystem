package com.fp.foodorderingsystem.activities.customer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.adapters.ReceiptItemAdapter;
import com.fp.foodorderingsystem.config.PayMongoConfig;
import com.fp.foodorderingsystem.models.CartItem;
import com.fp.foodorderingsystem.models.Order;
import com.fp.foodorderingsystem.services.AuthService;
import com.fp.foodorderingsystem.services.CartService;
import com.fp.foodorderingsystem.services.NotificationService;
import com.fp.foodorderingsystem.services.OrderService;
import com.fp.foodorderingsystem.services.PayMongoService;
import com.fp.foodorderingsystem.utils.NotificationHelper;
import com.fp.foodorderingsystem.utils.PreferenceUtil;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.LocationServices;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.IOException;
import java.lang.reflect.Type;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CheckoutActivity extends AppCompatActivity {
    private TextView tvTotalAmount, tvSubtotal, tvDeliveryFee, etDeliveryAddress, tvOrderDate, tvPaymentMethod;
    private RadioGroup rgPaymentMethod;
    private RecyclerView rvOrderItems;
    private MapView osmMapView;
    private Marker selectedMarker;
    private MapEventsOverlay mapEventsOverlay;
    private MyLocationNewOverlay myLocationOverlay;
    private static final double DEFAULT_LAT = 14.5995;
    private static final double DEFAULT_LNG = 120.9842;
    private FusedLocationProviderClient fusedLocationClient;
    private Geocoder geocoder;
    private double totalAmount;
    private double subtotal;
    private List<CartItem> cartItems;
    private OrderService orderService;
    private CartService cartService;
    private AuthService authService;
    private NotificationService notificationService;
    private NotificationHelper notificationHelper;
    private PreferenceUtil preferenceUtil;
    private PayMongoService payMongoService;
    private String selectedPaymentMethod = "cod";
    private double selectedLat = 0, selectedLng = 0;
    private ReceiptItemAdapter receiptAdapter;
    private android.app.AlertDialog paymentProgressDialog;
    private android.app.AlertDialog paymentStatusDialog;
    private android.app.AlertDialog paymentSuccessDialog;
    private String currentPaymentIntentId;
    private String pendingAddress;
    private boolean isCheckingPaymentStatus = false;
    private long lastPaymentCheckTime = 0;
    private Handler periodicCheckHandler;
    private Runnable periodicCheckRunnable;
    private ActivityResultLauncher<Intent> paymentLauncher;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_checkout);
        
        orderService = new OrderService(this);
        cartService = new CartService(this);
        authService = new AuthService(this);
        notificationService = new NotificationService(this);
        notificationHelper = new NotificationHelper(this);
        preferenceUtil = new PreferenceUtil(this);
        payMongoService = PayMongoService.getInstance(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        geocoder = new Geocoder(this, Locale.getDefault());
        
        paymentLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK &&
                        result.getData() != null &&
                        result.getData().getBooleanExtra(PaymentWebViewActivity.EXTRA_PAYMENT_COMPLETED, false)) {
                    if (currentPaymentIntentId != null && !currentPaymentIntentId.isEmpty()) {
                        checkPaymentStatus();
                    }
                } else {
                    stopPeriodicPaymentCheck();
                    Toast.makeText(this, "Payment was canceled or not completed.", Toast.LENGTH_SHORT).show();
                }
            }
        );
        
        // Handle returning from payment gateway (deep link or intent)
        handlePaymentReturn(getIntent());
        
        // Verify user is logged in and has access token
        String userId = preferenceUtil.getUserId();
        String accessToken = authService.getAccessToken();
        if (userId == null || userId.isEmpty() || accessToken == null || accessToken.isEmpty()) {
            android.util.Log.w("CheckoutActivity", "User not logged in or token missing. User ID: " + userId + ", Token: " + (accessToken != null ? "exists" : "null"));
        }
        
        String cartJson = getIntent().getStringExtra("cart_items");
        if (cartJson != null) {
            Type type = new TypeToken<List<CartItem>>(){}.getType();
            cartItems = new Gson().fromJson(cartJson, type);
        }
        
        calculateTotal();
        initViews();
        setupMap();
        setupClickListeners();
    }
    
    private void initViews() {
        tvTotalAmount = findViewById(R.id.tvTotalAmount);
        tvSubtotal = findViewById(R.id.tvSubtotal);
        tvDeliveryFee = findViewById(R.id.tvDeliveryFee);
        etDeliveryAddress = findViewById(R.id.etDeliveryAddress);
        rgPaymentMethod = findViewById(R.id.rgPaymentMethod);
        tvOrderDate = findViewById(R.id.tvOrderDate);
        tvPaymentMethod = findViewById(R.id.tvPaymentMethod);
        rvOrderItems = findViewById(R.id.rvOrderItems);
        
        // Set up RecyclerView for receipt items
        rvOrderItems.setLayoutManager(new LinearLayoutManager(this));
        if (cartItems != null && !cartItems.isEmpty()) {
            receiptAdapter = new ReceiptItemAdapter(cartItems);
            rvOrderItems.setAdapter(receiptAdapter);
        }
        
        // Set order date
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
        tvOrderDate.setText(dateFormat.format(new Date()));
        
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
        currencyFormat.setCurrency(java.util.Currency.getInstance("PHP"));
        
        tvSubtotal.setText(currencyFormat.format(subtotal));
        tvDeliveryFee.setText(currencyFormat.format(50.0));
        tvTotalAmount.setText(currencyFormat.format(totalAmount));
        updatePaymentMethodDisplay();
        
        rgPaymentMethod.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbGCash) {
                selectedPaymentMethod = "gcash";
            } else if (checkedId == R.id.rbMaya) {
                selectedPaymentMethod = "maya";
            } else {
                selectedPaymentMethod = "cod";
            }
            updatePaymentMethodDisplay();
        });
    }
    
    private void updatePaymentMethodDisplay() {
        String paymentText;
        switch (selectedPaymentMethod) {
            case "gcash":
                paymentText = "GCash";
                break;
            case "maya":
                paymentText = "Maya";
                break;
            default:
                paymentText = "Cash on Delivery";
                break;
        }
        tvPaymentMethod.setText(paymentText);
    }
    
    private void setupMap() {
        osmMapView = findViewById(R.id.osmMapView);
        if (osmMapView == null) {
            android.util.Log.e("CheckoutActivity", "OSM map view not found in layout");
            Toast.makeText(this, "Map view not found. Please check the layout.", Toast.LENGTH_SHORT).show();
            return;
        }

        osmMapView.setTileSource(TileSourceFactory.MAPNIK);
        osmMapView.setMultiTouchControls(true);
        IMapController controller = osmMapView.getController();
        controller.setZoom(12.0);
        controller.setCenter(new GeoPoint(DEFAULT_LAT, DEFAULT_LNG));

        addMapTapListener();
    }

    private void addMapTapListener() {
        if (osmMapView == null || mapEventsOverlay != null) {
            return;
        }

        mapEventsOverlay = new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                updateSelectedLocation(p.getLatitude(), p.getLongitude(), true);
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        });
        osmMapView.getOverlays().add(0, mapEventsOverlay);
    }

    private void updateSelectedLocation(double latitude, double longitude, boolean animate) {
        selectedLat = latitude;
        selectedLng = longitude;

        if (osmMapView == null) {
            return;
        }

        GeoPoint point = new GeoPoint(latitude, longitude);

        if (selectedMarker == null) {
            selectedMarker = new Marker(osmMapView);
            selectedMarker.setTitle("Delivery Location");
            selectedMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            osmMapView.getOverlays().add(selectedMarker);
        }

        selectedMarker.setPosition(point);

        IMapController controller = osmMapView.getController();
        if (animate) {
            controller.animateTo(point);
        } else {
            controller.setCenter(point);
        }

        osmMapView.invalidate();
        getAddressFromLocation(latitude, longitude);
    }

    private void enableMyLocationOverlay() {
        if (osmMapView == null || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        if (myLocationOverlay == null) {
            myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), osmMapView);
            myLocationOverlay.enableMyLocation();
            myLocationOverlay.runOnFirstFix(() -> {
                GeoPoint location = myLocationOverlay.getMyLocation();
                if (location != null) {
                    runOnUiThread(() -> centerMapOn(location));
                }
            });
            osmMapView.getOverlays().add(myLocationOverlay);
        } else {
            myLocationOverlay.enableMyLocation();
        }
    }

    private void centerMapOn(GeoPoint point) {
        if (osmMapView == null || point == null) {
            return;
        }
        osmMapView.getController().animateTo(point);
    }
    
    private void setupClickListeners() {
        findViewById(R.id.btnPlaceOrder).setOnClickListener(v -> placeOrder());
        findViewById(R.id.btnSelectLocation).setOnClickListener(v -> getCurrentLocation());
    }
    
    private void calculateTotal() {
        subtotal = 0.0;
        for (CartItem item : cartItems) {
            subtotal += item.getSubtotal();
        }
        totalAmount = subtotal + 50.0; // Delivery fee
    }
    
    private void getAddressFromLocation(double latitude, double longitude) {
        new Thread(() -> {
            try {
                List<android.location.Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    android.location.Address address = addresses.get(0);
                    StringBuilder addressString = new StringBuilder();
                    
                    // Build address string
                    for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                        if (i > 0) addressString.append(", ");
                        addressString.append(address.getAddressLine(i));
                    }
                    
                    String finalAddress = addressString.toString();
                    runOnUiThread(() -> {
                        etDeliveryAddress.setText(finalAddress);
                    });
                } else {
                    runOnUiThread(() -> {
                        etDeliveryAddress.setText(String.format(Locale.getDefault(), "Lat: %.6f, Lng: %.6f", latitude, longitude));
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    etDeliveryAddress.setText(String.format(Locale.getDefault(), "Lat: %.6f, Lng: %.6f", latitude, longitude));
                });
            }
        }).start();
    }
    
    private void getCurrentLocation() {
        // Check location permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    1
            );
            return;
        }

        // Check if location services are enabled (GPS or network provider)
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        boolean isLocationEnabled =
                locationManager != null &&
                        (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));

        if (!isLocationEnabled) {
            Toast.makeText(this, "Please enable location services to use current location.", Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            } catch (Exception ignored) { }
            return;
        }

        // Request a fresh high-accuracy location
        enableMyLocationOverlay();

        fusedLocationClient
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        selectedLat = location.getLatitude();
                        selectedLng = location.getLongitude();

                        updateSelectedLocation(selectedLat, selectedLng, true);
                    } else {
                        Toast.makeText(this, "Unable to get current location. Please try again in a moment.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("CheckoutActivity", "Error getting current location", e);
                    Toast.makeText(this, "Failed to get current location. Please try again.", Toast.LENGTH_SHORT).show();
                });
    }
    
    private void placeOrder() {
        String address = etDeliveryAddress.getText().toString().trim();
        
        if (address.isEmpty() || address.equals("Tap on map to select location")) {
            Toast.makeText(this, "Please select delivery address", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (selectedLat == 0 || selectedLng == 0) {
            Toast.makeText(this, "Please select location on map", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Handle payment gateway based on selected method
        if (selectedPaymentMethod.equals("gcash")) {
            processGCashPayment(address);
        } else if (selectedPaymentMethod.equals("maya")) {
            processMayaPayment(address);
        } else {
            // Cash on Delivery - proceed directly to order creation (no payment dialog)
            createOrder(address);
        }
    }
    
    private void processGCashPayment(String address) {
        // Validate PayMongo API keys
        if (PayMongoConfig.PAYMONGO_SECRET_KEY.contains("YOUR_SECRET_KEY") || 
            PayMongoConfig.PAYMONGO_SECRET_KEY.equals("sk_test_YOUR_SECRET_KEY_HERE")) {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("PayMongo Configuration Required");
            builder.setMessage("Please configure your PayMongo API keys in PayMongoConfig.java\n\n" +
                "Get your test keys from: https://dashboard.paymongo.com/\n\n" +
                "For demo purposes, using simulated payment.");
            builder.setPositiveButton("Simulate Payment", (dialog, which) -> {
                // Fallback to simulated payment if keys not configured
                Toast.makeText(this, "GCash payment processed (Simulated)", Toast.LENGTH_SHORT).show();
                createOrder(address);
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
            return;
        }
        
        pendingAddress = address;
        
        // Show professional loading dialog
        showProcessingDialog("Processing GCash Payment", "Creating payment intent...");
        
        String description = "Food Order - " + String.format(Locale.getDefault(), "₱%.2f", totalAmount);
        
        // Create PayMongo payment intent for GCash
        payMongoService.createPaymentIntent(
            totalAmount,
            PayMongoConfig.PAYMENT_METHOD_GCASH,
            description,
            new PayMongoService.PaymentIntentCallback() {
                @Override
                public void onSuccess(String paymentIntentId, String checkoutUrl) {
                    runOnUiThread(() -> {
                        if (paymentProgressDialog != null && paymentProgressDialog.isShowing()) {
                            paymentProgressDialog.dismiss();
                        }
                        
                        currentPaymentIntentId = paymentIntentId;
                        
                        // Open PayMongo checkout URL in browser
                        openPaymentCheckout(checkoutUrl, "GCash");
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        if (paymentProgressDialog != null && paymentProgressDialog.isShowing()) {
                            paymentProgressDialog.dismiss();
                        }
                        
                        android.util.Log.e("CheckoutActivity", "PayMongo error: " + error);
                        Toast.makeText(CheckoutActivity.this, 
                            "Payment setup failed: " + error + "\n\nUsing simulated payment for demo.", 
                            Toast.LENGTH_LONG).show();
                        
                        // Fallback to simulated payment for demo
                        createOrder(address);
                    });
                }
            }
        );
    }
    
    private void processMayaPayment(String address) {
        // Validate PayMongo API keys
        if (PayMongoConfig.PAYMONGO_SECRET_KEY.contains("YOUR_SECRET_KEY") || 
            PayMongoConfig.PAYMONGO_SECRET_KEY.equals("sk_test_YOUR_SECRET_KEY_HERE")) {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("PayMongo Configuration Required");
            builder.setMessage("Please configure your PayMongo API keys in PayMongoConfig.java\n\n" +
                "Get your test keys from: https://dashboard.paymongo.com/\n\n" +
                "For demo purposes, using simulated payment.");
            builder.setPositiveButton("Simulate Payment", (dialog, which) -> {
                // Fallback to simulated payment if keys not configured
                Toast.makeText(this, "Maya payment processed (Simulated)", Toast.LENGTH_SHORT).show();
                createOrder(address);
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
            return;
        }
        
        pendingAddress = address;
        
        // Show professional loading dialog
        showProcessingDialog("Processing Maya Payment", "Creating payment intent...");
        
        String description = "Food Order - " + String.format(Locale.getDefault(), "₱%.2f", totalAmount);
        
        // Create PayMongo payment intent for Maya
        payMongoService.createPaymentIntent(
            totalAmount,
            PayMongoConfig.PAYMENT_METHOD_MAYA,
            description,
            new PayMongoService.PaymentIntentCallback() {
                @Override
                public void onSuccess(String paymentIntentId, String checkoutUrl) {
                    runOnUiThread(() -> {
                        if (paymentProgressDialog != null && paymentProgressDialog.isShowing()) {
                            paymentProgressDialog.dismiss();
                        }
                        
                        currentPaymentIntentId = paymentIntentId;
                        
                        // Open PayMongo checkout URL in browser
                        openPaymentCheckout(checkoutUrl, "Maya");
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        if (paymentProgressDialog != null && paymentProgressDialog.isShowing()) {
                            paymentProgressDialog.dismiss();
                        }
                        
                        android.util.Log.e("CheckoutActivity", "PayMongo error: " + error);
                        Toast.makeText(CheckoutActivity.this, 
                            "Payment setup failed: " + error + "\n\nUsing simulated payment for demo.", 
                            Toast.LENGTH_LONG).show();
                        
                        // Fallback to simulated payment for demo
                        createOrder(address);
                    });
                }
            }
        );
    }
    
    private void openPaymentCheckout(String checkoutUrl, String paymentMethodName) {
        // Create professional custom dialog
        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_payment_redirect, null);
        
        android.widget.TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        android.widget.TextView tvMessage = dialogView.findViewById(R.id.tvDialogMessage);
        android.widget.TextView tvAmount = dialogView.findViewById(R.id.tvDialogAmount);
        android.widget.ImageView ivIcon = dialogView.findViewById(R.id.ivPaymentIcon);
        
        tvTitle.setText(paymentMethodName + " Payment");
        tvMessage.setText("You will be redirected to " + paymentMethodName + " payment gateway to complete your transaction.");
        tvAmount.setText(String.format(Locale.getDefault(), "₱%.2f", totalAmount));
        
        // Set payment method icon
        if ("GCash".equalsIgnoreCase(paymentMethodName)) {
            ivIcon.setImageResource(android.R.drawable.ic_menu_share);
            ivIcon.setColorFilter(getResources().getColor(R.color.payment_gcash, null));
        } else if ("Maya".equalsIgnoreCase(paymentMethodName)) {
            ivIcon.setImageResource(android.R.drawable.ic_menu_share);
            ivIcon.setColorFilter(getResources().getColor(R.color.payment_maya, null));
        }
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this, R.style.CustomDialogTheme);
        builder.setView(dialogView);
        builder.setCancelable(true);
        
        android.app.AlertDialog dialog = builder.create();
        
        android.widget.Button btnContinue = dialogView.findViewById(R.id.btnContinue);
        android.widget.Button btnCancel = dialogView.findViewById(R.id.btnCancel);
        
        btnContinue.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(this, PaymentWebViewActivity.class);
            intent.putExtra(PaymentWebViewActivity.EXTRA_CHECKOUT_URL, checkoutUrl);
            intent.putExtra(PaymentWebViewActivity.EXTRA_PAYMENT_METHOD, paymentMethodName);
            paymentLauncher.launch(intent);
            startPeriodicPaymentCheck();
        });
        
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }
    
    private void showPaymentStatusDialog(String paymentMethodName) {
        // Dismiss existing dialog if showing
        if (paymentStatusDialog != null && paymentStatusDialog.isShowing()) {
            paymentStatusDialog.dismiss();
        }
        
        // Check if activity is finishing
        if (isFinishing() || isDestroyed()) {
            return;
        }
        
        // Create professional custom dialog
        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_payment_status, null);
        
        android.widget.TextView tvMessage = dialogView.findViewById(R.id.tvStatusMessage);
        tvMessage.setText("Complete your payment on " + paymentMethodName + ".\n\nAfter completing payment, return to this app and we'll automatically verify your payment.");
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this, R.style.CustomDialogTheme);
        builder.setView(dialogView);
        builder.setCancelable(false);
        
        paymentStatusDialog = builder.create();
        
        android.widget.Button btnCheckStatus = dialogView.findViewById(R.id.btnCheckStatus);
        android.widget.Button btnCancelPayment = dialogView.findViewById(R.id.btnCancelPayment);
        
        btnCheckStatus.setOnClickListener(v -> {
            if (paymentStatusDialog != null && paymentStatusDialog.isShowing()) {
                paymentStatusDialog.dismiss();
            }
            if (!isFinishing() && !isDestroyed()) {
                checkPaymentStatus();
            }
        });
        
        btnCancelPayment.setOnClickListener(v -> {
            if (paymentStatusDialog != null && paymentStatusDialog.isShowing()) {
                paymentStatusDialog.dismiss();
            }
            currentPaymentIntentId = null;
            pendingAddress = null;
        });
        
        // Auto-dismiss after 5 seconds and start checking
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isFinishing() && !isDestroyed() && paymentStatusDialog != null && paymentStatusDialog.isShowing()) {
                try {
                    paymentStatusDialog.dismiss();
                    // Start checking payment status automatically
                    checkPaymentStatus();
                } catch (Exception e) {
                    android.util.Log.e("CheckoutActivity", "Error dismissing payment status dialog", e);
                }
            }
        }, 5000);
        
        if (!isFinishing() && !isDestroyed()) {
            paymentStatusDialog.show();
        }
    }
    
    private void showProcessingDialog(String title, String message) {
        if (paymentProgressDialog != null && paymentProgressDialog.isShowing()) {
            try {
                paymentProgressDialog.dismiss();
            } catch (Exception e) {
                android.util.Log.e("CheckoutActivity", "Error dismissing progress dialog", e);
            }
        }
        
        // Check if activity is finishing
        if (isFinishing() || isDestroyed()) {
            return;
        }
        
        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_payment_processing, null);
        
        android.widget.TextView tvTitle = dialogView.findViewById(R.id.tvProcessingTitle);
        android.widget.TextView tvMessage = dialogView.findViewById(R.id.tvProcessingMessage);
        
        tvTitle.setText(title);
        tvMessage.setText(message);
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this, R.style.CustomDialogTheme);
        builder.setView(dialogView);
        builder.setCancelable(false);
        
        paymentProgressDialog = builder.create();
        if (!isFinishing() && !isDestroyed()) {
            paymentProgressDialog.show();
        }
    }
    
    private void showPaymentSuccessDialog() {
        // Dismiss existing dialog if showing
        if (paymentSuccessDialog != null && paymentSuccessDialog.isShowing()) {
            paymentSuccessDialog.dismiss();
        }
        
        // Check if activity is finishing
        if (isFinishing() || isDestroyed()) {
            return;
        }
        
        // Create professional success dialog
        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_payment_success, null);
        
        android.widget.TextView tvPaymentMethod = dialogView.findViewById(R.id.tvPaymentMethod);
        android.widget.TextView tvAmountPaid = dialogView.findViewById(R.id.tvAmountPaid);
        
        // Update payment method name
        String paymentMethodName = selectedPaymentMethod.equals("gcash") ? "GCash" : 
                                  selectedPaymentMethod.equals("maya") ? "Maya" : "Cash on Delivery";
        tvPaymentMethod.setText(paymentMethodName);
        tvAmountPaid.setText(String.format(Locale.getDefault(), "₱%.2f", totalAmount));
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this, R.style.CustomDialogTheme);
        builder.setView(dialogView);
        builder.setCancelable(false);
        
        paymentSuccessDialog = builder.create();
        
        android.widget.Button btnContinue = dialogView.findViewById(R.id.btnContinue);
        btnContinue.setOnClickListener(v -> {
            if (paymentSuccessDialog != null && paymentSuccessDialog.isShowing()) {
                paymentSuccessDialog.dismiss();
            }
            // Order creation will be handled after this dialog closes
        });
        
        if (!isFinishing() && !isDestroyed()) {
            paymentSuccessDialog.show();
        }
    }
    
    private void checkPaymentStatus() {
        if (currentPaymentIntentId == null || currentPaymentIntentId.isEmpty()) {
            Toast.makeText(this, "No active payment to check", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Prevent duplicate checks
        if (isCheckingPaymentStatus) {
            android.util.Log.d("CheckoutActivity", "Payment status check already in progress");
            return;
        }
        
        isCheckingPaymentStatus = true;
        
        // Show loading dialog
        showProcessingDialog("Checking Payment Status", "Verifying your payment...");
        
        payMongoService.checkPaymentStatus(currentPaymentIntentId, new PayMongoService.PaymentStatusCallback() {
            @Override
            public void onSuccess(String status, String paymentIntentId) {
                runOnUiThread(() -> {
                    // Check if activity is still valid
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    
                    isCheckingPaymentStatus = false;
                    
                    if (paymentProgressDialog != null && paymentProgressDialog.isShowing()) {
                        try {
                            paymentProgressDialog.dismiss();
                        } catch (Exception e) {
                            android.util.Log.e("CheckoutActivity", "Error dismissing progress dialog", e);
                        }
                    }
                    
                    android.util.Log.d("CheckoutActivity", "Payment status: " + status);
                    
                    if (PayMongoConfig.STATUS_SUCCEEDED.equals(status)) {
                        // Payment successful - stop periodic checks
                        stopPeriodicPaymentCheck();
                        
                        // Show payment success notification
                        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
                        currencyFormat.setCurrency(java.util.Currency.getInstance("PHP"));
                        String amountStr = currencyFormat.format(totalAmount);
                        notificationHelper.showPaymentSuccessNotification(amountStr);
                        
                        // Payment successful - show success dialog
                        android.util.Log.d("CheckoutActivity", "Payment succeeded! Creating order...");
                        showPaymentSuccessDialog();
                        if (pendingAddress != null) {
                            createOrder(pendingAddress);
                            currentPaymentIntentId = null;
                            pendingAddress = null;
                        } else {
                            android.util.Log.w("CheckoutActivity", "Payment succeeded but no pending address");
                            Toast.makeText(CheckoutActivity.this, "Payment successful! Please try placing your order again.", Toast.LENGTH_LONG).show();
                        }
                    } else if (PayMongoConfig.STATUS_PROCESSING.equals(status)) {
                        // Payment still processing
                        android.util.Log.d("CheckoutActivity", "Payment still processing, will retry...");
                        Toast.makeText(CheckoutActivity.this, "Payment is still processing. Please wait...", Toast.LENGTH_SHORT).show();
                        
                        // Retry checking after 3 seconds
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            if (!isFinishing() && !isDestroyed()) {
                                isCheckingPaymentStatus = false;
                                checkPaymentStatus();
                            }
                        }, 3000);
                    } else if (PayMongoConfig.STATUS_CANCELED.equals(status)) {
                        // Payment canceled - stop periodic checks
                        stopPeriodicPaymentCheck();
                        
                        // Payment canceled
                        android.util.Log.d("CheckoutActivity", "Payment was canceled");
                        Toast.makeText(CheckoutActivity.this, "Payment was canceled", Toast.LENGTH_SHORT).show();
                        currentPaymentIntentId = null;
                        pendingAddress = null;
                    } else {
                        // Payment pending or other status
                        android.util.Log.d("CheckoutActivity", "Payment status: " + status);
                        Toast.makeText(CheckoutActivity.this, 
                            "Payment status: " + status + "\nPlease complete the payment.", 
                            Toast.LENGTH_LONG).show();
                        
                        // Show dialog again to check later
                        showPaymentStatusDialog("Payment");
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    // Check if activity is still valid
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    
                    isCheckingPaymentStatus = false;
                    
                    if (paymentProgressDialog != null && paymentProgressDialog.isShowing()) {
                        try {
                            paymentProgressDialog.dismiss();
                        } catch (Exception e) {
                            android.util.Log.e("CheckoutActivity", "Error dismissing progress dialog", e);
                        }
                    }
                    
                    android.util.Log.e("CheckoutActivity", "Payment status check error: " + error);
                    Toast.makeText(CheckoutActivity.this, 
                        "Unable to check payment status: " + error, 
                        Toast.LENGTH_SHORT).show();
                    
                    // Ask user if they completed payment
                    android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(CheckoutActivity.this);
                    builder.setTitle("Payment Status");
                    builder.setMessage("Unable to verify payment status automatically.\n\n" +
                        "Did you complete the payment?");
                    builder.setPositiveButton("Yes, I Paid", (dialog, which) -> {
                        // Assume payment successful for demo
                        if (pendingAddress != null) {
                            Toast.makeText(CheckoutActivity.this, 
                                "Proceeding with order (payment assumed successful)", 
                                Toast.LENGTH_SHORT).show();
                            createOrder(pendingAddress);
                            currentPaymentIntentId = null;
                            pendingAddress = null;
                        }
                    });
                    builder.setNegativeButton("No, Try Again", null);
                    builder.show();
                });
            }
        });
    }
    
    private void createOrder(String address) {
        // Show loading feedback
        Toast.makeText(this, "Placing your order...", Toast.LENGTH_SHORT).show();
        
        String customerId = preferenceUtil.getUserId();
        if (customerId == null || customerId.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Validate totalAmount before creating order
        if (Double.isNaN(totalAmount) || Double.isInfinite(totalAmount) || totalAmount <= 0) {
            Toast.makeText(this, "Invalid order amount. Please try again.", Toast.LENGTH_SHORT).show();
            android.util.Log.e("CheckoutActivity", "Invalid totalAmount: " + totalAmount);
            return;
        }
        
        Order order = new Order(customerId, totalAmount, selectedPaymentMethod, address);
        order.setDeliveryLat(selectedLat);
        order.setDeliveryLng(selectedLng);
        
        // Try to get access token - check both AuthService and PreferenceUtil
        String accessToken = authService.getAccessToken();
        if (accessToken == null || accessToken.isEmpty()) {
            // Try to get from preferences directly as fallback
            accessToken = preferenceUtil.getAccessToken();
        }
        
        if (accessToken == null || accessToken.isEmpty()) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            android.util.Log.e("CheckoutActivity", "Access token is null or empty. User ID: " + customerId);
            return;
        }
        
        // Ensure cartItems is available
        if (cartItems == null || cartItems.isEmpty()) {
            Toast.makeText(this, "No items in cart. Please add items to cart first.", Toast.LENGTH_SHORT).show();
            android.util.Log.e("CheckoutActivity", "cartItems is null or empty when creating order");
            return;
        }
        
        // Pass cart items to save with order
        android.util.Log.d("CheckoutActivity", "Creating order with " + cartItems.size() + " items");
        orderService.createOrder(order, cartItems, accessToken, new OrderService.SingleOrderCallback() {
            @Override
            public void onSuccess(Order order) {
                runOnUiThread(() -> {
                    // Clear cart
                    getSharedPreferences("CartPrefs", MODE_PRIVATE).edit().clear().apply();
                    clearServerCart(customerId);
                    
                    // Send database notification
                    String orderIdDisplay = order.getIdString() != null ? order.getIdString() : String.valueOf(order.getId());
                    notificationService.createNotification(
                        customerId,
                        "Order Placed",
                        "Your order has been placed successfully! Order ID: " + orderIdDisplay,
                        "order",
                        order.getId()
                    );
                    
                    // Show system notification
                    notificationHelper.showOrderPlacedNotification(orderIdDisplay, order.getId());
                    
                    Toast.makeText(CheckoutActivity.this, "Order placed successfully!", Toast.LENGTH_SHORT).show();
                    
                    // Navigate to order history or dashboard
                    finish();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    android.util.Log.e("CheckoutActivity", "Order creation error: " + error);
                    
                    // If JWT expired, try to refresh token and retry
                    if (error != null && (error.contains("JWT expired") || error.contains("jwt expired") || error.contains("token expired"))) {
                        android.util.Log.d("CheckoutActivity", "JWT expired, attempting to refresh token...");
                        authService.refreshAccessToken(new AuthService.TokenCallback() {
                            @Override
                            public void onSuccess(String newAccessToken) {
                                android.util.Log.d("CheckoutActivity", "Token refreshed successfully, retrying order creation...");
                                // Retry order creation with new token
                                orderService.createOrder(order, cartItems, newAccessToken, new OrderService.SingleOrderCallback() {
                                    @Override
                                    public void onSuccess(Order retryOrder) {
                                        runOnUiThread(() -> {
                                            // Clear cart
                                            getSharedPreferences("CartPrefs", MODE_PRIVATE).edit().clear().apply();
                                            clearServerCart(customerId);
                                            
                                            // Send database notification
                                            String retryOrderIdDisplay = retryOrder.getIdString() != null ? retryOrder.getIdString() : String.valueOf(retryOrder.getId());
                                            notificationService.createNotification(
                                                customerId,
                                                "Order Placed",
                                                "Your order has been placed successfully! Order ID: " + retryOrderIdDisplay,
                                                "order",
                                                retryOrder.getId()
                                            );
                                            
                                            // Show system notification
                                            notificationHelper.showOrderPlacedNotification(retryOrderIdDisplay, retryOrder.getId());
                                            
                                            Toast.makeText(CheckoutActivity.this, "Order placed successfully!", Toast.LENGTH_SHORT).show();
                                            finish();
                                        });
                                    }
                                    
                                    @Override
                                    public void onError(String retryError) {
                                        runOnUiThread(() -> {
                                            android.util.Log.e("CheckoutActivity", "Order creation error after token refresh: " + retryError);
                                            Toast.makeText(CheckoutActivity.this, "Failed to place order: " + retryError, Toast.LENGTH_SHORT).show();
                                        });
                                    }
                                });
                            }
                            
                            @Override
                            public void onError(String refreshError) {
                                runOnUiThread(() -> {
                                    android.util.Log.e("CheckoutActivity", "Token refresh failed: " + refreshError);
                                    Toast.makeText(CheckoutActivity.this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
                    } else {
                        Toast.makeText(CheckoutActivity.this, "Failed to place order: " + error, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
    
    private void handlePaymentReturn(Intent intent) {
        // Check if returning from PayMongo payment gateway via deep link
        // Note: PayMongo redirects to HTTPS URL, so we check for app deep link
        // In a real implementation, you'd set up App Links or a web page redirect
        if (intent != null && intent.getData() != null) {
            Uri data = intent.getData();
            String scheme = data.getScheme();
            String host = data.getHost();
            
            // Check for app deep link (foodorderingsystem://payment-return)
            if ("foodorderingsystem".equals(scheme) && "payment-return".equals(host)) {
                // User returned from PayMongo payment
                android.util.Log.d("CheckoutActivity", "Returned from PayMongo payment via deep link");
                // Check payment status when we have a payment intent ID
                if (currentPaymentIntentId != null && !currentPaymentIntentId.isEmpty()) {
                    // Delay slightly to ensure UI is ready
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        checkPaymentStatus();
                    }, 1000);
                }
            }
            // Also check for HTTP/HTTPS redirect from PayMongo (if using App Links)
            // This would require Android App Links configuration in production
        }
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handlePaymentReturn(intent);
    }
    
    @Override
    protected void onResume() {
        super.onResume();

        if (osmMapView != null) {
            osmMapView.onResume();
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            enableMyLocationOverlay();
        }
        
        // Check payment status when user returns to app
        // This handles the case when user completes payment and returns
        if (currentPaymentIntentId != null && !currentPaymentIntentId.isEmpty()) {
            // Prevent multiple simultaneous checks
            long currentTime = System.currentTimeMillis();
            if (!isCheckingPaymentStatus && (currentTime - lastPaymentCheckTime > 1500)) {
                lastPaymentCheckTime = currentTime;
                android.util.Log.d("CheckoutActivity", "onResume: Detected active payment intent, checking status immediately");
                
                // Check immediately - no delay needed
                if (!isCheckingPaymentStatus) {
                    android.util.Log.d("CheckoutActivity", "User returned to app, checking payment status for: " + currentPaymentIntentId);
                    // Show toast on UI thread
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Verifying payment...", Toast.LENGTH_SHORT).show();
                    });
                    checkPaymentStatus();
                }
            }
        }
    }
    
    private void startPeriodicPaymentCheck() {
        // Stop any existing periodic check
        stopPeriodicPaymentCheck();
        
        periodicCheckHandler = new Handler(Looper.getMainLooper());
        periodicCheckRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentPaymentIntentId != null && !currentPaymentIntentId.isEmpty() && !isCheckingPaymentStatus) {
                    android.util.Log.d("CheckoutActivity", "Periodic check: Verifying payment status");
                    checkPaymentStatus();
                    // Check again in 5 seconds if still pending
                    if (currentPaymentIntentId != null) {
                        periodicCheckHandler.postDelayed(this, 5000);
                    }
                }
            }
        };
        
        // Start checking after 3 seconds, then every 5 seconds
        periodicCheckHandler.postDelayed(periodicCheckRunnable, 3000);
    }
    
    private void stopPeriodicPaymentCheck() {
        if (periodicCheckHandler != null && periodicCheckRunnable != null) {
            periodicCheckHandler.removeCallbacks(periodicCheckRunnable);
            periodicCheckHandler = null;
            periodicCheckRunnable = null;
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (osmMapView != null) {
            osmMapView.onPause();
        }
        if (myLocationOverlay != null) {
            myLocationOverlay.disableMyLocation();
        }
        // Don't stop periodic check - we want it to continue
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPeriodicPaymentCheck();
        if (osmMapView != null) {
            osmMapView.onDetach();
        }
        
        // Dismiss all dialogs to prevent window leaks
        dismissAllDialogs();
    }
    
    private void dismissAllDialogs() {
        try {
            if (paymentProgressDialog != null && paymentProgressDialog.isShowing()) {
                paymentProgressDialog.dismiss();
            }
        } catch (Exception e) {
            android.util.Log.e("CheckoutActivity", "Error dismissing progress dialog", e);
        }
        
        try {
            if (paymentStatusDialog != null && paymentStatusDialog.isShowing()) {
                paymentStatusDialog.dismiss();
            }
        } catch (Exception e) {
            android.util.Log.e("CheckoutActivity", "Error dismissing status dialog", e);
        }
        
        try {
            if (paymentSuccessDialog != null && paymentSuccessDialog.isShowing()) {
                paymentSuccessDialog.dismiss();
            }
        } catch (Exception e) {
            android.util.Log.e("CheckoutActivity", "Error dismissing success dialog", e);
        }
        
        paymentProgressDialog = null;
        paymentStatusDialog = null;
        paymentSuccessDialog = null;
    }

    private void clearServerCart(String customerId) {
        if (cartService == null || customerId == null || customerId.isEmpty()) {
            return;
        }
        cartService.clearCart(customerId, new CartService.SimpleCallback() {
            @Override
            public void onSuccess() {
                android.util.Log.d("CheckoutActivity", "Remote cart cleared for user " + customerId);
            }

            @Override
            public void onError(String error) {
                android.util.Log.e("CheckoutActivity", "Failed to clear remote cart: " + error);
            }
        });
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableMyLocationOverlay();
            getCurrentLocation();
        }
    }
}

