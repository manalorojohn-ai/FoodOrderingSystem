package com.fp.foodorderingsystem.utils;

import com.fp.foodorderingsystem.config.SupabaseConfig;

/**
 * Utility class for handling image URLs from Supabase Storage
 */
public class ImageUtil {
    
    // Default bucket names - update these based on your Supabase Storage buckets
    public static final String BUCKET_FOOD_ITEMS = "food-items";
    public static final String BUCKET_CATEGORIES = "categories";
    public static final String BUCKET_LOGO = "logo";
    
    /**
     * Get the full public URL for an image stored in Supabase Storage
     * 
     * @param bucketName The name of the storage bucket (e.g., "food-items", "categories")
     * @param filePath The path to the file within the bucket (e.g., "burger.jpg" or "images/burger.jpg")
     * @return Full public URL to the image
     */
    public static String getStorageUrl(String bucketName, String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }
        
        // If it's already a full URL, return as is
        if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
            return filePath;
        }
        
        // Remove leading slash if present
        if (filePath.startsWith("/")) {
            filePath = filePath.substring(1);
        }
        
        // URL encode the file path to handle special characters and spaces
        // But preserve slashes as they're part of the path structure
        try {
            // Split by slashes, encode each segment, then rejoin
            String[] pathParts = filePath.split("/");
            StringBuilder encodedPath = new StringBuilder();
            for (int i = 0; i < pathParts.length; i++) {
                if (i > 0) {
                    encodedPath.append("/");
                }
                // URL encode each path segment
                encodedPath.append(java.net.URLEncoder.encode(pathParts[i], "UTF-8")
                    .replace("+", "%20")); // Replace + with %20 for spaces
            }
            filePath = encodedPath.toString();
        } catch (Exception e) {
            // If encoding fails, use original path
        }
        
        // Construct Supabase Storage public URL
        return SupabaseConfig.SUPABASE_URL + "/storage/v1/object/public/" + bucketName + "/" + filePath;
    }
    
    /**
     * Get the full public URL for a food item image
     * 
     * @param imagePath The path to the image file
     * @return Full public URL to the image
     */
    public static String getFoodItemImageUrl(String imagePath) {
        return getStorageUrl(BUCKET_FOOD_ITEMS, imagePath);
    }
    
    /**
     * Get the full public URL for a category image
     * 
     * @param imagePath The path to the image file
     * @return Full public URL to the image
     */
    public static String getCategoryImageUrl(String imagePath) {
        return getStorageUrl(BUCKET_CATEGORIES, imagePath);
    }
    
    /**
     * Get the full public URL for the logo
     * 
     * @param imagePath The path to the logo file
     * @return Full public URL to the logo
     */
    public static String getLogoUrl(String imagePath) {
        return getStorageUrl(BUCKET_LOGO, imagePath);
    }
    
    /**
     * Extract filename from a path
     * Handles paths like "assets/images/menu/burger.jpg" -> "burger.jpg"
     * or "uploads/menu/123.jpg" -> "123.jpg"
     * Also handles URLs and normalizes the filename
     */
    private static String extractFileName(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        
        // Remove leading/trailing slashes and whitespace
        path = path.trim().replaceAll("^/+|/+$", "");
        
        // If it's a full URL, extract the path part first
        if (path.startsWith("http://") || path.startsWith("https://")) {
            try {
                int pathStart = path.indexOf('/', path.indexOf("://") + 3);
                if (pathStart > 0) {
                    int queryStart = path.indexOf('?', pathStart);
                    path = queryStart > 0 
                        ? path.substring(pathStart + 1, queryStart)
                        : path.substring(pathStart + 1);
                }
            } catch (Exception e) {
                // If URL parsing fails, continue with original path
            }
        }
        
        // If it's already just a filename, return it normalized
        if (!path.contains("/") && !path.contains("\\")) {
            return path;
        }
        
        // Extract the last part after slash (handles both / and \)
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            String fileName = path.substring(lastSlash + 1);
            // URL decode if needed (handles %20 for spaces, etc.)
            try {
                fileName = java.net.URLDecoder.decode(fileName, "UTF-8");
            } catch (Exception e) {
                // If decoding fails, use original
            }
            return fileName;
        }
        
        return path;
    }
    
    /**
     * Generate a likely image filename from a menu item name
     * Converts "Bicol Express" -> "bicol-express.jpg"
     * Handles special cases and common food item name variations
     */
    private static String generateImageFileName(String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return null;
        }
        
        // Remove common suffixes that might not be in filename
        String cleaned = itemName
            .replaceAll("(?i)\\s+with\\s+rice$", "")
            .replaceAll("(?i)\\s+with\\s+.*$", "")
            .trim();
        
        // Convert to lowercase and replace spaces with hyphens
        String filename = cleaned.toLowerCase()
            .replaceAll("\\s+", "-")
            .replaceAll("[^a-z0-9-]", "")
            .replaceAll("-+", "-") // Replace multiple hyphens with single
            .replaceAll("^-|-$", ""); // Remove leading/trailing hyphens
        
        // Special mappings for common food items
        java.util.Map<String, String> nameMappings = new java.util.HashMap<>();
        nameMappings.put("bicol-express", "bicol-express");
        nameMappings.put("adobo", "adobo");
        nameMappings.put("sinigang", "sinigang");
        nameMappings.put("pastil", "pastil");
        nameMappings.put("fried-chicken", "fried-chicken");
        nameMappings.put("halo-halo", "halo-halo");
        nameMappings.put("mais-con-yelo", "mais-con-yelo");
        nameMappings.put("leche-flan", "leche-flan");
        nameMappings.put("banana-split", "banana-split");
        nameMappings.put("cheese-burger", "cheese-burger");
        nameMappings.put("plain-burger", "plain-burger");
        nameMappings.put("coke", "coke");
        nameMappings.put("sprite", "sprite");
        nameMappings.put("royal", "royal");
        nameMappings.put("mango-juice", "mango-juice");
        nameMappings.put("calamansi-juice", "calamansi-juice");
        
        // Check if we have a mapping
        if (nameMappings.containsKey(filename)) {
            filename = nameMappings.get(filename);
        }
        
        // Default to .jpg extension
        return filename + ".jpg";
    }
    
    /**
     * Get the best available image URL from FoodItem
     * Checks imageUrl first, then imagePath, then tries to generate from item name
     * 
     * @param imageUrl The imageUrl field from FoodItem
     * @param imagePath The imagePath field from FoodItem
     * @param itemName Optional: menu item name to generate filename if paths are invalid
     * @return Full URL to the image, or null if neither is available
     */
    public static String getFoodItemUrl(String imageUrl, String imagePath) {
        return getFoodItemUrl(imageUrl, imagePath, null);
    }
    
    /**
     * Get the best available image URL from FoodItem with fallback to name-based generation
     * 
     * @param imageUrl The imageUrl field from FoodItem
     * @param imagePath The imagePath field from FoodItem
     * @param itemName Optional: menu item name to generate filename if paths are invalid
     * @return Full URL to the image, or null if neither is available
     */
    public static String getFoodItemUrl(String imageUrl, String imagePath, String itemName) {
        // Try imageUrl first
        if (imageUrl != null && !imageUrl.isEmpty()) {
            // If it's already a full URL, return as is
            if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
                return imageUrl;
            }
            // Extract filename and try it
            String fileName = extractFileName(imageUrl);
            if (fileName != null && !fileName.isEmpty()) {
                String url = getFoodItemImageUrl(fileName);
                if (url != null) {
                    return url;
                }
            }
        }
        
        // Try imagePath
        if (imagePath != null && !imagePath.isEmpty()) {
            // First, try using the path as-is (in case it's already a valid path in the bucket)
            // This handles cases where the path might be correct but not at root level
            if (!imagePath.startsWith("http://") && !imagePath.startsWith("https://")) {
                // Try the path as-is first
                String url = getFoodItemImageUrl(imagePath);
                if (url != null) {
                    return url;
                }
            }
            
            // Extract filename from path and try it
            String fileName = extractFileName(imagePath);
            if (fileName != null && !fileName.isEmpty()) {
                String url = getFoodItemImageUrl(fileName);
                if (url != null) {
                    return url;
                }
            }
        }
        
        // Fallback: try to generate filename from item name
        if (itemName != null && !itemName.isEmpty()) {
            String generatedFileName = generateImageFileName(itemName);
            if (generatedFileName != null && !generatedFileName.isEmpty()) {
                return getFoodItemImageUrl(generatedFileName);
            }
        }
        
        return null;
    }
}

