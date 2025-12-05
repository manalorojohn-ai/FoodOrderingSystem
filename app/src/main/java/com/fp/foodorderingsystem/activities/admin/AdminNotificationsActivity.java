package com.fp.foodorderingsystem.activities.admin;

import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.activities.common.NotificationActivity;

public class AdminNotificationsActivity extends NotificationActivity {

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_admin_notifications;
    }

    @Override
    protected String getScreenTitle() {
        return "Admin Notifications";
    }
}

