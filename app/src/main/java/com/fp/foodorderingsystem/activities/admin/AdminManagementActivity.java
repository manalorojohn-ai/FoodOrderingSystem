package com.fp.foodorderingsystem.activities.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.fp.foodorderingsystem.R;
import com.google.android.material.appbar.MaterialToolbar;

public class AdminManagementActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.admin_menu);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Management Center");
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        setCardClick(R.id.cardManageOrders, ManageOrdersActivity.class);
        setCardClick(R.id.cardManageItems, ManageItemsActivity.class);
        setCardClick(R.id.cardManageCategories, ManageCategoriesActivity.class);
        setCardClick(R.id.cardManageUsers, ManageUsersActivity.class);
        setCardClick(R.id.cardFakeBooking, FakeBookingTrackerActivity.class);
    }

    private void setCardClick(int viewId, Class<?> activity) {
        View card = findViewById(viewId);
        if (card != null) {
            card.setOnClickListener(v -> startActivity(new Intent(this, activity)));
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}

