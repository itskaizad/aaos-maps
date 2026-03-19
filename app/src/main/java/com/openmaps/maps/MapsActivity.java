package com.openmaps.maps;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.io.File;

/**
 * Main activity for the AAOS Maps application. Displays an OpenStreetMap view with a dark theme
 * inside the car launcher's maps panel via {@code android:allowEmbedded="true"}.
 *
 * <p>Supports location updates from GPS, network, and fused providers (including mock providers),
 * coordinate-based search, and {@code geo:} / {@code google.navigation:} intent handling.</p>
 */
public class MapsActivity extends Activity implements LocationListener {

    private static final String TAG = "MapsActivity";

    private static final GeoPoint DEFAULT_LOCATION = new GeoPoint(37.7749, -122.4194);
    private static final double INITIAL_ZOOM = 19.5;
    private static final double TARGET_ZOOM = 20.0;
    private static final double SEARCH_ZOOM = 18.0;
    private static final long ZOOM_ANIMATION_MS = 1500L;
    private static final long ZOOM_DELAY_MS = 200L;
    private static final long LOCATION_MIN_TIME_MS = 2000L;
    private static final float LOCATION_MIN_DISTANCE_M = 5f;
    private static final long BATTERY_REFRESH_INTERVAL_MS = 60_000L;
    private static final long RECENTER_DELAY_MS = 10_000L;

    /** Desaturation matrix applied to map tiles for a dark automotive theme. */
    private static final float[] DARK_TILE_COLOR_MATRIX = {
        0.2f, 0.2f, 0.2f, 0, 0,
        0.2f, 0.2f, 0.2f, 0, 0,
        0.2f, 0.2f, 0.2f, 0, 0,
        0,    0,    0,    1, 0
    };

