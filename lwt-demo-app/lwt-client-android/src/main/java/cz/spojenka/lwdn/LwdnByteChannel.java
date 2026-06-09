package cz.spojenka.lwdn;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public class LwdnByteChannel implements ByteChannel {

    private final LwdnSocket socket;

    private ReadableByteChannel inputChannel;
    private WritableByteChannel outputChannel;

    public LwdnByteChannel(LwdnSocket socket) {
        this.socket = socket;
    }

    private void ensureInputChannel() throws IOException {
        if (inputChannel == null) {
            inputChannel = Channels.newChannel(socket.getInputStream());
        }
    }

    private void ensureOutputChannel() throws IOException {
        if (outputChannel == null) {
            outputChannel = Channels.newChannel(socket.getOutputStream());
        }
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        ensureInputChannel();
        return inputChannel.read(dst);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        ensureOutputChannel();
        return outputChannel.write(src);
    }

    @Override
    public void close() throws IOException {
        if (inputChannel != null) {
            inputChannel.close();
        }
        if (outputChannel != null) {
            outputChannel.close();
        }
        socket.close();
    }

    @Override
    public boolean isOpen() {
        return socket.isOpen();
    }
}
