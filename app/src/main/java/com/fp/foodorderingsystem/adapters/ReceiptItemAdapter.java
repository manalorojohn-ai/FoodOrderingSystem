package com.fp.foodorderingsystem.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.models.CartItem;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class ReceiptItemAdapter extends RecyclerView.Adapter<ReceiptItemAdapter.ViewHolder> {
    private List<CartItem> cartItems;
    
    public ReceiptItemAdapter(List<CartItem> cartItems) {
        this.cartItems = cartItems;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_receipt_order, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (cartItems == null || position < 0 || position >= cartItems.size()) {
            android.util.Log.e("ReceiptItemAdapter", "Invalid position: " + position + ", list size: " + (cartItems != null ? cartItems.size() : "null"));
            return;
        }
        CartItem cartItem = cartItems.get(position);
        if (cartItem == null) {
            android.util.Log.e("ReceiptItemAdapter", "CartItem is null at position: " + position);
            return;
        }
        android.util.Log.d("ReceiptItemAdapter", "Binding item at position " + position + ": menuItemId=" + cartItem.getMenuItemId() + ", quantity=" + cartItem.getQuantity());
        holder.bind(cartItem);
    }
    
    @Override
    public int getItemCount() {
        return cartItems != null ? cartItems.size() : 0;
    }
    
    public void updateList(List<CartItem> newItems) {
        android.util.Log.d("ReceiptItemAdapter", "updateList called with " + (newItems != null ? newItems.size() : "null") + " items");
        this.cartItems = newItems != null ? newItems : new java.util.ArrayList<>();
        android.util.Log.d("ReceiptItemAdapter", "After update, cartItems size: " + (this.cartItems != null ? this.cartItems.size() : "null"));
        notifyDataSetChanged();
        android.util.Log.d("ReceiptItemAdapter", "notifyDataSetChanged called");
    }
    
    class ViewHolder extends RecyclerView.ViewHolder {
        private TextView tvItemName, tvItemQuantity, tvItemPrice;
        
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvItemName = itemView.findViewById(R.id.tvItemName);
            tvItemQuantity = itemView.findViewById(R.id.tvItemQuantity);
            tvItemPrice = itemView.findViewById(R.id.tvItemPrice);
        }
        
        void bind(CartItem cartItem) {
            if (cartItem == null) {
                return;
            }
            
            // Handle items with or without FoodItem details
            String itemName;
            if (cartItem.getFoodItem() != null && cartItem.getFoodItem().getName() != null) {
                itemName = cartItem.getFoodItem().getName();
            } else {
                // Fallback: show menu item ID if name not available
                itemName = "Item #" + cartItem.getMenuItemId();
            }
            
            tvItemName.setText(itemName != null ? itemName : "Unknown Item");
            tvItemQuantity.setText("Qty: " + cartItem.getQuantity());
            
            NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
            currencyFormat.setCurrency(java.util.Currency.getInstance("PHP"));
            double itemTotal = cartItem.getSubtotal();
            tvItemPrice.setText(currencyFormat.format(itemTotal));
        }
    }
}