    private static final String[] LOCATION_PROVIDERS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ? new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER}
            : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER};

    private MapView mapView;
    private Marker vehicleMarker;
    private EditText searchInput;
    private ImageView searchSubmitButton;
    private TextView batteryIndicator;
    private BatteryHelper batteryHelper;
    private final Runnable recenterRunnable = this::recenterIfNeeded;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureOsmdroid();
        setContentView(R.layout.activity_maps);

        initializeMap();
        initializeSearchBar();
        initializeBatteryIndicator();

        GeoPoint startPoint = resolveStartLocation();
        initializeVehicleMarker(startPoint);
        mapView.getController().setZoom(INITIAL_ZOOM);
        mapView.getController().setCenter(startPoint);

        // Apply UI offset once the view has dimensions
        mapView.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                mapView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                mapView.getController().setCenter(offsetForUI(vehicleMarker.getPosition()));
            }
        });

        requestLocationUpdates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        mapView.getController().setZoom(INITIAL_ZOOM);
        mapView.postDelayed(() ->
            mapView.getController().animateTo(mapView.getMapCenter(), TARGET_ZOOM, ZOOM_ANIMATION_MS),
            ZOOM_DELAY_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());
        Log.d(TAG, "Location: " + location.getLatitude() + "," + location.getLongitude()
                + " [" + location.getProvider() + "]");
        mapView.post(() -> {
            vehicleMarker.setPosition(point);
            mapView.getController().animateTo(offsetForUI(point), TARGET_ZOOM, ZOOM_ANIMATION_MS);
            mapView.invalidate();
        });
    }

    // ── Initialization ──────────────────────────────────────────────────────────

    private void configureOsmdroid() {
        IConfigurationProvider config = Configuration.getInstance();
        config.setUserAgentValue(getPackageName());
        config.setOsmdroidBasePath(getFilesDir());
        config.setOsmdroidTileCache(new File(getCacheDir(), "osmdroid"));
    }

    private void initializeMap() {
        mapView = findViewById(R.id.map);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.setUseDataConnection(true);
        mapView.getOverlayManager().getTilesOverlay()
                .setColorFilter(new ColorMatrixColorFilter(DARK_TILE_COLOR_MATRIX));
        mapView.setOnTouchListener((v, event) -> {
            dismissKeyboard();
            v.performClick();
            scheduleRecenter();
            return false;
        });
    }

    private void initializeSearchBar() {
        searchInput = findViewById(R.id.search_input);
        searchInput.setTypeface(Typeface.defaultFromStyle(Typeface.ITALIC));
        searchSubmitButton = findViewById(R.id.search_submit);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                searchSubmitButton.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                searchInput.setTypeface(Typeface.defaultFromStyle(
                        s.length() > 0 ? Typeface.NORMAL : Typeface.ITALIC));
            }
        });

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                handleSearch(searchInput.getText().toString().trim());
                return true;
            }
            return false;
        });

        searchSubmitButton.setOnClickListener(v ->
                handleSearch(searchInput.getText().toString().trim()));
    }

    private void initializeBatteryIndicator() {
        batteryIndicator = findViewById(R.id.battery_indicator);
        batteryHelper = new BatteryHelper(this);
        refreshBatteryDisplay();

        batteryIndicator.postDelayed(new Runnable() {
            @Override
            public void run() {
                refreshBatteryDisplay();
                batteryIndicator.postDelayed(this, BATTERY_REFRESH_INTERVAL_MS);
            }
        }, BATTERY_REFRESH_INTERVAL_MS);
    }

    private void refreshBatteryDisplay() {
        batteryIndicator.setText(batteryHelper.getFormattedLevel());
    }

    private void initializeVehicleMarker(GeoPoint position) {
        vehicleMarker = new Marker(mapView);
        vehicleMarker.setPosition(position);
        vehicleMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        vehicleMarker.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_arrow));
        vehicleMarker.setInfoWindow(null);
        mapView.getOverlays().add(vehicleMarker);
    }

    // ── Search ──────────────────────────────────────────────────────────────────

    private void handleSearch(String query) {
        if (query.isEmpty()) return;
        dismissKeyboard();
        searchInput.clearFocus();

        GeoPoint coordinates = parseCoordinates(query);
        if (coordinates != null) {
            navigateTo(coordinates, SEARCH_ZOOM);
            return;
        }

        // Fall back to geo intent for place name resolution
        try {
            Intent geoIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("geo:0,0?q=" + Uri.encode(query)));
            geoIntent.setPackage(getPackageName());
            startActivity(geoIntent);
        } catch (Exception e) {
            Log.w(TAG, "Failed to resolve search query: " + query, e);
        }
    }

    private void navigateTo(GeoPoint point, double zoom) {
        vehicleMarker.setPosition(point);
        mapView.getController().animateTo(offsetForUI(point), zoom, ZOOM_ANIMATION_MS);
        mapView.invalidate();
    }

    // ── Location ────────────────────────────────────────────────────────────────

    @SuppressWarnings("MissingPermission")
    private void requestLocationUpdates() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        for (String provider : LOCATION_PROVIDERS) {
            try {
                if (locationManager.getProvider(provider) != null) {
                    locationManager.requestLocationUpdates(
                            provider, LOCATION_MIN_TIME_MS, LOCATION_MIN_DISTANCE_M, this);
                    Location last = locationManager.getLastKnownLocation(provider);
                    if (last != null) onLocationChanged(last);
                }
            } catch (SecurityException e) {
                Log.w(TAG, "No permission for provider: " + provider, e);
            }
        }
    }

    @NonNull
    private GeoPoint resolveStartLocation() {
        GeoPoint fromIntent = parseGeoIntent();
        return fromIntent != null ? fromIntent : DEFAULT_LOCATION;
    }

    // ── Intent Parsing ──────────────────────────────────────────────────────────

    @Nullable
    private GeoPoint parseGeoIntent() {
        if (getIntent() == null || getIntent().getData() == null) return null;
        String ssp = getIntent().getData().getSchemeSpecificPart();
        return ssp != null ? parseCoordinates(ssp.split("\\?")[0]) : null;
    }

    @Nullable
    private static GeoPoint parseCoordinates(String input) {
        try {
            String[] parts = input.split(",");
            if (parts.length == 2) {
                double lat = Double.parseDouble(parts[0].trim());
                double lon = Double.parseDouble(parts[1].trim());
                if (lat != 0 || lon != 0) return new GeoPoint(lat, lon);
            }
        } catch (NumberFormatException ignored) {}
        return null;
    }

    // ── Recenter ─────────────────────────────────────────────────────────────────

    /**
     * Returns a point shifted north so the marker renders in the lower portion
     * of the viewport, keeping it clear of the top UI overlays.
     * Uses a fixed pixel offset converted to latitude at the current zoom.
     */
    private GeoPoint offsetForUI(GeoPoint point) {
        // Shift center up by 25% of the view height in pixels
        int offsetPx = mapView.getHeight() / 6;
        if (offsetPx <= 0) return point;
        org.osmdroid.api.IGeoPoint shifted = mapView.getProjection()
                .fromPixels(mapView.getWidth() / 2, mapView.getHeight() / 2 - offsetPx);
        org.osmdroid.api.IGeoPoint center = mapView.getProjection()
                .fromPixels(mapView.getWidth() / 2, mapView.getHeight() / 2);
        double latOffset = shifted.getLatitude() - center.getLatitude();
        return new GeoPoint(point.getLatitude() + latOffset, point.getLongitude());
    }

    /** Resets the inactivity timer that recenters the map on the vehicle marker. */
    private void scheduleRecenter() {
        mapView.removeCallbacks(recenterRunnable);
        mapView.postDelayed(recenterRunnable, RECENTER_DELAY_MS);
    }

    /** Recenters the map on the marker if it has drifted away. */
    private void recenterIfNeeded() {
        GeoPoint markerPos = vehicleMarker.getPosition();
        GeoPoint center = (GeoPoint) mapView.getMapCenter();
        if (markerPos.distanceToAsDouble(center) > 10) {
            mapView.getController().animateTo(offsetForUI(markerPos), TARGET_ZOOM, ZOOM_ANIMATION_MS);
        }
    }

    // ── Utilities ───────────────────────────────────────────────────────────────

    private void dismissKeyboard() {
        if (searchInput != null && searchInput.hasFocus()) {
            searchInput.clearFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }
    }
}
