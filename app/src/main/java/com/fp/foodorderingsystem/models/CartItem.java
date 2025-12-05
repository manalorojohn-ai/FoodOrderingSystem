package com.fp.foodorderingsystem.models;

import com.google.gson.annotations.SerializedName;

public class CartItem {
    private String id;
    @SerializedName("user_id")
    private String userId;
    @SerializedName("menu_item_id")
    private int menuItemId;
    private int quantity;
    @SerializedName("unit_price")
    private double unitPrice;
    @SerializedName("total_price")
    private double totalPrice;
    @SerializedName("menu_items")
    private FoodItem menuItem;

    // Legacy support field when creating cart items locally
    private transient FoodItem foodItem;

    public CartItem() {}

    public CartItem(FoodItem foodItem, int quantity) {
        this.menuItem = foodItem;
        this.foodItem = foodItem;
        this.quantity = quantity;
        if (foodItem != null) {
            this.unitPrice = foodItem.getPrice();
            this.totalPrice = this.unitPrice * quantity;
            this.menuItemId = foodItem.getId();
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getMenuItemId() {
        return menuItemId;
    }

    public void setMenuItemId(int menuItemId) {
        this.menuItemId = menuItemId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
        recalculateTotal();
    }

    public double getUnitPrice() {
        if (unitPrice > 0) {
            return unitPrice;
        }
        FoodItem item = getFoodItem();
        return item != null ? item.getPrice() : 0.0;
    }

    public void setUnitPrice(double unitPrice) {
        this.unitPrice = unitPrice;
        recalculateTotal();
    }

    public double getTotalPrice() {
        return totalPrice > 0 ? totalPrice : getUnitPrice() * quantity;
    }

    public void setTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public FoodItem getFoodItem() {
        if (menuItem != null) {
            return menuItem;
        }
        return foodItem;
    }

    public void setFoodItem(FoodItem foodItem) {
        this.menuItem = foodItem;
        this.foodItem = foodItem;
        if (foodItem != null) {
            this.unitPrice = foodItem.getPrice();
            this.menuItemId = foodItem.getId();
            recalculateTotal();
        }
    }

    public void setMenuItem(FoodItem menuItem) {
        setFoodItem(menuItem);
    }

    public double getSubtotal() {
        return getTotalPrice();
    }

    public void incrementQuantity() {
        this.quantity++;
        recalculateTotal();
    }

    public void decrementQuantity() {
        if (this.quantity > 1) {
            this.quantity--;
            recalculateTotal();
        }
    }

    private void recalculateTotal() {
        double price = getUnitPrice();
        if (price > 0) {
            this.totalPrice = price * this.quantity;
        }
    }
}

