package com.prabh.redis.command;

import com.prabh.redis.protocol.RespWriter;
import com.prabh.redis.store.Key;
import com.prabh.redis.store.Keyspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class CommandHandler {
    private final RespWriter respWriter;
    private final Keyspace keyspace;

    public CommandHandler(RespWriter respWriter, Keyspace keyspace) {
        this.respWriter = respWriter;
        this.keyspace = keyspace;
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
            case "SET" -> {
                if (command.size() != 3) {
                    respWriter.writeError("ERR wrong number of arguments for 'set' command");
                } else {
                    keyspace.set(new Key(command.get(1)), command.get(2));
                    respWriter.writeSimpleString("OK");
                }
            }
            case "GET" -> {
                if (command.size() != 2) {
                    respWriter.writeError("ERR wrong number of arguments for 'get' command");
                } else {
                    byte[] value = keyspace.get(new Key(command.get(1)));
                    if (value == null) {
                        respWriter.writeNull();
                    } else {
                        respWriter.writeBulkString(value);
                    }
                }
            }
            case "EXISTS" -> {
                if (command.size() != 2) {
                    respWriter.writeError("ERR wrong number of arguments for 'exists' command");
                }else{
                    int res = keyspace.exists(new Key(command.get(1))) ? 1 : 0;
                    respWriter.writeInteger(res);
                }
            }
            case "DEL" -> {
                if (command.size() != 2) {
                    respWriter.writeError("ERR wrong number of arguments for 'del' command");
                }else{
                    int res = keyspace.delete(new Key(command.get(1))) ? 1 : 0;
                    respWriter.writeInteger(res);
                }
            }
            default -> respWriter.writeError("ERR unknown command");
        }
        respWriter.flush();
    }
}
