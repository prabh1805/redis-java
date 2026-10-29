package com.prabh.redis.store;

import java.util.Arrays;
import java.util.Objects;

public class Key {

    private final byte[] key;

    public Key(byte[] key) {
        this.key = key;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Key key1 = (Key) o;
        return Objects.deepEquals(key, key1.key);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(key);
    }



}
