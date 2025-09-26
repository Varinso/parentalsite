
package com.pa.server;

import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import com.pa.server.dao.Db;

public class ServerMain {
  public static void main(String[] args) throws Exception {
    int port = 5555;
    try {
      Db.init();
    } catch (Exception e) {
      e.printStackTrace();
      return;
    }
    ExecutorService pool = Executors.newFixedThreadPool(50);
    try (ServerSocket server = new ServerSocket(port)) {
      System.out.println("Server listening on " + port);
      while (true) {
        Socket socket = server.accept();
        pool.submit(new ClientHandler(socket));
      }
    }
  }
}
