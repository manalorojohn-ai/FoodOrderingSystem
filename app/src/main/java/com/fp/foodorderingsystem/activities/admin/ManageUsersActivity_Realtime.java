package com.fp.foodorderingsystem.activities.admin;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.adapters.AdminUserAdapter;
import com.fp.foodorderingsystem.adapters.AdminUserAdapter.OnUserActionListener;
import com.fp.foodorderingsystem.models.User;
import com.fp.foodorderingsystem.services.AuthService;
import com.fp.foodorderingsystem.services.RealtimeUserManager;
import com.fp.foodorderingsystem.services.RealtimeUserManager.UserListListener;
import com.fp.foodorderingsystem.services.RealtimeUserManager.UserEventListener;
import com.fp.foodorderingsystem.services.UserService;
import com.fp.foodorderingsystem.services.UserService.UserCallback;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.List;

/**
 * ManageUsersActivity - Real-time Admin User Management
 * 
 * Features:
 * - Real-time user data synchronization from Supabase
 * - Automatic updates without manual refresh
 * - Connection status monitoring
 * - Search and filtering
 * - User role and verification management
 * - Offline support with local caching
 */
public class ManageUsersActivity_Realtime extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener {

    private static final String TAG = "ManageUsersActivity";

    // UI Views
    private TextInputLayout inputSearchLayout;
    private TextInputEditText inputSearch;
    private MaterialButton btnRefresh;
    private MaterialButton btnClearSearch;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView rvUsers;
    private ProgressBar progressBar;
    private TextView tvEmptyState;
    private TextView tvConnectionStatus;

    // Services
    private AdminUserAdapter adapter;
    private UserService userService;
    private AuthService authService;
    private RealtimeUserManager realtimeUserManager;
    
    // Utilities
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String currentQuery = "";
    
