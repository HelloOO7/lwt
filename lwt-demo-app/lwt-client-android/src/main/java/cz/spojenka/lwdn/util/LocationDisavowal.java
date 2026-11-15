package cz.spojenka.lwdn.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

public class LocationDisavowal {

    private static final String TAG = LocationDisavowal.class.getName();

    public static boolean isAssertedForPermission(Context context, String permission) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            // REQUESTED_PERMISSION_NEVER_FOR_LOCATION was added in API 31
            return false;
        }
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);
            if (pi.requestedPermissions != null && pi.requestedPermissionsFlags != null) {
                for (int i = 0; i < pi.requestedPermissions.length; i++) {
                    if (permission.equals(pi.requestedPermissions[i]) &&
                            (pi.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_NEVER_FOR_LOCATION) != 0) {
                        return true;
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to get package info", e);
        }

        return false;
    }
}
