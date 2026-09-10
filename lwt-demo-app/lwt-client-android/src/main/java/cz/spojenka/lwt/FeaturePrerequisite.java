package cz.spojenka.lwt;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.aware.WifiAwareManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import java.util.Set;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import cz.spojenka.lwdn.BluetoothLwdnScanner;
import cz.spojenka.lwdn.HybridLwdnScanner;
import cz.spojenka.lwdn.util.DeviceSpecifics;
import cz.spojenka.lwt.util.PermissionRequestFlow;

public interface FeaturePrerequisite {

    public static final FeaturePrerequisite[] NO_DEPENDENCIES = new FeaturePrerequisite[0];

    public default boolean isApplicable(Context context) {
        return true;
    }

    public default FeaturePrerequisite[] getDependencies() {
        return NO_DEPENDENCIES;
    }

    public default boolean checkDependenciesMet(Context context) {
        for (FeaturePrerequisite dependency : getDependencies()) {
            if (dependency.isApplicable(context) && (!dependency.check(context) || !dependency.checkDependenciesMet(context))) {
                return false;
            }
        }
        return true;
    }

    public boolean check(Context context);

    public default @Nullable PermissionRequestFlow createRemedyFlow(AppCompatActivity activity) {
        return null;
    }

    public static class AlwaysSatisfiedPrerequisite implements FeaturePrerequisite {

        @Override
        public boolean isApplicable(Context context) {
            return false;
        }

        @Override
        public boolean check(Context context) {
            return true;
        }
    }

    public static class AbstractPermissionPrerequisite implements FeaturePrerequisite {

        private final String[] permissions;

        protected AbstractPermissionPrerequisite(String... permissions) {
            this.permissions = permissions;
        }

        @Override
        public boolean check(Context context) {
            for (String permission : permissions) {
                if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public PermissionRequestFlow createRemedyFlow(AppCompatActivity activity) {
            return new PermissionRequestFlow(activity, permissions);
        }
    }

    public static abstract class AbstractSimpleFlowPrerequisite implements FeaturePrerequisite {

        @Override
        public PermissionRequestFlow createRemedyFlow(AppCompatActivity activity) {
            return new PermissionRequestFlow(activity, this::startRemedyActivity);
        }

        public abstract void startRemedyActivity(Context context);
    }

    public static final FeaturePrerequisite LWT_HARDWARE = new FeaturePrerequisite() {
        @Override
        public boolean check(Context context) {
            return HybridLwdnScanner.isSupported(context);
        }
    };

    public static final FeaturePrerequisite CICO_HARDWARE = new FeaturePrerequisite() {
        @Override
        public boolean check(Context context) {
            return CICOService.isSupported(context);
        }
    };

    public static final FeaturePrerequisite BLUETOOTH_PERMISSIONS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ?
            new AbstractPermissionPrerequisite(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN) {

                @RequiresApi(api = Build.VERSION_CODES.S)
                @Override
                public boolean check(Context context) {
                    return context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                            && context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
                }
            }
            : new AlwaysSatisfiedPrerequisite();

    public static final FeaturePrerequisite BLUETOOTH_ON = new AbstractSimpleFlowPrerequisite() {
        @Override
        public boolean check(Context context) {
            return context.getSystemService(BluetoothManager.class).getAdapter().isEnabled();
        }

        @Override
        public void startRemedyActivity(Context context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
            }
            context.startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        }

        @Override
        public FeaturePrerequisite[] getDependencies() {
            return new FeaturePrerequisite[]{BLUETOOTH_PERMISSIONS};
        }
    };

    public static final FeaturePrerequisite AWARE_AVAILABLE = new AbstractSimpleFlowPrerequisite() {
        @Override
        public boolean check(Context context) {
            WifiAwareManager awareManager = context.getSystemService(WifiAwareManager.class);
            if (awareManager == null) {
                return false;
            }
            return awareManager.isAvailable();
        }

        @Override
        public void startRemedyActivity(Context context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.startActivity(new Intent(Settings.Panel.ACTION_WIFI));
            } else {
                context.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
            }
        }
    };

    public static final FeaturePrerequisite LOCATION_FOR_LE_SCAN = new AbstractPermissionPrerequisite(
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? Manifest.permission.ACCESS_FINE_LOCATION
                    : Manifest.permission.ACCESS_COARSE_LOCATION
    );

    public static final FeaturePrerequisite NEARBY_WIFI_DEVICES = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ? new AbstractPermissionPrerequisite(Manifest.permission.NEARBY_WIFI_DEVICES)
            : new AlwaysSatisfiedPrerequisite();

    public static final FeaturePrerequisite LOCATION_FOR_AWARE_SCAN = new AbstractPermissionPrerequisite(Manifest.permission.ACCESS_FINE_LOCATION);

