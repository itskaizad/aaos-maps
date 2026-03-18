# AI Agent Instructions for AAOS Maps

AI agents working on this project MUST follow these instructions.

## Project Context

This is a lightweight maps app for Android Automotive OS (AAOS). It replaces the stock `CarMapsPlaceholder` app that shows a green "No maps application installed" message in the car launcher's maps panel.

The app uses osmdroid (OpenStreetMap native tiles) with a dark color matrix filter and a red arrow vehicle marker. It embeds inside the car launcher's `TaskView` — it does NOT launch fullscreen.

## Target Device

- AAOS device running as user 10 (the automotive user profile)
- userdebug/eng build with `adb root` and `adb remount` access
- WiFi connected for map tile loading
- No GPS hardware — relies on mock location providers for location updates
- Car launcher is `com.android.car.carlauncher`

## Critical Manifest Requirements

The activity MUST have these attributes or the launcher embedding will break:

- `android:allowEmbedded="true"` — required for the launcher's TaskView to host the activity
- `android:resizeableActivity="true"` — required for multi-window mode in the launcher panel
- `android:launchMode="singleTask"` — prevents duplicate instances

The activity MUST register these intent filters:

- `android.intent.action.MAIN` with `android.intent.category.APP_MAPS` and `android.intent.category.DEFAULT` — this is how the car launcher discovers the maps app
- `android.intent.action.VIEW` with scheme `geo:` — standard maps intent
- `android.intent.action.VIEW` with scheme `google.navigation:` — navigation intent compatibility

## Installation Requirements

- APK goes in `/system/priv-app/Maps/Maps.apk` (NOT `/system/app/`)
- The stock `CarMapsPlaceholder` at `/system/app/CarMapsPlaceholder/` must be removed
- Location permissions must be explicitly granted via `adb shell pm grant` after reboot — the default-permissions XML alone is not sufficient on this device
- A default-permissions XML should be placed in `/product/etc/default-permissions/` for future compatibility
- Mock location providers (gps, network) should be set up via `adb shell cmd location providers add-test-provider`

## Map Implementation

- Uses `org.osmdroid:osmdroid-android:6.1.17`
- Dark theme is achieved via a ColorMatrixColorFilter on the tile overlay (0.3 multiplier on RGB channels) — same approach as the AlexaAutoLauncher project in `../AlexaAutoLauncher`
- Vehicle marker uses a red arrow vector drawable (`ic_arrow.xml`)
- Default location is San Francisco (37.7749, -122.4194) at zoom level 20
- When a location update arrives (including from mock providers), the map animates to the new position and moves the marker
- The app suppresses the `MissingPermission` lint warning since permissions are pre-granted as a system app

## Build

- Gradle-based Android project, `./gradlew assembleDebug`
- minSdk 28, targetSdk 34, compileSdk 34
- Dependencies: `androidx.appcompat:appcompat:1.6.1` and `org.osmdroid:osmdroid-android:6.1.17`

## Testing Mock Locations

```bash
adb shell cmd location providers set-test-provider-location gps --location <LAT>,<LON>
```

The `onLocationChanged` callback fires for mock provider updates. The marker and map center will animate to the new coordinates.

## Common Issues

- **Black/empty map panel**: Device has no internet — osmdroid needs to download tiles from `tile.openstreetmap.org`
- **App launches fullscreen instead of in panel**: Missing `android:allowEmbedded="true"` in manifest
- **Green placeholder still showing**: `CarMapsPlaceholder` was not removed, or the `APP_MAPS` category intent filter is missing the `DEFAULT` category
- **No marker visible**: No location provider available and no mock location injected — the marker shows at the default SF location, ensure zoom level is high enough to see it
- **Permission dialog appears**: Location permissions not pre-granted via `pm grant` — run `install.sh` or grant manually
- **Tiles fail to load (UnknownHostException)**: DNS not working for the app's user context — verify WiFi is connected and the app has INTERNET permission granted
