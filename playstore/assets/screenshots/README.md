# Play Store Screenshot Capture Guide

## Requirements

Google Play requires minimum 2 screenshots per device type.
Recommended: 8 screenshots for each form factor (phone, tablet).

### Phone Screenshots
- Minimum shortest side: 320px
- Maximum shortest side: 3840px
- Aspect ratio: between 16:9 and 9:16
- Format: PNG or JPEG, no transparency

### Tablet Screenshots
- 7-inch: min 320px shortest side
- 10-inch: min 320px shortest side
- Same format requirements

## How to Capture Screenshots

### Method 1: From Android Emulator
1. Start the emulator: `emulator -avd Medium_Phone_API_36.1`
2. Open the app and navigate to the desired screen
3. Click the camera icon in the emulator toolbar
4. Save to this directory

### Method 2: From ADB
```
adb exec-out screencap -p > screenshot.png
```

### Method 3: From Android Studio
1. Open Logcat / Device File Explorer
2. Use the screenshot button

## Screenshots to Capture

### 1. Library Screen (book grid)
- Pre-populate the library with at least 9 books
- Show the grid view with covers

### 2. Reader Screen (EPUB open)
- Open a book with visible text
- Show the clean reading interface

### 3. TTS Active / Notification
- Show the TTS notification with controls
- Or the in-app TTS controls overlay

### 4. Statistics Dashboard
- Populate with sample reading data
- Show charts/graphs

### 5. Settings Screen
- Scroll to show multiple sections

### 6. Collections / Bookshelves
- Create a few collections
- Show the collections view

### 7. Highlights and Notes
- Show a page with highlights visible
- Or the highlights list view

### 8. Cloud Sync / OPDS Browser
- Show the OPDS source list
- Or the sync settings

## Data Generation Script

Use the Python script at `playstore/scripts/populate_test_data.py` to
generate sample books and reading data for screenshot purposes.

## Naming Convention

```
phoneScreenshots/
  screenshot_01_library.png
  screenshot_02_reader.png
  screenshot_03_tts.png
  screenshot_04_stats.png
  screenshot_05_settings.png
  screenshot_06_collections.png
  screenshot_07_highlights.png
  screenshot_08_sync.png

tenInchScreenshots/
  (same naming, but on 10-inch tablet)
```

## Notes

- No device frames needed (Google adds them automatically)
- Don't include status bar screenshots with promotional text
- Don't include Android navigation bar overlays (Google strips them)
- Keep text legible at small sizes
- Avoid showing real personal data in screenshots
