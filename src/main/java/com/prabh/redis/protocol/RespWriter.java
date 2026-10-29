package com.prabh.redis.protocol;

import java.io.IOException;
import java.io.OutputStream;

public class RespWriter {
    private final OutputStream out;

    public RespWriter(OutputStream out) {
        this.out = out;
    }


    public void writeSimpleString(String s) throws IOException {
        String res = "+" + s + "\r\n";
        out.write(res.getBytes());
    }


    public void writeError(String msg) throws IOException {
        String res = "-" + msg + "\r\n";
        out.write(res.getBytes());
    }

    public void writeBulkString(byte[] data) throws IOException {
        String res = "$" + String.valueOf(data.length) + "\r\n";
        out.write(res.getBytes());
        out.write(data);
        out.write("\r\n".getBytes());
    }


    public void writeNull() throws IOException {
        out.write("$-1\r\n".getBytes());
    }

    public void flush() throws IOException {
        out.flush();
    }
}