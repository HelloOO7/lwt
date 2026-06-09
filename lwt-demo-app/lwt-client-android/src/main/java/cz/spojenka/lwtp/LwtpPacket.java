package cz.spojenka.lwtp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;

public class LwtpPacket {

    private final Header header;
    private final ByteBuffer payload;

    public LwtpPacket(InputStream in) throws IOException {
        DataInputStream dis = new DataInputStream(in);
        byte[] headerBuffer = new byte[Header.MINIMUM_SIZE];
        dis.readFully(headerBuffer);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(headerBuffer)) {
            header = new Header(new DataInputStream(bais));
        }
        header.validate();
        int skipSize = header.headerSize - Header.MINIMUM_SIZE;
        if (skipSize > 0) {
            dis.skipBytes(skipSize);
        }
        payload = ByteBuffer.allocate(header.payloadSize);
        ReadableByteChannel inChannel = Channels.newChannel(in);
        while (payload.hasRemaining()) {
            if (inChannel.read(payload) == -1) {
                throw new EOFException("Unexpected end of stream while reading payload");
            }
        }
        payload.rewind();
    }

    public LwtpPacket(ByteBuffer payload) {
        this.header = new Header();
        this.header.payloadSize = payload.remaining();
        this.payload = payload;
    }

    public static LwtpPacket createControlMessage(ByteBuffer payload) {
        LwtpPacket packet = new LwtpPacket(payload);
        packet.header.flags |= Header.FLAG_CONTROL_MESSAGE;
        return packet;
    }

    public static LwtpPacket createSimpleControlMessage(int command) {
        ByteBuffer commandData;
        if (command <= 0xFF) {
            commandData = ByteBuffer.allocate(1).put((byte) command);
        } else if (command <= 0xFFFF) {
            commandData = ByteBuffer.allocate(2).putShort((short) command);
        } else {
            commandData = ByteBuffer.allocate(4).putInt(command);
        }
        commandData.flip();
        return createControlMessage(commandData);
    }

    public static int decodeSimpleControlCommand(LwtpPacket packet) throws IOException {
        if (!packet.isControlMessage()) {
            throw new IOException("Packet is not a control message");
        }
        ByteBuffer payload = packet.getPayload();
        int result = 0;
        while (payload.hasRemaining()) {
            result <<= 8;
            result |= (payload.get() & 0xFF);
        }
        return result;
    }

    public boolean isControlMessage() {
        return (header.flags & Header.FLAG_CONTROL_MESSAGE) != 0;
    }

    public ByteBuffer getPayload() {
        return payload;
    }

    public void write(OutputStream out) throws IOException {
        // write to temporary buffer to reduce number of write calls to the output stream
        try (ByteArrayOutputStream headerOs = new ByteArrayOutputStream()) {
            DataOutputStream headerDos = new DataOutputStream(headerOs);
            header.write(headerDos);
            out.write(headerOs.toByteArray());
        }
        if (payload != null && payload.hasRemaining()) {
            WritableByteChannel outChannel = Channels.newChannel(out);
            while (payload.hasRemaining()) {
                outChannel.write(payload);
            }
        }
    }

    private static class Header {

        public static final int MINIMUM_SIZE = 10;
        public static final String MAGIC = "LWTP";

        public static final int FLAG_CONTROL_MESSAGE = 1;

        public String magic = MAGIC;
        public int version = 1;
        public int flags;
        public int headerSize = MINIMUM_SIZE;
        public int payloadSize;

        public Header(DataInputStream dis) throws IOException {
            byte[] magicBytes = new byte[4];
            dis.readFully(magicBytes);
            this.magic = new String(magicBytes, StandardCharsets.US_ASCII);
            this.version = dis.readUnsignedShort();
            this.flags = dis.readUnsignedByte();
            this.headerSize = dis.readUnsignedByte();
            this.payloadSize = dis.readUnsignedShort();
        }

        public Header() {

        }

        public void validate() throws IOException {
            if (!MAGIC.equals(magic)) {
                throw new IOException("Invalid magic: " + magic);
            }
        }

        public void write(DataOutputStream dos) throws IOException {
            dos.write(MAGIC.getBytes(StandardCharsets.US_ASCII));
            dos.writeShort(version);
            dos.writeByte(flags);
            dos.writeByte(headerSize);
            dos.writeShort(payloadSize);
        }
    }
}
