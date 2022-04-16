package flejne.dev.uuid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

import java.lang.String;

public class UuidPlusTest {

	private final String validStrict = "adb41c24-2dbf-4a6d-958b-4457c0d27b95";
	private final String validHexaStrict = "ADB41C242DBF4A6D958B4457C0D27B95";
	private final String validMixEndianHexaStrict = "241CB4ADBF2D6D4A958B4457C0D27B95";

	private final String validNotStrict = "adb41c24-2dbf-5a6d-958b-4457c0d27b95";
	private final String KEYID_UUID = "458aeb12-112f-4459-a260-2f9d8f800564";
	private final byte[] KEYID_UUID_BYTES = {
			(byte) 0x45, (byte) 0x8a, (byte) 0xeb, (byte) 0x12, 
			(byte) 0x11, (byte) 0x2f, (byte) 0x44, (byte) 0x59, 
			(byte) 0xa2, (byte) 0x60, (byte) 0x2f, (byte) 0x9d, 
			(byte) 0x8f, (byte) 0x80, (byte) 0x05, (byte) 0x64};

	private final byte[] KEYID_UUID_BYTES_MS = {
			(byte) 0x12, (byte) 0xEB, (byte) 0x8A, (byte) 0x45, 
			(byte) 0x2F, (byte) 0x11, (byte) 0x59, (byte) 0x44, 
			(byte) 0xA2, (byte) 0x60, (byte) 0x2F, (byte) 0x9D, 
			(byte) 0x8F, (byte) 0x80, (byte) 0x05, (byte) 0x64};
	
	private final String KEYID_HEXA_BI = "458AEB12112F4459A2602F9D8F800564";
	private final String KEYID_HEXA_LI = "12EB8A452F115944A2602F9D8F800564";

	@ParameterizedTest
	@ValueSource(strings = { validNotStrict, UuidPlus.EMPTY, validStrict })
	public void string_uuid_size_should_be_valid(String candidate) {
		assertTrue(UuidPlus.isStringSizeValid.test(candidate), " Size of '" + candidate + "' is not valid!");
	}

	@ParameterizedTest
	@ValueSource(strings = { validNotStrict, UuidPlus.EMPTY, validStrict })
	public void string_uuid_should_be_valid(String candidate) {
		assertTrue(UuidPlus.isPatternLoose.test(validNotStrict),
				" UUID hexa format of '" + candidate + "' is not valid!");
	}

	@ParameterizedTest
	@ValueSource(strings = { validStrict })
	public void string_uuid_should_match_with_strict_pattern(String candidate) {
		assertTrue(UuidPlus.isPatternStrict.test(candidate));
	}

	@Test
	public void convertKeyIdFromUuid() {
	  var HEXASTRING = UuidPlus.bytesToHexa.apply(KEYID_UUID_BYTES);
	  assertTrue(UuidPlus.hexaStringSizeValid.test(HEXASTRING), () -> "Hexa string size is not valid");
		assertEquals(KEYID_HEXA_BI, HEXASTRING, () -> "Hexa string does not match");
		String hexa = UuidPlus.printHexBinary(KEYID_UUID_BYTES);
		assertTrue(UuidPlus.uuidEquals(KEYID_UUID, hexa),() -> "UUID string does not match!");
	}

	@Test
	public void convertKeyIdFromUuidForMicrosoft() {
	   assertEquals(KEYID_HEXA_LI, UuidPlus.bytesToHexa.apply(KEYID_UUID_BYTES_MS), () -> "Hexa string does not match");
	   String hexaMS = UuidPlus.printHexBinary(KEYID_UUID_BYTES);
	   assertTrue(UuidPlus.uuidEquals(KEYID_UUID, hexaMS),() -> "UUID string does not match!");
	}

	@Test
	public void uuid_converted_to_big_endian_bytes() {
		byte[] uuidBytes = UuidPlus.uuidToBytes.apply(validStrict);
		assertEquals(validHexaStrict, UuidPlus.bytesToHexa.apply(uuidBytes));

		UUID uuidFromBytes = UuidPlus.bigEndianToUuid.apply(uuidBytes);
		assertEquals(UUID.fromString(validStrict), uuidFromBytes, () -> "UUID big endian should be equals");
	}

	@Test
	public void uuid_converted_to_mix_endian_bytes() {
		byte[] uuidBytes = UuidPlus.uuidToMixEndianBytes.apply(validStrict);
		assertEquals(validMixEndianHexaStrict, UuidPlus.bytesToHexa.apply(uuidBytes));

		UUID uuidFromBytes = UuidPlus.mixEndianToUuid.apply(uuidBytes);
		assertEquals(UUID.fromString(validStrict), uuidFromBytes, () -> "UUID<>bytes mix endian should be equals");
	}

	@Test
	public void uuidToBigInteger() {
		UUID uuid = UUID.randomUUID();
		BigInteger value = UuidPlus.toBigInteger(uuid);
		UUID result = UuidPlus.bigIntegerToUuid.apply(value);
		assertEquals(uuid.toString(), result.toString());
	}
	
	

	private static <T> Duration ellapsedTime(int max, Supplier<T> supplier) {
				Instant start = Instant.now(); 
				for (int i = 0 ; i < max ; i++)
					supplier.get();
				Instant end = Instant.now(); 
				return Duration.between(start, end);
	}

	@Test
	public void isFastRamdomFastEnough() {
		int max = 10000000;
		Duration randomUUIDTime = ellapsedTime(max, UUID::randomUUID);
		Duration fastRandomUUIDTime = ellapsedTime(max, UuidPlus::fastRandom);

		assertTrue(fastRandomUUIDTime.compareTo(randomUUIDTime) < 0);
	}


}
