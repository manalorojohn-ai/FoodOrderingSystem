package com.fp.foodorderingsystem.adapters;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.models.Notification;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    public interface OnNotificationClickListener {
        void onNotificationClick(Notification notification);
    }

    private final List<Notification> notifications = new ArrayList<>();
    private final OnNotificationClickListener clickListener;
    private final SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.getDefault());
    private final SimpleDateFormat displayFormat = new SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault());

    public NotificationAdapter(OnNotificationClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void submitList(List<Notification> newNotifications) {
        notifications.clear();
        if (newNotifications != null) {
            notifications.addAll(newNotifications);
        }
        notifyDataSetChanged();
    }

    public void markAsRead(int notificationId) {
        for (int i = 0; i < notifications.size(); i++) {
            Notification notification = notifications.get(i);
            if (notification.getId() == notificationId) {
                notification.setRead(true);
                notifyItemChanged(i);
                break;
            }
        }
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        Notification notification = notifications.get(position);
        holder.bind(notification);
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    class NotificationViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTitle;
        private final TextView tvMessage;
        private final TextView tvTimestamp;
        private final TextView tvOrderId;
        private final View viewUnreadIndicator;

        NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvOrderId = itemView.findViewById(R.id.tvOrderId);
            viewUnreadIndicator = itemView.findViewById(R.id.viewUnreadIndicator);

            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && clickListener != null) {
                    clickListener.onNotificationClick(notifications.get(position));
                }
            });
        }

        void bind(Notification notification) {
            tvTitle.setText(notification.getTitle());
            tvMessage.setText(notification.getMessage());
            tvTimestamp.setText(formatTimestamp(notification.getCreatedAt()));

            if (notification.getOrderId() != null) {
                tvOrderId.setVisibility(View.VISIBLE);
                tvOrderId.setText(String.format(Locale.getDefault(), "#%s", notification.getOrderId()));
            } else {
                tvOrderId.setVisibility(View.GONE);
            }

            viewUnreadIndicator.setVisibility(notification.isRead() ? View.GONE : View.VISIBLE);
            itemView.setAlpha(notification.isRead() ? 0.6f : 1f);
        }

        private String formatTimestamp(String isoString) {
            if (TextUtils.isEmpty(isoString)) {
                return "";
            }
            try {
                Date date = isoFormat.parse(isoString);
                if (date != null) {
                    return displayFormat.format(date);
                }
            } catch (ParseException ignored) { }
            return isoString;
        }
    }
}

