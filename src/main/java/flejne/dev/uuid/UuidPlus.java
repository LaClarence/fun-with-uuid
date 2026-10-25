package flejne.dev.uuid;

import module java.base;


/// Utility class for UUID manipulation, conversion and validation.
///
/// Supports round-tripping between UUID string formats (formatted, hex, mixed-endian),
/// byte arrays and `BigInteger`, as well as format validation and fast random UUID generation.
///
/// ## Strict UUID strings
///
/// `java.util.UUID.fromString()` is lenient for historical reasons: it reads each dash-separated
/// group as a hex number and left-pads it with zeros, so `"1-1-1-1-1"` is silently turned into
/// `00000001-0001-0001-0001-000000000001`. RFC 9562 defines the text form as exactly
/// 8-4-4-4-12 hex digits, and this class follows that definition everywhere:
///
/// - [#isUuid(String)] returns `true` only for the canonical form.
/// - [#fromString(String)] and [#tryFromString(String)] parse only what `isUuid` accepts.
/// - [#uuidToBytes(String)] and [#uuidToMixEndianBytes(String)] also accept 32 hex digits without dashes.
///
/// Letters may be upper- or lowercase. Being strict guarantees that a string accepted by one method
/// is accepted by all the others, and that `fromString(s).toString()` equals `s` (ignoring case),
/// so the same UUID can never be stored under two different spellings.
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
        /// Strict RFC 9562 pattern: enforces version digits `1`–`8` and variant bits `8`, `9`, `a` or `b`.
        STRICT("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[1-8][a-fA-F0-9]{3}-[89abAB][a-fA-F0-9]{3}-[a-fA-F0-9]{12}"),
        /// Loose pattern: only checks hex digits and dash placement, any version and variant are accepted.
        LOOSE("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"),
        /// Hex pattern: 32 hex digits without dashes.
        HEX("[a-fA-F0-9]{32}");

        REGEXP(final String value) {
            this.value = value;
            this.compiled = Pattern.compile(value);
        }

        private final String value;

        private final Pattern compiled;

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
            return compiled.matcher(input).matches();
        }

    }

    /// Predicate that validates a UUID string against the strict RFC 9562 pattern
    /// (versions 1–8, variant bits enforced), after a length pre-check.
    public static final Predicate<String> isPatternStrict = isSizeValid.and(REGEXP.STRICT::matches);

    /// Predicate that validates a UUID string against the loose pattern
    /// (hex digits and dash placement only), after a length pre-check.
    public static final Predicate<String> isPatternLoose = isSizeValid.and(REGEXP.LOOSE::matches);

    /// Predicate that accepts a UUID string either formatted with dashes or as 32 hex digits.
    private static final Predicate<String> isUuidOrHex = isPatternLoose.or(isSizeValid.and(REGEXP.HEX::matches));


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

    private static final UnaryOperator<String> requireUuidString = s -> {
        if (isUuidOrHex.test(s)) return s;
        throw new IllegalArgumentException("'" + s + "' is not a UUID string (formatted or 32 hex digits)");
    };

    private static final UnaryOperator<String> stripDashes = s -> s.replace("-", "");

    private static final Function<String, byte[]> uuidToBytesFn = requireUuidString.andThen(stripDashes).andThen(hexToBytesFn);

    private static final Function<String, byte[]> uuidToMixEndianBytesFn = uuidToBytesFn.andThen(toMixEndian);

    private static final UnaryOperator<byte[]> requireUuidBytes = bytes -> {
        if (UUID_LENGTH.isLengthValid(bytes)) return bytes;
        throw new IllegalArgumentException("The byte array length is expected to be '" + UUID_LENGTH.BYTES.length() + "'");
    };

    private static final IntFunction<Function<byte[], Long>> toLong = offset -> bytes ->
            ByteBuffer.wrap(requireUuidBytes.apply(bytes), offset, Long.BYTES).getLong();

    private static final int OFFSET_MSB = 0;

    private static final int OFFSET_LSB = 8;

    private static final Function<byte[], Long> mostSignificantBitsFn = toLong.apply(OFFSET_MSB);

    private static final Function<byte[], Long> leastSignificantBitsFn = toLong.apply(OFFSET_LSB);

    private static final Function<byte[], UUID> uuidFromBytesFn = bytes ->
            new UUID(mostSignificantBitsFn.apply(bytes), leastSignificantBitsFn.apply(bytes));

    private static final Function<Function<byte[], byte[]>, Function<byte[], UUID>> arrayStrategyToUuid = strategy ->
            requireUuidBytes.andThen(strategy).andThen(uuidFromBytesFn);

    private static final Function<byte[], UUID> mixEndianToUuidFn = arrayStrategyToUuid.apply(toMixEndian);

    private static final Function<byte[], UUID> bigEndianToUuidFn = arrayStrategyToUuid.apply(x -> x);

    private static final Function<String, UUID> hexToUuidFn = hexToBytesFn.andThen(bigEndianToUuidFn);

    private static final Function<String, UUID> hexMixEndianToUuidFn = hexToBytesFn.andThen(mixEndianToUuidFn);

    private static final int UUID_BITS = 128;

    private static final Function<BigInteger, UUID> bigIntegerToUuidFn = v -> {
        if (v.signum() < 0 || v.bitLength() > UUID_BITS) {
            throw new IllegalArgumentException("The value is expected to be an unsigned " + UUID_BITS + "-bit integer: " + v);
        }
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
    /// @throws IllegalArgumentException if `uuid` is neither a formatted UUID nor 32 hex digits
    public static byte[] uuidToBytes(String uuid) {
        return uuidToBytesFn.apply(uuid);
    }

    /// Converts a `UUID` to a 16-byte big-endian array.
    ///
    /// @param uuid the UUID to convert
    /// @return 16-byte big-endian array
    public static byte[] uuidToBytes(UUID uuid) {
        return ByteBuffer.allocate(UUID_LENGTH.BYTES.length())
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }

    /// Converts a UUID string to a 16-byte array in mixed-endian byte order.
    ///
    /// @param uuid the UUID string (formatted with dashes or raw 32-char hex)
    /// @return 16-byte mixed-endian array
    /// @throws IllegalArgumentException if `uuid` is neither a formatted UUID nor 32 hex digits
    public static byte[] uuidToMixEndianBytes(String uuid) {
        return uuidToMixEndianBytesFn.apply(uuid);
    }

    /// Converts a `UUID` to a 16-byte array in mixed-endian byte order.
    ///
    /// @param uuid the UUID to convert
    /// @return 16-byte mixed-endian array
    public static byte[] uuidToMixEndianBytes(UUID uuid) {
        return toMixEndian.apply(uuidToBytes(uuid));
    }

    /// Converts a `UUID` to a 32-character uppercase hex string (no dashes), big-endian.
    ///
    /// @param uuid the UUID to convert
    /// @return 32-character uppercase hex string
    public static String uuidToHex(UUID uuid) {
        return bytesToHexFn.apply(uuidToBytes(uuid));
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
    /// @param value the BigInteger value (must be non-negative and fit in 128 bits)
    /// @return the corresponding UUID
    /// @throws IllegalArgumentException if `value` is negative or longer than 128 bits
    public static UUID bigIntegerToUuid(BigInteger value) {
        return bigIntegerToUuidFn.apply(value);
    }

    /// Reads the most-significant 64 bits from a 16-byte UUID array.
    ///
    /// @param bytes 16-byte UUID array
    /// @return the most-significant long
    /// @throws IllegalArgumentException if the array is not exactly 16 bytes
    public static long mostSignificantBits(byte[] bytes) {
        return mostSignificantBitsFn.apply(bytes);
    }

    /// Reads the least-significant 64 bits from a 16-byte UUID array.
    ///
    /// @param bytes 16-byte UUID array
    /// @return the least-significant long
    /// @throws IllegalArgumentException if the array is not exactly 16 bytes
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
        return new BigInteger(1, uuidToBytes(uuid));
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

    /// Silently checks whether a string is a valid UUID in canonical form (`xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`).
    ///
    /// Unlike `UUID.fromString()`, shortened groups such as `1-1-1-1-1` are rejected
    /// (see "Strict UUID strings" in the class documentation). Any version and variant are accepted;
    /// use [#isPatternStrict] to also check them.
    ///
    /// ```
    /// isUuid("adb41c24-2dbf-4a6d-958b-4457c0d27b95") // true
    /// isUuid("ADB41C24-2DBF-4A6D-958B-4457C0D27B95") // true
    /// isUuid("1-1-1-1-1")                            // false, although UUID.fromString accepts it
    /// isUuid("adb41c242dbf4a6d958b4457c0d27b95")     // false, no dashes
    /// isUuid(null)                                   // false
    /// ```
    ///
    /// @param uuid the string to validate, may be `null`
    /// @return `true` if `uuid` is a well-formed UUID string, `false` otherwise (including `null`)
    public static boolean isUuid(final String uuid) {
        return isPatternLoose.test(uuid);
    }

    /// Parses a UUID string in canonical form (`xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`).
    ///
    /// Strict counterpart of `UUID.fromString()`: shortened groups such as `1-1-1-1-1`,
    /// which the JDK silently pads with zeros, are rejected. Accepts exactly the strings
    /// for which [#isUuid(String)] returns `true`.
    ///
    /// ```
    /// UUID.fromString("1-1-1-1-1")     // 00000001-0001-0001-0001-000000000001
    /// UuidPlus.fromString("1-1-1-1-1") // IllegalArgumentException
    /// ```
    ///
    /// @param uuid the string to parse
    /// @return the corresponding UUID
    /// @throws IllegalArgumentException if `uuid` is `null` or not in canonical form
    public static UUID fromString(final String uuid) {
        if (!isUuid(uuid)) {
            throw new IllegalArgumentException("'" + uuid + "' is not a UUID in canonical form (8-4-4-4-12 hex digits)");
        }
        return UUID.fromString(uuid);
    }

    /// Parses a UUID string in canonical form, without throwing.
    ///
    /// Same rules as [#fromString(String)].
    ///
    /// @param uuid the string to parse, may be `null`
    /// @return an `Optional` containing the UUID, or empty if `uuid` is `null` or not in canonical form
    public static Optional<UUID> tryFromString(final String uuid) {
        return isUuid(uuid) ? Optional.of(UUID.fromString(uuid)) : Optional.empty();
    }

    /// Returns the type of a UUID, derived from its version field.
    ///
    /// @param uuid the UUID to inspect, may be `null`
    /// @return the UUID type, `UuidType.UNDEFINED` for `null` or an unknown version
    public static UuidType typeOf(UUID uuid) {
        return UuidType.type(uuid);
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
    /// @param uuid the UUID to inspect, may be `null`
    /// @return an `Optional` containing the embedded `Instant`, or empty if the UUID is `null` or not version 7
    public static Optional<Instant> tryExtractTimestamp(UUID uuid) {
        if (uuid == null || uuid.version() != UuidType.TIME_ORDERED_EPOCH.version()) return Optional.empty();
        return Optional.of(Instant.ofEpochMilli(uuid.getMostSignificantBits() >>> 16));
    }

    // --- Fast random generation ---
    //
    // The default generator is a per-thread L128X256MixRandom rather than ThreadLocalRandom:
    // ThreadLocalRandom has a single 64-bit state, so it can produce at most 2^64 distinct UUIDs
    // and duplicates follow the 64-bit birthday bound (~50% after 4 billion UUIDs).
    // L128X256MixRandom has a 384-bit state (128-bit LCG + 256-bit xoshiro), keeping collisions
    // at the level a 122-bit random UUID is designed for.
    //
    // Its no-argument constructor seeds from a single 64-bit value, which would bring the 2^64
    // limit back across generator instances; each one is therefore seeded with a full 64-byte
    // seed (8 longs) from SecureRandom, once per thread.

    private static final String FAST_RNG_ALGORITHM = "L128X256MixRandom";

    private static final int FAST_RNG_SEED_BYTES = 64;

    private static final RandomGeneratorFactory<RandomGenerator> FAST_RNG_FACTORY = RandomGeneratorFactory.of(FAST_RNG_ALGORITHM);

    private static final SecureRandom SEEDER = new SecureRandom();

    private static final ThreadLocal<RandomGenerator> FAST_RNG = ThreadLocal.withInitial(() -> {
        var seed = new byte[FAST_RNG_SEED_BYTES];
        SEEDER.nextBytes(seed);
        return FAST_RNG_FACTORY.create(seed);
    });

    /// Returns a randomly generated UUID using the provided `RandomGenerator`.
    ///
    /// Accepts any `RandomGenerator` implementation, for example a seeded `Random` for
    /// reproducible tests. The UUID is only as unique as the generator: a generator with a
    /// 64-bit state, such as `ThreadLocalRandom` or `Random`, can produce at most 2^64
    /// distinct UUIDs.
    ///
    /// **Note:** the version and variant bits are _not_ set, so the result does not
    /// conform to RFC 9562 UUID v4. Use [#fastRandomV4(RandomGenerator)] for valid v4 UUIDs.
    ///
    /// @param rng the random generator to use
    /// @return a randomly generated UUID
    public static UUID fastRandom(RandomGenerator rng) {
        return new UUID(rng.nextLong(), rng.nextLong());
    }

    /// Returns a randomly generated UUID using a fast, non-cryptographic generator.
    ///
    /// Faster than `UUID.randomUUID()` because it avoids `SecureRandom` on each call.
    /// See [#fastRandomV4()] for the generator used and its limits.
    ///
    /// **Note:** the version and variant bits are _not_ set, so the result does not
    /// conform to RFC 9562 UUID v4. Use [#fastRandomV4()] for valid v4 UUIDs.
    ///
    /// @return a randomly generated UUID
    public static UUID fastRandom() {
        return fastRandom(FAST_RNG.get());
    }

    private static final long VERSION_MASK = 0xFFFFFFFFFFFF0FFFL;
    private static final long VERSION_4 = 0x0000000000004000L;
    private static final long VARIANT_MASK = 0x3FFFFFFFFFFFFFFFL;
    private static final long VARIANT_RFC = 0x8000000000000000L;

    /// Returns an RFC 9562 version 4 UUID using the provided `RandomGenerator`.
    ///
    /// Same as [#fastRandom(RandomGenerator)], with the version (`4`) and variant (`10`) bits set,
    /// leaving 122 random bits.
    ///
    /// **Note:** not suitable where unpredictability matters unless `rng` is cryptographically strong,
    /// and only as unique as `rng`: a generator with a 64-bit state (`ThreadLocalRandom`, `Random`)
    /// gives a ~50% chance of a duplicate after about 4 billion UUIDs.
    ///
    /// @param rng the random generator to use
    /// @return a version 4 UUID
    public static UUID fastRandomV4(RandomGenerator rng) {
        long msb = (rng.nextLong() & VERSION_MASK) | VERSION_4;
        long lsb = (rng.nextLong() & VARIANT_MASK) | VARIANT_RFC;
        return new UUID(msb, lsb);
    }

    /// Returns an RFC 9562 version 4 UUID using a fast, non-cryptographic generator.
    ///
    /// Each thread uses its own `L128X256MixRandom` (384-bit state), seeded once from
    /// `SecureRandom`, so collisions stay as unlikely as with `UUID.randomUUID()`
    /// while avoiding `SecureRandom` on each call.
    ///
    /// **Not cryptographically secure:** the output can be predicted from previous outputs.
    /// Use `UUID.randomUUID()` for anything that must be unguessable (session ids, tokens,
    /// password-reset links, ids exposed in public URLs).
    ///
    /// Seeding costs one `SecureRandom` call per thread, which is negligible for pooled
    /// threads, but comparable to `UUID.randomUUID()` when each UUID is generated by a new
    /// short-lived (e.g. virtual) thread.
    ///
    /// @return a version 4 UUID
    public static UUID fastRandomV4() {
        return fastRandomV4(FAST_RNG.get());
    }

    private UuidPlus() {
        // Not instantiable class
    }

}