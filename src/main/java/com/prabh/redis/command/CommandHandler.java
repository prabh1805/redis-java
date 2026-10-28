package com.prabh.redis.command;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class CommandHandler {
    private final OutputStream out;

    public CommandHandler(OutputStream out) {
        this.out = out;
    }

    public void handle(List<byte[]> command) throws IOException {
        String name = new String(command.get(0), StandardCharsets.UTF_8).toUpperCase();

        String res = switch (name) {
            case "PING" -> "+PONG\r\n";
            default -> "-ERR unknown command\r\n";
        };
        out.write(res.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}
