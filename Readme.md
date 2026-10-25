# uuidplus

A small, dependency-free Java library for **converting, validating and generating UUIDs**.

## Purpose

`java.util.UUID` only covers the basics: parsing the standard 36-character string,
`randomUUID()` and `nameUUIDFromBytes()` (MD5, v3). Real systems often store or exchange
UUIDs in other shapes, such as raw 16-byte columns, 32-character hex strings, unsigned
128-bit integers, or the mixed-endian layout used by Microsoft GUIDs. Each conversion is
easy to get subtly wrong.

`uuidplus` gathers these conversions and checks into one static utility class,
`flejne.dev.uuid.UuidPlus`, so you don't have to rewrite them in every project.

## Features

- **Conversions** between formatted strings, 32-char hex strings, 16-byte arrays
  (big-endian or mixed-endian), `BigInteger` and `java.util.UUID`. Invalid input
  (wrong length, non-hex, out-of-range `BigInteger`) throws `IllegalArgumentException`.
- **Validation**:
  - `isUuid` checks for the canonical `8-4-4-4-12` form; it returns `false` for `null`.
  - `fromString` / `tryFromString` parse with the same rules. Unlike the JDK's
    `UUID.fromString`, they reject shortened forms such as `1-1-1-1-1`.
  - `isPatternStrict` matches only RFC 9562 versions 1–8 with the right variant bits.
  - `isPatternLoose` checks hex digits and dash placement only.
  - `isSizeValid` checks the length.
  - `uuidEquals` compares two UUID strings, ignoring case.
- **Special values**: nil and max UUIDs (RFC 9562), with `isNil` / `isMax` predicates.
- **Version 5 from SHA-1**: `nameUUIDFromSHA1Bytes` turns a 20-byte SHA-1 digest into a v5 UUID.
- **Type detection**: `typeOf` returns the `UuidType` (random, time-ordered, name-based, …).
- **Timestamp extraction**: `tryExtractTimestamp` reads the embedded `Instant` from a v7 UUID.
  To *generate* v7 UUIDs, use the JDK's `UUID.ofEpochMillis(long)`.
