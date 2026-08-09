package cz.spojenka.lwdn;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class InetLwdnSocket implements LwdnSocket {

    private final Socket socket;

    private InputStream inputStream;
    private OutputStream outputStream;

    public InetLwdnSocket(Socket socket) {
        this.socket = socket;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (inputStream == null) {
            inputStream = socket.getInputStream();
        }
        return inputStream;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        if (outputStream == null) {
            outputStream = socket.getOutputStream();
        }
        return outputStream;
    }

    @Override
    public boolean isOpen() {
        return socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
