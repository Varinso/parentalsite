package com.pa.server;

import com.pa.server.dao.Db;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerMain {
    public static void main(String[] args) {
        int port = 5555;

        // Initialize DB (creates tables/columns if needed)
        try {
            Db.get();               // <-- don't call Db.init(); Db.get() runs init internally
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        ExecutorService pool = Executors.newFixedThreadPool(50);  // <-- plain Java, no named arg
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Server listening on " + port);
            while (true) {
                Socket socket = server.accept();
                pool.submit(new ClientHandler(socket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            pool.shutdown();
        }
    }
}
