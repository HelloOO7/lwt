package cz.spojenka.android.polyfills;

import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;

import java.lang.reflect.Array;

public class BundleCompat {

    @SuppressWarnings("deprecation")
    public static <T extends Parcelable> T[] getParcelableArray(Bundle bundle, String key, Class<T> clazz) {
        if (bundle == null) {
            return null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return bundle.getParcelableArray(key, clazz);
        } else {
            return typeifyParcelableArray(bundle.getParcelableArray(key), clazz);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T[] typeifyParcelableArray(Parcelable[] parcelables, Class<T> clazz) {
        if (parcelables == null) {
            return null;
        }
        T[] stronglyTypedArray = (T[]) Array.newInstance(clazz, parcelables.length);
        for (int i = 0; i < parcelables.length; i++) {
            stronglyTypedArray[i] = clazz.cast(parcelables[i]);
        }
        return stronglyTypedArray;
    }
}
