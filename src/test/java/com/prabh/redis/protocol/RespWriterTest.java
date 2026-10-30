package com.prabh.redis.protocol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class RespWriterTest {

    private ByteArrayOutputStream out;
    private RespWriter writer;

    @BeforeEach
    void setUp() {
        out = new ByteArrayOutputStream();
        writer = new RespWriter(out);
    }

    @Test
    void writesASimpleString() throws Exception {
        writer.writeSimpleString("OK");

        assertArrayEquals("+OK\r\n".getBytes(), out.toByteArray());
    }

    @Test
    void writesAnError() throws Exception {
        writer.writeError("ERR something broke");

        assertArrayEquals("-ERR something broke\r\n".getBytes(), out.toByteArray());
    }

    @Test
    void writesABulkString() throws Exception {
        writer.writeBulkString("hello".getBytes());

        assertArrayEquals("$5\r\nhello\r\n".getBytes(), out.toByteArray());
    }

    @Test
    void writesAnEmptyBulkString() throws Exception {
        writer.writeBulkString(new byte[0]);

        assertArrayEquals("$0\r\n\r\n".getBytes(), out.toByteArray());
    }

    @Test
    void writesABulkStringWithBinaryBytesUntouched() throws Exception {
        writer.writeBulkString(new byte[]{(byte) 0xFF, 0x00, '\r', '\n'});

        byte[] expected = {'$', '4', '\r', '\n', (byte) 0xFF, 0x00, '\r', '\n', '\r', '\n'};
        assertArrayEquals(expected, out.toByteArray());
    }

    @Test
    void writesNull() throws Exception {
        writer.writeNull();

        assertArrayEquals("$-1\r\n".getBytes(), out.toByteArray());
    }

    @Test
    void writesAnInteger() throws Exception {
        writer.writeInteger(42);

        assertArrayEquals(":42\r\n".getBytes(), out.toByteArray());
    }

    @Test
    void writesZero() throws Exception {
        writer.writeInteger(0);

        assertArrayEquals(":0\r\n".getBytes(), out.toByteArray());
    }

    @Test
    void writesANegativeInteger() throws Exception {
        writer.writeInteger(-7);

        assertArrayEquals(":-7\r\n".getBytes(), out.toByteArray());
    }

    @Test
    void writesAnIntegerLargerThanAnInt() throws Exception {
        writer.writeInteger(Long.MAX_VALUE);

        assertArrayEquals(":9223372036854775807\r\n".getBytes(), out.toByteArray());
    }

    @Test
    void flushesTheUnderlyingStream() throws Exception {
        int[] flushes = {0};
        ByteArrayOutputStream counting = new ByteArrayOutputStream() {
            @Override
            public void flush() {
                flushes[0]++;
            }
        };

        new RespWriter(counting).flush();

        assertEquals(1, flushes[0]);
        assertEquals(0, counting.size());
    }
}
