package flejne.dev.uuid;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.xml.bind.DatatypeConverter;

public final class UuidPlus {


  enum UUID_LENGTH {
    FORMATTED_STRING(36), BYTES(16), HEXA_STRING(32);

    private final int length;

    private UUID_LENGTH(int length) {
      this.length = length;
    }

    public static <T> boolean isLengthValid(T uuid) {
      if (uuid instanceof String uuidStr) {
        return uuidStr.length() == (uuidStr.contains("-") ? FORMATTED_STRING.length : HEXA_STRING.length);
      } else if (uuid instanceof byte[] uuidBytes) {
        return uuidBytes.length == BYTES.length;
      }
      ;
      return false;
    }

    public String toString() {
      return String.valueOf(length);
    }

    public int length() {
      return length;
    }
  }

  private static final int HEXA_RADIX = 16;

  private static final ThreadLocalRandom RANDOM = ThreadLocalRandom.current();

  static final String NIL_UUID = "00000000-0000-0000-0000-000000000000";

  static Predicate<String> isNil = NIL_UUID::equals;

  public static Predicate<String> isSizeValid = UUID_LENGTH::isLengthValid;

  enum REGEXP {
    STRICT("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[12345][a-fA-F0-9]{3}-[89abAB][a-fA-F0-9]{3}-[a-fA-F0-9]{12}"),
    LOOSE("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}");

    private REGEXP(final String value) {
      this.value = value;
    }

    private final String value;

    String pattern() {
      return this.value;
    }

    public boolean matches(String input) {
      return input.matches(this.pattern());
    }

  }

  public static Predicate<String> isPatternStrict = isSizeValid.and(REGEXP.STRICT::matches);

  public static Predicate<String> isPatternLoose = isSizeValid.and(REGEXP.LOOSE::matches);


  private static Function<int[], Function<byte[], byte[]>> reOrder = indexes -> bytes -> {
    ByteBuffer output = ByteBuffer.allocate(bytes.length);
    for (int i : indexes) {
      output.put(bytes[i]);
    }
    for (int j = indexes.length; j < bytes.length; j++) {
      output.put(bytes[j]);
    }
    return output.array();
  };

  /**
   * Here the detail of an Mix endian:<br>
   * 0..1..2..3..4..5..6..7<br>
   * 45 8A EB 12 11 2F 44 59<br>
   * 12 EB 8A 45 2F 11 59 44<br>
   * 3..2..1..0..5..4..7..6<br>
   * 
   * @param bytes array of bytes
   * @return the bytes array reordering according to the indexes given
   */
  private static Function<byte[], byte[]> toMixEndian = reOrder.apply(new int[] { 3, 2, 1, 0, 5, 4, 7, 6 });

  //////////////
  public static Function<byte[], String> bytesToHexa = DatatypeConverter::printHexBinary;

  private static Function<String, byte[]> hexaToBytes = DatatypeConverter::parseHexBinary;

  private static Function<String, String> uuidToHexa = s -> s.replace("-", "");

  public static Function<String, byte[]> uuidToBytes = uuidToHexa.andThen(hexaToBytes);

  public static Function<String, byte[]> uuidToMixEndianBytes = uuidToBytes.andThen(toMixEndian);

  // Bytes to long AND long to UUID
  static Function<Integer, Function<byte[], Long>> toLong = offset -> bytes -> ByteBuffer
      .wrap(bytes, offset, Long.BYTES).getLong();

  private static int OFFSET_MSB = 0;

  private static int OFFSET_LSB = 8;

  static Function<byte[], Long> mostSignificantBits = toLong.apply(OFFSET_MSB);
  static Function<byte[], Long> leastSignificantBits = toLong.apply(OFFSET_LSB);

  // Byte to UUID && Bytes array to UUID
  private static Function<byte[], UUID> uuidFromBytes = bytes -> new UUID(mostSignificantBits.apply(bytes),
      leastSignificantBits.apply(bytes));

  private static Function<Function<byte[], byte[]>, Function<byte[], UUID>> arrayStrategyToUuid = strategy -> bytes -> {
    if (UUID_LENGTH.isLengthValid(bytes))
      return strategy.andThen(uuidFromBytes).apply(bytes);
    throw new IllegalArgumentException("The byte array length is expected to be '" + UUID_LENGTH.BYTES + "'");
  };

  static Function<byte[], UUID> mixEndianToUuid = arrayStrategyToUuid.apply(toMixEndian);
  static Function<byte[], UUID> bigEndianToUuid = arrayStrategyToUuid.apply(x -> x);

  static Function<String, UUID> hexaToUuid = hexaToBytes.andThen(bigEndianToUuid);

  static Function<String, UUID> hexaMixEndianToUuid = hexaToBytes.andThen(mixEndianToUuid);

  // 
  private static long of(String hexa, int beginIndex) {
    return (new BigInteger(hexa.substring(beginIndex, Long.BYTES), HEXA_RADIX)).longValue();
  }
  
  // Hexa string UUID BigInteger Part
  static Function<String, UUID> hexaStringToUuid = hexa -> {
    var mostSigBits = of(hexa ,OFFSET_MSB);
    var leastSigBits = of(hexa,OFFSET_LSB);
    return new UUID(mostSigBits, leastSigBits);
  };

  static Function<BigInteger, UUID> bigIntegerToUuid = v -> bigEndianToUuid
      .apply(ByteBuffer.allocate(UUID_LENGTH.BYTES.length).put(v.toByteArray()).array());

  static BigInteger toBigInteger(UUID uuid) {
    var bb = ByteBuffer.wrap(new byte[UUID_LENGTH.BYTES.length]);
    bb.putLong(uuid.getMostSignificantBits());
    bb.putLong(uuid.getLeastSignificantBits());
    return new BigInteger(bb.array());
  }

  /**
   * Compare formatted UUID
   * @param uuid1
   * @param uuid2
   * @return
   */
  static boolean uuidEquals(String uuid1, String uuid2) {
    return uuid1 != null && uuid2 != null && uuid2.equalsIgnoreCase(uuid1) && isPatternLoose.test(uuid1);
  }

  /**
   * Check silently that a string is an UUID or not. It uses the UUID class
   * 
   * @param uuid the string to check
   * @return true when string is UUID
   * 
   */
  public static boolean isUuid(final String uuid) {
    try {
      UUID.fromString(uuid);
    } catch (final IllegalArgumentException e) {
      return false;
    }
    return true;

  }

  /**
   * Random alternative to UUID.randomUUID() that should be faster
   * 
   * @return a random UUID
   */
  public static UUID fastRandom() {
    return new UUID(RANDOM.nextLong(), RANDOM.nextLong());
  };

  public static String printHexBinary(byte[] val) {
    return uuidFromBytes.apply(val).toString();
  }

  private UuidPlus() {
    // Not instantiable class
  }

}
