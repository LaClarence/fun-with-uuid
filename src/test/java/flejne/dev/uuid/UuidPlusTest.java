package flejne.dev.uuid;

import module java.base;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UuidPlusTest {

    private static final String VALID_STRICT = "adb41c24-2dbf-4a6d-958b-4457c0d27b95";
    private static final String VALID_HEXA_STRICT = "ADB41C242DBF4A6D958B4457C0D27B95";
    private static final String VALID_MIX_ENDIAN_HEXA_STRICT = "241CB4ADBF2D6D4A958B4457C0D27B95";

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

    private static final String KEYID_HEXA_BI = "458AEB12112F4459A2602F9D8F800564";
    private static final String KEYID_HEXA_LI = "12EB8A452F115944A2602F9D8F800564";

    @ParameterizedTest
    @ValueSource(strings = {VALID_NOT_STRICT, UuidPlus.NIL_UUID, VALID_STRICT})
    void string_uuid_size_should_be_valid(String candidate) {
        assertTrue(UuidPlus.isSizeValid.test(candidate), " Size of '" + candidate + "' is not valid!");
    }

    @ParameterizedTest
    @ValueSource(strings = {VALID_NOT_STRICT, UuidPlus.NIL_UUID, VALID_STRICT})
    void string_uuid_should_be_valid(String candidate) {
        assertTrue(UuidPlus.isPatternLoose.test(VALID_NOT_STRICT),
                " UUID hexa format of '" + candidate + "' is not valid!");
    }

    @ParameterizedTest
    @ValueSource(strings = {VALID_STRICT})
    void string_uuid_should_match_with_strict_pattern(String candidate) {
        assertTrue(UuidPlus.isPatternStrict.test(candidate));
    }

    @Test
    void convertKeyIdFromUuid() {
        var hexaString = UuidPlus.bytesToHexa.apply(KEYID_UUID_BYTES);
        assertTrue(UuidPlus.isSizeValid.test(hexaString), () -> "Hexa string size is not valid");
        assertEquals(KEYID_HEXA_BI, hexaString, () -> "Hexa string does not match");
        String hexa = UuidPlus.printHexBinary(KEYID_UUID_BYTES);
        assertTrue(UuidPlus.uuidEquals(KEYID_UUID, hexa), () -> "UUID string does not match!");
    }

    @Test
    void convertKeyIdFromUuidForMicrosoft() {
        assertEquals(KEYID_HEXA_LI, UuidPlus.bytesToHexa.apply(KEYID_UUID_BYTES_MS), () -> "Hexa string does not match");
        String hexaMS = UuidPlus.printHexBinary(KEYID_UUID_BYTES);
        assertTrue(UuidPlus.uuidEquals(KEYID_UUID, hexaMS), () -> "UUID string does not match!");
    }

    @Test
    void uuid_converted_to_big_endian_bytes() {
        byte[] uuidBytes = UuidPlus.uuidToBytes.apply(VALID_STRICT);
        assertEquals(VALID_HEXA_STRICT, UuidPlus.bytesToHexa.apply(uuidBytes));

        UUID uuidFromBytes = UuidPlus.bigEndianToUuid.apply(uuidBytes);
        assertEquals(UUID.fromString(VALID_STRICT), uuidFromBytes, () -> "UUID big endian should be equals");
    }

    @Test
    void uuid_converted_to_mix_endian_bytes() {
        byte[] uuidBytes = UuidPlus.uuidToMixEndianBytes.apply(VALID_STRICT);
        assertEquals(VALID_MIX_ENDIAN_HEXA_STRICT, UuidPlus.bytesToHexa.apply(uuidBytes));

        UUID uuidFromBytes = UuidPlus.mixEndianToUuid.apply(uuidBytes);
        assertEquals(UUID.fromString(VALID_STRICT), uuidFromBytes, () -> "UUID<>bytes mix endian should be equals");
    }

    @Test
    void uuidToBigInteger() {
        UUID uuid = UUID.randomUUID();
        BigInteger value = UuidPlus.toBigInteger(uuid);
        UUID result = UuidPlus.bigIntegerToUuid.apply(value);
        assertEquals(uuid.toString(), result.toString());
    }


    // --- isUuid ---

    @ParameterizedTest
    @ValueSource(strings = {VALID_STRICT, VALID_NOT_STRICT, UuidPlus.NIL_UUID, KEYID_UUID})
    void isUuid_should_return_true_for_valid_uuid(String candidate) {
        assertTrue(UuidPlus.isUuid(candidate));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "", "ZZZZZZZZ-ZZZZ-ZZZZ-ZZZZ-ZZZZZZZZZZZZ", "458aeb12-112f-4459-a260"})
    void isUuid_should_return_false_for_invalid_string(String candidate) {
        assertFalse(UuidPlus.isUuid(candidate));
    }

    @Test
    void isUuid_should_throw_for_null() {
        assertThrows(NullPointerException.class, () -> UuidPlus.isUuid(null));
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

    // --- hexaToUuid ---

    @Test
    void hexaToUuid_should_convert_big_endian_hex_to_uuid() {
        assertEquals(UUID.fromString(KEYID_UUID), UuidPlus.hexaToUuid.apply(KEYID_HEXA_BI));
    }

    // --- hexaMixEndianToUuid ---

    @Test
    void hexaMixEndianToUuid_should_convert_mix_endian_hex_to_uuid() {
        assertEquals(UUID.fromString(KEYID_UUID), UuidPlus.hexaMixEndianToUuid.apply(KEYID_HEXA_LI));
    }

    // --- hexaStringToUuid ---

    @Test
    void hexaStringToUuid_should_convert_hex_to_uuid() {
        assertEquals(UUID.fromString(KEYID_UUID), UuidPlus.hexaStringToUuid.apply(KEYID_HEXA_BI));
    }

    // --- mostSignificantBits / leastSignificantBits ---

    @Test
    void mostSignificantBits_should_extract_correctly() {
        assertEquals(UUID.fromString(KEYID_UUID).getMostSignificantBits(),
                UuidPlus.mostSignificantBits.apply(KEYID_UUID_BYTES));
    }

    @Test
    void leastSignificantBits_should_extract_correctly() {
        assertEquals(UUID.fromString(KEYID_UUID).getLeastSignificantBits(),
                UuidPlus.leastSignificantBits.apply(KEYID_UUID_BYTES));
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

    // --- arrayStrategyToUuid error path (via bigEndianToUuid / mixEndianToUuid) ---

    @Test
    void bigEndianToUuid_should_throw_for_invalid_byte_array_length() {
        assertThrows(IllegalArgumentException.class, () -> UuidPlus.bigEndianToUuid.apply(new byte[15]));
    }

    @Test
    void mixEndianToUuid_should_throw_for_invalid_byte_array_length() {
        assertThrows(IllegalArgumentException.class, () -> UuidPlus.mixEndianToUuid.apply(new byte[17]));
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

    private static <T> Duration ellapsedTime(int max, Supplier<T> supplier) {
        Instant start = Instant.now();
        for (int i = 0; i < max; i++)
            supplier.get();
        Instant end = Instant.now();
        return Duration.between(start, end);
    }

    @Test
    void isFastRamdomFastEnough() {
        int max = 10000000;
        Duration randomUUIDTime = ellapsedTime(max, UUID::randomUUID);
        Duration fastRandomUUIDTime = ellapsedTime(max, UuidPlus::fastRandom);

        assertTrue(fastRandomUUIDTime.compareTo(randomUUIDTime) < 0);
    }


}
