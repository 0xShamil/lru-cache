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

package dev.shamil.lru;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public final class CacheEntry<K, V> {
    private static final Object CLAIM_TOKEN = new Object();

    private static final AtomicIntegerFieldUpdater<CacheEntry> HITS_UPDATER
            = AtomicIntegerFieldUpdater.newUpdater(CacheEntry.class, "hits");

    private static final AtomicReferenceFieldUpdater<CacheEntry, Object> TOKEN_UPDATOR
            = AtomicReferenceFieldUpdater.newUpdater(CacheEntry.class, Object.class, "accessToken");

    private final K key;
    private final long expires;
    private volatile V value;
    private volatile int hits = 1;
    private volatile Object accessToken;

    CacheEntry(K key, V value, final long expires) {
        this.key = key;
        this.value = value;
        this.expires = expires;
    }

    public V getValue() {
        return value;
    }

    public void setValue(final V value) {
        this.value = value;
    }

    public int hit() {
        while (true) {
            int i = hits;
            if (HITS_UPDATER.weakCompareAndSet(this, i, ++i)) {
                return i;
            }
        }
    }

    public K key() {
        return key;
    }

    Object claimToken() {
        while (true) {
            Object current = this.accessToken;
            if (current == CLAIM_TOKEN) {
                return Boolean.FALSE;
            }

            if (TOKEN_UPDATOR.compareAndSet(this, current, CLAIM_TOKEN)) {
                return current;
            }
        }
    }

    boolean setToken(Object token) {
        return TOKEN_UPDATOR.compareAndSet(this, CLAIM_TOKEN, token);
    }

    Object clearToken() {
        Object old = TOKEN_UPDATOR.getAndSet(this, null);
        return old == CLAIM_TOKEN ? null : old;
    }

    public long getExpires() {
        return expires;
    }
}
