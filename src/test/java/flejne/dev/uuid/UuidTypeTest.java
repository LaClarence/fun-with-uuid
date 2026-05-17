package flejne.dev.uuid;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;

/// IN PROGRESS
class UuidTypeTest {

    @Test
    void shouldBeUUIDVersion7() {
        var uuidToCheck = UUID.ofEpochMillis(Instant.now().getEpochSecond());
        IO.println("Version is : " + UuidType.version(uuidToCheck));
        var typeResult = UuidType.type(uuidToCheck);
        assertSame(UuidType.TIME_ORDERED_EPOCH, typeResult, () -> "Type '" + typeResult + "' not expected!");
    }

    @Test
    void shouldBeUUIDVersion4() {
        var uuidToCheck = UUID.randomUUID();
        IO.println("Version is : " + UuidType.version(uuidToCheck));
        var typeResult = UuidType.type(uuidToCheck);
        assertSame(UuidType.RANDOM, typeResult, () -> "Type '" + typeResult + "' not expected!");
    }

    private byte[] generateDigest(String algorithm) {
        String quote = "So often we work hard to find correct solutions to the wrong problems, without ever realizing it. Venkat Subramaniam";
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            md.update(quote.getBytes());
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("MD5 is mandatory per JCA spec — cannot happen", e);
        }
    }

    @Test
    void shouldBeUUIDVersion3() {
        byte[] digest = generateDigest("MD5");
        IO.println("Lenght MD5 digest: " + digest.length);
        assertSame(digest.length, 16);
        var uuidToCheck = UUID.nameUUIDFromBytes(digest);
        IO.println("Version is : " + UuidType.version(uuidToCheck));
        var typeResult = UuidType.type(uuidToCheck);
        assertSame(UuidType.NAME_BASED_MD5, typeResult, () -> "Type '" + typeResult + "' not expected!");
    }

    @Test
    void shouldBeUUIDVersion5() {
        byte[] digest = generateDigest("SHA-1");
        var uuidToCheck = UuidPlus.nameUUIDFromSHA1Bytes (digest);
        IO.println("Version is : " + UuidType.version(uuidToCheck));
        var typeResult = UuidType.type(uuidToCheck);
        assertSame(UuidType.NAME_BASED_SHA1, typeResult, () -> "Type '" + typeResult + "' not expected!");
    }
}