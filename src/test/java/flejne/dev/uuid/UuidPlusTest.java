package flejne.dev.uuid;

import module java.base;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UuidPlusTest {

    private static final String VALID_STRICT = "adb41c24-2dbf-4a6d-958b-4457c0d27b95";
    private static final String VALID_HEX_STRICT = "ADB41C242DBF4A6D958B4457C0D27B95";
    private static final String VALID_MIX_ENDIAN_HEX_STRICT = "241CB4ADBF2D6D4A958B4457C0D27B95";

    private static final String VALID_NOT_STRICT = "adb41c24-2dbf-5a6d-958b-4457c0d27b95";
    private static final String KEYID_UUID = "458aeb12-112f-4459-a260-2f9d8f800564";
    private static final byte[] KEYID_UUID_BYTES = {
            (byte) 0x45, (byte) 0x8a, (byte) 0xeb, (byte) 0x12,
            (byte) 0x11, (byte) 0x2f, (byte) 0x44, (byte) 0x59,
            (byte) 0xa2, (byte) 0x60, (byte) 0x2f, (byte) 0x9d,
            (byte) 0x8f, (byte) 0x80, (byte) 0x05, (byte) 0x64};

    private static final byte[] KEYID_UUID_BYTES_MS = {
            (byte) 0x12, (byte) 0xEB, (byte) 0x8A, (byte) 0x45,
            (byte) 0x2F, (byte) 0x11, (byte) 0x59, (byte) 0x44,
            (byte) 0xA2, (byte) 0x60, (byte) 0x2F, (byte) 0x9D,
            (byte) 0x8F, (byte) 0x80, (byte) 0x05, (byte) 0x64};

    private static final String KEYID_HEX_BI = "458AEB12112F4459A2602F9D8F800564";
    private static final String KEYID_HEX_LI = "12EB8A452F115944A2602F9D8F800564";



    @ParameterizedTest
    @ValueSource(strings = {VALID_NOT_STRICT, UuidPlus.NIL_UUID, VALID_STRICT})
    void string_uuid_size_should_be_valid(String candidate) {
        assertTrue(UuidPlus.isSizeValid.test(candidate), " Size of '" + candidate + "' is not valid!");
    }

    @ParameterizedTest
    @ValueSource(strings = {VALID_NOT_STRICT, UuidPlus.NIL_UUID, VALID_STRICT})
    void string_uuid_should_be_valid(String candidate) {
        assertTrue(UuidPlus.isPatternLoose.test(VALID_NOT_STRICT),
                " UUID hex format of '" + candidate + "' is not valid!");
    }

    @ParameterizedTest
    @ValueSource(strings = {VALID_STRICT})
    void string_uuid_should_match_with_strict_pattern(String candidate) {
        assertTrue(UuidPlus.isPatternStrict.test(candidate));
    }

    static final Supplier<String> hexStringDoesNotMatch = () -> "Hex string does not match";
    static final Supplier<String> uuidStringDoesNotMatch = () -> "UUID string does not match";

    @Test
    void convertKeyIdFromUuid() {
        var hexaString = UuidPlus.bytesToHex(KEYID_UUID_BYTES);
        assertTrue(UuidPlus.isSizeValid.test(hexaString), hexStringDoesNotMatch);
        assertEquals(KEYID_HEX_BI, hexaString, hexStringDoesNotMatch);
        String hex = UuidPlus.bytesToUuidString(KEYID_UUID_BYTES);
        assertTrue(UuidPlus.uuidEquals(KEYID_UUID, hex), uuidStringDoesNotMatch);
    }

    @Test
    void convertKeyIdFromUuidForMicrosoft() {
        assertEquals(KEYID_HEX_LI, UuidPlus.bytesToHex(KEYID_UUID_BYTES_MS), hexStringDoesNotMatch);
        String hexMS = UuidPlus.bytesToUuidString(KEYID_UUID_BYTES);
        assertTrue(UuidPlus.uuidEquals(KEYID_UUID, hexMS), uuidStringDoesNotMatch);
    }

    @Test
    void uuid_converted_to_big_endian_bytes() {
        byte[] uuidBytes = UuidPlus.uuidToBytes(VALID_STRICT);
        assertEquals(VALID_HEX_STRICT, UuidPlus.bytesToHex(uuidBytes));

        UUID uuidFromBytes = UuidPlus.bigEndianToUuid(uuidBytes);
        assertEquals(UUID.fromString(VALID_STRICT), uuidFromBytes, () -> "UUID big endian should be equals");
    }

    @Test
    void uuid_converted_to_mix_endian_bytes() {
        byte[] uuidBytes = UuidPlus.uuidToMixEndianBytes(VALID_STRICT);
        assertEquals(VALID_MIX_ENDIAN_HEX_STRICT, UuidPlus.bytesToHex(uuidBytes));

        UUID uuidFromBytes = UuidPlus.mixEndianToUuid(uuidBytes);
        assertEquals(UUID.fromString(VALID_STRICT), uuidFromBytes, () -> "UUID<>bytes mix endian should be equals");
    }

    @Test
    void uuidToBigInteger() {
        UUID uuid = UUID.randomUUID();
        BigInteger value = UuidPlus.toBigInteger(uuid);
        UUID result = UuidPlus.bigIntegerToUuid(value);
        assertEquals(uuid.toString(), result.toString());
    }

    @Test
    void toBigInteger_max_uuid_should_be_positive_and_round_trip() {
        BigInteger maxValue = UuidPlus.toBigInteger(UuidPlus.maxUUID());

        assertTrue(maxValue.signum() > 0, "BigInteger representation of MAX UUID must be positive");
        assertEquals(BigInteger.TWO.pow(128).subtract(BigInteger.ONE), maxValue, "MAX UUID must equal 2^128 - 1");
        assertEquals(UuidPlus.maxUUID(), UuidPlus.bigIntegerToUuid(maxValue), "Round-trip MAX UUID must be preserved");
    }

    @Test
    void toBigInteger_nil_uuid_should_be_zero_and_round_trip() {
        BigInteger value = UuidPlus.toBigInteger(UuidPlus.nilUUID());

        assertEquals(BigInteger.ZERO, value, "NIL UUID must equal zero");
        assertEquals(UuidPlus.nilUUID(), UuidPlus.bigIntegerToUuid(value), "Round-trip NIL UUID must be preserved");
    }


    // --- isUuid ---

    @ParameterizedTest
    @ValueSource(strings = {VALID_STRICT, VALID_NOT_STRICT, UuidPlus.NIL_UUID, KEYID_UUID})
    void isUuid_should_return_true_for_valid_uuid(String candidate) {
        assertTrue(UuidPlus.isUuid(candidate));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "", "ZZZZZZZZ-ZZZZ-ZZZZ-ZZZZ-ZZZZZZZZZZZZ", "458aeb12-112f-4459-a260",
            "1-1-1-1-1", "458aeb12-112f-4459-a260-2f9d8f80056", VALID_HEX_STRICT})
    void isUuid_should_return_false_for_invalid_string(String candidate) {
        assertFalse(UuidPlus.isUuid(candidate));
    }

    @Test
    void isUuid_should_return_false_for_null() {
        assertFalse(UuidPlus.isUuid(null));
    }

    // --- fromString / tryFromString ---

    @ParameterizedTest
    @ValueSource(strings = {VALID_STRICT, VALID_NOT_STRICT, UuidPlus.NIL_UUID, UuidPlus.MAX_UUID, "ADB41C24-2DBF-4A6D-958B-4457C0D27B95"})
    void fromString_should_parse_canonical_uuid(String candidate) {
        var expected = UUID.fromString(candidate);
        assertEquals(expected, UuidPlus.fromString(candidate));
        assertEquals(Optional.of(expected), UuidPlus.tryFromString(candidate));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1-1-1-1-1", "0-0-0-0-0", "458aeb12-112f-4459-a260-2f9d8f80056", "not-a-uuid", "", VALID_HEX_STRICT})
    void fromString_should_reject_non_canonical_string(String candidate) {
        assertThrows(IllegalArgumentException.class, () -> UuidPlus.fromString(candidate));
        assertTrue(UuidPlus.tryFromString(candidate).isEmpty());
    }

    @Test
    void fromString_should_reject_null() {
        assertThrows(IllegalArgumentException.class, () -> UuidPlus.fromString(null));
        assertTrue(UuidPlus.tryFromString(null).isEmpty());
    }

    @Test
    void jdk_fromString_accepts_shortened_form_that_fromString_rejects() {
        assertEquals("00000001-0001-0001-0001-000000000001", UUID.fromString("1-1-1-1-1").toString());
        assertFalse(UuidPlus.isUuid("1-1-1-1-1"));
    }

    // --- isNil ---

    @Test
    void nil_predicate_should_match_nil_uuid() {
        assertTrue(UuidPlus.isNil.test(UuidPlus.NIL_UUID));
    }

    @ParameterizedTest
    @ValueSource(strings = {VALID_STRICT, KEYID_UUID})
    void nil_predicate_should_not_match_non_nil_uuid(String candidate) {
        assertFalse(UuidPlus.isNil.test(candidate));
    }

    // --- isMax ---

    @Test
    void max_predicate_should_match_max_uuid() {
        assertTrue(UuidPlus.isMax.test(UuidPlus.MAX_UUID));
        assertTrue(UuidPlus.isMax.test(UuidPlus.MAX_UUID.toUpperCase()));
    }

    @ParameterizedTest
    @ValueSource(strings = {VALID_STRICT, KEYID_UUID, UuidPlus.NIL_UUID})
    void max_predicate_should_not_match_non_max_uuid(String candidate) {
        assertFalse(UuidPlus.isMax.test(candidate));
    }

    // --- isSizeValid byte[] ---

    @Test
    void byte_array_size_should_be_valid() {
        assertTrue(UuidPlus.UUID_LENGTH.isLengthValid(KEYID_UUID_BYTES));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 15, 17})
    void byte_array_size_should_be_invalid_for_wrong_length(int length) {
        assertFalse(UuidPlus.UUID_LENGTH.isLengthValid(new byte[length]));
    }

    // --- isPatternStrict negative ---

    @ParameterizedTest
    @ValueSource(strings = {UuidPlus.NIL_UUID, "adb41c24-2dbf-0a6d-258b-4457c0d27b95"})
    void string_uuid_should_not_match_strict_pattern(String candidate) {
        assertFalse(UuidPlus.isPatternStrict.test(candidate));
    }

    // --- hexToUuid ---

    @Test
    void hexToUuid_should_convert_big_endian_hex_to_uuid() {
        assertEquals(UUID.fromString(KEYID_UUID), UuidPlus.hexToUuid(KEYID_HEX_BI));
    }

    // --- hexMixEndianToUuid ---

    @Test
    void hexMixEndianToUuid_should_convert_mix_endian_hex_to_uuid() {
        assertEquals(UUID.fromString(KEYID_UUID), UuidPlus.hexMixEndianToUuid(KEYID_HEX_LI));
    }

    // --- mostSignificantBits / leastSignificantBits ---

    @Test
    void mostSignificantBits_should_extract_correctly() {
        assertEquals(UUID.fromString(KEYID_UUID).getMostSignificantBits(),
                UuidPlus.mostSignificantBits(KEYID_UUID_BYTES));
    }

    @Test
    void leastSignificantBits_should_extract_correctly() {
        assertEquals(UUID.fromString(KEYID_UUID).getLeastSignificantBits(),
                UuidPlus.leastSignificantBits(KEYID_UUID_BYTES));
    }

    // --- uuidEquals negative cases ---

    @Test
    void uuidEquals_should_return_false_when_second_arg_is_null() {
        assertFalse(UuidPlus.uuidEquals(KEYID_UUID, null));
    }

    @Test
    void uuidEquals_should_be_case_insensitive() {
        assertTrue(UuidPlus.uuidEquals(KEYID_UUID, KEYID_UUID.toUpperCase()));
    }

    @Test
    void uuidEquals_should_return_false_for_different_uuids() {
        assertFalse(UuidPlus.uuidEquals(KEYID_UUID, VALID_STRICT));
    }

    @Test
    void uuidEquals_should_return_false_for_non_uuid_pattern() {
        assertFalse(UuidPlus.uuidEquals("not-a-uuid", "not-a-uuid"));
    }

    // --- bigEndianToUuid / mixEndianToUuid error path ---

    @Test
    void bigEndianToUuid_should_throw_for_invalid_byte_array_length() {
        assertThrows(IllegalArgumentException.class, () -> UuidPlus.bigEndianToUuid(new byte[15]));
    }

    @Test
    void mixEndianToUuid_should_throw_for_invalid_byte_array_length() {
        assertThrows(IllegalArgumentException.class, () -> UuidPlus.mixEndianToUuid(new byte[17]));
    }

    // --- fastRandom structural ---

    @Test
    void fastRandom_should_return_non_null() {
        assertNotNull(UuidPlus.fastRandom());
    }

    @Test
    void fastRandom_should_produce_different_values() {
        assertNotEquals(UuidPlus.fastRandom(), UuidPlus.fastRandom());
    }

    @Test
    void fastRandom_with_seeded_random_should_be_reproducible() {
        long seed = 42L;
        assertEquals(UuidPlus.fastRandom(new Random(seed)), UuidPlus.fastRandom(new Random(seed)));
    }

    @Test
    void fastRandomV4_should_not_repeat_across_threads() throws Exception {
        int threads = 8;
        int perThread = 10_000;
        try (var executor = Executors.newFixedThreadPool(threads)) {
            var tasks = Collections.nCopies(threads, (Callable<List<UUID>>) () ->
                    Stream.generate(UuidPlus::fastRandomV4).limit(perThread).toList());
            Set<UUID> all = new HashSet<>();
            for (var future : executor.invokeAll(tasks)) {
                all.addAll(future.get());
            }
            assertEquals(threads * perThread, all.size(), "Each thread must use a differently seeded generator");
        }
    }

    // --- tryExtractTimestamp ---

    @Test
    void tryExtractTimestamp_should_extract_embedded_instant_from_v7() {
        long epochMilli = Instant.now().toEpochMilli();
        UUID uuid = UUID.ofEpochMillis(epochMilli);
        assertEquals(Optional.of(Instant.ofEpochMilli(epochMilli)), UuidPlus.tryExtractTimestamp(uuid));
    }

    @Test
    void tryExtractTimestamp_should_return_empty_for_non_v7_uuid() {
        assertTrue(UuidPlus.tryExtractTimestamp(UUID.randomUUID()).isEmpty());
    }

    @Test
    void tryExtractTimestamp_should_return_empty_for_null() {
        assertTrue(UuidPlus.tryExtractTimestamp(null).isEmpty());
    }

    // --- bigIntegerToUuid bounds ---

    @Test
    void bigIntegerToUuid_should_throw_for_negative_value() {
        assertThrows(IllegalArgumentException.class, () -> UuidPlus.bigIntegerToUuid(BigInteger.valueOf(-1)));
    }

    @Test
    void bigIntegerToUuid_should_throw_for_value_longer_than_128_bits() {
        assertThrows(IllegalArgumentException.class, () -> UuidPlus.bigIntegerToUuid(BigInteger.ONE.shiftLeft(128)));
    }

    @Test
    void bigIntegerToUuid_should_accept_largest_128_bit_value() {
        var max = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE);
        assertEquals(UuidPlus.maxUUID(), UuidPlus.bigIntegerToUuid(max));
    }

    // --- isPatternStrict RFC 9562 versions ---

    @ParameterizedTest
    @ValueSource(strings = {
            "1ec9414c-232a-6b00-b3c8-9e6bdeced846",  // v6
            "01890a5d-ac96-774b-bcce-b302099a8057",  // v7
            "320c3d4d-cc00-875b-8ec9-32d5f69181c0"}) // v8
    void string_uuid_v6_to_v8_should_match_strict_pattern(String candidate) {
        assertTrue(UuidPlus.isPatternStrict.test(candidate));
    }

    @Test
    void string_uuid_v9_should_not_match_strict_pattern() {
        assertFalse(UuidPlus.isPatternStrict.test("adb41c24-2dbf-9a6d-958b-4457c0d27b95"));
    }

    // --- input validation ---

    @ParameterizedTest
    @ValueSource(strings = {"abcd", "", "not-a-uuid", "ZZB41C242DBF4A6D958B4457C0D27B95", "1-1-1-1-1"})
    void uuidToBytes_should_throw_for_invalid_string(String candidate) {
        assertThrows(IllegalArgumentException.class, () -> UuidPlus.uuidToBytes(candidate));
        assertThrows(IllegalArgumentException.class, () -> UuidPlus.uuidToMixEndianBytes(candidate));
    }

    @Test
    void uuidToBytes_should_accept_hex_string() {
        assertArrayEquals(UuidPlus.uuidToBytes(VALID_STRICT), UuidPlus.uuidToBytes(VALID_HEX_STRICT));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4, 15, 17})
    void significant_bits_should_throw_for_invalid_byte_array_length(int length) {
        assertThrows(IllegalArgumentException.class, () -> UuidPlus.mostSignificantBits(new byte[length]));
        assertThrows(IllegalArgumentException.class, () -> UuidPlus.leastSignificantBits(new byte[length]));
        assertThrows(IllegalArgumentException.class, () -> UuidPlus.bytesToUuidString(new byte[length]));
    }

    // --- UUID overloads ---

    @Test
    void uuidToBytes_from_uuid_should_match_string_conversion() {
        var uuid = UUID.fromString(KEYID_UUID);
        assertArrayEquals(KEYID_UUID_BYTES, UuidPlus.uuidToBytes(uuid));
        assertArrayEquals(KEYID_UUID_BYTES_MS, UuidPlus.uuidToMixEndianBytes(uuid));
        assertEquals(KEYID_HEX_BI, UuidPlus.uuidToHex(uuid));
    }

    @Test
    void uuidToHex_should_round_trip() {
        var uuid = UUID.randomUUID();
        assertEquals(uuid, UuidPlus.hexToUuid(UuidPlus.uuidToHex(uuid)));
    }

    // --- typeOf ---

    @Test
    void typeOf_should_return_uuid_type() {
        assertEquals(UuidType.RANDOM, UuidPlus.typeOf(UUID.randomUUID()));
        assertEquals(UuidType.TIME_ORDERED_EPOCH, UuidPlus.typeOf(UUID.ofEpochMillis(0L)));
        assertEquals(UuidType.UNDEFINED, UuidPlus.typeOf(null));
    }

    // --- fastRandomV4 ---

    @Test
    void fastRandomV4_should_be_rfc_version_4() {
        for (int i = 0; i < 1_000; i++) {
            UUID uuid = UuidPlus.fastRandomV4();
            assertEquals(4, uuid.version());
            assertEquals(2, uuid.variant());
            assertTrue(UuidPlus.isPatternStrict.test(uuid.toString()));
        }
    }

    @Test
    void fastRandomV4_with_seeded_random_should_be_reproducible() {
        assertEquals(UuidPlus.fastRandomV4(new Random(42)), UuidPlus.fastRandomV4(new Random(42)));
    }



}