    public static final FeaturePrerequisite BACKGROUND_LOCATION_FOR_LE_SCAN = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ?
            new AbstractPermissionPrerequisite(Manifest.permission.ACCESS_BACKGROUND_LOCATION) {

                @Override
                public boolean isApplicable(Context context) {
                    return BluetoothLwdnScanner.isBackgroundLocationPermissionNeeded(context);
                }

                @Override
                public FeaturePrerequisite[] getDependencies() {
                    return new FeaturePrerequisite[]{LOCATION_FOR_LE_SCAN};
                }
            }
            : new AlwaysSatisfiedPrerequisite();

    public static final FeaturePrerequisite NOTIFICATION_PERMISSION = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ? new AbstractPermissionPrerequisite(Manifest.permission.POST_NOTIFICATIONS)
            : new AlwaysSatisfiedPrerequisite();

    public static final FeaturePrerequisite BATTERY_EXEMPTION = new FeaturePrerequisite() {

        private static final Set<String> CAPRICIOUS_MANUFACTURERS = Set.of(
                "xiaomi",
                "oppo",
                "vivo",
                "oneplus",
                "realme",
                "huawei",
                "honor"
        );

        @Override
        public boolean isApplicable(Context context) {
            if (DeviceSpecifics.isLineageOs()) {
                // lineage does not suck
                return false;
            }
            if (DeviceSpecifics.isXiaomi()) {
                return true;
            }
            String manufacturer = Build.MANUFACTURER.toLowerCase();
            if ("samsung".equals(manufacturer)) {
                // https://android-developers.googleblog.com/2023/05/improving-consistency-of-background-work-on-android.html
                return Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
            }
            return CAPRICIOUS_MANUFACTURERS.contains(manufacturer);
        }

        private Boolean xiaomiIsNoRestrict;

        @Override
        public boolean check(Context context) {
            if (xiaomiIsNoRestrict != null && xiaomiIsNoRestrict) {
                return true;
            }
            return context.getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(context.getPackageName());
        }

        @SuppressLint("BatteryLife")
        @Override
        public PermissionRequestFlow createRemedyFlow(AppCompatActivity activity) {
            ActivityResultLauncher<Intent> xiaomiLauncher = activity.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                xiaomiIsNoRestrict = null;
                if (result.getData() != null) {
                    String selection = result.getData().getStringExtra("user_configure");
                    if ("no_restrict".equals(selection)) {
                        xiaomiIsNoRestrict = true;
                    }
                }
            });
            return new PermissionRequestFlow(activity, context -> {
                if (DeviceSpecifics.isXiaomi() || DeviceSpecifics.isMiui()) {
                    // MIUI can be installed on non-Xiaomi devices too.
                    // we try to call this on all Xiaomis incl. custom ROMs. If it fails, we
                    // continue with the standard method.
                    try {
                        startXiaomiPowerSavingDialog(context, xiaomiLauncher);
                        return;
                    } catch (ActivityNotFoundException ignored) {
                        // fall through to AOSP
                    }
                }
                if (context.checkSelfPermission(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) == PackageManager.PERMISSION_GRANTED) {
                    context.startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS));
                } else {
                    context.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                }
            });
        }

        private void startXiaomiPowerSavingDialog(Context context, ActivityResultLauncher<Intent> launcher) {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"));
            intent.putExtra("package_label", context.getString(context.getApplicationInfo().labelRes));
            intent.putExtra("package_name", context.getPackageName());
            launcher.launch(intent);
        }
    };

    public static FeaturePrerequisite[] LWT_OVER_BLE = {
            LWT_HARDWARE,
            BLUETOOTH_ON,
            LOCATION_FOR_LE_SCAN
    };

    public static FeaturePrerequisite[] LWT_OVER_AWARE = {
            LWT_HARDWARE,
            AWARE_AVAILABLE,
            NEARBY_WIFI_DEVICES,
            LOCATION_FOR_AWARE_SCAN
    };

    public static FeaturePrerequisite[] CICO = {
            CICO_HARDWARE,
            BLUETOOTH_ON,
            LOCATION_FOR_LE_SCAN,
            BACKGROUND_LOCATION_FOR_LE_SCAN,
            NOTIFICATION_PERMISSION,
            BATTERY_EXEMPTION
    };

    public static int getNumUnsatisfied(Context context, FeaturePrerequisite[] prerequisites) {
        int count = 0;
        for (FeaturePrerequisite prerequisite : prerequisites) {
            if (prerequisite.isApplicable(context) && !prerequisite.check(context)) {
                count++;
            }
        }
        return count;
    }

    public static boolean checkAllSatisfied(Context context, FeaturePrerequisite[] prerequisites) {
        return getNumUnsatisfied(context, prerequisites) == 0;
    }

    public static boolean checkCICOSatisfied(Context context) {
        return checkAllSatisfied(context, CICO);
    }

    public static boolean checkCICOSatisfiedExceptBTOn(Context context) {
        return getNumUnsatisfied(context, CICO) == 1 && !BLUETOOTH_ON.check(context);
    }
}
