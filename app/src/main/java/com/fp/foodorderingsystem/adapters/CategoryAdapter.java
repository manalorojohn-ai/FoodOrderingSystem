package com.fp.foodorderingsystem.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.models.Category;
import com.fp.foodorderingsystem.utils.ImageUtil;
import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.ViewHolder> {
    private List<Category> categories;
    private OnItemClickListener listener;
    
    public interface OnItemClickListener {
        void onItemClick(Category category);
    }
    
    public CategoryAdapter(List<Category> categories) {
        this.categories = categories;
    }
    
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_category, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Category category = categories.get(position);
        holder.bind(category);
    }
    
    @Override
    public int getItemCount() {
        return categories != null ? categories.size() : 0;
    }
    
    public void updateList(List<Category> newCategories) {
        this.categories = newCategories;
        notifyDataSetChanged();
    }
    
    class ViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivCategoryIcon;
        private TextView tvCategoryName;
        
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCategoryIcon = itemView.findViewById(R.id.ivCategoryIcon);
            tvCategoryName = itemView.findViewById(R.id.tvCategoryName);
        }
        
        void bind(Category category) {
            tvCategoryName.setText(category.getName());
            
            // Load image from Supabase Storage
            String imageUrl = ImageUtil.getCategoryImageUrl(category.getImageUrl());
            if (imageUrl != null && !imageUrl.isEmpty()) {
                Glide.with(itemView.getContext())
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_food_banner)
                    .error(R.drawable.ic_food_banner)
                    .centerCrop()
                    .into(ivCategoryIcon);
            } else {
                ivCategoryIcon.setImageResource(R.drawable.ic_food_banner);
            }
            
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(category);
                }
            });
        }
    }
}

