package com.prabh.redis.net;

import java.io.IOException;
import java.io.InputStream;

public class ByteReader {
    private InputStream source;
    private byte[] tray = new byte[8192];
    private int position = 0;
    private int filled = 0;

    public ByteReader(InputStream source) {
        this.source = source;
    }

    public int read() throws IOException {
        if(position >= filled) {
            filled = source.read(tray);
            position = 0;
            if(filled == -1) {
                return -1;
            }
        }
        // without the mask, byte 255 returns -1 and looks like a disconnect
        return tray[position++] & 0xFF;
    }

    public byte[] readN(int n) throws IOException{
        byte[] result = new byte[n];
        for(int i = 0; i < n; i++) {
            int b = read();
            if(b == -1) {
                throw new IOException("Client disconnected");
            }
            result[i] = (byte) b;
        }
        return result;
    }
}
