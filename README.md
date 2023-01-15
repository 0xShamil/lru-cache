# LRU Cache

[![license](http://img.shields.io/badge/license-MIT-red.svg?style=flat)](https://raw.githubusercontent.com/0xShamil/lru-cache/main/LICENSE) [![Build Status](https://travis-ci.org/0xShamil/lru-cache.svg?branch=main)](https://travis-ci.org/0xShamil/lru-cache)

A non-blocking cache where entries are indexed by a key. 

The implementation is mostly taken from the [Undertow](https://github.com/undertow-io/undertow) project and modified for standalone usage. The original source code, being an internal implementation, is not very user-friendly. This implementation tries to provide fluent APIs on top of that. It is not to be considered a fork, since I still regularly synchronize changes from the upstream project.


## Installation

### Gradle

```gradle
dependencies {
    implementation 'dev.shamil:lru-cache:1.0.0'
}
```

### Maven

```xml
<dependency>
  <groupId>dev.shamil</groupId>
  <artifactId>lru-cache</artifactId>
  <version>1.0.0</version>
</dependency>
```

## Usage
Create `LRUCache<K, V>` instance.

```java
final LRUCache<String, String> cache = new LRUCache.Builder<String, String>()
        .initialCapacity(64) // defaults to 16
        .maximumSize(8096) // required
        .expiresAfterWrite(Duration.ofSeconds(180)) // defaults to -1, never expires
        .build();
```

## Licenses
The original code is licensed under the Apache 2.0. All modifications to the source code is licensed under the [MIT License](https://github.com/0xShamil/lru-cache/main/LICENSE).