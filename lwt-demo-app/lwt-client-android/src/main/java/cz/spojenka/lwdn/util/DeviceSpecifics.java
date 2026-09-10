package cz.spojenka.lwdn.util;

import android.os.Build;

public class DeviceSpecifics {

    public static boolean checkBrand(String brand) {
        return Build.MANUFACTURER.equalsIgnoreCase(brand);
    }

    private static boolean checkSysprop(String prop) {
        return !SystemProperties.read(prop).isEmpty();
    }

    public static boolean isHuawei() {
        return checkBrand("huawei");
    }

    public static boolean isHuaweiOs() {
        return isHuawei() && checkSysprop("ro.huawei.build.date");
    }

    public static boolean isHonor() {
        return checkBrand("honor");
    }

    public static boolean isXiaomi() {
        return checkBrand("xiaomi") || checkBrand("redmi") || checkBrand("poco");
    }

    public static boolean isMiui() {
        return checkSysprop("ro.miui.ui.version.code");
    }

    public static boolean isLineageOs() {
        return checkSysprop("ro.lineage.version");
    }
}
