package cientistavuador.cienbot.storage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 *
 * @author Cien
 */
public class PacketCipherFileStream {

    public static final String MAGIC_NUMBER = "<7efe60a9bfbcac76> CienBOT V1.0";
    public static final int SECRET_KEY_ITERATIONS = 1_000_000;
    public static final int SALT_SIZE = 32;

    private static byte[] generateSalt() throws IOException {
        try {
            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(byteArray)) {
                out.writeLong(System.currentTimeMillis());
                out.writeLong(System.nanoTime());

                Runtime r = Runtime.getRuntime();
                out.writeLong(r.totalMemory());
                out.writeLong(r.maxMemory());
                out.writeLong(r.freeMemory());

                out.writeInt(r.availableProcessors());

                Set<Entry<Object, Object>> javaProperties = System.getProperties().entrySet();
                for (Entry<Object, Object> e : javaProperties) {
                    byte[] key = e.getKey().toString().getBytes(StandardCharsets.UTF_8);
                    byte[] value = e.getValue().toString().getBytes(StandardCharsets.UTF_8);

                    out.writeInt(key.length);
                    out.write(key);

                    out.writeInt(value.length);
                    out.write(value);
                }

                Set<Entry<String, String>> sysProperties = System.getenv().entrySet();
                for (Entry<String, String> e : sysProperties) {
                    byte[] key = e.getKey().getBytes(StandardCharsets.UTF_8);
                    byte[] value = e.getValue().getBytes(StandardCharsets.UTF_8);

                    out.writeInt(key.length);
                    out.write(key);

                    out.writeInt(value.length);
                    out.write(value);
                }
            }

            SecretKey secretKey = new SecretKeySpec(
                    SecureRandom.getInstanceStrong().generateSeed(SALT_SIZE),
                    HashChain.MAC_ALGORITHM
            );

            Mac mac = Mac.getInstance(HashChain.MAC_ALGORITHM);
            mac.init(secretKey);
            mac.update(byteArray.toByteArray());

            return mac.doFinal();
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IOException(ex);
        }
    }

    private static SecretKey getSecretKey(byte[] salt, char[] password, int iterations) throws IOException {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            PBEKeySpec pbe = new PBEKeySpec(password, salt, iterations, 256);
            try {
                return factory.generateSecret(pbe);
            } finally {
                pbe.clearPassword();
            }
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IOException(ex);
        }
    }
    
    private final Path file;

    private PacketCipher cipher;
    private DataOutputStream out;

    public PacketCipherFileStream(Path file) {
        this.file = Objects.requireNonNull(file, "file is null");
    }

    public Path getFile() {
        return file;
    }

    public void init(char[] password) throws IOException, InvalidPasswordException {
        if (this.cipher != null) {
            throw new IOException("Already initialized!");
        }

        if (Files.isRegularFile(this.file)) {
            InputStream in = Files.newInputStream(this.file);
            BufferedInputStream buffered = new BufferedInputStream(in);
            try (DataInputStream data = new DataInputStream(buffered)) {
                byte[] salt = new byte[SALT_SIZE];
                data.readFully(salt);

                byte[] fileSaltSignature = new byte[HashChain.SIGNATURE_SIZE];
                data.readFully(fileSaltSignature);

                byte[] fileMagicSignature = new byte[HashChain.SIGNATURE_SIZE];
                data.readFully(fileMagicSignature);
                
                HashChain chain = new HashChain(getSecretKey(salt, password, SECRET_KEY_ITERATIONS));

                chain.update(salt);
                chain.doFinal();
                byte[] saltSignature = chain.getHash();
                
                if (!MessageDigest.isEqual(fileSaltSignature, saltSignature)) {
                    throw new InvalidPasswordException("Password is invalid.");
                }
                
                byte[] magicData = MAGIC_NUMBER.getBytes(StandardCharsets.UTF_8);
                chain.updateInt(magicData.length);
                chain.update(magicData);
                chain.doFinal();
                byte[] magicSignature = chain.getHash();

                if (!MessageDigest.isEqual(fileMagicSignature, magicSignature)) {
                    throw new IOException("Invalid magic number!");
                }

                PacketCipher c = new PacketCipher(chain);
                
                Packet p;
                while ((p = c.decrypt(data)) != null) {
                    onPacketRead(p);
                }
                
                this.cipher = c;
            }
            this.out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(this.file, StandardOpenOption.APPEND)));
        } else {
            Path parent = this.file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            DataOutputStream o = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(this.file, StandardOpenOption.CREATE)));

            byte[] salt = generateSalt();
            o.write(salt, 0, salt.length);

            HashChain chain = new HashChain(getSecretKey(salt, password, SECRET_KEY_ITERATIONS));

            chain.update(salt);
            chain.doFinal();
            o.write(chain.getHash(), 0, HashChain.SIGNATURE_SIZE);

            byte[] magicData = MAGIC_NUMBER.getBytes(StandardCharsets.UTF_8);
            chain.updateInt(magicData.length);
            chain.update(magicData);
            chain.doFinal();
            o.write(chain.getHash(), 0, HashChain.SIGNATURE_SIZE);

            o.flush();

            this.cipher = new PacketCipher(chain);
            this.out = o;
        }
    }

    public void onPacketRead(Packet p) throws IOException {

    }

    public synchronized void writePacket(Packet p) throws IOException {
        if (this.cipher == null) {
            throw new IOException("Not initialized!");
        }
        
        this.cipher.encrypt(this.out, p);
    }

    public void flush() throws IOException {
        if (this.cipher == null) {
            throw new IOException("Not initialized!");
        }

        this.out.flush();
    }

}
