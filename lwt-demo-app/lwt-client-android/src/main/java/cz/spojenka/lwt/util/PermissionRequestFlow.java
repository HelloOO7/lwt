package cz.spojenka.lwt.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

import java.util.function.Consumer;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class PermissionRequestFlow {

    private static final int PERMISSION_STATE_INITIAL = 0;
    private static final int PERMISSION_STATE_GRANTED = -1;

    private final AppCompatActivity context;
    private final SharedPreferences prefs;
    private final String[] permissions;

    private ActivityResultLauncher<String[]> permissionLauncher;

    private Consumer<AppCompatActivity> customFlow;

    public PermissionRequestFlow(AppCompatActivity context, String... permissions) {
        this.context = context;
        prefs = context.getSharedPreferences("permission_states", Context.MODE_PRIVATE);
        this.permissions = permissions;
        putAllToSettingsIfGranted();
        if (permissions.length > 0) {
            registerForActivityResults(context);
        }
    }

    public PermissionRequestFlow(AppCompatActivity context, Consumer<AppCompatActivity> customFlow) {
        this(context);
        this.customFlow = customFlow;
    }

    private void registerForActivityResults(ActivityResultCaller caller) {
        permissionLauncher = caller.registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            for (var entry : result.entrySet()) {
                if (entry.getValue()) {
                    prefs.edit().putInt(entry.getKey(), PERMISSION_STATE_GRANTED).apply();
                } else {
                    updatePermissionDenialCount(entry.getKey());
                }
            }
        });
    }

    public void requestPermissions() {
        if (customFlow != null) {
            customFlow.accept(context);
            return;
        }
        if (!isAnyPermissionTerminallyDenied()) {
            permissionLauncher.launch(permissions);
        } else {
            context.startActivity(
                    new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", context.getPackageName(), null))
            );
        }
    }

    private void putAllToSettingsIfGranted() {
        for (String permission : permissions) {
            putToSettingsIfGranted(permission);
        }
    }

    public boolean shouldShowRationale() {
        for (String permission : permissions) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(context, permission)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAnyPermissionTerminallyDenied() {
        for (String permission : permissions) {
            int count = prefs.getInt(permission, PERMISSION_STATE_INITIAL);
            if (count > 0 && !ActivityCompat.shouldShowRequestPermissionRationale(context, permission)) {
                return true;
            }
        }
        return false;
    }

    private void putToSettingsIfGranted(String permission) {
        if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putInt(permission, PERMISSION_STATE_GRANTED).apply();
        }
    }

    private void updatePermissionDenialCount(String permission) {
        int count = prefs.getInt(permission, PERMISSION_STATE_INITIAL);
        prefs.edit().putInt(permission, count + 1).apply();
    }
}
