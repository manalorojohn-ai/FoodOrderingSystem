package com.fp.foodorderingsystem.models;

import com.google.gson.annotations.SerializedName;

public class User {
    private String id;
    private String email;
    @SerializedName("full_name")
    private String fullName;
    private String phone;
    private String address;
    @SerializedName("user_type")
    private String userType; // "customer" or "admin"
    @SerializedName("cancellation_count")
    private int cancellationCount;
    @SerializedName("is_blocked")
    private boolean isBlocked;
    @SerializedName("is_verified")
    private boolean isVerified;
    @SerializedName("created_at")
    private String createdAt;
    @SerializedName("updated_at")
    private String updatedAt;
    @SerializedName("profile_picture")
    private String profilePicture;
    @SerializedName("profile_picture_url")
    private String profilePictureUrl;

    public User() {}

    public User(String id, String email, String fullName, String phone, String address, String userType) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.phone = phone;
        this.address = address;
        this.userType = userType;
        this.cancellationCount = 0;
        this.isBlocked = false;
        this.isVerified = false;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }

    public int getCancellationCount() { return cancellationCount; }
    public void setCancellationCount(int cancellationCount) { this.cancellationCount = cancellationCount; }

    public boolean isBlocked() { return isBlocked; }
    public void setBlocked(boolean blocked) { isBlocked = blocked; }

    public boolean isVerified() { return isVerified; }
    public void setVerified(boolean verified) { isVerified = verified; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getProfilePicture() { return profilePicture; }
    public void setProfilePicture(String profilePicture) { this.profilePicture = profilePicture; }

    public String getProfilePictureUrl() { return profilePictureUrl; }
    public void setProfilePictureUrl(String profilePictureUrl) { this.profilePictureUrl = profilePictureUrl; }

    public boolean isAdmin() {
        return "admin".equalsIgnoreCase(userType);
    }

    public void incrementCancellationCount() {
        this.cancellationCount++;
    }
}

