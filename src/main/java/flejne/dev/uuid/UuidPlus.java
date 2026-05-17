package flejne.dev.uuid;

import module java.base;


/// Utility class for UUID manipulation, conversion and validation.
///
/// Supports round-tripping between UUID string formats (formatted, hex, mixed-endian),
/// byte arrays and `BigInteger`, as well as format validation and fast random UUID generation.
///
/// This class is not instantiable.
public final class UuidPlus {


    /// Defines the expected byte-lengths for the three UUID wire representations.
    enum UUID_LENGTH {
        /// Standard formatted UUID string: `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx` (36 chars).
        FORMATTED_STRING(36),
        /// Raw byte array (16 bytes).
        BYTES(16),
        /// Hex string without dashes (32 chars).
        HEXA_STRING(32);

        private final int length;

        private UUID_LENGTH(int length) {
            this.length = length;
        }

        /// Returns `true` when the given value has a length that matches a known UUID representation.
        ///
        /// - `String`: accepts 36 characters (formatted with dashes) or 32 characters (hex without dashes).
        /// - `byte[]`: accepts exactly 16 bytes.
        ///
        /// @param <T>  either `String` or `byte[]`
        /// @param uuid the value whose length is checked
        /// @return `true` if the length is valid for the inferred representation
        public static <T> boolean isLengthValid(T uuid) {
            if (uuid instanceof String uuidStr) {
                return uuidStr.length() == (uuidStr.contains("-") ? FORMATTED_STRING.length : HEXA_STRING.length);
            } else if (uuid instanceof byte[] uuidBytes) {
                return uuidBytes.length == BYTES.length;
            }
            return false;
        }

        /// Returns the numeric length associated with this representation.
        ///
        /// @return the expected character or byte count
        @Override
        public String toString() {
            return String.valueOf(length);
        }

        /// Returns the numeric length associated with this representation.
        ///
        /// @return the expected character or byte count
        public int length() {
            return length;
        }
    }

    private static final int HEXA_RADIX = 16;

    private static final ThreadLocalRandom RANDOM = ThreadLocalRandom.current();

    /// The all-zero UUID string `00000000-0000-0000-0000-000000000000` (nil UUID per RFC 4122).
    static final String NIL_UUID = "00000000-0000-0000-0000-000000000000";

    /// Predicate that returns `true` when a UUID string equals the nil UUID.
    static Predicate<String> isNil = NIL_UUID::equals;

    /// Predicate that validates the character length of a UUID string.
    ///
    /// Accepts 36-character formatted strings and 32-character hex strings.
    public static final Predicate<String> isSizeValid = UUID_LENGTH::isLengthValid;

    /// Regular-expression patterns used to validate UUID string format.
    enum REGEXP {
        /// Strict RFC 4122 pattern: enforces version digits `1`–`5` and variant bits `8`, `9`, `a` or `b`.
        STRICT("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[12345][a-fA-F0-9]{3}-[89abAB][a-fA-F0-9]{3}-[a-fA-F0-9]{12}"),
        /// Loose pattern: only checks hex digits and dash placement, any version and variant are accepted.
        LOOSE("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}");

        REGEXP(final String value) {
            this.value = value;
        }

        private final String value;

        /// Returns the raw regex pattern string for this validation mode.
        ///
        /// @return the pattern string
        String pattern() {
            return this.value;
        }

        /// Tests whether `input` matches this UUID regex pattern.
        ///
        /// @param input the string to test
        /// @return `true` if the input matches
        public boolean matches(String input) {
            return input.matches(this.pattern());
        }

    }

    /// Predicate that validates a UUID string against the strict RFC 4122 pattern
    /// (versions 1–5, variant bits enforced), after a length pre-check.
    public static final Predicate<String> isPatternStrict = isSizeValid.and(REGEXP.STRICT::matches);

    /// Predicate that validates a UUID string against the loose pattern
    /// (hex digits and dash placement only), after a length pre-check.
    public static final Predicate<String> isPatternLoose = isSizeValid.and(REGEXP.LOOSE::matches);


