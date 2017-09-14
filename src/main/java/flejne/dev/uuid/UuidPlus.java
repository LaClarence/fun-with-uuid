package flejne.dev.uuid;

import java.util.function.Predicate;

public interface UuidPlus {
    static final int UUID_STRING_SIZE = 36;
    
    static final String NIL = "00000000-0000-0000-0000-000000000000";
    
    enum REGEXP {
        STRICT("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[12345][a-fA-F0-9]{3}-[89abAB][a-fA-F0-9]{3}-[a-fA-F0-9]{12}"),
        NO_VERSIONNING("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}");
        private REGEXP(final String value) {
            this.value = value;
        }
        private final String value;
        public String getRegexp() {
            return this.value;
        }
    }

    static Predicate<String> isVersionnedUuid = s -> s.matches(REGEXP.STRICT.getRegexp());
    static Predicate<String> isStricPatternValid = isVersionnedUuid;
    static Predicate<String> isPatternValid = s -> s.matches(REGEXP.NO_VERSIONNING.getRegexp());
    static Predicate<String> isUuidSizeValid = s -> s == null ? false : UUID_STRING_SIZE == s.length();

}
