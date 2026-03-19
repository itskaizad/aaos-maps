# AI Agent Instructions for AAOS Maps

AI agents working on this project MUST follow these instructions.

## Agent Workflow Rules

1. **Ask before committing or pushing.** Never run `git commit` or `git push` without explicit user approval.
2. **Clean up failed attempts.** If a proposed solution doesn't work, revert all related changes before trying a different approach. Don't leave dead code, debug logging, or experimental workarounds in the codebase.
3. **Follow best practices.** Write code as a senior engineer would — extract constants, use descriptive names, add annotations, document non-obvious decisions, and keep lint clean.
4. **Build and verify before declaring done.** Always run `./gradlew assembleDebug` and confirm the build succeeds before telling the user a change is ready.
5. **Test on device when possible.** If an adb device is connected, push the APK and verify the change works. Don't assume it works just because it compiles.
6. **Keep changes minimal.** Only modify what's necessary to address the current task. Don't refactor unrelated code unless asked.
7. **Read this file and README.md first.** Before making any changes, understand the project context, constraints, and known issues documented here.

## Project Context

This is a lightweight maps app for Android Automotive OS (AAOS). It replaces the stock `CarMapsPlaceholder` app that shows a green "No maps application installed" message in the car launcher's maps panel.

The app uses osmdroid (OpenStreetMap native tiles) with a dark color matrix filter, a red arrow vehicle marker, a search bar (top-left), and a battery indicator (top-right). It embeds inside the car launcher's `TaskView` — it does NOT launch fullscreen.

Package name: `com.openmaps.maps`

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
- `android:supportsRtl="true"` — on the application tag

The activity MUST register these intent filters:

- `android.intent.action.MAIN` with `android.intent.category.APP_MAPS` and `android.intent.category.DEFAULT` — this is how the car launcher discovers the maps app
- `android.intent.action.VIEW` with scheme `geo:` — standard maps intent
- `android.intent.action.VIEW` with scheme `google.navigation:` — navigation intent compatibility

The activity must NOT have `android.intent.category.LAUNCHER` — the app is hidden from the app drawer and only appears in the launcher's maps panel.

## Network Requirements

The manifest MUST include:

- `android.permission.INTERNET` — for tile downloads
- `android.permission.ACCESS_NETWORK_STATE` — osmdroid checks this before attempting downloads; without it, tiles will silently fail to load
- `android:usesCleartextTraffic="true"` — on the application tag
- `android:networkSecurityConfig="@xml/network_security_config"` — references `res/xml/network_security_config.xml` allowing cleartext and system trust anchors

## Osmdroid Configuration

The osmdroid tile cache MUST be explicitly configured to the app's private directories. On AAOS multi-user setups, the default paths may not be writable, causing silent tile download failures:

```java
IConfigurationProvider config = Configuration.getInstance();
config.setUserAgentValue(getPackageName());
config.setOsmdroidBasePath(getFilesDir());
config.setOsmdroidTileCache(new File(getCacheDir(), "osmdroid"));
```

Additionally, `mapView.setUseDataConnection(true)` must be called explicitly.

## Installation Requirements

- APK goes in `/system/priv-app/Maps/Maps.apk` (NOT `/system/app/`)
- The stock `CarMapsPlaceholder` at `/system/app/CarMapsPlaceholder/` must be removed
- Location permissions must be explicitly granted via `adb shell pm grant` after reboot — the default-permissions XML alone is not sufficient on this device
- A default-permissions XML should be placed in `/product/etc/default-permissions/default-permissions-com.openmaps.maps.xml`
- Mock location providers (gps, network) should be set up via `adb shell cmd location providers add-test-provider`
- Use `install.sh` for automated installation

## Map Implementation

- Uses `org.osmdroid:osmdroid-android:6.1.17`
- Dark theme via `ColorMatrixColorFilter` on the tile overlay (0.3 multiplier on RGB channels) — same approach as `../AlexaAutoLauncher`
- Vehicle marker uses a red arrow vector drawable (`ic_arrow.xml`)
- Default location: San Francisco (37.7749, -122.4194)
- Initial zoom: 19.5, animates to 20.0 on every `onResume` (each time the launcher shows the map)
- Search bar: top-left, pill-shaped, 420dp wide, supports `lat,lon` coordinate input
- Battery indicator: top-right, pill-shaped, refreshes every 60 seconds, shows level + charging emoji
- Both overlays use `@drawable/search_bg` (solid `#1A1A2E` fill, white 2dp border, 40dp corner radius)

## Code Style

- Constants extracted with descriptive names (no magic numbers)
- Methods organized into sections: Initialization, Search, Location, Intent Parsing, Utilities
- `@NonNull`/`@Nullable` annotations on all applicable parameters and return types
- Javadoc on classes and non-obvious methods
- Zero lint warnings — suppressions documented in `build.gradle` with rationale

## Build

- Gradle-based Android project, `./gradlew assembleDebug`
- minSdk 28, targetSdk 34, compileSdk 34
- Dependencies: `androidx.appcompat:appcompat:1.6.1` and `org.osmdroid:osmdroid-android:6.1.17`
- Do NOT upgrade appcompat beyond 1.6.1 — causes duplicate class conflicts with the current Gradle setup

## Testing Mock Locations

```bash
adb shell cmd location providers set-test-provider-location gps --location <LAT>,<LON>
```

The `onLocationChanged` callback fires for mock provider updates. The marker and map center will animate to the new coordinates.

## Common Issues

- **Blank grid / no tiles**: Missing `ACCESS_NETWORK_STATE` permission, or osmdroid cache paths not explicitly set, or no internet connectivity. All three are required.
- **App launches fullscreen instead of in panel**: Missing `android:allowEmbedded="true"` in manifest
- **Green placeholder still showing**: `CarMapsPlaceholder` was not removed, or the `APP_MAPS` category intent filter is missing the `DEFAULT` category
- **Tiles load only after touch interaction**: The `OnGlobalLayoutListener` must properly remove itself (pass `this`, not a new lambda) to avoid repeated firing that blocks the UI thread
- **Map jittery / unresponsive to touch**: Layout listener firing repeatedly — see above
- **Permission dialog appears**: Location permissions not pre-granted via `pm grant` — run `install.sh` or grant manually
- **install.sh fails during reboot**: adb daemon may restart — the script includes retry logic with `sleep 5` before reconnecting

## Git

- Repository: `git@github.com:itskaizad/aaos-maps.git`
- Branch: `main`
