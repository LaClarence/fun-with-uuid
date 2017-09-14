package flejne.dev.uuid;

import static org.junit.Assert.*;

import org.junit.Test;

public class UuisPlusTest {

    final String validStrict = "adb41c24-2dbf-4a6d-958b-4457c0d27b95";
    
    @Test
    public void uuidIsStrictlyValid()
    {
        assertTrue(UuidPlus.isUuidValid.test(validStrict));
        assertTrue(UuidPlus.isUuidNotVersionned.test(validStrict));
        assertTrue(UuidPlus.isUuidSizeValid.test(validStrict));
    }

}
