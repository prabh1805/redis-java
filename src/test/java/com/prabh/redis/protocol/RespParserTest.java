package com.prabh.redis.protocol;

import com.prabh.redis.net.ByteReader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RespParserTest {

    private static final int MAX_BULK_LENGTH = 16 * 1024 * 1024;

    private static RespParser parserFor(byte[] input) {
        return new RespParser(new ByteReader(new ByteArrayInputStream(input)));
    }

    private static RespParser parserFor(String input) {
        return parserFor(input.getBytes());
    }

    private static void assertRejected(String input, String expectedMessage) {
        ProtocolException e = assertThrows(ProtocolException.class, () -> parserFor(input).readCommand());
        assertEquals(expectedMessage, e.getMessage());
    }

    private static void assertClientDisconnected(String input) {
        assertThrowsExactly(IOException.class, () -> parserFor(input).readCommand());
    }

    /** Hands back at most chunkSize bytes per read, like a socket delivering partial data. */
    private static class ChunkedInputStream extends FilterInputStream {
        private final int chunkSize;

        ChunkedInputStream(InputStream source, int chunkSize) {
            super(source);
            this.chunkSize = chunkSize;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return super.read(b, off, Math.min(len, chunkSize));
        }
    }

    @Test
    void parsesACommandWithTwoArguments() throws Exception {
        byte[] input = "*2\r\n$4\r\nECHO\r\n$2\r\nhi\r\n".getBytes();
        RespParser parser = new RespParser(new ByteReader(new ByteArrayInputStream(input)));

        List<byte[]> command = parser.readCommand();

        assertEquals(2, command.size());
        assertArrayEquals("ECHO".getBytes(), command.get(0));
        assertArrayEquals("hi".getBytes(), command.get(1));
    }

    @Test
    void parsesACommandWithOneArguments() throws Exception {
        byte[] input = "*1\r\n$4\r\nPING\r\n".getBytes();
        RespParser parser = new RespParser(new ByteReader(new ByteArrayInputStream(input)));

        List<byte[]> command = parser.readCommand();

        assertEquals(1, command.size());
        assertArrayEquals("PING".getBytes(), command.get(0));
    }

    @Test
    void parsesACommandWithManyArguments() throws Exception {
        List<byte[]> command = parserFor("*3\r\n$3\r\nSET\r\n$3\r\nfoo\r\n$3\r\nbar\r\n").readCommand();

        assertEquals(3, command.size());
        assertArrayEquals("SET".getBytes(), command.get(0));
        assertArrayEquals("foo".getBytes(), command.get(1));
        assertArrayEquals("bar".getBytes(), command.get(2));
    }

    @Test
    void parsesAnEmptyValue() throws Exception {
        List<byte[]> command = parserFor("*2\r\n$4\r\nECHO\r\n$0\r\n\r\n").readCommand();

        assertEquals(2, command.size());
        assertArrayEquals(new byte[0], command.get(1));
    }

    @Test
    void parsesAZeroElementCommandAsEmpty() throws Exception {
        assertTrue(parserFor("*0\r\n").readCommand().isEmpty());
    }

    @Test
    void valueContaining0xFFSurvivesIntact() throws Exception {
        byte[] value = {'a', (byte) 0xFF, 0x00, (byte) 0xFF, 'b'};
        ByteArrayOutputStream input = new ByteArrayOutputStream();
        input.write("*1\r\n$5\r\n".getBytes());
        input.write(value);
        input.write("\r\n".getBytes());

        List<byte[]> command = parserFor(input.toByteArray()).readCommand();

        assertEquals(1, command.size());
        assertArrayEquals(value, command.get(0));
    }

    @Test
    void valueContainingCrlfSurvivesIntact() throws Exception {
        List<byte[]> command = parserFor("*1\r\n$6\r\nab\r\ncd\r\n").readCommand();

        assertArrayEquals("ab\r\ncd".getBytes(), command.get(0));
    }

    @Test
    void parsesACommandSplitAcrossSingleByteReads() throws Exception {
        byte[] input = "*2\r\n$4\r\nECHO\r\n$5\r\nhello\r\n".getBytes();
        RespParser parser = new RespParser(new ByteReader(
                new ChunkedInputStream(new ByteArrayInputStream(input), 1)));

        List<byte[]> command = parser.readCommand();

        assertEquals(2, command.size());
        assertArrayEquals("ECHO".getBytes(), command.get(0));
        assertArrayEquals("hello".getBytes(), command.get(1));
    }

    @Test
    void parsesACommandSplitInsideAValue() throws Exception {
        byte[] input = "*2\r\n$4\r\nECHO\r\n$5\r\nhello\r\n".getBytes();
        RespParser parser = new RespParser(new ByteReader(
                new ChunkedInputStream(new ByteArrayInputStream(input), 7)));

        List<byte[]> command = parser.readCommand();

        assertArrayEquals("ECHO".getBytes(), command.get(0));
        assertArrayEquals("hello".getBytes(), command.get(1));
    }

    @Test
    void readsConsecutiveCommandsThenNull() throws Exception {
        RespParser parser = parserFor("*1\r\n$4\r\nPING\r\n*1\r\n$4\r\nPONG\r\n");

        assertArrayEquals("PING".getBytes(), parser.readCommand().get(0));
        assertArrayEquals("PONG".getBytes(), parser.readCommand().get(0));
        assertNull(parser.readCommand());
    }

    @Test
    void emptyInputReturnsNull() throws Exception {
        assertNull(parserFor("").readCommand());
    }

    @Test
    void acceptsAValueExactlyAtTheCap() throws Exception {
        ByteArrayOutputStream input = new ByteArrayOutputStream();
        input.write(("*1\r\n$" + MAX_BULK_LENGTH + "\r\n").getBytes());
        byte[] value = new byte[MAX_BULK_LENGTH];
        Arrays.fill(value, (byte) 'x');
        input.write(value);
        input.write("\r\n".getBytes());

        List<byte[]> command = parserFor(input.toByteArray()).readCommand();

        assertArrayEquals(value, command.get(0));
    }

    @Test
    void rejectsANonDigitBulkLength() {
        assertRejected("*1\r\n$ab\r\nPING\r\n", "invalid character in length");
    }

    @Test
    void rejectsANonDigitCount() {
        assertRejected("*x\r\n", "invalid character in length");
    }

    @Test
    void rejectsANegativeBulkLength() {
        assertRejected("*1\r\n$-1\r\n", "invalid character in length");
    }

    @Test
    void rejectsANegativeCount() {
        assertRejected("*-1\r\n", "invalid character in length");
    }

    @Test
    void rejectsAnEmptyBulkLength() {
        assertRejected("*1\r\n$\r\n\r\n", "empty length");
    }

    @Test
    void rejectsAnEmptyCount() {
        assertRejected("*\r\n", "empty length");
    }

    @Test
    void rejectsABulkLengthThatOverflowsAnInt() {
        assertRejected("*1\r\n$2147483648\r\nPING\r\n", "length too large");
    }

    @Test
    void rejectsABulkLengthThatWrapsToASmallPositiveValue() {
        assertRejected("*1\r\n$4294967300\r\nPING\r\n", "length too large");
    }

    @Test
    void rejectsACountThatOverflowsAnInt() {
        assertRejected("*4294967297\r\n$4\r\nPING\r\n", "length too large");
    }

    @Test
    void rejectsABulkLengthOverTheCap() {
        assertRejected("*1\r\n$" + (MAX_BULK_LENGTH + 1) + "\r\n", "invalid bulk length");
    }

    @Test
    void rejectsTheLargestIntAsABulkLength() {
        assertRejected("*1\r\n$2147483647\r\n", "invalid bulk length");
    }

    @Test
    void rejectsAMissingCrAfterABulkString() {
        assertRejected("*1\r\n$4\r\nPINGXX", "expected CR after bulk string");
    }

    @Test
    void rejectsAMissingLfAfterABulkString() {
        assertRejected("*1\r\n$4\r\nPING\rX", "expected LF after bulk string");
    }

    @Test
    void rejectsAMissingLfAfterABulkLength() {
        assertRejected("*1\r\n$4\rXPING\r\n", "expected LF after length");
    }

    @Test
    void rejectsAMissingLfAfterACount() {
        assertRejected("*1\rX$4\r\nPING\r\n", "expected LF after length");
    }

    @Test
    void rejectsAWrongLeadingByte() {
        assertRejected("PING\r\n", "expected '*'");
    }

    @Test
    void rejectsAWrongElementByte() {
        assertRejected("*1\r\n:4\r\n", "expected '$'");
    }

    @Test
    void streamEndingInsideACountIsAPlainIOException() {
        assertClientDisconnected("*1");
    }

    @Test
    void streamEndingAfterTheCrOfACountIsAPlainIOException() {
        assertClientDisconnected("*1\r");
    }

    @Test
    void streamEndingAfterACountIsAPlainIOException() {
        assertClientDisconnected("*1\r\n");
    }

    @Test
    void streamEndingInsideABulkLengthIsAPlainIOException() {
        assertClientDisconnected("*1\r\n$4");
    }

    @Test
    void streamEndingAfterTheCrOfABulkLengthIsAPlainIOException() {
        assertClientDisconnected("*1\r\n$4\r");
    }

    @Test
    void streamEndingInsideAValueIsAPlainIOException() {
        assertClientDisconnected("*1\r\n$4\r\nPI");
    }

    @Test
    void streamEndingAfterAValueIsAPlainIOException() {
        assertClientDisconnected("*1\r\n$4\r\nPING");
    }

    @Test
    void streamEndingAfterTheCrOfAValueIsAPlainIOException() {
        assertClientDisconnected("*1\r\n$4\r\nPING\r");
    }

    @Test
    void streamEndingBetweenElementsIsAPlainIOException() {
        assertClientDisconnected("*2\r\n$4\r\nPING\r\n");
    }
}
