package com.fp.foodorderingsystem.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.models.Category;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;

public class AdminCategoryAdapter extends RecyclerView.Adapter<AdminCategoryAdapter.ViewHolder> {

    public interface OnCategoryActionListener {
        void onEdit(Category category);
        void onDelete(Category category);
    }

    private final List<Category> categories = new ArrayList<>();
    private OnCategoryActionListener listener;

    public void setOnCategoryActionListener(OnCategoryActionListener listener) {
        this.listener = listener;
    }

    public void setItems(List<Category> items) {
        categories.clear();
        if (items != null) {
            categories.addAll(items);
        }
        notifyDataSetChanged();
    }

    public void upsertCategory(Category category) {
        if (category == null) {
            return;
        }
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).getId() == category.getId()) {
                categories.set(i, category);
                notifyItemChanged(i);
                return;
            }
        }
        categories.add(0, category);
        notifyItemInserted(0);
    }

    public void removeCategory(int categoryId) {
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).getId() == categoryId) {
                categories.remove(i);
                notifyItemRemoved(i);
                return;
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_manage_category, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(categories.get(position));
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvName;
        private final TextView tvDescription;
        private final TextView tvCreatedAt;
        private final MaterialButton btnEdit;
        private final MaterialButton btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvCategoryName);
            tvDescription = itemView.findViewById(R.id.tvCategoryDescription);
            tvCreatedAt = itemView.findViewById(R.id.tvCategoryCreatedAt);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }

        void bind(Category category) {
            tvName.setText(category.getName());

            String description = category.getDescription();
            if (description == null || description.trim().isEmpty()) {
                tvDescription.setText("No description provided.");
                tvDescription.setTextColor(itemView.getResources().getColor(
                    android.R.color.darker_gray,
                    itemView.getContext().getTheme()
                ));
            } else {
                tvDescription.setText(description);
                tvDescription.setTextColor(itemView.getResources().getColor(
                    android.R.color.black,
                    itemView.getContext().getTheme()
                ));
            }

            String createdAtLabel = "Created";
            String createdAt = category.getCreatedAt();
            if (createdAt != null && !createdAt.isEmpty()) {
                createdAtLabel = "Created " + createdAt.replace("T", " ").replace("Z", "");
            }
            tvCreatedAt.setText(createdAtLabel);

            btnEdit.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEdit(category);
                }
            });

            btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDelete(category);
                }
            });
        }
    }
}

