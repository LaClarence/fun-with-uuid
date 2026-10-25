package flejne.dev.uuid;

import java.util.UUID;

/// UUID types as defined by the version field of RFC 9562.
///
/// The ordinal of each constant is its version number.
public enum UuidType {
    /// Version 0, or any version not defined by RFC 9562 (also used for `null`).
    UNDEFINED,
    /// Version 1: Gregorian time-based.
    TIME_BASED_GREGORIAN,
    /// Version 2: DCE Security.
    DCE_SECURITY,
    /// Version 3: name-based, MD5 hashing.
    NAME_BASED_MD5,
    /// Version 4: random.
    RANDOM,
    /// Version 5: name-based, SHA-1 hashing.
    NAME_BASED_SHA1,
    /// Version 6: reordered Gregorian time-based.
    TIME_ORDERED_GREGORIAN,
    /// Version 7: Unix epoch time-based.
    TIME_ORDERED_EPOCH,
    /// Version 8: custom, vendor-specific layout.
    CUSTOM;

    /// Returns the version number of this type.
    ///
    /// @return the version number, `0` to `8`
    public int version() {
        return ordinal();
    }

    /// Returns the version number of a UUID.
    ///
    /// @param uuid the UUID to inspect, may be `null`
    /// @return the version number, `0` for `null`
    public static int version(UUID uuid) {
        return uuid == null ? 0 : uuid.version();
    }

    /// Returns the type of a UUID, derived from its version field.
    ///
    /// @param uuid the UUID to inspect, may be `null`
    /// @return the UUID type, `UNDEFINED` for `null` or an unknown version
    public static UuidType type(UUID uuid) {
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
