package com.prabh.redis;

import com.prabh.redis.command.CommandHandler;
import com.prabh.redis.net.ByteReader;
import com.prabh.redis.protocol.ProtocolException;
import com.prabh.redis.protocol.RespParser;
import com.prabh.redis.protocol.RespWriter;
import com.prabh.redis.store.Keyspace;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
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
        System.out.println("Listening on port " + server.serverSocket.getLocalPort());
        while(true){
            Socket clientSocket = server.acceptClient();

            Thread.ofVirtual().start(() -> {
                try (clientSocket) {
                    OutputStream out = clientSocket.getOutputStream();
                    ByteReader reader = new ByteReader(clientSocket.getInputStream());
                    RespParser parser = new RespParser(reader);
                    RespWriter respWriter = new RespWriter(out);
                    CommandHandler handler = new CommandHandler(respWriter, keyspace);

                    try {
                        while (true) {
                            List<byte[]> command = parser.readCommand();
                            if (command == null) break;
                            handler.handle(command);
                        }
                    } catch (ProtocolException e) {
                        respWriter.writeError("ERR Protocol error: " + e.getMessage());
                        respWriter.flush();
                        drain(clientSocket, 32 * 1024 * 1024);
                    }
                } catch (IOException e) {
                    // socket error, or the error reply itself failed — nothing to say
                }
            });

        }
    }

    private static void drain(Socket socket, int limit) throws IOException {
        InputStream in = socket.getInputStream();
        byte[] scratch = new byte[8192];
        int total = 0;
        long deadline = System.nanoTime() + 1_000_000_000L;   // 1 second
        try {
            while (total < limit) {
                long remainingMs = (deadline - System.nanoTime()) / 1_000_000;
                if (remainingMs <= 0) break;
                socket.setSoTimeout((int) remainingMs);
                int n = in.read(scratch);
                if (n == -1) break;
                total += n;
            }
        } catch (SocketTimeoutException e) {
            // client stopped sending — close anyway
        }
    }
}