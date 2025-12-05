package com.fp.foodorderingsystem.utils;

import android.text.TextUtils;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class ValidationUtil {
    // Email patterns
    private static final Pattern EMAIL_PATTERN = 
        Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    
    // Phone pattern - minimum 10 digits, can start with + or 0
    private static final Pattern PHONE_PATTERN =
        Pattern.compile("^(\\+?[0-9]{10,15}|0[0-9]{9,14})$");
    
    // Full name pattern - letters, spaces, hyphens, apostrophes only, no numbers
    private static final Pattern FULL_NAME_PATTERN =
        Pattern.compile("^[A-Za-z][A-Za-z\\s'-]{1,98}$");
    
    // Password patterns
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile(".*[a-z].*");
    private static final Pattern DIGIT_PATTERN = Pattern.compile(".*[0-9].*");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*");
    
    /**
     * Validates email format
     */
    public static boolean isValidEmail(String email) {
        if (TextUtils.isEmpty(email)) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }
    
    /**
     * Validates password strength:
     * - Minimum 8 characters
     * - At least one uppercase letter
     * - At least one lowercase letter
     * - At least one number
     * - At least one special character
     */
    public static boolean isValidPassword(String password) {
        if (TextUtils.isEmpty(password)) {
            return false;
        }
        String pwd = password.trim();
        if (pwd.length() < 8) {
            return false;
        }
        return UPPERCASE_PATTERN.matcher(pwd).matches() &&
               LOWERCASE_PATTERN.matcher(pwd).matches() &&
               DIGIT_PATTERN.matcher(pwd).matches() &&
               SPECIAL_CHAR_PATTERN.matcher(pwd).matches();
    }
    
    /**
     * Gets password validation error message
     */
    public static String getPasswordErrorMessage(String password) {
        if (TextUtils.isEmpty(password)) {
            return "Password cannot be empty";
        }
        String pwd = password.trim();
        if (pwd.length() < 8) {
            return "Password must be at least 8 characters";
        }
        if (!UPPERCASE_PATTERN.matcher(pwd).matches()) {
            return "Password must contain at least one uppercase letter";
        }
        if (!LOWERCASE_PATTERN.matcher(pwd).matches()) {
            return "Password must contain at least one lowercase letter";
        }
        if (!DIGIT_PATTERN.matcher(pwd).matches()) {
            return "Password must contain at least one number";
        }
        if (!SPECIAL_CHAR_PATTERN.matcher(pwd).matches()) {
            return "Password must contain at least one special character (!@#$%^&*...)";
        }
        return null; // Valid
    }
    
    /**
     * Validates phone number - minimum 10 digits
     */
    public static boolean isValidPhone(String phone) {
        if (TextUtils.isEmpty(phone)) {
            return false;
        }
        // Remove spaces, dashes, parentheses
        String cleaned = phone.replaceAll("[\\s\\-\\(\\)]", "");
        return PHONE_PATTERN.matcher(cleaned).matches() && cleaned.replaceAll("[^0-9]", "").length() >= 10;
    }
    
    /**
     * Gets phone validation error message
     */
    public static String getPhoneErrorMessage(String phone) {
        if (TextUtils.isEmpty(phone)) {
            return "Phone number cannot be empty";
        }
        String cleaned = phone.replaceAll("[\\s\\-\\(\\)]", "");
        String digitsOnly = cleaned.replaceAll("[^0-9]", "");
        if (digitsOnly.length() < 10) {
            return "Phone number must have at least 10 digits";
        }
        if (!PHONE_PATTERN.matcher(cleaned).matches()) {
            return "Please enter a valid phone number format";
        }
        return null; // Valid
    }

    /**
     * Validates full name:
     * - Not empty
     * - Minimum 2 characters
     * - No numbers
     * - Only letters, spaces, hyphens, apostrophes
     */
    public static boolean isValidFullName(String name) {
        if (TextUtils.isEmpty(name)) {
            return false;
        }
        String normalized = name.trim().replaceAll("\\s+", " ");
        if (normalized.length() < 2) {
            return false;
        }
        // Check for numbers
        if (Pattern.compile(".*[0-9].*").matcher(normalized).matches()) {
            return false;
        }
        return FULL_NAME_PATTERN.matcher(normalized).matches();
    }
    
    /**
     * Gets full name validation error message
     */
    public static String getFullNameErrorMessage(String name) {
        if (TextUtils.isEmpty(name)) {
            return "Full name cannot be empty";
        }
        String normalized = name.trim().replaceAll("\\s+", " ");
        if (normalized.length() < 2) {
            return "Full name must be at least 2 characters";
        }
        if (Pattern.compile(".*[0-9].*").matcher(normalized).matches()) {
            return "Full name cannot contain numbers";
        }
        if (!FULL_NAME_PATTERN.matcher(normalized).matches()) {
            return "Full name can only contain letters, spaces, hyphens, and apostrophes";
        }
        return null; // Valid
    }
    
    /**
     * Validates address:
     * - Not empty
     * - Minimum 5 characters
     */
    public static boolean isValidAddress(String address) {
        if (TextUtils.isEmpty(address)) {
            return false;
        }
        return address.trim().length() >= 5;
    }
    
    /**
     * Gets address validation error message
     */
    public static String getAddressErrorMessage(String address) {
        if (TextUtils.isEmpty(address)) {
            return "Address cannot be empty";
        }
        if (address.trim().length() < 5) {
            return "Address must be at least 5 characters";
        }
        return null; // Valid
    }
    
    /**
     * Sanitizes input to prevent SQL injection and XSS
     * Removes or escapes potentially dangerous characters
     */
    public static String sanitizeInput(String input) {
        if (TextUtils.isEmpty(input)) {
            return "";
        }
        // Remove SQL injection patterns
        String sanitized = input.trim()
            .replaceAll("(?i)(union|select|insert|update|delete|drop|create|alter|exec|execute|script)", "")
            .replaceAll("[;'\"\\\\]", "")
            .replaceAll("<[^>]*>", ""); // Remove HTML tags
        
        // Normalize whitespace
        sanitized = sanitized.replaceAll("\\s+", " ");
        
        return sanitized;
    }
    
    /**
     * Sanitizes email (less aggressive, preserves email format)
     */
    public static String sanitizeEmail(String email) {
        if (TextUtils.isEmpty(email)) {
            return "";
        }
        return email.trim().toLowerCase(Locale.US)
            .replaceAll("[^a-z0-9@._+-]", "");
    }
    
    /**
     * Sanitizes phone number (preserves digits and common formatting)
     */
    public static String sanitizePhone(String phone) {
        if (TextUtils.isEmpty(phone)) {
            return "";
        }
        return phone.replaceAll("[^0-9+\\-\\s()]", "");
    }
    
    public static boolean isEmpty(String text) {
        return TextUtils.isEmpty(text) || text.trim().isEmpty();
    }
}

