package cz.spojenka.lwt.util;

import java.nio.ByteBuffer;

public class ByteBufferUtils {

    public static byte[] toByteArray(ByteBuffer buffer) {
        return toByteArray(buffer, 0, buffer.remaining());
    }

    public static byte[] toByteArray(ByteBuffer buffer, int offset, int length) {
        int pos = buffer.position();
        if (offset != 0) {
            buffer.position(pos + offset);
        }
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        buffer.position(pos);
        return bytes;
    }
}
