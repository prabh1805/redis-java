package com.prabh.redis.store;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KeyTest {

    @Test
    void keysWithEqualContentsAreEqualAndHashEqually() {
        Key first = new Key("foo".getBytes());
        Key second = new Key("foo".getBytes());

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void keysWithEqualBinaryContentsAreEqualAndHashEqually() {
        Key first = new Key(new byte[]{(byte) 0xFF, 0x00});
        Key second = new Key(new byte[]{(byte) 0xFF, 0x00});

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void keysWithDifferentContentsAreNotEqual() {
        assertNotEquals(new Key("foo".getBytes()), new Key("bar".getBytes()));
    }

    @Test
    void keysWhereOneIsAPrefixOfTheOtherAreNotEqual() {
        assertNotEquals(new Key("foo".getBytes()), new Key("foobar".getBytes()));
    }
}
