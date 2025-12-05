package com.fp.foodorderingsystem.adapters;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.models.FoodItem;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminMenuItemAdapter extends RecyclerView.Adapter<AdminMenuItemAdapter.ViewHolder> {
    public interface OnItemActionListener {
        void onToggleStatus(FoodItem item);
        void onEdit(FoodItem item);
        void onDelete(FoodItem item);
    }
    
    private final List<FoodItem> items = new ArrayList<>();
    private final Map<Integer, String> categoryLookup = new LinkedHashMap<>();
    private OnItemActionListener listener;
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
    
    public void setOnItemActionListener(OnItemActionListener listener) {
        this.listener = listener;
    }
    
    public void setItems(List<FoodItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }
    
    public void upsertItem(FoodItem item) {
        if (item == null) return;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getId() == item.getId()) {
                items.set(i, item);
                notifyItemChanged(i);
                return;
            }
        }
        items.add(0, item);
        notifyItemInserted(0);
    }
    
    public void removeItem(int itemId) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getId() == itemId) {
                items.remove(i);
                notifyItemRemoved(i);
                return;
            }
        }
    }
    
    public void setCategoryLookup(Map<Integer, String> lookup) {
        categoryLookup.clear();
        if (lookup != null) {
            categoryLookup.putAll(lookup);
        }
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_manage_menu, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }
    
    @Override
    public int getItemCount() {
        return items.size();
    }
    
    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvName;
        private final TextView tvPrice;
        private final TextView tvDescription;
        private final TextView tvCategory;
        private final Chip chipStatus;
        private final MaterialButton btnToggleStatus;
        private final MaterialButton btnEdit;
        private final MaterialButton btnDelete;
        
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvItemName);
            tvPrice = itemView.findViewById(R.id.tvItemPrice);
            tvDescription = itemView.findViewById(R.id.tvItemDescription);
            tvCategory = itemView.findViewById(R.id.tvItemCategory);
            chipStatus = itemView.findViewById(R.id.chipStatus);
            btnToggleStatus = itemView.findViewById(R.id.btnToggleStatus);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
        
        void bind(FoodItem item) {
            tvName.setText(item.getName());
            tvPrice.setText(currencyFormat.format(item.getPrice()));
            tvDescription.setText(item.getDescription() != null && !item.getDescription().isEmpty()
                ? item.getDescription()
                : "No description provided.");
            
            String categoryName = categoryLookup.getOrDefault(
                item.getCategoryId(),
                "Category ID: " + item.getCategoryId()
            );
            tvCategory.setText("Category: " + categoryName);
            
            String status = item.getStatus() != null ? item.getStatus() : "available";
            boolean isAvailable = "available".equalsIgnoreCase(status);
            chipStatus.setText(isAvailable ? "Available" : "Unavailable");
            chipStatus.setChipBackgroundColor(ColorStateList.valueOf(
                itemView.getResources().getColor(
                    isAvailable ? R.color.teal_200 : android.R.color.darker_gray,
                    itemView.getContext().getTheme()
                )
            ));
            chipStatus.setTextColor(itemView.getResources().getColor(
                isAvailable ? R.color.teal_700 : android.R.color.white,
                itemView.getContext().getTheme()
            ));
            
            btnToggleStatus.setText(isAvailable ? "Mark unavailable" : "Mark available");
            
            btnToggleStatus.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onToggleStatus(item);
                }
            });
            
            btnEdit.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEdit(item);
                }
            });
            
            btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDelete(item);
                }
            });
        }
    }
}

