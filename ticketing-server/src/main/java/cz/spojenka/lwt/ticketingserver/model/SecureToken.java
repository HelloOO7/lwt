package cz.spojenka.lwt.ticketingserver.model;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.BiPredicate;
import java.util.function.Function;

public class SecureToken {

    public static byte[] create(byte[] data, int keyInfo, Function<byte[], byte[]> signFunction) {
        byte[] dataWithKeyInfo = ByteBuffer.allocate(data.length + Integer.BYTES)
                .put(data)
                .putInt(keyInfo)
                .array();
        byte[] signature = signFunction.apply(dataWithKeyInfo);

        return ByteBuffer.allocate(dataWithKeyInfo.length + signature.length + Short.BYTES)
                .put(dataWithKeyInfo)
                .put(signature)
                .putShort((short) signature.length) //will be read from end
                .array();
    }

    public static byte[] parseAndVerify(byte[] token, BiPredicate<byte[], byte[]> verifyFunction) throws SecurityException {
        return parseAndVerify(token, (data, signature, keyInfo) -> verifyFunction.test(data, signature));
    }

    public static byte[] parseAndVerify(byte[] token, SignatureVerifier verifier) throws SecurityException {
        ByteBuffer buffer = ByteBuffer.wrap(token);
        int sigLength = buffer.getShort(token.length - Short.BYTES);
        int sigStart = token.length - Short.BYTES - sigLength;
        byte[] sig = new byte[sigLength];
        buffer.get(sigStart, sig);
        int keyInfo = buffer.getInt(sigStart - Integer.BYTES);
        byte[] dataWithKeyInfo = new byte[sigStart];
        buffer.get(dataWithKeyInfo);
        if (!verifier.verify(dataWithKeyInfo, sig, keyInfo)) {
            throw new SecurityException("Invalid signature");
        }
        return Arrays.copyOf(dataWithKeyInfo, dataWithKeyInfo.length - Integer.BYTES); // remove keyInfo
    }

    public static interface SignatureVerifier {

        public boolean verify(byte[] data, byte[] signature, int keyInfo);
    }
}
