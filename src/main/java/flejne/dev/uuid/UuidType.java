package flejne.dev.uuid;

import java.util.UUID;

public enum UuidType {
    UNDEFINED,
    TIME_BASED_GREGORIAN,
    DCE_SECURITY,
    NAME_BASED_MD5,
    RANDOM,
    NAME_BASED_SHA1,
    TIME_ORDERED_GREGORIAN,
    TIME_ORDERED_EPOCH,
    CUSTOM;

    int version() {
        return ordinal();
    }
    static int version(UUID uuid) {
        return uuid == null ? 0 : uuid.version();
    }

    static UuidType type(UUID uuid) {
        return switch (version(uuid)) {
            case 0 -> UNDEFINED;
            case 1 -> TIME_BASED_GREGORIAN;
            case 2 -> DCE_SECURITY;
            case 3 -> NAME_BASED_MD5;
            case 4 -> RANDOM;
            case 5 -> NAME_BASED_SHA1;
            case 6 -> TIME_ORDERED_GREGORIAN;
            case 7 -> TIME_ORDERED_EPOCH;
            case 8 -> CUSTOM;
            default -> UNDEFINED;
        };
    }

}
