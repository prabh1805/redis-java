package com.prabh.redis;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

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
                    InputStream in = clientSocket.getInputStream();
                    OutputStream out = clientSocket.getOutputStream();
                    byte[] buffer = new byte[1024];
                    while (true) {
                        int bytesRead = in.read(buffer);
                        if (bytesRead == -1) break;
                        System.out.println(new String(buffer, 0, bytesRead));
                        out.write("+PONG\r\n".getBytes());
                        out.flush();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

        }
    }
}