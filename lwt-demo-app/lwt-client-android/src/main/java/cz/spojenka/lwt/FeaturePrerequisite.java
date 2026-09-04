package cz.spojenka.lwt;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import java.util.Set;

import androidx.annotation.ChecksSdkIntAtLeast;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import cz.spojenka.lwt.util.PermissionRequestFlow;

public interface FeaturePrerequisite {

    public default boolean isApplicable(Context context) {
        return true;
    }

    public boolean check(Context context);

    public default PermissionRequestFlow createRemedyFlow(AppCompatActivity activity) {
        return new PermissionRequestFlow(activity, activity1 -> {
        });
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
                    return context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                            && context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED;
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
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                context.startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            }
        }
    };

    public static final FeaturePrerequisite LOCATION_FOR_LE_SCAN = new AbstractPermissionPrerequisite(
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? Manifest.permission.ACCESS_FINE_LOCATION
                    : Manifest.permission.ACCESS_COARSE_LOCATION
    );

    public static final FeaturePrerequisite NOTIFICATION_PERMISSION = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ? new AbstractPermissionPrerequisite(Manifest.permission.POST_NOTIFICATIONS)
            : new AlwaysSatisfiedPrerequisite();

    public static final FeaturePrerequisite BATTERY_EXEMPTION = new AbstractSimpleFlowPrerequisite() {

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
            String manufacturer = Build.MANUFACTURER.toLowerCase();
            if ("samsung".equals(manufacturer)) {
                // https://android-developers.googleblog.com/2023/05/improving-consistency-of-background-work-on-android.html
                return Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
            }
            return CAPRICIOUS_MANUFACTURERS.contains(manufacturer);
        }

        @Override
        public boolean check(Context context) {
            return context.getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(context.getPackageName());
        }

        @SuppressLint("BatteryLife")
        @Override
        public void startRemedyActivity(Context context) {
            if (context.checkSelfPermission(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) == PackageManager.PERMISSION_GRANTED) {
                context.startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS));
            } else {
                context.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        }
    };

    public static FeaturePrerequisite[] CICO = {
            CICO_HARDWARE,
            BLUETOOTH_ON,
            LOCATION_FOR_LE_SCAN,
            NOTIFICATION_PERMISSION,
            BATTERY_EXEMPTION
    };

    public static boolean checkAllSatisfied(Context context, FeaturePrerequisite[] prerequisites) {
        for (FeaturePrerequisite prerequisite : prerequisites) {
            if (prerequisite.isApplicable(context) && !prerequisite.check(context)) {
                return false;
            }
        }
        return true;
    }

    public static boolean checkCICOSatisfied(Context context) {
        return checkAllSatisfied(context, CICO);
    }
}
