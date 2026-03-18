package com.example.aaos.maps;

import android.app.Activity;
import android.content.Context;
import android.graphics.ColorMatrixColorFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

public class MapsActivity extends Activity implements LocationListener {

    private MapView mapView;
    private Marker vehicleMarker;
    private EditText searchBox;
    private ImageView searchAction;
    private static final GeoPoint DEFAULT_LOCATION = new GeoPoint(37.7749, -122.4194);

    private static final float[] DARK_MATRIX = {
        0.3f, 0.3f, 0.3f, 0, 0,
        0.3f, 0.3f, 0.3f, 0, 0,
        0.3f, 0.3f, 0.3f, 0, 0,
        0,    0,    0,    1, 0
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_maps);

        mapView = findViewById(R.id.map);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getOverlayManager().getTilesOverlay()
                .setColorFilter(new ColorMatrixColorFilter(DARK_MATRIX));

        // Dismiss keyboard when tapping the map
        mapView.setOnTouchListener((v, event) -> {
            dismissKeyboard();
            return false;
        });

        setupSearch();

        GeoPoint intentLocation = parseGeoIntent();
        GeoPoint startPoint = intentLocation != null ? intentLocation : DEFAULT_LOCATION;

        addMarker(startPoint);
        mapView.getController().setZoom(20.0);
        mapView.getController().setCenter(startPoint);

        startLocationUpdates();
    }

    private void setupSearch() {
        searchBox = findViewById(R.id.search_box);
        searchAction = findViewById(R.id.search_action);

        // Show/hide the enter arrow based on text content
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                searchAction.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }
        });

        // Handle keyboard search action
        searchBox.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(searchBox.getText().toString().trim());
                return true;
            }
            return false;
        });

        // Handle tap on enter arrow
        searchAction.setOnClickListener(v ->
                performSearch(searchBox.getText().toString().trim()));
    }

    private void performSearch(String query) {
        if (query.isEmpty()) return;
        dismissKeyboard();
        searchBox.clearFocus();

        // Try parsing as "lat,lon"
        try {
            String[] parts = query.split(",");
            if (parts.length == 2) {
                double lat = Double.parseDouble(parts[0].trim());
                double lon = Double.parseDouble(parts[1].trim());
                GeoPoint point = new GeoPoint(lat, lon);
                vehicleMarker.setPosition(point);
                mapView.getController().animateTo(point, 18.0, 1500L);
                mapView.invalidate();
                return;
            }
        } catch (Exception ignored) {}

        // Fall back to OSM Nominatim search via intent
        try {
            android.content.Intent intent = new android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    Uri.parse("geo:0,0?q=" + Uri.encode(query)));
            intent.setPackage(getPackageName());
            startActivity(intent);
        } catch (Exception ignored) {}
    }

    private void dismissKeyboard() {
        if (searchBox != null && searchBox.hasFocus()) {
            searchBox.clearFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(searchBox.getWindowToken(), 0);
        }
    }

    private GeoPoint parseGeoIntent() {
        if (getIntent() == null || getIntent().getData() == null) return null;
        String ssp = getIntent().getData().getSchemeSpecificPart();
        if (ssp == null) return null;
        String[] parts = ssp.split("\\?")[0].split(",");
        try {
            double lat = Double.parseDouble(parts[0]);
            double lon = Double.parseDouble(parts[1]);
            if (lat != 0 || lon != 0) return new GeoPoint(lat, lon);
        } catch (Exception ignored) {}
        return null;
    }

    private void addMarker(GeoPoint point) {
        vehicleMarker = new Marker(mapView);
        vehicleMarker.setPosition(point);
        vehicleMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        vehicleMarker.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_arrow));
        vehicleMarker.setInfoWindow(null);
        mapView.getOverlays().add(vehicleMarker);
    }

    @SuppressWarnings("MissingPermission")
    private void startLocationUpdates() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        try {
            for (String p : new String[]{LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER}) {
                try {
                    if (lm.getProvider(p) != null) {
                        lm.requestLocationUpdates(p, 2000, 5, this);
                        Location last = lm.getLastKnownLocation(p);
                        if (last != null) onLocationChanged(last);
                    }
                } catch (Exception ignored) {}
            }
        } catch (SecurityException ignored) {}
    }

    @Override
    public void onLocationChanged(Location location) {
        GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());
        android.util.Log.d("MapsActivity", "Location update: " + location.getLatitude() + "," + location.getLongitude() + " provider=" + location.getProvider());
        mapView.post(() -> {
            vehicleMarker.setPosition(point);
            mapView.getController().animateTo(point, 20.0, 1500L);
            mapView.invalidate();
        });
    }

    @Override protected void onResume() { super.onResume(); mapView.onResume(); }
    @Override protected void onPause() { super.onPause(); mapView.onPause(); }
}
