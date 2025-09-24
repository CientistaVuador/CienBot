package cientistavuador.cienbot.storage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 *
 * @author Cien
 */
public class PacketCipher {

    public static final String CIPHER_ALGORITHM = "AES_256/GCM/NoPadding";

    public static final int IV_SIZE = 12;
    public static final int AUTHENTICATION_TAG_SIZE = 128;

    private final HashChain hashChain;
    private final Cipher cipher;

    public PacketCipher(HashChain hashChain) {
        this.hashChain = Objects.requireNonNull(hashChain, "hash chain is null");
        try {
            this.cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException ex) {
            throw new RuntimeException(ex);
        }
        this.hashChain.doFinal();
    }

    private int getPacketSizeKey(byte[] lastSignature) {
        int key = 0;
        for (int i = 0; i < 4; i++) {
            key |= ((lastSignature[i] & 0xFF) << ((3 - i) * 8));
        }
        return key;
    }

    private byte[] getIV(byte[] lastSignature) {
        return Arrays.copyOfRange(lastSignature, 4, 4 + IV_SIZE);
    }

    private SecretKey generatePacketKey() {
        this.hashChain.doFinal();
        return new SecretKeySpec(this.hashChain.getHash(), "AES");
    }

    private void updateHashChain(int packetSizeXOR, byte[] packetData) {
        this.hashChain.updateInt(packetSizeXOR);
        this.hashChain.update(packetData);
        this.hashChain.doFinal();
    }

    public void encrypt(DataOutputStream out, Packet packet) throws IOException {
        byte[] lastSignature = this.hashChain.getHash();
        int packetSizeKey = getPacketSizeKey(lastSignature);
        byte[] iv = getIV(lastSignature);

        SecretKey packetKey = generatePacketKey();

        byte[] packetData;
        try {
            Cipher c = this.cipher;
            GCMParameterSpec gcm = new GCMParameterSpec(AUTHENTICATION_TAG_SIZE, iv);
            c.init(Cipher.ENCRYPT_MODE, packetKey, gcm);
            packetData = c.doFinal(packet.serialize());
        } catch (InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException ex) {
            throw new IOException(ex);
        }

        int packetSizeXOR = packetSizeKey ^ packetData.length;
        updateHashChain(packetSizeXOR, packetData);

        out.writeInt(packetSizeXOR);
        out.write(packetData, 0, packetData.length);
    }

    public Packet decrypt(DataInputStream in) throws IOException {
        byte[] lastSignature = this.hashChain.getHash();
        int packetSizeKey = getPacketSizeKey(lastSignature);
        byte[] iv = getIV(lastSignature);

        try {
            byte[] packetData = new byte[packetSizeKey ^ in.readInt()];
            in.readFully(packetData);

            SecretKey packetKey = generatePacketKey();

            byte[] serializedPacket;
            try {
                Cipher c = this.cipher;
                GCMParameterSpec gcm = new GCMParameterSpec(AUTHENTICATION_TAG_SIZE, iv);
                c.init(Cipher.DECRYPT_MODE, packetKey, gcm);
                serializedPacket = c.doFinal(packetData);
            } catch (InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException ex) {
                throw new IOException(ex);
            }
            
            updateHashChain(packetSizeKey ^ packetData.length, packetData);
            
            return new Packet(serializedPacket);
        } catch (EOFException ex) {
            return null;
        }
    }

}
