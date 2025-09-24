package cientistavuador.cienbot.storage;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;

/**
 *
 * @author Cien
 */
public class HashChain {
    
    public static final String MAC_ALGORITHM = "HmacSHA256";
    public static final int SIGNATURE_SIZE;
    static {
        try {
            SIGNATURE_SIZE = Mac.getInstance(MAC_ALGORITHM).getMacLength();
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    private final Mac mac;
    private final SecretKey masterKey;
    
    private final byte[] hash;
    private long counter = 0;
    
    public HashChain(SecretKey masterKey) {
        try {
            this.mac = Mac.getInstance(MAC_ALGORITHM);
            this.masterKey = Objects.requireNonNull(masterKey, "master key is null");
            this.hash = new byte[this.mac.getMacLength()];
            
            this.mac.init(this.masterKey);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    private void updateImpl(byte b) {
        this.mac.update(b);
    }
    
    private void updateIntImpl(int i) {
        for (int j = 0; j < 4; j++) {
            updateImpl((byte) (i >> ((3 - j) * 8)));
        }
    }
    
    private void updateLongImpl(long l) {
        for (int j = 0; j < 8; j++) {
            updateImpl((byte) (l >> ((7 - j) * 8)));
        }
    }
    
    private void updateImpl(byte[] input, int offset, int len) {
        this.mac.update(input, offset, len);
    }
    
    public byte[] getHash() {
        return hash.clone();
    }

    public long getCounter() {
        return counter;
    }
    
    public void update(byte[] input, int offset, int len) {
        updateImpl(input, offset, len);
    }
    
    public void update(byte[] input) {
        update(input, 0, input.length);
    }
    
    public void update(byte b) {
        updateImpl(b);
    }
    
    public void updateInt(int i) {
        updateIntImpl(i);
    }
    
    public void updateLong(long l) {
        updateLongImpl(l);
    }
    
    public void doFinal() {
        try {
            updateLong(this.counter);
            this.mac.doFinal(this.hash, 0);
            
            update(this.hash);
            this.counter++;
        } catch (ShortBufferException | IllegalStateException ex) {
            throw new RuntimeException(ex);
        }
    }
    
}
