package cz.spojenka.lwt.util;

import android.content.Intent;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.function.Function;

public class FlatbufferUtils {

    @SuppressWarnings("unchecked")
    public static <T> T reflectOpenFlatbuffer(ByteBuffer data, Class<T> clazz) {
        try {
            Method getRootAsMethod = clazz.getMethod("getRootAs" + clazz.getSimpleName(), ByteBuffer.class);
            return (T) getRootAsMethod.invoke(null, data);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T reflectOpenFlatbuffer(byte[] data, Class<T> clazz) {
        return reflectOpenFlatbuffer(ByteBuffer.wrap(data), clazz);
    }

    public static <T> T getFlatBufferExtra(Intent intent, String name, Class<T> clazz) {
        byte[] data = intent.getByteArrayExtra(name);
        if (data == null) {
            return null;
        }
        return reflectOpenFlatbuffer(data, clazz);
    }

    public static <T> T getFlatBufferExtra(Intent intent, String name, Function<ByteBuffer, T> deserializer) {
        byte[] data = intent.getByteArrayExtra(name);
        if (data == null) {
            return null;
        }
        return deserializer.apply(ByteBuffer.wrap(data));
    }
}
