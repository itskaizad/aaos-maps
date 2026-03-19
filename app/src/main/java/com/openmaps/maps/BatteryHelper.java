package com.openmaps.maps;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import androidx.annotation.NonNull;

/**
 * Reads the device battery level and charging state.
 * Used to display a battery indicator overlay on the map.
 */
public class BatteryHelper {

    private static final int LOW_BATTERY_THRESHOLD = 40;

    private final Context context;

    public BatteryHelper(@NonNull Context context) {
        this.context = context;
    }

    /** Returns a formatted string like "85% 🔋" or "62% ⚡". */
    @NonNull
    public String getFormattedLevel() {
        BatteryManager manager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        int level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);

        Intent batteryStatus = context.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int status = batteryStatus != null
                ? batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                : -1;
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;

        String icon = charging ? "⚡" : (level >= LOW_BATTERY_THRESHOLD ? "🔋" : "🪫");
        return level + "% " + icon;
    }
}
