package cz.spojenka.lwt.util;

import java.nio.ByteBuffer;

public class ByteBufferUtils {

    public static byte[] toByteArray(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }
}
