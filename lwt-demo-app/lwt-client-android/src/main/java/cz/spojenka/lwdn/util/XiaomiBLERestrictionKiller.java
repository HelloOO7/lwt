package cz.spojenka.lwdn.util;

import android.util.Log;

import java.lang.reflect.Method;

public class XiaomiBLERestrictionKiller {

    private static final String TAG = "XiaomiBLE";

    private static boolean applied;

    public static void apply() {
        if (applied) {
            return;
        }
        applied = true;
        if (!DeviceSpecifics.isXiaomi() || DeviceSpecifics.isLineageOs()) {
            return;
        }
        Class<?> cWhetstoneActivityManager;
        Class<?> cIWhetstoneActivityManager;
        Class<?> cIPowerKeeperPolicy;
        try {
            cWhetstoneActivityManager = Class.forName("com.miui.whetstone.WhetstoneActivityManager");
            cIWhetstoneActivityManager = Class.forName("com.miui.whetstone.server.IWhetstoneActivityManager");
            cIPowerKeeperPolicy = Class.forName("com.miui.whetstone.IPowerKeeperPolicy");
        } catch (ClassNotFoundException ignored) {
            return;
        }

        try {
            Method mGetService = cWhetstoneActivityManager.getDeclaredMethod("getService");
            mGetService.setAccessible(true);
            Object whetstoneService = mGetService.invoke(null);
            Method mGetPowerKeeperPolicy = cIWhetstoneActivityManager.getMethod("getPowerKeeperPolicy");
            Object powerKeeperPolicy = mGetPowerKeeperPolicy.invoke(whetstoneService);
            Method setLeScanFeature = cIPowerKeeperPolicy.getMethod("setLeScanFeature", boolean.class);
            setLeScanFeature.invoke(powerKeeperPolicy, false);
            Log.d(TAG, "Success! So long, gay Bowser.");
        } catch (ReflectiveOperationException ex) {
            Log.e(TAG, "Failed to apply Xiaomi BLE restriction killer", ex);
        }
    }
}
