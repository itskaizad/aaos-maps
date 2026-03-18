#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APK="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
DEST="/system/priv-app/Maps/Maps.apk"
PLACEHOLDER="/system/app/CarMapsPlaceholder"
DEFAULT_PERMS="/product/etc/default-permissions/default-permissions-com.example.aaos.maps.xml"
PKG="com.example.aaos.maps"

echo "=== AAOS Maps Installer ==="

# Check for connected device
if ! adb devices | grep -q "device$"; then
    echo "ERROR: No ADB device connected"
    exit 1
fi

# Build if APK doesn't exist
if [ ! -f "$APK" ]; then
    echo "Building APK..."
    cd "$SCRIPT_DIR"
    ./gradlew assembleDebug
fi

echo "Preparing device..."
adb root
adb remount

echo "Removing CarMapsPlaceholder..."
adb shell rm -rf "$PLACEHOLDER"

echo "Installing Maps to priv-app..."
adb shell mkdir -p /system/priv-app/Maps
adb push "$APK" "$DEST"
adb shell chmod 644 "$DEST"

echo "Creating default permissions..."
adb shell "cat > $DEFAULT_PERMS" << 'EOF'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<exceptions>
    <exception package="com.example.aaos.maps">
        <permission name="android.permission.ACCESS_FINE_LOCATION" fixed="true"/>
        <permission name="android.permission.ACCESS_COARSE_LOCATION" fixed="true"/>
    </exception>
</exceptions>
EOF

echo "Rebooting..."
adb reboot
adb wait-for-device shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 2; done'
echo "Device booted."

echo "Granting location permissions..."
adb shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION
adb shell pm grant "$PKG" android.permission.ACCESS_COARSE_LOCATION

echo "Setting up mock location provider..."
adb shell cmd location set-location-enabled true --user 10
adb shell cmd location providers add-test-provider gps 2>/dev/null || true
adb shell cmd location providers set-test-provider-enabled gps true 2>/dev/null || true
adb shell cmd location providers add-test-provider network 2>/dev/null || true
adb shell cmd location providers set-test-provider-enabled network true 2>/dev/null || true

echo ""
echo "=== Installation complete ==="
echo "The Maps app should now be visible in the car launcher."
echo ""
echo "To inject a mock location:"
echo "  adb shell cmd location providers set-test-provider-location gps --location <LAT>,<LON>"
echo ""
echo "Example (Paris):"
echo "  adb shell cmd location providers set-test-provider-location gps --location 48.8566,2.3522"
