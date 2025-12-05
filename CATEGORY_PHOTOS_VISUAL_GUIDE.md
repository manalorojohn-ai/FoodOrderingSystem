# Category Photos Display - Visual Guide

## How Categories Will Display

### Desktop Browser View (Supabase Dashboard)
```
Categories Bucket (/storage/files/buckets/Categories)
├── burgers.jpg         [400 × 300px image]
├── pizza.png          [400 × 300px image]
├── pasta.jpg          [400 × 300px image]
├── chicken.jpg        [400 × 300px image]
├── desserts.png       [400 × 300px image]
└── beverages.jpg      [400 × 300px image]
```

### App View - Menu Screen

```
┌─────────────────────────────────────┐
│  MENU                          🔔   │
├─────────────────────────────────────┤
│                                     │
│  Categories (Horizontal Scroll)     │
│                                     │
│ ┌──────────────┐ ┌──────────────┐  │
│ │              │ │              │  │
│ │  [Pizza]     │ │  [Burgers]   │  │
│ │   Image      │ │   Image      │  │
│ │ ────────────│ │ ────────────│  │
│ │    Pizza    │ │   Burgers   │  │
│ └──────────────┘ └──────────────┘  │
│                                     │
│ ┌──────────────┐ ┌──────────────┐  │
│ │              │ │              │  │
│ │  [Pasta]     │ │  [Chicken]   │  │
│ │   Image      │ │   Image      │  │
│ │ ────────────│ │ ────────────│  │
│ │    Pasta    │ │   Chicken   │  │
│ └──────────────┘ └──────────────┘  │
│                                     │
│  Menu Items (Grid)                  │
│                                     │
│ ┌──────────────┐ ┌──────────────┐  │
│ │              │ │              │  │
│ │   Item 1     │ │   Item 2     │  │
│ │   Image      │ │   Image      │  │
│ │    ₱399      │ │    ₱349      │  │
│ │  + Add       │ │  + Add       │  │
│ └──────────────┘ └──────────────┘  │
│                                     │
└─────────────────────────────────────┘
```

## Category Card Layout

### Dimensions
```
┌──────────────────────────┐
│     140 pixels wide      │
│                          │
│  ┌────────────────────┐  │ ↑
│  │                    │  │ │
│  │   Category Image   │  │ 120px
│  │   (centerCrop)     │  │ │
│  │                    │  │ │
│  │ ─────────────────  │  │ Gradient
│  │  Category Name     │  │ Overlay
│  └────────────────────┘  │ (50px)
│                          │ ↓
│   Spacing               │ ↓
│   (10px)                │ 10px
│                          │
│  Pizza                   │ ↓
│  (max 2 lines)           │
│                          │
│  Spacing                 │
│  (10px)                  │
└──────────────────────────┘
   ↕
 wrap_content (grows with content)
```

### Visual Breakdown

```
CARD VIEW (140dp wide)
├─ FrameLayout (match_parent × 120dp)
│  ├─ ImageView (match_parent × 120dp)
│  │  └─ Image loaded from Supabase
│  │     [e.g., pizza.png]
│  │
│  └─ Gradient Overlay (match_parent × 50dp)
│     └─ Black gradient (transparent → semi-opaque)
│        (Makes text readable over image)
│
└─ TextView (match_parent, wrap_content)
   ├─ Text: "Pizza"
   ├─ Color: Dark gray (#333333)
   ├─ Size: 14sp
   ├─ Style: Bold
   ├─ Max Lines: 2
   ├─ Margin: 10dp all sides
   └─ Alignment: Center
```

## Data Flow

```
1. App Load
   │
   ├─→ MenuActivity.loadCategories()
   │
   ├─→ Supabase Query: SELECT * FROM categories
   │   └─ Returns:
   │      { "id": 1, "name": "Pizza", "imageUrl": "pizza.png" }
   │      { "id": 2, "name": "Burgers", "imageUrl": "burgers.jpg" }
   │      ...
   │
   ├─→ CategoryAdapter.onBindViewHolder()
   │
   ├─→ ViewHolder.bind(category)
   │   │
   │   ├─→ tvCategoryName.setText("Pizza")
   │   │
   │   └─→ loadCategoryImage(category)
   │       │
   │       ├─→ imagePath = "pizza.png"
   │       │
   │       ├─→ imageUrl = ImageUtil.getCategoryImageUrl("pizza.png")
   │       │   Result: "https://wnsebtlndonfskwbhjfb.supabase.co/storage/v1/object/public/Categories/pizza.png"
   │       │
   │       ├─→ Glide.load(imageUrl)
   │       │   ├─ Network request to Supabase
   │       │   ├─ Image cached locally
   │       │   ├─ Cross-fade transition (300ms)
   │       │   └─ Display in ImageView
   │       │
   │       └─→ User sees Pizza card with image
   │
   └─→ Layout renders perfectly!
```

