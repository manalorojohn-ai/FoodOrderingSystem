package com.fp.foodorderingsystem.utils;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.fp.foodorderingsystem.R;

/**
 * Utility for showing branded Toasts that include the app logo.
 */
public final class ToastUtil {
    private ToastUtil() {}

    public static void show(Context context, CharSequence message) {
        show(context, message, Toast.LENGTH_SHORT);
    }

    public static void show(Context context, CharSequence message, int duration) {
        if (context == null) {
            return;
        }

        Context appContext = context.getApplicationContext();
        LayoutInflater inflater = LayoutInflater.from(appContext);
        View layout = inflater.inflate(R.layout.view_toast_logo, null);

        ImageView iconView = layout.findViewById(R.id.toastIcon);
        TextView messageView = layout.findViewById(R.id.toastMessage);

        iconView.setImageResource(R.drawable.logo);
        messageView.setText(message != null ? message : "");

        Toast toast = new Toast(appContext);
        toast.setDuration(duration);
        toast.setView(layout);
        toast.setGravity(
            Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL,
            0,
            (int) (appContext.getResources().getDisplayMetrics().density * 72)
        );
        toast.show();
    }
}


