package io.portfolio.urlshortener.shortener;

/**
 * Base62 codec over the alphabet {@code [0-9a-zA-Z]} (index order: digits,
 * lowercase, uppercase). Encodes non-negative longs — Snowflake ids are always
 * non-negative (sign bit unused, ADR-001).
 */
public final class Base62 {

    static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = 62;

    // Precomputed powers of 62 up to 62^10
    private static final long[] POWERS_OF_62 = new long[] {
            1L,
            62L,
            3_844L,
            238_328L,
            14_776_336L,
            916_132_832L,
            56_800_235_584L,
            3_521_614_606_208L,
            218_340_105_584_896L,
            13_537_086_546_263_552L,
            839_299_365_868_340_224L
    };

    private Base62() {
    }

    public static String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Base62.encode requires a non-negative value: " + value);
        }
        if (value == 0) {
            return "0";
        }
        StringBuilder sb = new StringBuilder(11); // Long.MAX_VALUE is 11 base62 digits
        while (value > 0) {
            sb.append(ALPHABET.charAt((int) (value % BASE)));
            value /= BASE;
        }
        return sb.reverse().toString();
    }

    /**
     * Encodes a 64-bit ID into a fixed-length Base62 string of exactly {@code length} characters.
     * Uses SplitMix64 mixing to uniformly disperse timestamp and sequence bits across the 62^length
     * combinatorial space when length < 11.
     * When length >= 11, returns standard Base62 encoding.
     */
    public static String encode(long value, int length) {
        if (value < 0) {
            throw new IllegalArgumentException("Base62.encode requires a non-negative value: " + value);
        }
        if (length <= 0) {
            throw new IllegalArgumentException("Base62.encode length must be positive: " + length);
        }
        if (length >= 11) {
            return encode(value);
        }

        long mod = POWERS_OF_62[length];
        long mixed = Math.abs(mix64(value)) % mod;

        char[] chars = new char[length];
        for (int i = length - 1; i >= 0; i--) {
            chars[i] = ALPHABET.charAt((int) (mixed % BASE));
            mixed /= BASE;
        }
        return new String(chars);
    }

    /**
     * 64-bit bit-mixer (Stafford variant 13 of SplitMix64).
     * High avalanche characteristics: every input bit affects every output bit with ~50% probability.
     */
    static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    public static long decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            throw new IllegalArgumentException("Base62.decode requires a non-empty string");
        }
        long value = 0;
        for (int i = 0; i < encoded.length(); i++) {
            int digit = digitOf(encoded.charAt(i));
            value = Math.addExact(Math.multiplyExact(value, BASE), digit); // overflow → ArithmeticException
        }
        return value;
    }

    private static int digitOf(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'z') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'Z') {
            return c - 'A' + 36;
        }
        throw new IllegalArgumentException("invalid base62 character: '" + c + "'");
    }
}
