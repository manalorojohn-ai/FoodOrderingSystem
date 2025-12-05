package com.fp.foodorderingsystem.models;

import com.google.gson.annotations.SerializedName;

public class Notification {
    private int id;
    @SerializedName("user_id")
    private String userId;
    private String title;
    private String message;
    private String type; // order, payment, system
    @SerializedName("order_id")
    private Integer orderId;
    @SerializedName("is_read")
    private boolean isRead;
    @SerializedName("created_at")
    private String createdAt;

    public Notification() {}

    public Notification(String userId, String title, String message, String type) {
        this.userId = userId;
        this.title = title;
        this.message = message;
        this.type = type;
        this.isRead = false;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Integer getOrderId() { return orderId; }
    public void setOrderId(Integer orderId) { this.orderId = orderId; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}

