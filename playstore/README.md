# Play Store Listing — Mimiral

This directory contains all assets and metadata needed for the Google Play Store listing.

## Directory Structure

```
playstore/
├── listings/
│   └── en-US/
│       └── store-listing.md      # Complete listing copy + metadata
├── assets/
│   ├── icon/
│   │   └── playstore_icon_512.png    # 512x512 Play Store icon
│   ├── feature_graphic.png           # 1024x500 feature graphic
│   └── screenshots/
│       ├── README.md                 # Screenshot capture guide
│       └── library_screen.png        # Mock screenshot (replace with real)
└── scripts/
    └── generate_playstore_assets.py  # Asset generation script

fastlane/
├── Appfile                           # Package name + API key config
├── Fastfile                          # Deployment lanes
└── metadata/android/en-US/
    ├── title.txt                     # App name
    ├── short_description.txt         # 80-char short description
    ├── full_description.txt          # 4000-char full description
    ├── category.txt                  # App category
    ├── contact_email.txt             # Support email (TODO: fill in)
    ├── contact_website.txt           # Website URL
    ├── privacy_policy.txt            # Privacy policy URL (TODO: fill in)
    └── images/
        ├── icon/icon.png             # 512x512 icon
        ├── featureGraphic.png        # 1024x500 feature graphic
        └── phoneScreenshots/
            └── screenshot_01_library.png  # Mock (replace with real)
```

## Before Publishing — TODO

1. **Replace placeholder contact info** in:
   - `fastlane/metadata/android/en-US/contact_email.txt`
   - `fastlane/metadata/android/en-US/privacy_policy.txt`
   - `playstore/listings/en-US/store-listing.md`

2. **Capture real screenshots** — See `playstore/assets/screenshots/README.md`
   - Need minimum 2, recommended 8 phone screenshots
   - Need tablet screenshots (7-inch and 10-inch) for better visibility

3. **Create privacy policy** — Required by Google Play
   - Host on GitHub Pages or similar
   - URL goes in `privacy_policy.txt`

4. **Set up Google Play Console**
   - Create app entry
   - Complete content rating questionnaire
   - Set up merchant account (if paid/IAP)

5. **Generate signed release AAB**
   - Create signing key (if not exists)
   - Build: `./gradlew bundleRelease`
   - Output: `app/build/outputs/bundle/release/app-release.aab`

6. **Optional: Set up Fastlane**
   - Create Google Play service account
   - Download JSON key to `fastlane/play-store-key.json`
   - Uncomment `json_key_file` in `Appfile`
   - Run `fastlane deploy`

## Version Info

- Current version: 1.0.0 (versionCode: 2)
- Min SDK: 31 (Android 12)
- Target SDK: 34 (Android 14)
- Package: com.mimiral.app
