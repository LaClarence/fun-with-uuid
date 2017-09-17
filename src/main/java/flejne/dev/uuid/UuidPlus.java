package flejne.dev.uuid;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.xml.bind.DatatypeConverter;

public interface UuidPlus {
    static final int UUID_STRING_SIZE = 36;
    static final int UUID_BYTE_SIZE = 16;
    
    static final String NIL = "00000000-0000-0000-0000-000000000000";
    

    
    enum PATTERN {
        STRICT("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[12345][a-fA-F0-9]{3}-[89abAB][a-fA-F0-9]{3}-[a-fA-F0-9]{12}"),
        // Version and Variant is not checked
        LAYOUT_ONLY("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}");
        private PATTERN(final String value) {
            this.value = value;
        }
        private final String value;
        public String regexp() {
            return this.value;
        }
    }

    static Predicate<String> isSizeValid = s -> s == null ? false : UUID_STRING_SIZE == s.length();
    static Predicate<String> isPatternMatching = isSizeValid.and(s -> s.matches(PATTERN.STRICT.regexp()));
    static Predicate<String> isLayoutMatch = isSizeValid.and(s -> s.matches(PATTERN.LAYOUT_ONLY.regexp()));

    /**
     *  return an hexa string into corresponding byte arrays value
     */
    static Function<String, byte[]> hexaToBytes = s -> DatatypeConverter.parseHexBinary(s);
 
    /**
     * Return the UUID corresponding to the 16 bytes array.
     * @param bytes a 16 bytes array length
     * @return the UUID
     * @throws IllegalArgumentException when byte arrays is not 16 bytes size?
     */
    static Function<byte[],UUID> bytesToUuid = bytes -> {
        if (bytes.length != UUID_BYTE_SIZE) { 
            throw new IllegalArgumentException("Invalid size!");
        }
        return new UUID(ByteBuffer.wrap(bytes, 0, 8).getLong(), ByteBuffer.wrap(bytes, 8, 8).getLong());
    };

    static Function<byte[],Optional<UUID>> bytesToOptUuid = bytes -> 
        (bytes.length != UUID_BYTE_SIZE) ? Optional.empty() :
        Optional.of(new UUID(ByteBuffer.wrap(bytes, 0, 8).getLong(), ByteBuffer.wrap(bytes, 8, 8).getLong()));
    
    static Function<String, UUID> hexaToUuid = hexaToBytes.andThen(bytesToUuid); 
 
    /**
     * Return the UUID corresponding to the 16 bytes array.
     * @param bytes a 16 bytes array length
     * @return the UUID
     * @throws IllegalArgumentException when byte arrays is not 16 bytes size?
     */
    @Deprecated
    static UUID toUuid(byte[] bytes) {
        if (bytes.length == UUID_BYTE_SIZE) { 
        return new UUID(ByteBuffer.wrap(bytes, 0, 8).getLong(), ByteBuffer.wrap(bytes, 8, 8).getLong());
        }
        throw new IllegalArgumentException("Invalid size!");
    }
    
    @Deprecated
    static UUID toUuid(BigInteger value) {
        return toUuid(ByteBuffer.allocate(UUID_BYTE_SIZE).put(value.toByteArray()).array());
    }
    
    static Function<BigInteger,UUID> bigIntegerToUuid = v -> bytesToUuid.apply(ByteBuffer.allocate(UUID_BYTE_SIZE).put(v.toByteArray()).array());
 
    static BigInteger toBigInteger(UUID uuid) {
        ByteBuffer bb = ByteBuffer.wrap(new byte[UUID_BYTE_SIZE]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return new BigInteger(bb.array());
    }
    
    static UUID fastRandom() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        return new UUID(rnd.nextLong(), rnd.nextLong());
    }
    
    static UUID toUUID2(String hexa) {
        BigInteger mostSigBits = new BigInteger(hexa.substring(0, 8), 16);
        BigInteger leastSigBits = new BigInteger(hexa.substring(8, 8), 16);
        return new UUID(mostSigBits.longValue(), leastSigBits.longValue());
    }
    
    static boolean areEquals(String uuid1, String uuid2) {
        return isLayoutMatch.test(uuid1) && uuid1.equalsIgnoreCase(uuid2);
    }
}
