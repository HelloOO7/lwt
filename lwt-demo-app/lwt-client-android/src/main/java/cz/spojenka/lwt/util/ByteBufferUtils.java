package cz.spojenka.lwt.util;

import java.nio.ByteBuffer;

public class ByteBufferUtils {

    public static byte[] toByteArray(ByteBuffer buffer) {
        int pos = buffer.position();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        buffer.position(pos);
        return bytes;
    }
}
