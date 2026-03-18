# AAOS Maps

A lightweight maps app for Android Automotive OS that replaces the default "No maps application installed" placeholder in the car launcher.

Uses [OpenStreetMap](https://www.openstreetmap.org/) tiles via [osmdroid](https://github.com/osmdroid/osmdroid) with a dark theme, and displays a vehicle location marker.

## Features

- Embeds directly in the AAOS car launcher maps panel (no fullscreen takeover)
- Dark-themed OpenStreetMap tiles
- Red arrow vehicle marker with live location updates
- Search bar with coordinate and place name input
- Handles `geo:` and `google.navigation:` intents
- Supports mock location providers for testing/demo
- Installs as a privileged system app with pre-granted permissions

## Requirements

- AAOS device or emulator with ADB access
- `adb root` and `adb remount` must work (userdebug/eng build)
- Device must have internet access for map tile loading
- JDK 17+ and Android SDK (for building)

## Quick Install

```bash
chmod +x install.sh
./install.sh
```

This will:
1. Build the APK (if not already built)
2. Remove the `CarMapsPlaceholder` app
3. Install the Maps app to `/system/priv-app/`
4. Set up default permissions for location access
5. Reboot and grant permissions
6. Set up mock location providers

## Manual Build

```bash
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Mock Locations

Since most AAOS devices don't have GPS hardware, you can inject mock locations:

```bash
# Inject a location (Paris)
adb shell cmd location providers set-test-provider-location gps --location 48.8566,2.3522

# Inject a location (San Francisco)
adb shell cmd location providers set-test-provider-location gps --location 37.7749,-122.4194

# Inject a location (Tokyo)
adb shell cmd location providers set-test-provider-location gps --location 35.6762,139.6503
```

The map will animate to the new location and the marker will move.

## How It Works

The AAOS car launcher (`com.android.car.carlauncher`) has a maps panel that looks for an app registered with `android.intent.category.APP_MAPS`. The stock `CarMapsPlaceholder` app shows the green "No maps application installed" message.

This app registers for that category and declares `android:allowEmbedded="true"` so the launcher can host it inside its `TaskView` rather than launching it fullscreen.

## Default Location

When no GPS fix is available, the map defaults to San Francisco (37.7749, -122.4194) at zoom level 20.

## AI Agents

All AI agents working on this project must read and follow the instructions in [agents.md](agents.md).