## Example Categories

### 1. Pizza Category
```
Database Entry:
{
  "id": 1,
  "name": "Pizza",
  "description": "Delicious pizzas",
  "imageUrl": "pizza.png",
  "isActive": true,
  "createdAt": "2025-01-01T00:00:00"
}

Supabase Storage:
Categories/pizza.png (uploaded file)

Public URL:
https://wnsebtlndonfskwbhjfb.supabase.co/storage/v1/object/public/Categories/pizza.png

Display:
┌──────────────┐
│ [Pizza Image]│
│ ──────────── │
│    Pizza     │
└──────────────┘
```

### 2. Burgers Category
```
Database Entry:
{
  "id": 2,
  "name": "Burgers",
  "description": "Juicy burgers",
  "imageUrl": "burgers.jpg",
  "isActive": true
}

Display:
┌──────────────┐
│[Burger Image]│
│ ──────────── │
│   Burgers    │
└──────────────┘
```

## Image Loading Sequence

```
Timeline:
T+0ms   : User opens Menu Activity
T+100ms : Query sent to Supabase for categories
T+200ms : Categories data received (with imageUrl)
T+300ms : CategoryAdapter.onBindViewHolder() called
T+350ms : ImageUtil.getCategoryImageUrl() builds URL
T+400ms : Glide.load(url) initiates network request
T+800ms : Image downloaded from Supabase
T+900ms : Image cached locally
T+950ms : Cross-fade transition starts (300ms duration)
T+1250ms: Image fully displayed on screen

Total Time: ~1.2 seconds to show image
(Much faster on second load due to caching)
```

## Error Handling

```
If imageUrl is NULL:
   └─ Use ic_food_banner placeholder
   
If imageUrl is empty string:
   └─ Use ic_food_banner placeholder
   
If image file doesn't exist in Supabase:
   └─ Show ic_food_banner placeholder
   
If network error during download:
   └─ Show ic_food_banner placeholder
   
If Glide encounters exception:
   └─ Catch exception and show ic_food_banner
```

## Performance Characteristics

| Metric | Value |
|--------|-------|
| **Memory Usage** | ~5-10MB per image (with Glide caching) |
| **Network** | One request per unique image |
| **Disk Cache** | Automatic (Glide manages) |
| **Load Time** | ~500-800ms (first load), 50-100ms (cached) |
| **Transition** | 300ms cross-fade |

## Browser View - Supabase Storage

When you visit the Categories bucket at:
`https://supabase.com/dashboard/project/wnsebtlndonfskwbhjfb/storage/files/buckets/Categories`

You'll see:
```
Categories Bucket
├─ File Management
│  ├─ Upload file (button)
│  ├─ Create folder (button)
│  └─ Files list:
│     ├─ burgers.jpg         2.4 MB   Created 2025-12-05
│     ├─ pizza.png           1.8 MB   Created 2025-12-05
│     ├─ pasta.jpg           2.1 MB   Created 2025-12-05
│     ├─ chicken.jpg         2.0 MB   Created 2025-12-05
│     ├─ desserts.png        1.5 MB   Created 2025-12-05
│     └─ beverages.jpg       1.9 MB   Created 2025-12-05
```

Each file can be clicked to get its public URL.

## Quick Visual Reference

### Before (Without Images)
```
┌──────────────┐  ┌──────────────┐
│              │  │              │
│  [Icon]      │  │  [Icon]      │
│              │  │              │
│   Pizza      │  │   Burgers    │
└──────────────┘  └──────────────┘
```

### After (With Images)
```
┌──────────────┐  ┌──────────────┐
│              │  │              │
│  [🍕 Photo]  │  │  [🍔 Photo]  │
│              │  │              │
│   Pizza      │  │   Burgers    │
└──────────────┘  └──────────────┘
```

Much more attractive and professional! ✨

---

**Note**: All images must be uploaded to Supabase Storage Categories bucket for this to work. See CATEGORY_PHOTOS_CHECKLIST.md for setup instructions.