    private static final Function<int[], Function<byte[], byte[]>> reOrder = indexes -> bytes -> {
        ByteBuffer output = ByteBuffer.allocate(bytes.length);
        for (int i : indexes) {
            output.put(bytes[i]);
        }
        for (int j = indexes.length; j < bytes.length; j++) {
            output.put(bytes[j]);
        }
        return output.array();
    };

    /// Reorders the first 8 bytes of a UUID byte array to mixed-endian layout.
    ///
    /// Mixed-endian swaps each 4-byte and 2-byte group within the time fields.
    /// Given a source byte array, the output byte array is reordered as follows:
    ///
    /// ```
    /// Input index:   0  1  2  3  4  5  6  7
    /// Example:      45 8A EB 12 11 2F 44 59
    /// Output:       12 EB 8A 45 2F 11 59 44
    /// Output index:  3  2  1  0  5  4  7  6
    /// ```
    private static final Function<byte[], byte[]> toMixEndian = reOrder.apply(new int[]{3, 2, 1, 0, 5, 4, 7, 6});

    /// Converts a byte array to an uppercase hexadecimal string.
    public static final Function<byte[], String> bytesToHexa = HexFormat.of().withUpperCase()::formatHex;

    private static final Function<String, byte[]> hexaToBytes = HexFormat.of()::parseHex;

    private static final UnaryOperator<String> uuidToHexa = s -> s.replace("-", "");

    /// Converts a UUID string (formatted or hex) to a 16-byte big-endian array.
    public static final Function<String, byte[]> uuidToBytes = uuidToHexa.andThen(hexaToBytes);

    /// Converts a UUID string to a 16-byte array in mixed-endian byte order.
    public static final Function<String, byte[]> uuidToMixEndianBytes = uuidToBytes.andThen(toMixEndian);

    /// Extracts the `long` at the given byte offset from a byte array wrapping strategy.
    static IntFunction<Function<byte[], Long>> toLong = offset -> bytes -> ByteBuffer
            .wrap(bytes, offset, Long.BYTES).getLong();

    private static final int OFFSET_MSB = 0;

    private static final int OFFSET_LSB = 8;

    /// Reads the most-significant 64 bits from a 16-byte UUID array.
    static Function<byte[], Long> mostSignificantBits = toLong.apply(OFFSET_MSB);

    /// Reads the least-significant 64 bits from a 16-byte UUID array.
    static Function<byte[], Long> leastSignificantBits = toLong.apply(OFFSET_LSB);

    private static final Function<byte[], UUID> uuidFromBytes = bytes -> new UUID(mostSignificantBits.apply(bytes),
            leastSignificantBits.apply(bytes));

    private static final Function<Function<byte[], byte[]>, Function<byte[], UUID>> arrayStrategyToUuid = strategy -> bytes -> {
        if (UUID_LENGTH.isLengthValid(bytes))
            return strategy.andThen(uuidFromBytes).apply(bytes);
        throw new IllegalArgumentException("The byte array length is expected to be '" + UUID_LENGTH.BYTES + "'");
    };

    /// Converts a 16-byte mixed-endian array to a `UUID`.
    static Function<byte[], UUID> mixEndianToUuid = arrayStrategyToUuid.apply(toMixEndian);

    /// Converts a 16-byte big-endian array to a `UUID`.
    static Function<byte[], UUID> bigEndianToUuid = arrayStrategyToUuid.apply(x -> x);

    /// Converts a 32-character hex string (no dashes) to a `UUID` using big-endian byte order.
    static Function<String, UUID> hexaToUuid = hexaToBytes.andThen(bigEndianToUuid);

    /// Converts a 32-character hex string (no dashes) to a `UUID` using mixed-endian byte order.
    static Function<String, UUID> hexaMixEndianToUuid = hexaToBytes.andThen(mixEndianToUuid);

