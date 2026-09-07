package cz.spojenka.lwdn;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Build;

import java.io.IOException;
import java.lang.reflect.Method;

public class BluetoothDeviceCompat {

    public static BluetoothSocket createInsecureL2capChannel(BluetoothDevice bluetoothDevice, int psm) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return bluetoothDevice.createInsecureL2capChannel(psm);
        } else {
            try {
                Method method = bluetoothDevice.getClass().getDeclaredMethod("createInsecureL2capCocSocket", int.class, int.class);
                method.setAccessible(true);
                return (BluetoothSocket) method.invoke(bluetoothDevice, BluetoothDevice.TRANSPORT_LE, psm);
            } catch (ReflectiveOperationException ex) {
                throw new RuntimeException("If you intend to support Android 9 (Pie), you must bundle a hidden API unblocker such as RestrictionBypass with your app.", ex);
            }
        }
    }
}