- **Fast random UUIDs**: `fastRandomV4` (valid RFC v4 UUIDs) and `fastRandom` (all 128 bits
  random, not RFC UUIDs). See [Fast random UUIDs](#fast-random-uuids) for when to use them.

## Usage

```java
import flejne.dev.uuid.UuidPlus;
import flejne.dev.uuid.UuidType;

UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

byte[] raw     = UuidPlus.uuidToBytes(id);              // 16 bytes, big-endian
String hex     = UuidPlus.uuidToHex(id);                // "123E4567E89B12D3A456426614174000"
UUID   back    = UuidPlus.hexToUuid(hex);               // equals id
UuidType type  = UuidPlus.typeOf(id);                   // TIME_BASED_GREGORIAN (v1)
BigInteger n   = UuidPlus.toBigInteger(id);             // unsigned 128-bit value

boolean valid  = UuidPlus.isPatternStrict.test(id.toString());   // true
Optional<Instant> ts = UuidPlus.tryExtractTimestamp(id);         // empty: not a v7 UUID
```

## Strict UUID strings

The JDK's `UUID.fromString` is lenient for historical reasons. It reads each dash-separated
group as a hex number and pads it with zeros, so malformed strings are silently accepted:

```java
UUID.fromString("1-1-1-1-1");   // → 00000001-0001-0001-0001-000000000001
UUID.fromString("0-0-0-0-0");   // → the nil UUID
```

RFC 9562 defines the text form as exactly 8-4-4-4-12 hex digits, and `uuidplus` follows that
definition everywhere:

| Input                                    | `UUID.fromString` | `UuidPlus.isUuid` | `UuidPlus.fromString`      |
|------------------------------------------|-------------------|-------------------|----------------------------|
| `adb41c24-2dbf-4a6d-958b-4457c0d27b95`   | ✅                 | `true`            | ✅                          |
| `ADB41C24-2DBF-4A6D-958B-4457C0D27B95`   | ✅                 | `true`            | ✅                          |
| `1-1-1-1-1`                              | ✅ (padded)        | `false`           | `IllegalArgumentException` |
| `adb41c242dbf4a6d958b4457c0d27b95`       | exception         | `false`           | `IllegalArgumentException` |
| `null`                                   | exception         | `false`           | `IllegalArgumentException` |

Why strict:

- **Consistency**: a string accepted by one method of the library is accepted by all the others.
  (`uuidToBytes` additionally accepts 32 hex digits without dashes.)
- **One spelling per UUID**: `UuidPlus.fromString(s).toString()` equals `s` ignoring case, so the
  same UUID can't end up stored under two different keys such as `1-1-1-1-1` and
  `00000001-0001-0001-0001-000000000001`.
- **Input validation**: API or database input that isn't a real UUID is rejected instead of
  being silently reinterpreted.

Use `UuidPlus.tryFromString` to get an `Optional<UUID>` instead of an exception.

## Fast random UUIDs

`UUID.randomUUID()` calls `SecureRandom` for every UUID. `fastRandomV4()` avoids that: each
thread gets its own `L128X256MixRandom` generator, seeded once from `SecureRandom`.

**Not cryptographically secure.** Its output can be predicted from previous outputs. Use
`UUID.randomUUID()` for anything that must be unguessable: session ids, tokens, password-reset
links, ids exposed in public URLs.

**Why not `ThreadLocalRandom`?** It is the usual fast generator, but it has a single 64-bit
state, so it can produce at most 2^64 distinct UUIDs and duplicates follow the 64-bit birthday
bound. `L128X256MixRandom` has a 384-bit state, which keeps collisions at the level a 122-bit
random UUID is designed for. The chance of at least one duplicate:

| UUIDs generated | 122 random bits (`randomUUID`, `fastRandomV4()`) | 64-bit state (`ThreadLocalRandom`) |
|-----------------|--------------------------------------------------|------------------------------------|
| 1 million       | ~10⁻²⁵                                           | ~10⁻⁸                              |
| 100 million     | ~10⁻²¹                                           | ~10⁻⁴                              |
| 4 billion       | ~10⁻¹⁸                                           | **~50 %**                          |

The same limit applies if you pass a 64-bit generator yourself, such as
`fastRandomV4(ThreadLocalRandom.current())` or `fastRandomV4(new Random(seed))`. A seeded
`Random` is still useful for reproducible tests.

**Which one to use:**

| Use case                                | Choice                                                  |
|-----------------------------------------|---------------------------------------------------------|
| Default, anything security-related      | `UUID.randomUUID()`                                     |
| Database primary keys                   | `UUID.ofEpochMillis(...)` (v7, sortable, index-friendly) |
| High volume, not security-related       | `UuidPlus.fastRandomV4()`                               |
| Reproducible tests                      | `UuidPlus.fastRandomV4(new Random(seed))`               |

Seeding costs one `SecureRandom` call per thread. That is negligible with pooled threads, but
if every UUID is generated on a new short-lived (e.g. virtual) thread, `fastRandomV4()` is no
faster than `UUID.randomUUID()`.

## Requirements

- Java 27 or newer. The jar is a named module: `flejne.dev.uuid`.
- Maven 3.9 or newer to build.

```xml
<dependency>
    <groupId>flejne.dev.uuid</groupId>
    <artifactId>uuidplus</artifactId>
    <version>0.6-SNAPSHOT</version>
</dependency>
```

## References

- [RFC 9562 — Universally Unique IDentifiers (UUIDs)](https://www.rfc-editor.org/rfc/rfc9562)
- [Universally unique identifier — Wikipedia](https://en.wikipedia.org/wiki/Universally_unique_identifier)

## License

Apache License 2.0. See [LICENSE](LICENSE).
