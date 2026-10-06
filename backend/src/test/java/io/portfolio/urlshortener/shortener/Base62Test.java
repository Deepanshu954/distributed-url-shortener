package io.portfolio.urlshortener.shortener;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base62Test {

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "1, 1",
            "9, 9",
            "10, a",
            "35, z",
            "36, A",
            "61, Z",
            "62, 10",
            "3843, ZZ",
            "3844, 100",
            "238327, ZZZ",
            "9223372036854775807, aZl8N0y58M7"  // Long.MAX_VALUE
    })
    void knownVectors(long value, String encoded) {
        assertThat(Base62.encode(value)).isEqualTo(encoded);
        assertThat(Base62.decode(encoded)).isEqualTo(value);
    }

    @Test
    void roundTripPropertyOverRandomLongs() {
        Random random = new Random(42);
        for (int i = 0; i < 10_000; i++) {
            long value = random.nextLong() & Long.MAX_VALUE; // non-negative
            assertThat(Base62.decode(Base62.encode(value))).isEqualTo(value);
        }
    }

    @Test
    void encodedSnowflakeSizedIdsAreShort() {
        // ~2^63 max → 11 chars; typical 2026-era snowflake → 11 or fewer
        assertThat(Base62.encode(Long.MAX_VALUE)).hasSizeLessThanOrEqualTo(11);
    }

    @Test
    void encodeRejectsNegative() {
        assertThatThrownBy(() -> Base62.encode(-1L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decodeRejectsInvalidCharacters() {
        assertThatThrownBy(() -> Base62.decode("abc!")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Base62.decode("ab c")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decodeRejectsNullAndEmpty() {
        assertThatThrownBy(() -> Base62.decode(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Base62.decode("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decodeRejectsOverflow() {
        assertThatThrownBy(() -> Base62.decode("ZZZZZZZZZZZZZ")).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void fixedLengthEncodingProducesExactLength() {
        assertThat(Base62.encode(12345L, 5)).hasSize(5).matches("[0-9a-zA-Z]{5}");
        assertThat(Base62.encode(0L, 5)).isEqualTo("00000");
        assertThat(Base62.encode(987654321L, 6)).hasSize(6).matches("[0-9a-zA-Z]{6}");
        assertThat(Base62.encode(100L, 7)).hasSize(7).matches("[0-9a-zA-Z]{7}");
    }

    @Test
    void fixedLengthEncodingDispersion() {
        // Consecutive inputs produce distinct non-clustered 5-character codes
        String code1 = Base62.encode(1000L, 5);
        String code2 = Base62.encode(1001L, 5);
        assertThat(code1).isNotEqualTo(code2);
        assertThat(code1).hasSize(5);
        assertThat(code2).hasSize(5);
    }

    @Test
    void fixedLengthLengthGreaterThan10DelegatesToStandard() {
        assertThat(Base62.encode(3844L, 11)).isEqualTo(Base62.encode(3844L));
    }
}
