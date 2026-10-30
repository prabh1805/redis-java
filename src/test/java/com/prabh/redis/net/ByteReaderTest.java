package com.prabh.redis.net;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ByteReaderTest {

    private static final int TRAY_SIZE = 8192;

    private static ByteReader readerFor(byte... input) {
        return new ByteReader(new ByteArrayInputStream(input));
    }

    @Test
    void returnsBytesInOrderAcrossATrayRefill() throws Exception {
        byte[] input = new byte[TRAY_SIZE * 2 + 10];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) (i % 251);
        }
        ByteReader reader = readerFor(input);

        for (int i = 0; i < input.length; i++) {
            assertEquals(i % 251, reader.read(), "byte at offset " + i);
        }
        assertEquals(-1, reader.read());
    }

    @Test
    void byte0xFFReturns255NotMinusOne() throws Exception {
        ByteReader reader = readerFor((byte) 0xFF);

        assertEquals(255, reader.read());
        assertEquals(-1, reader.read());
    }

    @Test
    void readNReturnsExactlyNBytes() throws Exception {
        ByteReader reader = readerFor((byte) 1, (byte) 2, (byte) 3, (byte) 4);

        assertArrayEquals(new byte[]{1, 2, 3}, reader.readN(3));
        assertEquals(4, reader.read());
    }

    @Test
    void readNReturnsBytesAcrossATrayRefill() throws Exception {
        byte[] input = new byte[TRAY_SIZE + 100];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) (i % 251);
        }

        assertArrayEquals(input, readerFor(input).readN(input.length));
    }

    @Test
    void readNOfZeroReturnsAnEmptyArray() throws Exception {
        ByteReader reader = readerFor((byte) 7);

        assertArrayEquals(new byte[0], reader.readN(0));
        assertEquals(7, reader.read());
    }

    @Test
    void readNThrowsWhenTheStreamEndsEarly() {
        ByteReader reader = readerFor((byte) 1, (byte) 2);

        assertThrows(IOException.class, () -> reader.readN(3));
    }

    @Test
    void endOfStreamReturnsMinusOne() throws Exception {
        assertEquals(-1, readerFor().read());
    }

    @Test
    void endOfStreamKeepsReturningMinusOne() throws Exception {
        ByteReader reader = readerFor();

        assertEquals(-1, reader.read());
        assertEquals(-1, reader.read());
    }
}
