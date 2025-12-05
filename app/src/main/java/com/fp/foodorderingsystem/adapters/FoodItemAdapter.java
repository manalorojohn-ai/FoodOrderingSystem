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
import com.fp.foodorderingsystem.models.FoodItem;
import com.fp.foodorderingsystem.utils.ImageUtil;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class FoodItemAdapter extends RecyclerView.Adapter<FoodItemAdapter.ViewHolder> {
    private List<FoodItem> foodItems;
    private OnItemClickListener listener;
    
    public interface OnItemClickListener {
        void onItemClick(FoodItem foodItem);
        void onAddToCartClick(FoodItem foodItem);
    }
    
    public FoodItemAdapter(List<FoodItem> foodItems) {
        this.foodItems = foodItems;
    }
    
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_food, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FoodItem item = foodItems.get(position);
        holder.bind(item);
    }
    
    @Override
    public int getItemCount() {
        return foodItems != null ? foodItems.size() : 0;
    }
    
    public void updateList(List<FoodItem> newItems) {
        this.foodItems = newItems;
        notifyDataSetChanged();
    }
    
    class ViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivFoodImage;
        private TextView tvFoodName, tvFoodDescription, tvFoodPrice, tvAvailability, tvPrepTime;
        private View btnAddToCart;
        
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivFoodImage = itemView.findViewById(R.id.ivFoodImage);
            tvFoodName = itemView.findViewById(R.id.tvFoodName);
            tvFoodDescription = itemView.findViewById(R.id.tvFoodDescription);
            tvFoodPrice = itemView.findViewById(R.id.tvFoodPrice);
            tvAvailability = itemView.findViewById(R.id.tvAvailability);
            tvPrepTime = itemView.findViewById(R.id.tvPrepTime);
            btnAddToCart = itemView.findViewById(R.id.btnAddToCart);
        }
        
        void bind(FoodItem item) {
            tvFoodName.setText(item.getName());
            tvFoodDescription.setText(item.getDescription());
            
            NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
            currencyFormat.setCurrency(java.util.Currency.getInstance("PHP"));
            tvFoodPrice.setText(currencyFormat.format(item.getPrice()));
            
            if (item.isAvailable()) {
                tvAvailability.setText("Available");
                tvAvailability.setVisibility(View.VISIBLE);
            } else {
                tvAvailability.setText("Out of Stock");
                tvAvailability.setVisibility(View.VISIBLE);
            }
            
            // Display cooking/serving time, or actual prep time if > 0
            if (item.getPreparationTime() <= 0) {
                tvPrepTime.setText("Cooking/Serving Time");
            } else {
                tvPrepTime.setText(item.getPreparationTime() + " mins");
            }
            
            // Load image from Supabase Storage
            // Pass item name as fallback to generate filename if paths are invalid
            String imageUrl = ImageUtil.getFoodItemUrl(item.getImageUrl(), item.getImagePath(), item.getName());
            if (imageUrl != null && !imageUrl.isEmpty()) {
                Glide.with(itemView.getContext())
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_food_banner)
                    .error(R.drawable.ic_food_banner)
                    .centerCrop()
                    .into(ivFoodImage);
            } else {
                ivFoodImage.setImageResource(R.drawable.ic_food_banner);
            }
            
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item);
                }
            });
            
            btnAddToCart.setOnClickListener(v -> {
                if (listener != null && item.isAvailable()) {
                    listener.onAddToCartClick(item);
                }
            });
        }
    }
}

