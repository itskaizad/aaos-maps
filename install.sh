#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APK="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
DEST="/system/priv-app/Maps/Maps.apk"
PLACEHOLDER="/system/app/CarMapsPlaceholder"
DEFAULT_PERMS="/product/etc/default-permissions/default-permissions-com.openmaps.maps.xml"
PKG="com.openmaps.maps"

echo "=== AAOS Maps Installer ==="

# Check for connected device
if ! adb devices | grep -q "device$"; then
    echo "ERROR: No ADB device connected"
    exit 1
fi

# Always build to ensure latest code
echo "Building APK..."
cd "$SCRIPT_DIR"
./gradlew assembleDebug

if [ ! -f "$APK" ]; then
    echo "ERROR: Build failed - APK not found at $APK"
    exit 1
fi

echo "Preparing device..."
adb root
sleep 3
REMOUNT_OUT=$(adb remount 2>&1)
echo "$REMOUNT_OUT"

# If remount says reboot needed, do it and remount again
if echo "$REMOUNT_OUT" | grep -qi "reboot"; then
    echo "Rebooting for remount..."
    adb reboot
    sleep 5
    for i in 1 2 3; do
        adb wait-for-device 2>/dev/null && break
        sleep 3
    done
    adb wait-for-device shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 2; done'
    adb root
    sleep 3
    adb remount
fi

echo "Removing CarMapsPlaceholder..."
adb shell rm -rf "$PLACEHOLDER" 2>/dev/null || true

echo "Installing Maps to priv-app..."
adb shell mkdir -p /system/priv-app/Maps
adb push "$APK" "$DEST"
adb shell chmod 644 "$DEST"

echo "Creating default permissions..."
adb shell "cat > $DEFAULT_PERMS" << 'EOF'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<exceptions>
    <exception package="com.openmaps.maps">
        <permission name="android.permission.ACCESS_FINE_LOCATION" fixed="true"/>
        <permission name="android.permission.ACCESS_COARSE_LOCATION" fixed="true"/>
    </exception>
</exceptions>
EOF

echo "Rebooting to apply..."
adb reboot
sleep 5
# Retry connection in case adb daemon restarts during reboot
for i in 1 2 3; do
    adb wait-for-device 2>/dev/null && break
    sleep 3
done
adb wait-for-device shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 2; done'
echo "Device booted."

# Re-root after reboot
adb root
sleep 3

echo "Granting location permissions..."
adb shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION 2>/dev/null || true
adb shell pm grant "$PKG" android.permission.ACCESS_COARSE_LOCATION 2>/dev/null || true

echo "Setting up mock location provider..."
# Try both with and without --user flag for compatibility
adb shell cmd location set-location-enabled true --user 10 2>/dev/null || \
    adb shell cmd location set-location-enabled true 2>/dev/null || true
adb shell cmd location providers add-test-provider gps 2>/dev/null || true
adb shell cmd location providers set-test-provider-enabled gps true 2>/dev/null || true
adb shell cmd location providers add-test-provider network 2>/dev/null || true
adb shell cmd location providers set-test-provider-enabled network true 2>/dev/null || true

# Inject default location (San Francisco)
adb shell cmd location providers set-test-provider-location gps --location 37.7749,-122.4194 2>/dev/null || true

echo ""
echo "=== Installation complete ==="
echo "The Maps app should now be visible in the car launcher."
echo ""
echo "To inject a mock location:"
echo "  adb shell cmd location providers set-test-provider-location gps --location <LAT>,<LON>"
echo ""
echo "Examples:"
echo "  adb shell cmd location providers set-test-provider-location gps --location 48.8566,2.3522    # Paris"
echo "  adb shell cmd location providers set-test-provider-location gps --location 35.6762,139.6503  # Tokyo"
