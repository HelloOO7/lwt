package cz.spojenka.lwt.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

public class ByteBufferInputStream extends InputStream {

    private final ByteBuffer buf;

    public ByteBufferInputStream(ByteBuffer buf) {
        this.buf = buf;
    }

    @Override
    public int read() throws IOException {
        try {
            return buf.get() & 0xFF;
        } catch (BufferUnderflowException ex) {
            return -1;
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (buf.position() >= buf.limit()) {
            return -1;
        }
        int readSize = Math.min(len, buf.remaining());
        buf.get(b, off, readSize);
        return readSize;
    }

    @Override
    public int available() throws IOException {
        return buf.remaining();
    }
}
