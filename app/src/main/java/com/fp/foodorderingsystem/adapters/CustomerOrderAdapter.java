package com.fp.foodorderingsystem.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.models.Order;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class CustomerOrderAdapter extends RecyclerView.Adapter<CustomerOrderAdapter.ViewHolder> {
    private List<Order> orders;
    private OnItemClickListener listener;
    
    public interface OnItemClickListener {
        void onItemClick(Order order);
    }
    
    public CustomerOrderAdapter(List<Order> orders) {
        this.orders = orders;
    }
    
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_order_customer, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Order order = orders.get(position);
        holder.bind(order);
    }
    
    @Override
    public int getItemCount() {
        return orders != null ? orders.size() : 0;
    }
    
    public void updateList(List<Order> newOrders) {
        this.orders = newOrders;
        notifyDataSetChanged();
    }
    
    class ViewHolder extends RecyclerView.ViewHolder {
        private TextView tvOrderId, tvStatus, tvTotalAmount, tvDate, tvPaymentMethod;
        
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvOrderId = itemView.findViewById(R.id.tvOrderId);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvTotalAmount = itemView.findViewById(R.id.tvTotalAmount);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvPaymentMethod = itemView.findViewById(R.id.tvPaymentMethod);
        }
        
        void bind(Order order) {
            tvOrderId.setText("Order #" + order.getId());
            tvStatus.setText(capitalize(order.getStatus()));
            tvPaymentMethod.setText(order.getPaymentMethod() != null ? capitalize(order.getPaymentMethod()) : "N/A");
            
            NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
            currencyFormat.setCurrency(java.util.Currency.getInstance("PHP"));
            tvTotalAmount.setText(currencyFormat.format(order.getTotalAmount()));
            
            if (order.getCreatedAt() != null) {
                // Format date to be more readable
                String dateStr = order.getCreatedAt();
                if (dateStr.length() > 10) {
                    dateStr = dateStr.substring(0, 10);
                }
                tvDate.setText(dateStr);
            } else {
                tvDate.setText("N/A");
            }
            
            // Set status color and background
            int statusTextColor = getStatusTextColor(order.getStatus());
            tvStatus.setTextColor(statusTextColor);
            if (tvStatus.getBackground() != null) {
                tvStatus.getBackground().setTint(getStatusBackgroundColor(order.getStatus()));
            }
            
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(order);
                }
            });
        }
        
        private int getStatusBackgroundColor(String status) {
            if (status == null) return itemView.getContext().getColor(R.color.text_secondary);
            switch (status.toLowerCase()) {
                case "completed":
                    return itemView.getContext().getColor(R.color.green_primary);
                case "cancelled":
                    return itemView.getContext().getColor(R.color.error);
                case "pending":
                    return itemView.getContext().getColor(android.R.color.holo_orange_dark);
                case "preparing":
                case "ready":
                    return itemView.getContext().getColor(android.R.color.holo_blue_light);
                case "delivering":
                    return itemView.getContext().getColor(android.R.color.holo_green_light);
                default:
                    return itemView.getContext().getColor(R.color.text_secondary);
            }
        }
        
        private String capitalize(String str) {
            if (str == null || str.isEmpty()) return "";
            return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
        }
        
        private int getStatusTextColor(String status) {
            if (status == null) return itemView.getContext().getColor(R.color.text_secondary);
            switch (status.toLowerCase()) {
                case "completed":
                case "cancelled":
                case "pending":
                case "preparing":
                case "ready":
                case "delivering":
                    return itemView.getContext().getColor(R.color.text_white);
                default:
                    return itemView.getContext().getColor(R.color.text_primary);
            }
        }
    }
}

