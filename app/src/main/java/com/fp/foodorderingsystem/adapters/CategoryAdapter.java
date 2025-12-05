package com.fp.foodorderingsystem.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.models.Category;
import com.fp.foodorderingsystem.utils.ImageUtil;
import java.util.List;

/**
 * Adapter for displaying categories with photos from Supabase Storage
 */
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
        holder.bind(category, listener);
    }
    
    @Override
    public int getItemCount() {
        return categories != null ? categories.size() : 0;
    }
    
    public void updateList(List<Category> newCategories) {
        this.categories = newCategories;
        notifyDataSetChanged();
    }
    
    /**
     * ViewHolder for category items
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivCategoryIcon;
        private final TextView tvCategoryName;
        
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCategoryIcon = itemView.findViewById(R.id.ivCategoryIcon);
            tvCategoryName = itemView.findViewById(R.id.tvCategoryName);
        }
        
        void bind(Category category, OnItemClickListener listener) {
            // Set category name
            tvCategoryName.setText(category.getName());
            
            // Load category image from Supabase Storage
            loadCategoryImage(category);
            
            // Handle click event
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(category);
                }
            });
        }
        
        /**
         * Load category image from Supabase Storage with proper error handling
         */
        private void loadCategoryImage(Category category) {
            String imagePath = category.getImageUrl();
            
            // If no image path is set, use placeholder
            if (imagePath == null || imagePath.isEmpty()) {
                android.util.Log.d("CategoryAdapter", "No image path for category: " + category.getName());
                ivCategoryIcon.setImageResource(R.drawable.ic_food_banner);
                return;
            }
            
            try {
                // Get the full Supabase Storage URL for the category image
                String imageUrl = ImageUtil.getCategoryImageUrl(imagePath);
                
                android.util.Log.d("CategoryAdapter", "Loading image for category: " + category.getName() + 
                    ", path: " + imagePath + ", URL: " + imageUrl);
                
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    // Load image using Glide with smooth transitions
                    Glide.with(itemView.getContext())
                        .load(imageUrl)
                        .transition(DrawableTransitionOptions.withCrossFade(300))
                        .placeholder(R.drawable.ic_food_banner)
                        .error(R.drawable.ic_food_banner)
                        .centerCrop()
                        .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                            @Override
                            public boolean onLoadFailed(@androidx.annotation.Nullable com.bumptech.glide.load.engine.GlideException e, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                                android.util.Log.e("CategoryAdapter", "Failed to load image: " + imageUrl, e);
                                return false; // Let Glide handle the error (show placeholder)
                            }
                            
                            @Override
                            public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                                android.util.Log.d("CategoryAdapter", "Successfully loaded image: " + imageUrl);
                                return false;
                            }
                        })
                        .into(ivCategoryIcon);
                } else {
                    // Fallback to placeholder if URL generation failed
                    android.util.Log.w("CategoryAdapter", "Failed to generate URL for category: " + category.getName());
                    ivCategoryIcon.setImageResource(R.drawable.ic_food_banner);
                }
            } catch (Exception e) {
                // If there's any exception, use placeholder
                android.util.Log.e("CategoryAdapter", "Exception loading image for category: " + category.getName(), e);
                ivCategoryIcon.setImageResource(R.drawable.ic_food_banner);
            }
        }
    }
}