    /// Parses a `long` value from a hexadecimal string at the given **byte** offset.
    ///
    /// The byte offset is converted to a character offset (`byteOffset × 2`) and
    /// exactly 16 hex characters (8 bytes) are read.
    ///
    /// @param hexa       the hexadecimal string (at least `byteOffset × 2 + 16` chars long)
    /// @param byteOffset the byte position within the UUID (0 for MSB, 8 for LSB)
    /// @return the `long` value of the hex substring
    private static long of(String hexa, int byteOffset) {
        int start = byteOffset * 2;
        return new BigInteger(hexa.substring(start, start + Long.BYTES * 2), HEXA_RADIX).longValue();
    }

    /// Converts a 32-character hex string to a `UUID` by parsing MSB and LSB as `BigInteger` values.
    static Function<String, UUID> hexaStringToUuid = hexa -> {
        var mostSigBits = of(hexa, OFFSET_MSB);
        var leastSigBits = of(hexa, OFFSET_LSB);
        return new UUID(mostSigBits, leastSigBits);
    };

    /// Converts a `BigInteger` to a `UUID` via a 16-byte big-endian intermediate array.
    static Function<BigInteger, UUID> bigIntegerToUuid = v -> bigEndianToUuid
            .apply(ByteBuffer.allocate(UUID_LENGTH.BYTES.length).put(v.toByteArray()).array());

    /// Converts a `UUID` to its 128-bit `BigInteger` representation.
    ///
    /// @param uuid the UUID to convert
    /// @return a `BigInteger` holding the full 128-bit UUID value
    static BigInteger toBigInteger(UUID uuid) {
        var bb = ByteBuffer.wrap(new byte[UUID_LENGTH.BYTES.length]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return new BigInteger(bb.array());
    }

    /// Convert SHA-1 digest to UUID version 5
    /// @param digest to convert
    /// @return an UUID
    static UUID nameUUIDFromSHA1Bytes(byte[] digest) {
        if (digest == null || digest.length != 20) {
            throw new IllegalArgumentException("The digest array length is expected to be '" + 20 + "' bytes");
        }
        var digest16 = Arrays.copyOf(digest, 16);
        // Stamp the version nibble (bits 12–15 of octet 6)
        digest16[6] = (byte) ((digest16[6] & 0x0F) | 0x50);
        // Stamp the variant bits (bits 6–7 of octet 8)
        digest16[8] = (byte) ((digest16[8] & 0x3F) | 0x80);
        // signum=1 forces positive
        // The signum argument is critical: without it, if digest[0] has its high bit set the result is negative (two's complement sign extension).
        BigInteger value = new BigInteger(1, digest16);
        return UuidPlus.bigIntegerToUuid.apply(value);
    }

    /// Compares two UUID strings for equality, ignoring case, after a loose-pattern validation.
    ///
    /// @param uuid1 the first UUID string
    /// @param uuid2 the second UUID string
    /// @return `true` if both strings represent the same UUID value
    static boolean uuidEquals(String uuid1, String uuid2) {
        return uuid2 != null && uuid2.equalsIgnoreCase(uuid1) && isPatternLoose.test(uuid1);
    }

    /// Silently checks whether a string is a valid UUID.
    ///
    /// Delegates to `UUID.fromString()` and catches `IllegalArgumentException` without propagating it.
    ///
    /// @param uuid the string to validate
    /// @return `true` if `uuid` is a well-formed UUID string
    public static boolean isUuid(final String uuid) {
        try {
            UUID.fromString(uuid);
        } catch (final IllegalArgumentException _) {
            return false;
        }
        return true;

    }

    /// Returns a randomly generated UUID using `ThreadLocalRandom`.
    ///
    /// Faster than `UUID.randomUUID()` because it avoids `SecureRandom`.
    ///
    /// **Note:** the version and variant bits are _not_ set, so the result does not
    /// conform to RFC 4122 UUID v4.
    ///
    /// @return a randomly generated UUID
    public static UUID fastRandom() {
        return new UUID(RANDOM.nextLong(), RANDOM.nextLong());
    }

    /// Converts a 16-byte array to a formatted UUID string.
    ///
    /// @param val the byte array (must be exactly 16 bytes)
    /// @return the UUID string representation
    public static String printHexBinary(byte[] val) {
        return uuidFromBytes.apply(val).toString();
    }

    private UuidPlus() {
        // Not instantiable class
    }

}
