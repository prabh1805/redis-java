package com.prabh.redis.protocol;

import com.prabh.redis.net.ByteReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RespParser {
    private final ByteReader byteReader;

    public RespParser(ByteReader byteReader) {
        this.byteReader = byteReader;
    }

    private int readInteger() throws IOException {
        int res = 0;
        int b = byteReader.read();
        boolean sawDigit = false;
        while (b != '\r') {
            if (b == -1) {
                throw new IOException("Client disconnected mid-command");
            }
            if (b < '0' || b > '9') {
                throw new ProtocolException("invalid character in length");
            }
            if (res > (Integer.MAX_VALUE - (b - '0')) / 10) {
                throw new ProtocolException("length too large");
            }
            sawDigit = true;
            res = res * 10 + b - '0';
            b = byteReader.read();
        }
        if (!sawDigit) {
            throw new ProtocolException("empty length");
        }
        if (byteReader.read() != '\n') {
            throw new ProtocolException("expected LF after length");
        }
        return res;
    }

    public List<byte[]> readCommand() throws IOException {
        List<byte[]> command = new ArrayList<>();

        int type = byteReader.read();
        if (type == -1) {
            return null;          // client closed cleanly, nothing more coming
        }
        if (type != '*') {
            throw new ProtocolException("expected '*'");
        }

        int count = readInteger();

        for (int i = 0; i < count; i++) {
            int elementType = byteReader.read();
            if (elementType != '$') {
                throw new ProtocolException("expected '$'");
            }
            int length = readInteger();
            if (length < 0 || length > 16 * 1024 * 1024) {
                throw new ProtocolException("invalid bulk length");
            }
            command.add(byteReader.readN(length));

            if (byteReader.read() != '\r') {
                throw new ProtocolException("expected CR after bulk string");
            }
            if (byteReader.read() != '\n') {
                throw new ProtocolException("expected LF after bulk string");
            }
        }

        return command;
    }
}