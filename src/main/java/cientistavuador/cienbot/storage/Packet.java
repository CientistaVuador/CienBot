package cientistavuador.cienbot.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 *
 * @author Cien
 */
public class Packet {

    private final int id;
    private final long timestamp;
    private final Map<String, String> metadata = new HashMap<>();
    private byte[] data = null;
    
    public Packet(byte[] serializedPacket) {
        Objects.requireNonNull(serializedPacket, "serialized packet is null");
        try {
            ByteArrayInputStream byteArray = new ByteArrayInputStream(serializedPacket);
            try (DataInputStream in = new DataInputStream(byteArray)) {
                this.id = in.readInt();
                this.timestamp = in.readLong();

                int metadataEntries = in.readInt();
                for (int i = 0; i < metadataEntries; i++) {
                    byte[] keyData = new byte[in.readInt()];
                    in.readFully(keyData);

                    byte[] valueData = new byte[in.readInt()];
                    in.readFully(valueData);

                    this.metadata.put(
                            new String(keyData, StandardCharsets.UTF_8),
                            new String(valueData, StandardCharsets.UTF_8));
                }

                this.data = new byte[in.readInt()];
                in.readFully(this.data);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
    
    public Packet(int id, byte[] data) {
        this.id = id;
        this.timestamp = System.currentTimeMillis();
        this.data = data;
    }
    
    public Packet(int id, String data) {
        this(id, data == null ? null : data.getBytes(StandardCharsets.UTF_8));
    }
    
    public Packet(int id) {
        this(id, (byte[]) null);
    }

    public int getId() {
        return id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getTimestampFormatted() {
        return Instant
                .ofEpochMilli(this.timestamp)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT));
    }
    
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public byte[] getData() {
        return data;
    }
    
    public String getDataString() {
        if (this.data == null) {
            return null;
        }
        return new String(this.data, StandardCharsets.UTF_8);
    }

    public void setData(byte[] data) {
        this.data = data;
    }
    
    public void setData(String data) {
        this.data = (data == null ? null : data.getBytes(StandardCharsets.UTF_8));
    }
    
    public byte[] serialize() {
        try {
            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(byteArray)) {
                out.writeInt(this.id);
                out.writeLong(this.timestamp);
                
                Set<Map.Entry<String, String>> entries = this.metadata.entrySet();
                out.writeInt(entries.size());
                for (Map.Entry<String, String> entry : entries) {
                    byte[] key = entry.getKey().getBytes(StandardCharsets.UTF_8);
                    byte[] value = entry.getValue().getBytes(StandardCharsets.UTF_8);

                    out.writeInt(key.length);
                    out.write(key, 0, key.length);

                    out.writeInt(value.length);
                    out.write(value, 0, value.length);
                }

                if (this.data != null) {
                    out.writeInt(this.data.length);
                    out.write(this.data, 0, this.data.length);
                } else {
                    out.writeInt(0);
                }
            }
            return byteArray.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("Id: ").append(this.id).append("\n");
        b.append("Timestamp: ").append(this.timestamp).append(" (").append(getTimestampFormatted()).append(")").append("\n");

        Set<Map.Entry<String, String>> entries = this.metadata.entrySet();
        if (!entries.isEmpty()) {
            b.append("Metadata:").append("\n");
            for (Map.Entry<String, String> entry : entries) {
                b.append(" ".repeat(4)).append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
            }
        }
        
        if (this.data != null) {
            b.append("Data: ").append(Base64.getEncoder().encodeToString(this.data)).append("\n");
        }
        
        return b.toString();
    }

}
