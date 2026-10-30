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
        while(b != '\r'){
            if(b == -1) {
                throw new IOException("Client disconnected");
            }
            if (b < '0' || b > '9') {
                throw new IOException("Protocol error: invalid character in length");
            }
            res = res * 10 + b - '0';
            b = byteReader.read();
        }
        byteReader.read();
        return res;
    }

    public List<byte[]> readCommand() throws IOException {
        List<byte[]> command = new ArrayList<>();

        int type = byteReader.read();
        if (type == -1) {
            return null;          // client closed cleanly, nothing more coming
        }
        if (type != '*') {
            throw new IOException("Protocol error: expected '*'");
        }

        int count = readInteger();


        for (int i = 0; i < count; i++) {
            int elementType = byteReader.read();
            if (elementType != '$') {
                throw new IOException("Protocol error: expected '$'");
            }
            int length = readInteger();
            if (length < 0 || length > 512 * 1024 * 1024) {
                throw new IOException("Protocol error: invalid bulk length");
            }
            command.add(byteReader.readN(length));

            if (byteReader.read() != '\r') {
                throw new IOException("Protocol error: expected CR after bulk string");
            }
            if (byteReader.read() != '\n') {
                throw new IOException("Protocol error: expected LF after bulk string");
            }
        }

        return command;
    }
}
