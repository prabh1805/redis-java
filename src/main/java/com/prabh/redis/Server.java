package com.prabh.redis;

import com.prabh.redis.command.CommandHandler;
import com.prabh.redis.net.ByteReader;
import com.prabh.redis.protocol.RespParser;
import com.prabh.redis.protocol.RespWriter;
import com.prabh.redis.store.Keyspace;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

public class Server {
    ServerSocket serverSocket;

    public Server() throws IOException {
        serverSocket = new ServerSocket(6379);
    }

    public Socket acceptClient() throws IOException {
        return serverSocket.accept();
    }

    public static void main(String[] args) throws IOException {
        Server server = new Server();
        Keyspace keyspace = new Keyspace();
        while(true){
            Socket clientSocket = server.acceptClient();

            Thread.ofVirtual().start(() -> {
                try (clientSocket) {
                    OutputStream out = clientSocket.getOutputStream();
                    ByteReader reader = new ByteReader(clientSocket.getInputStream());
                    RespParser parser = new RespParser(reader);
                    RespWriter respWriter = new RespWriter(out);
                    CommandHandler handler = new CommandHandler(respWriter, keyspace);
                    while (true) {
                        List<byte[]> command = parser.readCommand();
                        if(command == null) break;
                        handler.handle(command);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

        }
    }
}