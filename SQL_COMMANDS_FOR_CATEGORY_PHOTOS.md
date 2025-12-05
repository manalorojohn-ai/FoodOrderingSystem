# Quick SQL Commands for Category Photos

## 📋 Copy & Paste Commands

Use these SQL commands to quickly set up category image URLs in your database.

---

## Option 1: Generic SQL (Safe for Any Category Names)

```sql
-- Update pizza category
UPDATE categories 
SET imageUrl = 'pizza.png' 
WHERE LOWER(name) LIKE LOWER('%pizza%');

-- Update burger category
UPDATE categories 
SET imageUrl = 'burgers.jpg' 
WHERE LOWER(name) LIKE LOWER('%burger%');

-- Update pasta category
UPDATE categories 
SET imageUrl = 'pasta.jpg' 
WHERE LOWER(name) LIKE LOWER('%pasta%');

-- Update chicken category
UPDATE categories 
SET imageUrl = 'chicken.jpg' 
WHERE LOWER(name) LIKE LOWER('%chicken%');

-- Update dessert category
UPDATE categories 
SET imageUrl = 'desserts.png' 
WHERE LOWER(name) LIKE LOWER('%dessert%');

-- Update beverage category
UPDATE categories 
SET imageUrl = 'beverages.jpg' 
WHERE LOWER(name) LIKE LOWER('%beverage%');

-- Update vegetarian category
UPDATE categories 
SET imageUrl = 'vegetarian.jpg' 
WHERE LOWER(name) LIKE LOWER('%vegetarian%');

-- Update salad category
UPDATE categories 
SET imageUrl = 'salads.png' 
WHERE LOWER(name) LIKE LOWER('%salad%');
```

---

## Option 2: By Category ID

First, find your category IDs:

```sql
-- View all categories with IDs
SELECT id, name, imageUrl FROM categories ORDER BY id;
```

Then update by ID (replace 1, 2, 3 with your actual IDs):

```sql
-- If Pizza is ID 1
UPDATE categories SET imageUrl = 'pizza.png' WHERE id = 1;

-- If Burgers is ID 2
UPDATE categories SET imageUrl = 'burgers.jpg' WHERE id = 2;

-- If Pasta is ID 3
UPDATE categories SET imageUrl = 'pasta.jpg' WHERE id = 3;

-- If Chicken is ID 4
UPDATE categories SET imageUrl = 'chicken.jpg' WHERE id = 4;

-- If Desserts is ID 5
UPDATE categories SET imageUrl = 'desserts.png' WHERE id = 5;

-- If Beverages is ID 6
UPDATE categories SET imageUrl = 'beverages.jpg' WHERE id = 6;

-- Adjust IDs based on your actual data
```

---

## Option 3: Bulk Update (All at Once)

Replace all imageUrl values at once using a CASE statement:

```sql
UPDATE categories 
SET imageUrl = CASE 
    WHEN LOWER(name) LIKE LOWER('%pizza%') THEN 'pizza.png'
    WHEN LOWER(name) LIKE LOWER('%burger%') THEN 'burgers.jpg'
    WHEN LOWER(name) LIKE LOWER('%pasta%') THEN 'pasta.jpg'
    WHEN LOWER(name) LIKE LOWER('%chicken%') THEN 'chicken.jpg'
    WHEN LOWER(name) LIKE LOWER('%dessert%') THEN 'desserts.png'
    WHEN LOWER(name) LIKE LOWER('%beverage%') THEN 'beverages.jpg'
    WHEN LOWER(name) LIKE LOWER('%vegetarian%') THEN 'vegetarian.jpg'
    WHEN LOWER(name) LIKE LOWER('%salad%') THEN 'salads.png'
    ELSE imageUrl
END
WHERE name IS NOT NULL;
```

---

## Verification Commands

### Check which categories have images:

```sql
SELECT 
  id, 
  name, 
  imageUrl,
  CASE 
    WHEN imageUrl IS NOT NULL AND imageUrl != '' THEN '✅'
    ELSE '❌'
  END as has_image
FROM categories 
ORDER BY id;
```

### Check which categories are missing images:

```sql
SELECT id, name, imageUrl
FROM categories 
WHERE imageUrl IS NULL OR imageUrl = ''
ORDER BY id;
```

### Count categories with images:

```sql
SELECT 
  COUNT(*) as total_categories,
  SUM(CASE WHEN imageUrl IS NOT NULL AND imageUrl != '' THEN 1 ELSE 0 END) as with_images,
  SUM(CASE WHEN imageUrl IS NULL OR imageUrl = '' THEN 1 ELSE 0 END) as without_images
FROM categories;
```

---

## How to Run SQL

1. Go to: https://supabase.com/dashboard/project/wnsebtlndonfskwbhjfb/sql/new
2. Copy one of the commands above
3. Paste into the SQL editor
4. Click **"RUN"** button
5. See results below

