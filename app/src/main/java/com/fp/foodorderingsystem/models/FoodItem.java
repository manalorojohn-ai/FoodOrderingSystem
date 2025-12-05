package com.fp.foodorderingsystem.models;

import com.google.gson.annotations.SerializedName;

public class FoodItem {
    private int id;
    private int categoryId;
    private String name;
    private String description;
    private double price;
    private String imageUrl;
    private String imagePath;
    private String status; // "available" or "out_of_stock"
    private int stock;
    @SerializedName("preparation_time")
    private int preparationTime; // in minutes
    private double averageRating;
    private int totalReviews;
    private int totalRatings;
    private String createdAt;
    private String updatedAt;

    public FoodItem() {}

    public FoodItem(String name, String description, double price, int categoryId) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.categoryId = categoryId;
        this.status = "available";
        this.stock = 100;
        this.preparationTime = 15;
        this.averageRating = 0.0;
        this.totalReviews = 0;
        this.totalRatings = 0;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getCategoryId() { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    public int getPreparationTime() { return preparationTime; }
    public void setPreparationTime(int preparationTime) { this.preparationTime = preparationTime; }

    public double getAverageRating() { return averageRating; }
    public void setAverageRating(double averageRating) { this.averageRating = averageRating; }

    public int getTotalReviews() { return totalReviews; }
    public void setTotalReviews(int totalReviews) { this.totalReviews = totalReviews; }

    public int getTotalRatings() { return totalRatings; }
    public void setTotalRatings(int totalRatings) { this.totalRatings = totalRatings; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public boolean isAvailable() {
        return "available".equalsIgnoreCase(status) && stock > 0;
    }
}

