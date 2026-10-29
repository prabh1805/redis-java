package com.prabh.redis.command;

import com.prabh.redis.protocol.RespWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class CommandHandler {
    private final RespWriter respWriter;

    public CommandHandler(RespWriter respWriter) {
        this.respWriter = respWriter;
    }

    public void handle(List<byte[]> command) throws IOException {
        String name = new String(command.get(0), StandardCharsets.UTF_8).toUpperCase();

        switch (name) {
            case "PING" -> respWriter.writeSimpleString("PONG");
            case "ECHO" -> {
                if (command.size() != 2) {
                    respWriter.writeError("ERR wrong number of arguments for 'echo' command");
                } else {
                    respWriter.writeBulkString(command.get(1));
                }
            }
            default -> respWriter.writeError("ERR unknown command");
        }
        respWriter.flush();
    }
}
