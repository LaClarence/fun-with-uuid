package flejne.dev.uuid;

import static org.junit.Assert.*;

import java.math.BigInteger;
import java.util.UUID;

import org.junit.Test;

public class UuidPlusTest {

    final String validStrict = "adb41c24-2dbf-4a6d-958b-4457c0d27b95";
    
    @Test
    public void uuidIsStrictlyValid()
    {
        assertTrue(UuidPlus.isPatternMatching.test(validStrict));
        assertTrue(UuidPlus.isLayoutMatch.test(validStrict));
        assertTrue(UuidPlus.isSizeValid.test(validStrict));
    }

    @Test
    public void uuidToBigInteger()
    {
       UUID uuid = UUID.randomUUID(); 
       BigInteger value = UuidPlus.toBigInteger(uuid);
       UUID result =UuidPlus.bigIntegerToUuid.apply(value);
       assertEquals(uuid.toString() , result.toString());
    }
    
}
