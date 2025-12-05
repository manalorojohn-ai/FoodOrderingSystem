# Google Maps Setup Guide

## Issue: Map Not Displaying

If you see a blank map with only the Google logo, it means the Google Maps API key is not properly configured.

## Steps to Fix:

### 1. Get a Google Maps API Key (FREE)

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Enable the following APIs:
   - **Maps SDK for Android** (Required)
   - **Geocoding API** (Optional, for address conversion)
4. Go to "Credentials" → "Create Credentials" → "API Key"
5. Copy your API key

### 2. Add API Key to Project

1. Open the `local.properties` file in the root of your project
2. Add this line (replace `YOUR_API_KEY_HERE` with your actual key):
   ```
   MAPS_API_KEY=YOUR_API_KEY_HERE
   ```
3. Save the file

### 3. Restart Android Studio

After adding the API key, restart Android Studio and rebuild the project.

### 4. Verify Setup

- The map should now display properly
- You should be able to see the map tiles
- Location selection should work

## Note

The Google Maps API has a free tier that includes:
- $200 free credit per month
- Enough for most small to medium apps
- No credit card required for the free tier

## Troubleshooting

If the map still doesn't show:
1. Check that `MAPS_API_KEY` is correctly set in `local.properties`
2. Verify the API key is enabled for "Maps SDK for Android"
3. Check Android Studio's Build Output for any error messages
4. Make sure you've restarted Android Studio after adding the key

