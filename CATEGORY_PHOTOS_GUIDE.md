# Category Photos Integration Guide

## Overview

The Food Ordering System is now fully configured to display category photos from Supabase Storage. This guide explains how to add and manage category images.

## Supabase Storage Setup

### Storage Bucket: `Categories`

Your Supabase Storage has a bucket named `Categories` where all category photos are stored. The URL structure is:

```
https://wnsebtlndonfskwbhjfb.supabase.co/storage/v1/object/public/Categories/{filename}
```

## How It Works

### 1. Category Model
The `Category` model includes an `imageUrl` field that stores the file path:

```java
public class Category {
    private String imageUrl;  // e.g., "burgers.jpg" or "pizza.png"
    // ... other fields
}
```

### 2. Image URL Construction
The `ImageUtil` class automatically builds the full Supabase URL:

```java
public static String getCategoryImageUrl(String imagePath) {
    return getStorageUrl(BUCKET_CATEGORIES, imagePath);
}
// Result: https://wnsebtlndonfskwbhjfb.supabase.co/storage/v1/object/public/Categories/burgers.jpg
```

### 3. Category Adapter
The `CategoryAdapter` loads images using Glide with proper error handling:

```java
Glide.with(itemView.getContext())
    .load(imageUrl)
    .transition(DrawableTransitionOptions.withCrossFade(300))
    .placeholder(R.drawable.ic_food_banner)
    .error(R.drawable.ic_food_banner)
    .centerCrop()
    .into(ivCategoryIcon);
```

## Adding Category Photos

### Step 1: Upload Image to Supabase Storage

1. Go to Supabase Dashboard: https://supabase.com/dashboard
2. Navigate to **Storage** > **Categories** bucket
3. Click **Upload file** and select your category image
4. Note the filename (e.g., `pizza.png`, `burgers.jpg`)

**Important**: Use descriptive, lowercase filenames without spaces
- ✅ Good: `pizza.png`, `burgers.jpg`, `chinese-food.jpg`
- ❌ Bad: `Pizza with Cheese.jpg`, `Burger 2.png`

### Step 2: Add or Update Category in Database

When creating/editing a category, set the `imageUrl` field to match the uploaded filename:

```json
{
  "name": "Pizza",
  "description": "Delicious pizzas",
  "imageUrl": "pizza.png",
  "isActive": true
}
```

### Step 3: Images Display Automatically

Once the category is created with an `imageUrl`, it will automatically display in the app:

- **Customer Menu**: Categories show as cards with images (140dp x 120dp images)
- **Admin Panel**: Categories display in the management list
- **Search & Filter**: Works with category photos

## Database Schema

Make sure your categories table in Supabase includes:

```sql
CREATE TABLE categories (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    name TEXT NOT NULL,
    description TEXT,
    imageUrl TEXT,  -- File path in Categories bucket
    isActive BOOLEAN DEFAULT true,
    createdAt TIMESTAMP DEFAULT NOW(),
    updatedAt TIMESTAMP DEFAULT NOW()
);
```

## Image Specifications

For best results, use these image specifications:

| Property | Specification |
|----------|--------------|
| **Format** | JPG, PNG, WebP |
| **Dimensions** | 800x600px or larger |
| **Aspect Ratio** | 4:3 (or flexible) |
| **File Size** | < 2MB |
| **Quality** | High quality (reduce compression artifacts) |
| **Naming** | Lowercase, no spaces, descriptive |

## Example Categories with Photos

Here are example categories you should upload:

1. **burgers.jpg** - Burger category
2. **pizza.png** - Pizza category
3. **pasta.jpg** - Pasta category
4. **chicken.jpg** - Chicken dishes
5. **desserts.png** - Desserts
6. **beverages.jpg** - Drinks
7. **vegetarian.jpg** - Vegetarian options
8. **salads.png** - Salads

## Troubleshooting

### Images Not Showing?

1. **Check imageUrl Field**
   - Verify category has `imageUrl` set in database
   - Check filename matches uploaded file

2. **Check Storage Bucket**
   - Visit https://supabase.com/dashboard/project/wnsebtlndonfskwbhjfb/storage/files/buckets/Categories
   - Confirm files are uploaded

3. **Check Network**
   - Ensure internet connection is working
   - Check Supabase service status

4. **Check Logs**
   - Open logcat in Android Studio
   - Filter for "CategoryAdapter" or "Glide"
   - Look for error messages

### File Upload Failed?

- Maximum file size: 2MB per file
- Allowed formats: JPG, PNG, WebP
- Check file permissions in Supabase bucket settings

## Code Files Modified

1. **CategoryAdapter.java** - Enhanced with Glide image loading
2. **item_category.xml** - Updated layout to show full images
3. **gradient_overlay_black.xml** - New overlay for text readability
4. **ImageUtil.java** - Already configured for category images

## Client-Side Implementation

### MenuActivity
```java
private void loadCategories() {
    // Loads all categories including imageUrl
    Request request = supabaseService.createRequest("categories").get().build();
    // Categories are automatically displayed with images
}
```

### CategoryAdapter
```java
private void loadCategoryImage(Category category) {
    String imagePath = category.getImageUrl();
    String imageUrl = ImageUtil.getCategoryImageUrl(imagePath);
    
    Glide.with(itemView.getContext())
        .load(imageUrl)
        .transition(DrawableTransitionOptions.withCrossFade(300))
        .placeholder(R.drawable.ic_food_banner)
        .error(R.drawable.ic_food_banner)
        .centerCrop()
        .into(ivCategoryIcon);
}
```

## Best Practices

✅ **Do:**
- Use high-quality category images
- Keep filenames simple and descriptive
- Test images load properly on device
- Use consistent image aspect ratios
- Optimize images before upload

❌ **Don't:**
- Use spaces in filenames
- Upload very large files (> 2MB)
- Change filename after adding to database
- Mix uppercase and lowercase
- Use special characters in filenames

## Performance Tips

1. **Image Caching**
   - Glide automatically caches images
   - No need for manual cache management

2. **Network Usage**
   - Images are only downloaded when displayed
   - Optimize image size for faster downloads

3. **UI Responsiveness**
   - Images load asynchronously
   - No UI blocking during image loading

## Public URL Access

All images in the Categories bucket are public and can be accessed via:

```
https://wnsebtlndonfskwbhjfb.supabase.co/storage/v1/object/public/Categories/{filename}
```

No authentication required for public buckets.

## Next Steps

1. ✅ Upload category images to Supabase Storage
2. ✅ Update categories in database with imageUrl
3. ✅ Test in app (reload categories to see images)
4. ✅ Deploy to production

---

**Last Updated**: December 5, 2025  
**Status**: ✅ Ready for Production
