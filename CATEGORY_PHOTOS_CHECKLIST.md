# Category Photos Setup Checklist ✅

## Current Status
- ✅ CategoryAdapter updated to load images from Supabase Storage
- ✅ item_category.xml layout redesigned for photo display
- ✅ gradient_overlay_black.xml created for text readability
- ✅ ImageUtil.java configured for category image URLs
- ✅ Build successful - ready for testing

## What You Need to Do

### 1. Upload Category Photos to Supabase (CRITICAL)

**Location**: https://supabase.com/dashboard/project/wnsebtlndonfskwbhjfb/storage/files/buckets/Categories

**Steps**:
1. Click the Categories bucket
2. Click "Upload file" button
3. Select your category image
4. Note the filename (e.g., `pizza.png`)

**Recommended Categories to Upload**:
```
- burgers.jpg          (Burgers)
- pizza.png            (Pizza)  
- pasta.jpg            (Pasta)
- chicken.jpg          (Chicken)
- desserts.png         (Desserts)
- beverages.jpg        (Beverages)
- vegetarian.jpg       (Vegetarian)
- salads.png           (Salads)
```

### 2. Update Categories in Database

For each category, add the `imageUrl` field with the filename:

**Via Supabase Dashboard**:
1. Go to Database > categories table
2. For each row, set `imageUrl` to the uploaded filename
   - Example: `pizza.png`, `burgers.jpg`

**Via SQL**:
```sql
UPDATE categories SET imageUrl = 'pizza.png' WHERE name = 'Pizza';
UPDATE categories SET imageUrl = 'burgers.jpg' WHERE name = 'Burgers';
-- ... etc
```

**Via API** (in your admin panel, when adding categories):
```json
{
  "name": "Pizza",
  "description": "Delicious pizzas",
  "imageUrl": "pizza.png",
  "isActive": true
}
```

### 3. Test in App

1. Build and deploy the app
2. Go to Menu > Categories section
3. Verify images load correctly
4. Check for smooth transitions and proper scaling

## File Structure

```
FoodOrderingSystem/
├── app/src/main/
│   ├── java/
│   │   └── adapters/
│   │       └── CategoryAdapter.java              ✅ UPDATED
│   └── res/
│       ├── layout/
│       │   └── item_category.xml                 ✅ UPDATED
│       └── drawable/
│           └── gradient_overlay_black.xml        ✅ NEW
└── CATEGORY_PHOTOS_GUIDE.md                     ✅ NEW
```

## Code Components

### CategoryAdapter.java
- Loads images from Supabase using `ImageUtil.getCategoryImageUrl()`
- Uses Glide for image loading with proper caching
- Handles errors with ic_food_banner placeholder
- Smooth cross-fade transitions (300ms)

### item_category.xml
- Card size: 140dp × variable height
- Image area: 120dp height with centerCrop scaling
- Category name below image with 2-line max
- Gradient overlay for text readability

### ImageUtil.java (Already Configured)
- URL: `https://wnsebtlndonfskwbhjfb.supabase.co/storage/v1/object/public/Categories/{filename}`
- Handles URL encoding for special characters
- Handles both full URLs and file paths

## Image Specifications

| Property | Value |
|----------|-------|
| Format | JPG, PNG, WebP |
| Resolution | 800x600px minimum |
| Aspect Ratio | 4:3 or square |
| File Size | < 2MB |
| Quality | High (optimize before upload) |

## Troubleshooting Quick Links

1. **Images not showing?**
   - Check `imageUrl` field in database
   - Verify file exists in Categories bucket
   - Check Supabase bucket is public

2. **Layout issues?**
   - Review item_category.xml (140dp width)
   - Check gradient_overlay_black.xml is present
   - Rebuild app with `./gradlew clean build`

3. **Performance issues?**
   - Optimize image file sizes
   - Use JPG for photos, PNG for graphics
   - Glide handles caching automatically

## Database Check

Run this query to verify imageUrl is populated:

```sql
SELECT id, name, imageUrl FROM categories WHERE imageUrl IS NOT NULL;
```

Expected output:
```
id | name    | imageUrl
1  | Pizza   | pizza.png
2  | Burgers | burgers.jpg
3  | Pasta   | pasta.jpg
...
```

## Build Status

```
✅ BUILD SUCCESSFUL
Compiled: CategoryAdapter.java
Compiled: item_category.xml
Created: gradient_overlay_black.xml
Status: Ready for Testing
```

## Quick Reference

**Supabase Bucket**: Categories  
**Bucket URL**: https://supabase.com/dashboard/project/wnsebtlndonfskwbhjfb/storage/files/buckets/Categories  
**Public URL Format**: `https://wnsebtlndonfskwbhjfb.supabase.co/storage/v1/object/public/Categories/{filename}`

## Next Steps

1. ⏳ Upload category photos to Supabase Storage
2. ⏳ Update categories table with imageUrl values
3. ⏳ Deploy and test in app
4. ⏳ Verify images load and display correctly

---

**Setup Time**: ~15 minutes  
**Difficulty**: Easy  
**Status**: ✅ Application Ready, Waiting for Images
