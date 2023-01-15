/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/*
 * The MIT License
 *
 * Copyright (C) 2023 Shamil
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package dev.shamil;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A non-blocking cache where entries are indexed by a key.
 * <p>
 * <p>To reduce contention, entry allocation and eviction execute in a sampling
 * fashion (entry hits modulo N). Eviction follows an LRU approach (oldest sampled
 * entries are removed first) when the cache is out of capacity.</p>
 * <p>
 * <p>
 * This cache can also be configured to run in FIFO mode, rather than LRU.
 */
public final class LRUCache<K, V> {
    private static final int SAMPLE_INTERVAL = 5;

    /**
     * Max active entries that are present in the cache.
     */
    private final int maxEntries;

    private final ConcurrentMap<K, CacheEntry<K, V>> cache;
    private final ConcurrentDirectDeque<CacheEntry<K, V>> accessQueue;
    /**
     * how long an item can stay in the cache in milliseconds
     */
    private final long maxAge;

    private LRUCache(final int maxEntries,
                     final long maxAge,
                     final int initialCapacity,
                     final ConcurrentDirectDeque<CacheEntry<K, V>> queue) {
        this.maxEntries = maxEntries;
        this.maxAge = maxAge;
        this.cache = new ConcurrentHashMap<>(initialCapacity);
        this.accessQueue = new FastConcurrentDirectDeque<>();
    }

    public void put(K key, V newValue) {
        CacheEntry<K, V> value = cache.get(key);
        if (value == null) {
            long expires;
            if (maxAge == -1) {
                expires = -1;
            } else {
                expires = System.currentTimeMillis() + maxAge;
            }
            value = new CacheEntry<>(key, newValue, expires);
            CacheEntry<K, V> result = cache.putIfAbsent(key, value);
            if (result != null) {
                value = result;
                value.setValue(newValue);
            }
            bumpAccess(value);
            if (cache.size() > maxEntries) {
                //remove the oldest
                CacheEntry<K, V> oldest = accessQueue.poll();
                if (oldest != value) {
                    this.remove(oldest.key());
                }
            }
        }
    }

    public V get(K key) {
        CacheEntry<K, V> cacheEntry = cache.get(key);
        if (cacheEntry == null) {
            return null;
        }
        long expires = cacheEntry.getExpires();
        if (expires != -1) {
            if (System.currentTimeMillis() > expires) {
                remove(key);
                return null;
            }
        }

        if (cacheEntry.hit() % SAMPLE_INTERVAL == 0) {
            bumpAccess(cacheEntry);
        }

        return cacheEntry.getValue();
    }

    private void bumpAccess(CacheEntry<K, V> cacheEntry) {
        Object prevToken = cacheEntry.claimToken();
        if (!Boolean.FALSE.equals(prevToken)) {
            if (prevToken != null) {
                accessQueue.removeToken(prevToken);
            }

            Object token = null;
            try {
                token = accessQueue.offerLastAndReturnToken(cacheEntry);
            } catch (Throwable t) {
                // In case of disaster (OOME), we need to release the claim, so leave it as null
            }

            if (!cacheEntry.setToken(token) && token != null) { // Always set if null
                accessQueue.removeToken(token);
            }
        }
    }

    public V remove(K key) {
        CacheEntry<K, V> remove = cache.remove(key);
        if (remove != null) {
            Object old = remove.clearToken();
            if (old != null) {
                accessQueue.removeToken(old);
            }
            return remove.getValue();
        } else {
            return null;
        }
    }

    public void clear() {
        cache.clear();
        accessQueue.clear();
    }

    public static final class Builder<K, V> {
        private Integer maxEntries;
        private ConcurrentDirectDeque<CacheEntry<K, V>> accessQueue;
        private Duration maxAge;
        private int initialCapacity = 16;

        public Builder<K, V> initialCapacity(int capacity) {
            this.initialCapacity = capacity;
            return this;
        }

        public Builder<K, V> maximumSize(int max) {
            this.maxEntries = max;
            return this;
        }

        public Builder<K, V> expiresAfterWrite(Duration cacheDuration) {
            this.maxAge = cacheDuration;
            return this;
        }

        public Builder<K, V> accessQueue(ConcurrentDirectDeque<CacheEntry<K, V>> queue) {
            this.accessQueue = queue;
            return this;
        }

        public LRUCache<K, V> build() {
            if (Objects.isNull(maxEntries) || maxEntries <= 0) {
                throw new IllegalArgumentException("Maximum size of the cache should be specified");
            }
            return new LRUCache<>(
                    maxEntries,
                    maxAge != null ? maxAge.toMillis() : -1,
                    initialCapacity,
                    accessQueue != null ? accessQueue : ConcurrentDirectDequeFactory.newInstance()
            );
        }
    }
}
