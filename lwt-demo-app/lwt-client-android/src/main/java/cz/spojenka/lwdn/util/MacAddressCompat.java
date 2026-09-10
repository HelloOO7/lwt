package cz.spojenka.lwdn.util;

import android.net.MacAddress;
import android.os.Build;

import androidx.annotation.Nullable;

import java.net.Inet6Address;
import java.net.UnknownHostException;

public class MacAddressCompat {

    public static @Nullable Inet6Address getLinkLocalIpv6FromEui48Mac(MacAddress macAddress) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return macAddress.getLinkLocalIpv6FromEui48Mac();
        } else {
            byte[] macEui48Bytes = macAddress.toByteArray();
            byte[] addr = new byte[16];

            addr[0] = (byte) 0xfe;
            addr[1] = (byte) 0x80;
            addr[8] = (byte) (macEui48Bytes[0] ^ (byte) 0x02); // flip the link-local bit
            addr[9] = macEui48Bytes[1];
            addr[10] = macEui48Bytes[2];
            addr[11] = (byte) 0xff;
            addr[12] = (byte) 0xfe;
            addr[13] = macEui48Bytes[3];
            addr[14] = macEui48Bytes[4];
            addr[15] = macEui48Bytes[5];

            try {
                return Inet6Address.getByAddress(null, addr, 0);
            } catch (UnknownHostException e) {
                return null;
            }
        }
    }
}
