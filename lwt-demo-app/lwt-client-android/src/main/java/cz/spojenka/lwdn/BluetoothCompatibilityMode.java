package cz.spojenka.lwdn;

import android.content.Context;
import android.content.SharedPreferences;

public class BluetoothCompatibilityMode {

    private static final String PK_FORCE_SOFTWARE_FILTERING = "force_software_filtering";

    private static BluetoothCompatibilityMode INSTANCE;

    private final SharedPreferences prefs;

    private BluetoothCompatibilityMode(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("bluetooth_compatibility_mode", Context.MODE_PRIVATE);
    }

    public static BluetoothCompatibilityMode getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = new BluetoothCompatibilityMode(context);
        }
        return INSTANCE;
    }

    public boolean isForcedSoftwareFiltering() {
        return prefs.getBoolean(PK_FORCE_SOFTWARE_FILTERING, false);
    }

    public void setForcedSoftwareFiltering(boolean forced) {
        prefs.edit().putBoolean(PK_FORCE_SOFTWARE_FILTERING, forced).apply();
    }
}
