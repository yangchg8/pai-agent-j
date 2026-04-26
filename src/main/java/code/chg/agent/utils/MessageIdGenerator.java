package code.chg.agent.utils;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author yangchg <yangchg314@gmail.com>
 * @title MessageIdGenerator
 * @description Utility for generating short, fixed-length, random-looking message IDs
 */
public class MessageIdGenerator {

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    private static final AtomicInteger sequence = new AtomicInteger(secureRandom.nextInt());

    /**
     * Generate a fixed-length, random-looking message ID
     * Combines: timestamp (5 bytes) + random (5 bytes) + sequence (2 bytes)
     * Total entropy: 82 bits, collision probability is negligible for practical use
     * Length: 16 characters
     *
     * @return unique message ID string
     */
    public static String generate() {
        byte[] bytes = new byte[12];

        // 5 bytes timestamp (millisecond precision, ~34 years range)
        long timestamp = Instant.now().toEpochMilli();
        bytes[0] = (byte) (timestamp >> 32);
        bytes[1] = (byte) (timestamp >> 24);
        bytes[2] = (byte) (timestamp >> 16);
        bytes[3] = (byte) (timestamp >> 8);
        bytes[4] = (byte) timestamp;

        // 5 bytes random (40 bits entropy)
        byte[] randomBytes = new byte[5];
        secureRandom.nextBytes(randomBytes);
        System.arraycopy(randomBytes, 0, bytes, 5, 5);

        // 2 bytes atomic sequence (per-millisecond uniqueness within JVM)
        int seq = sequence.getAndIncrement();
        bytes[10] = (byte) (seq >> 8);
        bytes[11] = (byte) seq;

        return encoder.encodeToString(bytes);
    }

    /**
     * Generate a message ID with custom prefix
     *
     * @param prefix the prefix to add (e.g., "msg", "evt")
     * @return unique message ID with prefix
     */
    public static String generateWithPrefix(String prefix) {
        return prefix + "_" + generate();
    }

    /**
     * Generate a UUID-based message ID (22 characters)
     * Use when absolute uniqueness is required across distributed systems
     *
     * @return UUID-based message ID string
     */
    public static String generateUUID() {
        UUID uuid = UUID.randomUUID();
        byte[] bytes = new byte[16];
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        for (int i = 7; i >= 0; i--) {
            bytes[i] = (byte) (msb & 0xFF);
            msb >>= 8;
            bytes[i + 8] = (byte) (lsb & 0xFF);
            lsb >>= 8;
        }
        return encoder.encodeToString(bytes);
    }
}
