package com.fp.foodorderingsystem.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.activities.common.NotificationActivity;
import com.fp.foodorderingsystem.activities.customer.OrderDetailActivity;

public class NotificationHelper {
    private static final String TAG = "NotificationHelper";

    // Notification Channels
    private static final String CHANNEL_ORDERS = "channel_orders";
    private static final String CHANNEL_PAYMENTS = "channel_payments";
    private static final String CHANNEL_STATUS = "channel_status";

    // Notification IDs
    private static final int NOTIFICATION_ID_PAYMENT = 1001;
    private static final int NOTIFICATION_ID_ORDER = 1002;
    private static final int NOTIFICATION_ID_STATUS = 1003;

    private Context context;
    private android.app.NotificationManager systemNotificationManager;

    public NotificationHelper(Context context) {
        this.context = context.getApplicationContext();
        this.systemNotificationManager = (android.app.NotificationManager)
            this.context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannels();
    }

    /**
     * Create notification channels for Android O and above
     */
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Orders Channel
            NotificationChannel ordersChannel = new NotificationChannel(
                CHANNEL_ORDERS,
                "Orders",
                android.app.NotificationManager.IMPORTANCE_HIGH
            );
            ordersChannel.setDescription("Notifications for order updates");
            ordersChannel.enableVibration(true);
            ordersChannel.enableLights(true);

            // Payments Channel
            NotificationChannel paymentsChannel = new NotificationChannel(
                CHANNEL_PAYMENTS,
                "Payments",
                android.app.NotificationManager.IMPORTANCE_HIGH
            );
            paymentsChannel.setDescription("Notifications for payment updates");
            paymentsChannel.enableVibration(true);
            paymentsChannel.enableLights(true);

            // Status Channel
            NotificationChannel statusChannel = new NotificationChannel(
                CHANNEL_STATUS,
                "Order Status",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            );
            statusChannel.setDescription("Notifications for order status changes");
            statusChannel.enableVibration(true);

            systemNotificationManager.createNotificationChannel(ordersChannel);
            systemNotificationManager.createNotificationChannel(paymentsChannel);
            systemNotificationManager.createNotificationChannel(statusChannel);
        }
    }

    /**
     * Show notification for successful payment
     */
    public void showPaymentSuccessNotification(String amount) {
        Intent intent = new Intent(context, NotificationActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_PAYMENT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_PAYMENTS)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle("Payment Successful")
            .setContentText("Your payment of " + amount + " has been processed successfully!")
            .setStyle(new NotificationCompat.BigTextStyle()
                .bigText("Your payment of " + amount + " has been processed successfully! Your order is being prepared."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_SOUND | NotificationCompat.DEFAULT_VIBRATE);

        systemNotificationManager.notify(NOTIFICATION_ID_PAYMENT, builder.build());
    }

    /**
     * Show notification when order is placed
     */
    public void showOrderPlacedNotification(String orderId, int orderIdInt) {
        Intent intent = new Intent(context, OrderDetailActivity.class);
        intent.putExtra("order_id", orderIdInt);
        intent.putExtra("order_id_string", orderId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_ORDER,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ORDERS)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle("Order Placed Successfully")
            .setContentText("Your order #" + orderId + " has been placed!")
            .setStyle(new NotificationCompat.BigTextStyle()
                .bigText("Your order #" + orderId + " has been placed successfully! We'll notify you when it's ready."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_SOUND | NotificationCompat.DEFAULT_VIBRATE);

        systemNotificationManager.notify(NOTIFICATION_ID_ORDER, builder.build());
    }

    /**
     * Show notification for order status changes
     */
    public void showOrderStatusNotification(String orderId, String status, int orderIdInt) {
        String title = getStatusTitle(status);
        String message = getStatusMessage(orderId, status);

        Intent intent = new Intent(context, OrderDetailActivity.class);
        intent.putExtra("order_id", orderIdInt);
        intent.putExtra("order_id_string", orderId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_STATUS + orderIdInt,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(getStatusIcon(status))
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(getStatusPriority(status))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_SOUND | NotificationCompat.DEFAULT_VIBRATE);

        systemNotificationManager.notify(NOTIFICATION_ID_STATUS + orderIdInt, builder.build());
    }

    /**
     * Get notification title based on status
     */
    private String getStatusTitle(String status) {
        if (status == null) return "Order Update";

        switch (status.toLowerCase()) {
            case "confirmed":
                return "Order Confirmed";
            case "preparing":
                return "Order Being Prepared";
            case "ready":
                return "Order Ready";
            case "delivering":
                return "Order Out for Delivery";
            case "completed":
                return "Order Completed";
            case "cancelled":
                return "Order Cancelled";
            default:
                return "Order Update";
        }
    }

    /**
     * Get notification message based on status
     */
    private String getStatusMessage(String orderId, String status) {
        if (status == null) return "Your order #" + orderId + " has been updated.";

        switch (status.toLowerCase()) {
            case "confirmed":
                return "Your order #" + orderId + " has been confirmed and is being prepared!";
            case "preparing":
                return "Your order #" + orderId + " is now being prepared. It will be ready soon!";
            case "ready":
                return "Your order #" + orderId + " is ready! You can pick it up or wait for delivery.";
            case "delivering":
                return "Your order #" + orderId + " is out for delivery! It will arrive soon.";
            case "completed":
                return "Your order #" + orderId + " has been completed. Thank you for your order!";
            case "cancelled":
                return "Your order #" + orderId + " has been cancelled.";
            default:
                return "Your order #" + orderId + " status has been updated to " + status + ".";
        }
    }

    /**
     * Get notification icon based on status
     */
    private int getStatusIcon(String status) {
        if (status == null) return R.drawable.ic_notifications;

        switch (status.toLowerCase()) {
            case "confirmed":
            case "preparing":
                return R.drawable.ic_notifications;
            case "ready":
                return R.drawable.ic_notifications;
            case "delivering":
                return R.drawable.ic_notifications;
            case "completed":
                return R.drawable.ic_notifications;
            case "cancelled":
                return R.drawable.ic_notifications;
            default:
                return R.drawable.ic_notifications;
        }
    }

    /**
     * Get notification priority based on status
     */
    private int getStatusPriority(String status) {
        if (status == null) return NotificationCompat.PRIORITY_DEFAULT;

        switch (status.toLowerCase()) {
            case "ready":
            case "delivering":
                return NotificationCompat.PRIORITY_HIGH;
            case "cancelled":
                return NotificationCompat.PRIORITY_HIGH;
            default:
                return NotificationCompat.PRIORITY_DEFAULT;
        }
    }

    /**
     * Cancel a specific notification
     */
    public void cancelNotification(int notificationId) {
        systemNotificationManager.cancel(notificationId);
    }

    /**
     * Cancel all notifications
     */
    public void cancelAllNotifications() {
        systemNotificationManager.cancelAll();
    }
}


