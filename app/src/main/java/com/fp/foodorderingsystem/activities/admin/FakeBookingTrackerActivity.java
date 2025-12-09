package com.fp.foodorderingsystem.activities.admin;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.fp.foodorderingsystem.R;
import com.google.android.material.appbar.MaterialToolbar;

public class FakeBookingTrackerActivity extends AppCompatActivity {
    private MaterialToolbar toolbar;
    private RecyclerView rvFlaggedUsers;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar progressBar;
    private TextView tvActiveReports, tvResolved;
    private View emptyState;
    private TextView tvEmptyState;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fake_booking_tracker);
        
        initViews();
        setupToolbar();
        setupRecyclerView();
        setupSwipeRefresh();
        loadData();
    }
    
    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        rvFlaggedUsers = findViewById(R.id.rvFlaggedUsers);
        swipeRefreshLayout = findViewById(R.id.swipeRefresh);
        progressBar = findViewById(R.id.progressBar);
        tvActiveReports = findViewById(R.id.tvActiveReports);
        tvResolved = findViewById(R.id.tvResolved);
        emptyState = findViewById(R.id.emptyState);
        tvEmptyState = findViewById(R.id.tvEmptyState);
    }
    
    private void setupToolbar() {
        if (toolbar != null) {
            // Don't use setSupportActionBar() to avoid conflict with theme's ActionBar
            // The navigation icon is set in XML, just set the click listener
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }
    }
    
    private void setupRecyclerView() {
        if (rvFlaggedUsers != null) {
            rvFlaggedUsers.setLayoutManager(new LinearLayoutManager(this));
            // Note: setHasFixedSize(true) is not used because RecyclerView is inside NestedScrollView
            // and uses wrap_content height, which is incompatible with setHasFixedSize
            // TODO: Set adapter when FlaggedUserAdapter is created
        }
    }
    
    private void setupSwipeRefresh() {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                loadData();
            });
            swipeRefreshLayout.setColorSchemeResources(
                R.color.green_primary,
                android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light
            );
        }
    }
    
    private void loadData() {
        showLoading(true);
        // TODO: Implement data loading from database
        // Query users table where cancellation_count >= 3
        
        // For now, show empty state
        showLoading(false);
        updateEmptyState(true);
    }
    
    private void showLoading(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(show);
        }
    }
    
    private void updateEmptyState(boolean isEmpty) {
        if (emptyState != null && rvFlaggedUsers != null) {
            if (isEmpty) {
                emptyState.setVisibility(View.VISIBLE);
                rvFlaggedUsers.setVisibility(View.GONE);
            } else {
                emptyState.setVisibility(View.GONE);
                rvFlaggedUsers.setVisibility(View.VISIBLE);
            }
        }
    }
    
    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}