---

## Common Issues & Fixes

### Issue: "No rows updated"

**Cause**: Category names don't match the LIKE pattern

**Fix**: Check your actual category names:

```sql
SELECT * FROM categories;
```

Then adjust the LIKE pattern:

```sql
-- Example: If your category is called "Grilled Burgers"
UPDATE categories 
SET imageUrl = 'burgers.jpg' 
WHERE LOWER(name) LIKE LOWER('%burger%');

-- This will match "Burgers", "Grilled Burgers", "Crispy Burgers", etc.
```

### Issue: Want to update specific category by exact name

```sql
-- Update category with exact name match (case-insensitive)
UPDATE categories 
SET imageUrl = 'pizza.png' 
WHERE LOWER(name) = LOWER('Pizza');
```

### Issue: Clear all images (reset)

```sql
-- Clear all imageUrl values
UPDATE categories SET imageUrl = NULL;

-- Or delete specific ones
UPDATE categories 
SET imageUrl = NULL 
WHERE LOWER(name) LIKE LOWER('%pizza%');
```

---

## Safe Testing Approach

### Step 1: Preview what will be updated

```sql
-- See what matches BEFORE updating
SELECT id, name, imageUrl
FROM categories 
WHERE LOWER(name) LIKE LOWER('%pizza%');
```

### Step 2: If it looks good, run the update

```sql
UPDATE categories 
SET imageUrl = 'pizza.png' 
WHERE LOWER(name) LIKE LOWER('%pizza%');
```

### Step 3: Verify the update

```sql
-- Check the result
SELECT id, name, imageUrl
FROM categories 
WHERE imageUrl IS NOT NULL;
```

---

## Batch Update Template

Use this template for any custom mappings:

```sql
UPDATE categories 
SET imageUrl = CASE 
    WHEN LOWER(name) LIKE LOWER('%YOUR_PATTERN_1%') THEN 'filename1.jpg'
    WHEN LOWER(name) LIKE LOWER('%YOUR_PATTERN_2%') THEN 'filename2.jpg'
    WHEN LOWER(name) LIKE LOWER('%YOUR_PATTERN_3%') THEN 'filename3.jpg'
    -- Add more patterns as needed
    ELSE imageUrl  -- Keep existing value if no match
END
WHERE name IS NOT NULL;
```

---

## Important Notes

⚠️ **Before Running Any UPDATE**:
1. Know your exact category names
2. Know your exact filenames in Supabase
3. Test with SELECT first
4. Verify results with SELECT after

✅ **Safe Operations**:
- `SELECT` commands don't change data
- `UPDATE` with `WHERE` clause is safer
- Always check results after update

❌ **Dangerous Operations**:
- `UPDATE` without `WHERE` (updates all rows!)
- Don't use if you're not sure

---

## Recovery If Something Goes Wrong

If you accidentally updated wrong values:

```sql
-- Set all imageUrl to NULL (clear them)
UPDATE categories SET imageUrl = NULL;

-- Then run the correct update command
```

---

## Example: Complete Workflow

```sql
-- 1. View current state
SELECT id, name, imageUrl FROM categories ORDER BY id;

-- 2. Update categories
UPDATE categories 
SET imageUrl = CASE 
    WHEN LOWER(name) LIKE LOWER('%pizza%') THEN 'pizza.png'
    WHEN LOWER(name) LIKE LOWER('%burger%') THEN 'burgers.jpg'
    WHEN LOWER(name) LIKE LOWER('%pasta%') THEN 'pasta.jpg'
    ELSE imageUrl
END
WHERE name IS NOT NULL;

-- 3. Verify results
SELECT id, name, imageUrl FROM categories ORDER BY id;

-- 4. Check for any missing
SELECT id, name FROM categories WHERE imageUrl IS NULL;
```

---

## File Names Reference

Make sure these files are uploaded to Supabase Categories bucket:

| Category | Filename |
|----------|----------|
| Pizza | pizza.png |
| Burgers | burgers.jpg |
| Pasta | pasta.jpg |
| Chicken | chicken.jpg |
| Desserts | desserts.png |
| Beverages | beverages.jpg |
| Vegetarian | vegetarian.jpg |
| Salads | salads.png |

Use **exactly** these filenames in the database!

---

## Need Help?

Check these guides:
- **Quick Start**: CATEGORY_PHOTOS_QUICK_START.md
- **Complete Guide**: CATEGORY_PHOTOS_GUIDE.md
- **Visual Guide**: CATEGORY_PHOTOS_VISUAL_GUIDE.md

---

**Ready?** Copy a command above, paste it in SQL editor at:  
https://supabase.com/dashboard/project/wnsebtlndonfskwbhjfb/sql/new

Then click **RUN**! 🚀
