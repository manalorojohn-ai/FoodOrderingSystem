package com.fp.foodorderingsystem.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class Order {
    // Supabase returns UUID as string, but we need int for compatibility
    // Store as String first, then convert to int when needed
    @SerializedName("id")
    private String idString; // UUID from Supabase (stored as string)
    private transient int id; // Numeric ID for compatibility (derived from UUID) - transient so Gson ignores it
    
    @SerializedName("customer_id")
    private String customerId;
    @SerializedName("total_amount")
    private double totalAmount;
    private String status; // pending, confirmed, preparing, ready, delivering, completed, cancelled
    @SerializedName("payment_method")
    private String paymentMethod; // gcash, maya, cod
    @SerializedName("delivery_address")
    private String deliveryAddress;
    @SerializedName("delivery_lat")
    private Double deliveryLat;
    @SerializedName("delivery_lng")
    private Double deliveryLng;
    @SerializedName("receipt_url")
    private String receiptUrl;
    @SerializedName("cancelled_by")
    private String cancelledBy; // "customer" or "admin"
    @SerializedName("cancellation_reason")
    private String cancellationReason;
    @SerializedName("created_at")
    private String createdAt;
    @SerializedName("updated_at")
    private String updatedAt;
    private List<CartItem> items;

    public Order() {}

    public Order(String customerId, double totalAmount, String paymentMethod, String deliveryAddress) {
        this.customerId = customerId;
        this.totalAmount = totalAmount;
        this.paymentMethod = paymentMethod;
        this.deliveryAddress = deliveryAddress;
        this.status = "pending";
    }

    // Getters and Setters
    public int getId() { 
        // If we already have a numeric ID, return it
        if (id != 0) {
            return id;
        }
        // Otherwise, try to convert from idString
        if (idString != null && !idString.isEmpty()) {
            try {
                // Try to parse as int first
                id = Integer.parseInt(idString);
                return id;
            } catch (NumberFormatException e) {
                // It's a UUID string - convert to a numeric hash
                // Use hash code as a stable numeric ID
                id = Math.abs(idString.hashCode() % 1000000);
                return id;
            }
        }
        return 0;
    }
    
    public void setId(int id) { 
        this.id = id;
        this.idString = String.valueOf(id); // Keep in sync
    }
    
    public String getIdString() {
        return idString != null ? idString : (id != 0 ? String.valueOf(id) : null);
    }
    
    public void setIdString(String idString) { 
        this.idString = idString;
        // Try to parse as int if possible
        if (idString != null && !idString.isEmpty()) {
            try {
                this.id = Integer.parseInt(idString);
            } catch (NumberFormatException e) {
                // It's a UUID, use hash code
                this.id = Math.abs(idString.hashCode() % 1000000);
            }
        }
    }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }

    public Double getDeliveryLat() { return deliveryLat; }
    public void setDeliveryLat(Double deliveryLat) { this.deliveryLat = deliveryLat; }

    public Double getDeliveryLng() { return deliveryLng; }
    public void setDeliveryLng(Double deliveryLng) { this.deliveryLng = deliveryLng; }

    public String getReceiptUrl() { return receiptUrl; }
    public void setReceiptUrl(String receiptUrl) { this.receiptUrl = receiptUrl; }

    public String getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(String cancelledBy) { this.cancelledBy = cancelledBy; }

    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public List<CartItem> getItems() { return items; }
    public void setItems(List<CartItem> items) { this.items = items; }

    public boolean isCancelled() {
        return "cancelled".equalsIgnoreCase(status);
    }

    public boolean isCompleted() {
        return "completed".equalsIgnoreCase(status);
    }
}

