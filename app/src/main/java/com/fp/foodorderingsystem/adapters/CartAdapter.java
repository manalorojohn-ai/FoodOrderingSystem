package com.fp.foodorderingsystem.adapters;

import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.models.CartItem;
import com.fp.foodorderingsystem.models.FoodItem;
import com.fp.foodorderingsystem.utils.ImageUtil;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.ViewHolder> {
    private final List<CartItem> cartItems;
    private OnItemChangeListener listener;
    
    public interface OnItemChangeListener {
        void onQuantityChanged(CartItem item, int quantity);
        void onItemRemoved(CartItem item);
    }
    
    public CartAdapter(List<CartItem> cartItems) {
        this.cartItems = cartItems;
    }
    
    public void setOnItemChangeListener(OnItemChangeListener listener) {
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_cart, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CartItem cartItem = cartItems.get(position);
        holder.bind(cartItem);
    }
    
    @Override
    public int getItemCount() {
        return cartItems != null ? cartItems.size() : 0;
    }
    
    public void updateList(List<CartItem> newItems) {
        cartItems.clear();
        if (newItems != null) {
            cartItems.addAll(newItems);
        }
        notifyDataSetChanged();
    }
    
    class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivCartItemImage;
        private final ImageView btnIncrease;
        private final ImageView btnDecrease;
        private final ImageView btnRemoveItem;
        private final TextView tvCartItemName;
        private final TextView tvCartItemPrice;
        private final EditText etQuantity;
        
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCartItemImage = itemView.findViewById(R.id.ivCartItemImage);
            tvCartItemName = itemView.findViewById(R.id.tvCartItemName);
            tvCartItemPrice = itemView.findViewById(R.id.tvCartItemPrice);
            etQuantity = itemView.findViewById(R.id.etQuantity);
            btnIncrease = itemView.findViewById(R.id.btnIncrease);
            btnDecrease = itemView.findViewById(R.id.btnDecrease);
            btnRemoveItem = itemView.findViewById(R.id.btnRemoveItem);

            etQuantity.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    handleManualQuantityChange();
                }
            });

            etQuantity.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP)) {
                    handleManualQuantityChange();
                    etQuantity.clearFocus();
                    return true;
                }
                return false;
            });
        }
        
        void bind(CartItem cartItem) {
            FoodItem foodItem = cartItem.getFoodItem();
            if (foodItem != null) {
                tvCartItemName.setText(foodItem.getName());
            } else {
                tvCartItemName.setText("Unknown item");
            }
            
            NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
            currencyFormat.setCurrency(java.util.Currency.getInstance("PHP"));
            tvCartItemPrice.setText(currencyFormat.format(cartItem.getUnitPrice()));
            
            etQuantity.setText(String.valueOf(cartItem.getQuantity()));
            
            String imageUrl = null;
            if (foodItem != null) {
                imageUrl = ImageUtil.getFoodItemUrl(
                    foodItem.getImageUrl(),
                    foodItem.getImagePath(),
                    foodItem.getName()
                );
            }
            if (imageUrl != null && !imageUrl.isEmpty()) {
                Glide.with(itemView.getContext())
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_food_banner)
                    .error(R.drawable.ic_food_banner)
                    .centerCrop()
                    .into(ivCartItemImage);
            } else {
                ivCartItemImage.setImageResource(R.drawable.ic_food_banner);
            }
            
            // Store foodItem reference for use in click listeners
            FoodItem currentFoodItem = foodItem;
            
            btnIncrease.setOnClickListener(v -> {
                if (listener != null) {
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        CartItem item = cartItems.get(position);
                        int currentQuantity = item.getQuantity();
                        FoodItem itemFoodItem = currentFoodItem != null ? currentFoodItem : item.getFoodItem();
                        
                        // Check stock availability
                        if (itemFoodItem != null) {
                            int availableStock = itemFoodItem.getStock();
                            if (currentQuantity >= availableStock) {
                                android.widget.Toast.makeText(
                                    itemView.getContext(),
                                    "Not enough stock. Only " + availableStock + " available",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show();
                                return;
                            }
                        }
                        
                        // Check max quantity (99)
                        if (currentQuantity >= 99) {
                            android.widget.Toast.makeText(
                                itemView.getContext(),
                                "Maximum quantity is 99",
                                android.widget.Toast.LENGTH_SHORT
                            ).show();
                            return;
                        }
                        
                        listener.onQuantityChanged(item, currentQuantity + 1);
                    }
                }
            });
            
            btnDecrease.setOnClickListener(v -> {
                if (listener != null) {
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        CartItem item = cartItems.get(position);
                        if (item.getQuantity() > 1) {
                            listener.onQuantityChanged(item, item.getQuantity() - 1);
                        }
                    }
                }
            });
            
            btnRemoveItem.setOnClickListener(v -> {
                if (listener != null) {
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onItemRemoved(cartItems.get(position));
                    }
                }
            });
        }

        private void handleManualQuantityChange() {
            int position = getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) {
                return;
            }

            CartItem item = cartItems.get(position);
            FoodItem itemFoodItem = item.getFoodItem();
            String input = etQuantity.getText() != null ? etQuantity.getText().toString().trim() : "";
            if (TextUtils.isEmpty(input)) {
                etQuantity.setText(String.valueOf(item.getQuantity()));
                return;
            }

            int newQuantity;
            try {
                newQuantity = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                etQuantity.setText(String.valueOf(item.getQuantity()));
                return;
            }

            // Validate minimum quantity
            if (newQuantity < 1) {
                newQuantity = 1;
                etQuantity.setText(String.valueOf(newQuantity));
            }
            
            // Validate maximum quantity (99)
            if (newQuantity > 99) {
                newQuantity = 99;
                etQuantity.setText(String.valueOf(newQuantity));
                android.widget.Toast.makeText(
                    itemView.getContext(),
                    "Maximum quantity is 99",
                    android.widget.Toast.LENGTH_SHORT
                ).show();
            }
            
            // Validate stock availability
            if (itemFoodItem != null) {
                int availableStock = itemFoodItem.getStock();
                if (newQuantity > availableStock) {
                    newQuantity = availableStock;
                    etQuantity.setText(String.valueOf(newQuantity));
                    android.widget.Toast.makeText(
                        itemView.getContext(),
                        "Not enough stock. Only " + availableStock + " available",
                        android.widget.Toast.LENGTH_SHORT
                    ).show();
                }
            }

            if (newQuantity != item.getQuantity()) {
                if (listener != null) {
                    listener.onQuantityChanged(item, newQuantity);
                }
            } else {
                etQuantity.setText(String.valueOf(newQuantity));
            }
        }
    }
}

