package com.fp.foodorderingsystem.activities.admin;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
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
import com.fp.foodorderingsystem.services.SupabaseRealtimeClient;
import com.fp.foodorderingsystem.services.SupabaseRealtimeClient.RealtimeListener;
import com.fp.foodorderingsystem.services.UserService;
import com.fp.foodorderingsystem.services.UserService.UserCallback;
import com.fp.foodorderingsystem.services.UserService.UserListCallback;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ManageUsersActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener {

    private TextInputLayout inputSearchLayout;
    private TextInputEditText inputSearch;
    private MaterialButton btnRefresh;
    private MaterialButton btnClearSearch;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView rvUsers;
    private ProgressBar progressBar;
    private TextView tvEmptyState;
    private TextView tvTotalUsers;
    private TextView tvAdminsCount;
    private TextView tvCustomersCount;

    private AdminUserAdapter adapter;
    private UserService userService;
    private AuthService authService;
    private SupabaseRealtimeClient realtimeClient;
    private final List<User> users = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();
    private String currentQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_users);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Manage Users");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        userService = new UserService(this);
        authService = new AuthService(this);
        realtimeClient = new SupabaseRealtimeClient();

        initViews();
        setupRecyclerView();
        setupListeners();

        loadUsers(true);
        subscribeToRealtimeUpdates();
    }

    private void initViews() {
        inputSearchLayout = findViewById(R.id.inputSearchLayout);
        inputSearch = findViewById(R.id.inputSearch);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        swipeRefreshLayout = findViewById(R.id.swipeRefresh);
        rvUsers = findViewById(R.id.rvUsers);
        progressBar = findViewById(R.id.progressBar);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        tvTotalUsers = findViewById(R.id.tvTotalUsers);
        tvAdminsCount = findViewById(R.id.tvAdminsCount);
        tvCustomersCount = findViewById(R.id.tvCustomersCount);
    }

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

    private void setupListeners() {
        swipeRefreshLayout.setOnRefreshListener(this);
        btnRefresh.setOnClickListener(v -> loadUsers(true));
        btnClearSearch.setOnClickListener(v -> {
            inputSearch.setText("");
            applyFilter("");
        });

        inputSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter(s != null ? s.toString() : "");
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadUsers(boolean showSpinner) {
        loadUsers(showSpinner, true);
    }

    private void loadUsers(boolean showSpinner, boolean allowTokenRefresh) {
        if (showSpinner) {
            showLoading(true);
        }

        android.util.Log.d("ManageUsersActivity", "Loading users...");
        
        // Try with authentication first, fallback to unauthenticated
        String accessToken = authService.getAccessToken();
        userService.getAllUsers(accessToken, new UserListCallback() {
            @Override
            public void onSuccess(List<User> fetchedUsers) {
                mainHandler.post(() -> {
                    android.util.Log.d("ManageUsersActivity", "Users loaded: " + (fetchedUsers != null ? fetchedUsers.size() : 0));
                    users.clear();
                    if (fetchedUsers != null) {
                        users.addAll(fetchedUsers);
                        android.util.Log.d("ManageUsersActivity", "Added " + fetchedUsers.size() + " users to list");
                    } else {
                        android.util.Log.w("ManageUsersActivity", "Fetched users list is null");
                    }
                    sortUsers();
                    applyFilter(currentQuery);
                    updateSummaryCards();
                    showLoading(false);
                    swipeRefreshLayout.setRefreshing(false);
                    updateEmptyState();
                });
            }

            @Override
            public void onError(String error) {
                android.util.Log.e("ManageUsersActivity", "Error loading users: " + error);
                
                if (allowTokenRefresh && isAuthTokenExpired(error)) {
                    android.util.Log.w("ManageUsersActivity", "Access token expired. Attempting refresh...");
                    authService.refreshAccessToken(new AuthService.TokenCallback() {
                        @Override
                        public void onSuccess(String newToken) {
                            android.util.Log.d("ManageUsersActivity", "Token refresh success. Reloading users.");
                            mainHandler.post(() -> loadUsers(showSpinner, false));
                        }

                        @Override
                        public void onError(String refreshError) {
                            android.util.Log.e("ManageUsersActivity", "Token refresh failed: " + refreshError);
                            mainHandler.post(() -> {
                                Toast.makeText(ManageUsersActivity.this, refreshError, Toast.LENGTH_LONG).show();
                                showLoading(false);
                                swipeRefreshLayout.setRefreshing(false);
                                updateEmptyState();
                            });
                        }
                    });
                } else {
                    mainHandler.post(() -> {
                        Toast.makeText(ManageUsersActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
                        showLoading(false);
                        swipeRefreshLayout.setRefreshing(false);
                        updateEmptyState();
                    });
                }
            }
        });
    }

    private boolean isAuthTokenExpired(String error) {
        if (error == null) {
            return false;
        }
        String lower = error.toLowerCase();
        return lower.contains("jwt expired") ||
            lower.contains("token expired") ||
            lower.contains("invalid token") ||
            lower.contains("expired signature") ||
            lower.contains("session not found");
    }

    private void applyFilter(String query) {
        currentQuery = query != null ? query.trim() : "";
        String lowerQuery = currentQuery.toLowerCase();
        List<User> filtered = new ArrayList<>();
        if (TextUtils.isEmpty(lowerQuery)) {
            filtered.addAll(users);
        } else {
            for (User user : users) {
                String name = user.getFullName() != null ? user.getFullName().toLowerCase() : "";
                String email = user.getEmail() != null ? user.getEmail().toLowerCase() : "";
                if (name.contains(lowerQuery) || email.contains(lowerQuery)) {
                    filtered.add(user);
                }
            }
        }
        android.util.Log.d("ManageUsersActivity", "Applying filter - Total users: " + users.size() + ", Filtered: " + filtered.size());
        adapter.setItems(filtered);
        updateEmptyState();
    }

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
        
        String createdAt = user.getCreatedAt() != null ? user.getCreatedAt().replace("T", " ").substring(0, Math.min(19, user.getCreatedAt().length())) : "N/A";
        String updatedAt = user.getUpdatedAt() != null ? user.getUpdatedAt().replace("T", " ").substring(0, Math.min(19, user.getUpdatedAt().length())) : "N/A";
        tvCreatedAt.setText(createdAt);
        tvUpdatedAt.setText(updatedAt);
        
        new AlertDialog.Builder(this)
            .setTitle("User Details")
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show();
    }

    private void confirmRoleToggle(User user) {
        if (user == null) {
            return;
        }
        String newRole = user.isAdmin() ? "customer" : "admin";
        String message = "Change " + user.getFullName() + " to " + newRole + "?";
        new AlertDialog.Builder(this)
            .setTitle("Change role")
            .setMessage(message)
            .setPositiveButton("Change", (dialog, which) -> updateUserRole(user, newRole))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void updateUserRole(User user, String newRole) {
        showLoading(true);
        userService.updateUserRole(user.getId(), newRole, new UserCallback() {
            @Override
            public void onSuccess(User updatedUser) {
                mainHandler.post(() -> {
                    Toast.makeText(ManageUsersActivity.this, "Role updated", Toast.LENGTH_SHORT).show();
                    upsertLocalUser(updatedUser);
                    showLoading(false);
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    Toast.makeText(ManageUsersActivity.this, error, Toast.LENGTH_SHORT).show();
                    showLoading(false);
                });
            }
        });
    }

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
                    Toast.makeText(ManageUsersActivity.this, "Verification updated", Toast.LENGTH_SHORT).show();
                    upsertLocalUser(updatedUser);
                    showLoading(false);
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    Toast.makeText(ManageUsersActivity.this, error, Toast.LENGTH_SHORT).show();
                    showLoading(false);
                });
            }
        });
    }

    private void confirmResetCancellations(User user) {
        if (user == null) {
            return;
        }
        if (user.getCancellationCount() == 0) {
            Toast.makeText(this, "No cancellations to reset", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Reset cancellations")
            .setMessage("Reset cancellation count for " + user.getFullName() + "?")
            .setPositiveButton("Reset", (dialog, which) -> resetCancellations(user))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void resetCancellations(User user) {
        showLoading(true);
        userService.resetCancellationCount(user.getId(), new UserCallback() {
            @Override
            public void onSuccess(User updatedUser) {
                mainHandler.post(() -> {
                    Toast.makeText(ManageUsersActivity.this, "Cancellation count reset", Toast.LENGTH_SHORT).show();
                    upsertLocalUser(updatedUser);
                    showLoading(false);
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    Toast.makeText(ManageUsersActivity.this, error, Toast.LENGTH_SHORT).show();
                    showLoading(false);
                });
            }
        });
    }

    private void upsertLocalUser(User user) {
        if (user == null) {
            return;
        }
        boolean found = false;
        for (int i = 0; i < users.size(); i++) {
            if (TextUtils.equals(users.get(i).getId(), user.getId())) {
                users.set(i, user);
                found = true;
                break;
            }
        }
        if (!found) {
            users.add(0, user);
        }
        sortUsers();
        
        // Update adapter efficiently using upsertUser if user matches filter
        String lowerQuery = currentQuery.toLowerCase();
        boolean matchesFilter = TextUtils.isEmpty(lowerQuery) || 
            (user.getFullName() != null && user.getFullName().toLowerCase().contains(lowerQuery)) ||
            (user.getEmail() != null && user.getEmail().toLowerCase().contains(lowerQuery));
        
        if (matchesFilter) {
            adapter.upsertUser(user);
        } else {
            // User doesn't match filter, but might have matched before, so refresh
            applyFilter(currentQuery);
        }
        updateSummaryCards();
        updateEmptyState();
    }

    private void removeLocalUser(String userId) {
        if (TextUtils.isEmpty(userId)) {
            return;
        }
        for (int i = 0; i < users.size(); i++) {
            if (TextUtils.equals(users.get(i).getId(), userId)) {
                users.remove(i);
                break;
            }
        }
        adapter.removeUser(userId);
        updateSummaryCards();
        updateEmptyState();
    }
    
    private void updateSummaryCards() {
        if (tvTotalUsers == null || tvAdminsCount == null || tvCustomersCount == null) {
            return;
        }
        
        int totalCount = users.size();
        int adminsCount = 0;
        int customersCount = 0;
        
        for (User user : users) {
            if (user != null && user.isAdmin()) {
                adminsCount++;
            } else {
                customersCount++;
            }
        }
        
        tvTotalUsers.setText(String.valueOf(totalCount));
        tvAdminsCount.setText(String.valueOf(adminsCount));
        tvCustomersCount.setText(String.valueOf(customersCount));
    }

    private void updateEmptyState() {
        boolean isEmpty = adapter.getItemCount() == 0;
        android.util.Log.d("ManageUsersActivity", "Updating empty state - isEmpty: " + isEmpty + ", adapter count: " + adapter.getItemCount() + ", total users: " + users.size());
        
        if (isEmpty) {
            if (users.isEmpty()) {
                tvEmptyState.setText("No users found.\nTry refreshing the list.");
            } else if (!TextUtils.isEmpty(currentQuery)) {
                tvEmptyState.setText("No users match your search.\nTry adjusting your filters or refresh the list.");
            } else {
                tvEmptyState.setText("No users found.\nTry refreshing the list.");
            }
        }
        
        tvEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvUsers.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    private void sortUsers() {
        Collections.sort(users, (a, b) -> {
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
        realtimeClient.subscribeToTable("public", "users", new RealtimeListener() {
            @Override
            public void onOpen() {
                android.util.Log.d("ManageUsersActivity", "Realtime connection established for users table");
                // Connection successfully established - users list will update in real-time
            }

            @Override
            public void onChange(JsonObject payload) {
                handleRealtimePayload(payload);
            }

            @Override
            public void onError(String error) {
                android.util.Log.e("ManageUsersActivity", "Realtime error: " + error);
                mainHandler.post(() -> {
                    // Only show error toast for critical errors, not connection issues
                    if (error != null && !error.contains("connection") && !error.contains("timeout")) {
                        Toast.makeText(ManageUsersActivity.this, "Realtime error: " + error, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void handleRealtimePayload(JsonObject payload) {
        if (payload == null) {
            return;
        }
        String eventTypeValue = "";
        if (payload.has("eventType")) {
            eventTypeValue = payload.get("eventType").getAsString();
        } else if (payload.has("type")) {
            eventTypeValue = payload.get("type").getAsString();
        }

        final String eventType = eventTypeValue;

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
                    try {
                        User updatedUser = gson.fromJson(newRecord, User.class);
                        if (updatedUser != null) {
                            mainHandler.post(() -> {
                                upsertLocalUser(updatedUser);
                                android.util.Log.d("ManageUsersActivity", "Realtime update: " + eventType + " for user " + updatedUser.getId());
                            });
                        }
                    } catch (Exception e) {
                        android.util.Log.e("ManageUsersActivity", "Error parsing user from realtime payload", e);
                    }
                }
                break;
            case "DELETE":
                if (oldRecord != null && oldRecord.has("id")) {
                    try {
                        String id = oldRecord.get("id").getAsString();
                        mainHandler.post(() -> {
                            removeLocalUser(id);
                            android.util.Log.d("ManageUsersActivity", "Realtime delete: user " + id);
                        });
                    } catch (Exception e) {
                        android.util.Log.e("ManageUsersActivity", "Error parsing user ID from delete payload", e);
                    }
                }
                break;
            default:
                // ignore other events
                android.util.Log.d("ManageUsersActivity", "Ignoring realtime event: " + eventType);
        }
    }

    @Override
    public void onRefresh() {
        loadUsers(false);
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
}
