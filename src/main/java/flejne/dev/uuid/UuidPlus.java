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
        HEX_STRING(32);

        private final int length;

        UUID_LENGTH(int length) {
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
            return switch (uuid) {
                case String uuidStr   -> uuidStr.length() == (uuidStr.contains("-") ? FORMATTED_STRING.length : HEX_STRING.length);
                case byte[] uuidBytes -> uuidBytes.length == BYTES.length;
                case null, default    -> false;
            };
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


    /// The all-zero UUID string — nil UUID per RFC 9562.
    public static final String NIL_UUID = "00000000-0000-0000-0000-000000000000";

    /// The all-ones UUID string — max UUID per RFC 9562.
    public static final String MAX_UUID = "ffffffff-ffff-ffff-ffff-ffffffffffff";

    /// Predicate that returns `true` when a UUID string equals the nil UUID (case-insensitive).
    public static final Predicate<String> isNil = NIL_UUID::equalsIgnoreCase;

    /// Predicate that returns `true` when a UUID string equals the max UUID (case-insensitive).
    public static final Predicate<String> isMax = MAX_UUID::equalsIgnoreCase;

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


    // --- Internal implementation ---

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

    private static final int[] MIXED_ENDIAN_BYTE_REORDERING = {3, 2, 1, 0, 5, 4, 7, 6};
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
    private static final Function<byte[], byte[]> toMixEndian = reOrder.apply(MIXED_ENDIAN_BYTE_REORDERING);

    private static final Function<byte[], String> bytesToHexFn = HexFormat.of().withUpperCase()::formatHex;

    private static final Function<String, byte[]> hexToBytesFn = HexFormat.of()::parseHex;

    private static final UnaryOperator<String> uuidToHex = s -> s.replace("-", "");

    private static final Function<String, byte[]> uuidToBytesFn = uuidToHex.andThen(hexToBytesFn);

    private static final Function<String, byte[]> uuidToMixEndianBytesFn = uuidToBytesFn.andThen(toMixEndian);

    private static final IntFunction<Function<byte[], Long>> toLong = offset -> bytes ->
            ByteBuffer.wrap(bytes, offset, Long.BYTES).getLong();

    private static final int OFFSET_MSB = 0;

    private static final int OFFSET_LSB = 8;

    private static final Function<byte[], Long> mostSignificantBitsFn = toLong.apply(OFFSET_MSB);

    private static final Function<byte[], Long> leastSignificantBitsFn = toLong.apply(OFFSET_LSB);

    private static final Function<byte[], UUID> uuidFromBytesFn = bytes ->
            new UUID(mostSignificantBitsFn.apply(bytes), leastSignificantBitsFn.apply(bytes));

    private static final Function<Function<byte[], byte[]>, Function<byte[], UUID>> arrayStrategyToUuid = strategy -> bytes -> {
        if (UUID_LENGTH.isLengthValid(bytes))
            return strategy.andThen(uuidFromBytesFn).apply(bytes);
        throw new IllegalArgumentException("The byte array length is expected to be '" + UUID_LENGTH.BYTES + "'");
    };

    private static final Function<byte[], UUID> mixEndianToUuidFn = arrayStrategyToUuid.apply(toMixEndian);

    private static final Function<byte[], UUID> bigEndianToUuidFn = arrayStrategyToUuid.apply(x -> x);

    private static final Function<String, UUID> hexToUuidFn = hexToBytesFn.andThen(bigEndianToUuidFn);

    private static final Function<String, UUID> hexMixEndianToUuidFn = hexToBytesFn.andThen(mixEndianToUuidFn);

    private static final Function<BigInteger, UUID> bigIntegerToUuidFn = v -> {
        byte[] raw = v.toByteArray();
        byte[] bytes16 = new byte[UUID_LENGTH.BYTES.length()];
        int srcPos = Math.max(0, raw.length - 16);
        int destPos = Math.max(0, 16 - raw.length);
        System.arraycopy(raw, srcPos, bytes16, destPos, raw.length - srcPos);
        return bigEndianToUuidFn.apply(bytes16);
    };


    // --- Public API ---

    /// Converts a byte array to an uppercase hexadecimal string.
    ///
    /// @param bytes the byte array to convert
    /// @return uppercase hex string
    public static String bytesToHex(byte[] bytes) {
        return bytesToHexFn.apply(bytes);
    }

    /// Converts a UUID string (formatted or hex) to a 16-byte big-endian array.
    ///
    /// @param uuid the UUID string (formatted with dashes or raw 32-char hex)
    /// @return 16-byte big-endian array
    public static byte[] uuidToBytes(String uuid) {
        return uuidToBytesFn.apply(uuid);
    }

    /// Converts a UUID string to a 16-byte array in mixed-endian byte order.
    ///
    /// @param uuid the UUID string
    /// @return 16-byte mixed-endian array
    public static byte[] uuidToMixEndianBytes(String uuid) {
        return uuidToMixEndianBytesFn.apply(uuid);
    }

    /// Converts a 16-byte big-endian array to a `UUID`.
    ///
    /// @param bytes 16-byte big-endian array
    /// @return the corresponding UUID
    /// @throws IllegalArgumentException if the array is not exactly 16 bytes
    public static UUID bigEndianToUuid(byte[] bytes) {
        return bigEndianToUuidFn.apply(bytes);
    }

    /// Converts a 16-byte mixed-endian array to a `UUID`.
    ///
    /// @param bytes 16-byte mixed-endian array
    /// @return the corresponding UUID
    /// @throws IllegalArgumentException if the array is not exactly 16 bytes
    public static UUID mixEndianToUuid(byte[] bytes) {
        return mixEndianToUuidFn.apply(bytes);
    }

    /// Converts a 32-character hex string (no dashes) to a `UUID` using big-endian byte order.
    ///
    /// @param hex 32-character uppercase or lowercase hex string
    /// @return the corresponding UUID
    public static UUID hexToUuid(String hex) {
        return hexToUuidFn.apply(hex);
    }

    /// Converts a 32-character hex string (no dashes) to a `UUID` using mixed-endian byte order.
    ///
    /// @param hex 32-character uppercase or lowercase hex string
    /// @return the corresponding UUID
    public static UUID hexMixEndianToUuid(String hex) {
        return hexMixEndianToUuidFn.apply(hex);
    }

    /// Converts a `BigInteger` to a `UUID` via a 16-byte big-endian intermediate array.
    ///
    /// Handles both values shorter than 16 bytes (right-aligned with leading zeros)
    /// and the optional leading `0x00` sign byte that `toByteArray()` prepends for
    /// values whose bit 127 is set.
    ///
    /// @param value the BigInteger value (must be non-negative)
    /// @return the corresponding UUID
    public static UUID bigIntegerToUuid(BigInteger value) {
        return bigIntegerToUuidFn.apply(value);
    }

    /// Reads the most-significant 64 bits from a 16-byte UUID array.
    ///
    /// @param bytes 16-byte UUID array
    /// @return the most-significant long
    public static long mostSignificantBits(byte[] bytes) {
        return mostSignificantBitsFn.apply(bytes);
    }

    /// Reads the least-significant 64 bits from a 16-byte UUID array.
    ///
    /// @param bytes 16-byte UUID array
    /// @return the least-significant long
    public static long leastSignificantBits(byte[] bytes) {
        return leastSignificantBitsFn.apply(bytes);
    }

    /// Converts a `UUID` to its unsigned 128-bit `BigInteger` representation.
    ///
    /// `signum = 1` ensures the result is always non-negative, faithfully reflecting
    /// the unsigned nature of UUIDs (RFC 9562).
    ///
    /// @param uuid the UUID to convert
    /// @return a non-negative `BigInteger` holding the full 128-bit UUID value
    public static BigInteger toBigInteger(UUID uuid) {
        var bb = ByteBuffer.wrap(new byte[UUID_LENGTH.BYTES.length()]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return new BigInteger(1, bb.array());
    }

    private static final int SHA1_DIGEST_SIZE = 20;

    /// Converts a SHA-1 digest to a UUID version 5.
    ///
    /// Truncates the 160-bit digest to 128 bits, then stamps the version nibble
    /// (octet 6, bits 12–15 → `0x50`) and the variant bits (octet 8, bits 6–7 → `0x80`).
    ///
    /// @param digest 20-byte SHA-1 digest
    /// @return a UUID version 5
    /// @throws IllegalArgumentException if the digest is null or not exactly 20 bytes
    public static UUID nameUUIDFromSHA1Bytes(byte[] digest) {
        if (digest == null || digest.length != SHA1_DIGEST_SIZE) {
            throw new IllegalArgumentException("The digest array length is expected to be '" + SHA1_DIGEST_SIZE + "' bytes");
        }
        var digest16 = Arrays.copyOf(digest, 16);
        digest16[6] = (byte) ((digest16[6] & 0x0F) | 0x50);
        digest16[8] = (byte) ((digest16[8] & 0x3F) | 0x80);
        return bigIntegerToUuidFn.apply(new BigInteger(1, digest16));
    }

    /// Compares two UUID strings for equality, ignoring case, after a loose-pattern validation.
    ///
    /// @param uuid1 the first UUID string
    /// @param uuid2 the second UUID string
    /// @return `true` if both strings represent the same UUID value
    public static boolean uuidEquals(String uuid1, String uuid2) {
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

    /// Converts a 16-byte array to a formatted UUID string (`xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`).
    ///
    /// @param bytes the byte array (must be exactly 16 bytes)
    /// @return the formatted UUID string
    public static String bytesToUuidString(byte[] bytes) {
        return uuidFromBytesFn.apply(bytes).toString();
    }

    /// Returns the nil UUID `00000000-0000-0000-0000-000000000000` (all 128 bits zero, per RFC 9562).
    ///
    /// @return the nil UUID
    public static UUID nilUUID() {
        return new UUID(0L, 0L);
    }

    /// Returns the max UUID `ffffffff-ffff-ffff-ffff-ffffffffffff` (all 128 bits one, per RFC 9562).
    ///
    /// @return the max UUID
    public static UUID maxUUID() {
        return new UUID(-1L, -1L);
    }

    /// Tries to extract the Unix timestamp embedded in a UUID version 7.
    ///
    /// UUID v7 encodes the Unix epoch in milliseconds in the 48 most-significant bits
    /// (bits 127–80). Extracting them is a simple unsigned right-shift by 16.
    ///
    /// @param uuid the UUID to inspect
    /// @return an `Optional` containing the embedded `Instant`, or empty if the UUID is not version 7
    public static Optional<Instant> tryExtractTimestamp(UUID uuid) {
        if (uuid.version() != UuidType.TIME_ORDERED_EPOCH.version()) return Optional.empty();
        return Optional.of(Instant.ofEpochMilli(uuid.getMostSignificantBits() >>> 16));
    }

    /// Returns a randomly generated UUID using the provided `RandomGenerator`.
    ///
    /// Accepts any `RandomGenerator` implementation — use `ThreadLocalRandom.current()`
    /// for performance in production, or a seeded `Random` for reproducible tests.
    ///
    /// **Note:** the version and variant bits are _not_ set, so the result does not
    /// conform to RFC 4122 UUID v4.
    ///
    /// @param rng the random generator to use
    /// @return a randomly generated UUID
    public static UUID fastRandom(RandomGenerator rng) {
        return new UUID(rng.nextLong(), rng.nextLong());
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
        return fastRandom(ThreadLocalRandom.current());
    }

    private UuidPlus() {
        // Not instantiable class
    }

}