package com.prabh.redis;

import java.io.IOException;
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
        }
    }
}