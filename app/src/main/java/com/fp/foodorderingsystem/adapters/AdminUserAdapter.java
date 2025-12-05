package com.fp.foodorderingsystem.adapters;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.models.User;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import java.util.ArrayList;
import java.util.List;

public class AdminUserAdapter extends RecyclerView.Adapter<AdminUserAdapter.ViewHolder> {

    public interface OnUserActionListener {
        void onViewDetails(User user);
        void onToggleRole(User user);
        void onToggleVerification(User user);
        void onResetCancellations(User user);
    }

    private final List<User> users = new ArrayList<>();
    private OnUserActionListener listener;

    public void setOnUserActionListener(OnUserActionListener listener) {
        this.listener = listener;
    }

    public void setItems(List<User> newUsers) {
        users.clear();
        if (newUsers != null) {
            users.addAll(newUsers);
        }
        notifyDataSetChanged();
    }

    public void upsertUser(User user) {
        if (user == null) {
            return;
        }
        for (int i = 0; i < users.size(); i++) {
            if (TextUtils.equals(users.get(i).getId(), user.getId())) {
                users.set(i, user);
                notifyItemChanged(i);
                return;
            }
        }
        users.add(0, user);
        notifyItemInserted(0);
    }

    public void removeUser(String userId) {
        if (TextUtils.isEmpty(userId)) {
            return;
        }
        for (int i = 0; i < users.size(); i++) {
            if (TextUtils.equals(users.get(i).getId(), userId)) {
                users.remove(i);
                notifyItemRemoved(i);
                return;
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_manage_user, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(users.get(position));
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvFullName;
        private final TextView tvEmail;
        private final TextView tvPhone;
        private final TextView tvAddress;
        private final TextView tvMeta;
        private final Chip chipRole;
        private final Chip chipVerified;
        private final MaterialButton btnViewDetails;
        private final MaterialButton btnToggleRole;
        private final MaterialButton btnToggleVerification;
        private final MaterialButton btnResetCancellations;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFullName = itemView.findViewById(R.id.tvFullName);
            tvEmail = itemView.findViewById(R.id.tvEmail);
            tvPhone = itemView.findViewById(R.id.tvPhone);
            tvAddress = itemView.findViewById(R.id.tvAddress);
            tvMeta = itemView.findViewById(R.id.tvMeta);
            chipRole = itemView.findViewById(R.id.chipRole);
            chipVerified = itemView.findViewById(R.id.chipVerified);
            btnViewDetails = itemView.findViewById(R.id.btnViewDetails);
            btnToggleRole = itemView.findViewById(R.id.btnToggleRole);
            btnToggleVerification = itemView.findViewById(R.id.btnToggleVerification);
            btnResetCancellations = itemView.findViewById(R.id.btnResetCancellations);
        }

        void bind(User user) {
            Context context = itemView.getContext();

            tvFullName.setText(user.getFullName() != null ? user.getFullName() : "Unnamed user");
            tvEmail.setText(user.getEmail() != null ? user.getEmail() : "No email provided");
            tvPhone.setText(!TextUtils.isEmpty(user.getPhone()) ? user.getPhone() : "No phone");
            tvAddress.setText(!TextUtils.isEmpty(user.getAddress()) ? user.getAddress() : "No address on file");

            String createdAt = user.getCreatedAt() != null ? user.getCreatedAt().replace("T", " ").replace("Z", "") : "Unknown date";
            tvMeta.setText("Joined " + createdAt + " • " + user.getCancellationCount() + " cancellations");

            String role = user.getUserType() != null ? user.getUserType() : "customer";
            boolean isAdmin = "admin".equalsIgnoreCase(role);
            chipRole.setText(isAdmin ? "Admin" : "Customer");
            chipRole.setChipBackgroundColor(
                ContextCompat.getColorStateList(context, isAdmin ? R.color.green_primary_light : R.color.light_gray)
            );
            chipRole.setChipStrokeColor(
                ContextCompat.getColorStateList(context, isAdmin ? R.color.green_primary : R.color.divider)
            );

            boolean verified = user.isVerified();
            chipVerified.setText(verified ? "Verified" : "Unverified");
            chipVerified.setChipBackgroundColor(
                ContextCompat.getColorStateList(context, verified ? R.color.green_primary_light : R.color.light_gray)
            );
            chipVerified.setChipStrokeColor(
                ContextCompat.getColorStateList(context, verified ? R.color.green_primary : R.color.divider)
            );

            btnToggleRole.setText(isAdmin ? "Make customer" : "Make admin");
            btnToggleVerification.setText(verified ? "Mark unverified" : "Mark verified");

            btnViewDetails.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onViewDetails(user);
                }
            });

            btnToggleRole.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onToggleRole(user);
                }
            });

            btnToggleVerification.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onToggleVerification(user);
                }
            });

            btnResetCancellations.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onResetCancellations(user);
                }
            });
        }
    }
}

