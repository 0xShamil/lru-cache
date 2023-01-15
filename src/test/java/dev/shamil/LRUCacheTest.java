package dev.shamil;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author shamil
 */
class LRUCacheTest {

    @Test
    void test_whenNoMaximumSize_throwsError() {
        Throwable thrown = assertThrows(IllegalArgumentException.class, () -> {
            final LRUCache<String, String> cache = new LRUCache.Builder<String, String>()
                    .initialCapacity(64)
                    .expiresAfterWrite(Duration.ofSeconds(180))
                    .build();
        });

        assertTrue(thrown.getMessage().contentEquals("Maximum size of the cache should be specified"));
    }
}