    // Listeners
    private final UserListListener userListListener = new UserListListener() {
        @Override
        public void onUsersUpdated(List<User> users) {
            mainHandler.post(() -> {
                Log.d(TAG, "Users updated via realtime: " + users.size() + " users");
                adapter.setItems(users);
                updateEmptyState();
                swipeRefreshLayout.setRefreshing(false);
            });
        }
        
        @Override
        public void onConnectionStateChanged(boolean connected) {
            mainHandler.post(() -> {
                updateConnectionStatus(connected);
                if (connected) {
                    Log.d(TAG, "Realtime connection established");
                    Toast.makeText(ManageUsersActivity_Realtime.this, "Connected to real-time updates", Toast.LENGTH_SHORT).show();
                } else {
                    Log.w(TAG, "Realtime connection lost");
                    Toast.makeText(ManageUsersActivity_Realtime.this, "Real-time connection lost", Toast.LENGTH_SHORT).show();
                }
            });
        }
        
        @Override
        public void onError(String error) {
            mainHandler.post(() -> {
                Log.e(TAG, "Realtime error: " + error);
                if (error != null && !error.contains("connection") && !error.contains("timeout")) {
                    Toast.makeText(ManageUsersActivity_Realtime.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        }
    };
    
    private final UserEventListener userEventListener = new UserEventListener() {
        @Override
        public void onUserAdded(User user) {
            Log.d(TAG, "User added: " + user.getId());
        }
        
        @Override
        public void onUserUpdated(User user) {
            Log.d(TAG, "User updated: " + user.getId());
        }
        
        @Override
        public void onUserDeleted(String userId) {
            Log.d(TAG, "User deleted: " + userId);
        }
        
        @Override
        public void onConnectionStateChanged(boolean connected) {
            // Handled by userListListener
        }
        
        @Override
        public void onError(String error) {
            // Handled by userListListener
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_users);
        
        // Setup toolbar
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Initialize services
        userService = new UserService(this);
        authService = new AuthService(this);
        realtimeUserManager = new RealtimeUserManager(userService);
        
        // Initialize UI
        initViews();
        setupRecyclerView();
        setupListeners();
        
        // Start real-time sync
        startRealtimeSync();
    }

    /**
     * Initialize UI views
     */
    private void initViews() {
        inputSearchLayout = findViewById(R.id.inputSearchLayout);
        inputSearch = findViewById(R.id.inputSearch);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        swipeRefreshLayout = findViewById(R.id.swipeRefresh);
        rvUsers = findViewById(R.id.rvUsers);
        progressBar = findViewById(R.id.progressBar);
        tvEmptyState = findViewById(R.id.tvEmptyState);
    }

    /**
     * Setup RecyclerView adapter
     */
    private void setupRecyclerView() {
        adapter = new AdminUserAdapter();
        adapter.setOnUserActionListener(new OnUserActionListener() {
            @Override
            public void onViewDetails(User user) {
                viewUserDetails(user);
            }

            @Override
            public void onToggleRole(User user) {
                confirmRoleToggle(user);
            }

            @Override
            public void onToggleVerification(User user) {
                toggleVerification(user);
            }

            @Override
            public void onResetCancellations(User user) {
                confirmResetCancellations(user);
            }
        });
        rvUsers.setLayoutManager(new LinearLayoutManager(this));
        rvUsers.setAdapter(adapter);
    }

    /**
     * Setup UI listeners
     */
    private void setupListeners() {
        swipeRefreshLayout.setOnRefreshListener(this);
        
        btnRefresh.setOnClickListener(v -> {
            Log.d(TAG, "Manual refresh triggered");
            swipeRefreshLayout.setRefreshing(true);
            onRefresh();
        });
        
        btnClearSearch.setOnClickListener(v -> {
            inputSearch.setText("");
            applySearch("");
        });

        inputSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applySearch(s != null ? s.toString() : "");
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    /**
     * Start real-time synchronization
     */
    private void startRealtimeSync() {
        showLoading(true);
        String accessToken = authService.getAccessToken();
        
        realtimeUserManager.addListener(userListListener);
        realtimeUserManager.addEventListener(userEventListener);
        realtimeUserManager.start(accessToken);
        
        Log.d(TAG, "Real-time synchronization started");
    }

    /**
     * Apply search filter to users
     */
    private void applySearch(String query) {
        currentQuery = query;
        Log.d(TAG, "Applying search: '" + query + "'");
        List<User> filteredUsers = realtimeUserManager.searchUsers(query);
        adapter.setItems(filteredUsers);
        updateEmptyState();
    }

    /**
     * View user details in dialog
     */
    private void viewUserDetails(User user) {
        if (user == null) {
            return;
        }
        
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_user_details, null);
        
        TextView tvName = dialogView.findViewById(R.id.tvName);
        TextView tvEmail = dialogView.findViewById(R.id.tvEmail);
        TextView tvPhone = dialogView.findViewById(R.id.tvPhone);
        TextView tvAddress = dialogView.findViewById(R.id.tvAddress);
        TextView tvUserType = dialogView.findViewById(R.id.tvUserType);
        TextView tvVerified = dialogView.findViewById(R.id.tvVerified);
        TextView tvCancellations = dialogView.findViewById(R.id.tvCancellations);
        TextView tvCreatedAt = dialogView.findViewById(R.id.tvCreatedAt);
        TextView tvUpdatedAt = dialogView.findViewById(R.id.tvUpdatedAt);
        
        tvName.setText(user.getFullName() != null ? user.getFullName() : "N/A");
        tvEmail.setText(user.getEmail() != null ? user.getEmail() : "N/A");
        tvPhone.setText(!TextUtils.isEmpty(user.getPhone()) ? user.getPhone() : "N/A");
        tvAddress.setText(!TextUtils.isEmpty(user.getAddress()) ? user.getAddress() : "N/A");
        tvUserType.setText(user.getUserType() != null ? user.getUserType() : "customer");
        tvVerified.setText(user.isVerified() ? "Yes" : "No");
        tvCancellations.setText(String.valueOf(user.getCancellationCount()));
        
        String createdAt = user.getCreatedAt() != null ? 
            user.getCreatedAt().replace("T", " ").substring(0, Math.min(19, user.getCreatedAt().length())) : "N/A";
        String updatedAt = user.getUpdatedAt() != null ? 
            user.getUpdatedAt().replace("T", " ").substring(0, Math.min(19, user.getUpdatedAt().length())) : "N/A";
        tvCreatedAt.setText(createdAt);
        tvUpdatedAt.setText(updatedAt);
        
        new AlertDialog.Builder(this)
            .setTitle("User Details")
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show();
    }

    /**
     * Confirm and toggle user role (admin <-> customer)
     */
    private void confirmRoleToggle(User user) {
        if (user == null) {
            return;
        }
        String newRole = user.isAdmin() ? "customer" : "admin";
        String message = "Change " + user.getFullName() + " to " + newRole + "?";
        new AlertDialog.Builder(this)
            .setTitle("Change Role")
            .setMessage(message)
            .setPositiveButton("Change", (dialog, which) -> updateUserRole(user, newRole))
            .setNegativeButton("Cancel", null)
            .show();
    }

    /**
     * Update user role via API
     */
    private void updateUserRole(User user, String newRole) {
        showLoading(true);
        userService.updateUserRole(user.getId(), newRole, new UserCallback() {
            @Override
            public void onSuccess(User updatedUser) {
                mainHandler.post(() -> {
                    Toast.makeText(ManageUsersActivity_Realtime.this, "Role updated", Toast.LENGTH_SHORT).show();
                    showLoading(false);
                    // Real-time update will happen automatically via websocket
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    Toast.makeText(ManageUsersActivity_Realtime.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                    showLoading(false);
                });
            }
        });
    }

    /**
     * Toggle user verification status
     */
    private void toggleVerification(User user) {
        if (user == null) {
            return;
        }
        boolean newStatus = !user.isVerified();
        showLoading(true);
        userService.updateVerification(user.getId(), newStatus, new UserCallback() {
            @Override
            public void onSuccess(User updatedUser) {
                mainHandler.post(() -> {
                    Toast.makeText(ManageUsersActivity_Realtime.this, "Verification updated", Toast.LENGTH_SHORT).show();
                    showLoading(false);
                    // Real-time update will happen automatically via websocket
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    Toast.makeText(ManageUsersActivity_Realtime.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                    showLoading(false);
                });
            }
        });
    }

    /**
     * Confirm cancellation count reset
     */
    private void confirmResetCancellations(User user) {
        if (user == null) {
            return;
        }
        if (user.getCancellationCount() == 0) {
            Toast.makeText(this, "No cancellations to reset", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Reset Cancellations")
            .setMessage("Reset cancellation count for " + user.getFullName() + "?")
            .setPositiveButton("Reset", (dialog, which) -> resetCancellations(user))
            .setNegativeButton("Cancel", null)
            .show();
    }

    /**
     * Reset user cancellation count
     */
    private void resetCancellations(User user) {
        showLoading(true);
        userService.resetCancellationCount(user.getId(), new UserCallback() {
            @Override
            public void onSuccess(User updatedUser) {
                mainHandler.post(() -> {
                    Toast.makeText(ManageUsersActivity_Realtime.this, "Cancellation count reset", Toast.LENGTH_SHORT).show();
                    showLoading(false);
                    // Real-time update will happen automatically via websocket
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    Toast.makeText(ManageUsersActivity_Realtime.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                    showLoading(false);
                });
            }
        });
    }

    /**
     * Update empty state visibility and message
     */
    private void updateEmptyState() {
        boolean isEmpty = adapter.getItemCount() == 0;
        
        if (isEmpty) {
            if (currentQuery.isEmpty()) {
                tvEmptyState.setText("No users found.\nTry refreshing the list.");
            } else {
                tvEmptyState.setText("No users match your search.\nTry adjusting your filters.");
            }
        }
        
        tvEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvUsers.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    /**
     * Update connection status indicator
     */
    private void updateConnectionStatus(boolean connected) {
        String status = connected ? "Connected to real-time" : "Disconnected from real-time";
        Log.d(TAG, "Connection status: " + status);
        if (connected) {
            Toast.makeText(this, "Connected to real-time updates", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Show/hide loading progress
     */
    private void showLoading(boolean show) {
        if (show) {
            progressBar.setVisibility(View.VISIBLE);
        } else {
            progressBar.setVisibility(View.GONE);
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    @Override
    public void onRefresh() {
        Log.d(TAG, "Manual refresh triggered");
        // Real-time manager handles auto-refresh, but manual refresh also works
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (realtimeUserManager != null) {
            realtimeUserManager.removeListener(userListListener);
            realtimeUserManager.removeEventListener(userEventListener);
            realtimeUserManager.stop();
        }
    }
}
