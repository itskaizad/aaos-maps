package com.example.aaos.maps;

import android.app.Activity;
import android.content.Context;
import android.graphics.ColorMatrixColorFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.core.content.ContextCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

public class MapsActivity extends Activity implements LocationListener {

    private MapView mapView;
    private Marker vehicleMarker;
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

        mapView = new MapView(this);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getOverlayManager().getTilesOverlay()
                .setColorFilter(new ColorMatrixColorFilter(DARK_MATRIX));
        setContentView(mapView);

        GeoPoint intentLocation = parseGeoIntent();
        GeoPoint startPoint = intentLocation != null ? intentLocation : DEFAULT_LOCATION;
        double zoom = 20.0;

        addMarker(startPoint);
        mapView.getController().setZoom(zoom);
        mapView.getController().setCenter(startPoint);

        startLocationUpdates();
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
            mapView.getController().animateTo(point, 18.0, 1500L);
            mapView.invalidate();
        });
    }

    @Override protected void onResume() { super.onResume(); mapView.onResume(); }
    @Override protected void onPause() { super.onPause(); mapView.onPause(); }
}
