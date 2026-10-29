package com.prabh.redis;

import com.prabh.redis.command.CommandHandler;
import com.prabh.redis.net.ByteReader;
import com.prabh.redis.protocol.RespParser;
import com.prabh.redis.protocol.RespWriter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;

public class Server {
    ServerSocket serverSocket;

    public Server() throws IOException {
        serverSocket = new ServerSocket(6379);
    }

    public Socket acceptClient() throws IOException {   // ← renamed
        return serverSocket.accept();
    }

    public static void main(String[] args) throws IOException {
        Server server = new Server();
        while(true){
            Socket clientSocket = server.acceptClient();
            System.out.println(clientSocket.getRemoteSocketAddress());

            Thread.ofVirtual().start(() -> {
                try {
                    OutputStream out = clientSocket.getOutputStream();
                    ByteReader reader = new ByteReader(clientSocket.getInputStream());
                    RespParser parser = new RespParser(reader);
                    RespWriter respWriter = new RespWriter(out);
                    CommandHandler handler = new CommandHandler(respWriter);
                    while (true) {
                        List<byte[]> command = parser.readCommand();
                        for (byte[] bytes : command) {
                            System.out.println(new String(bytes));
                        }
                        handler.handle(command);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

        }
    }
}