
package com.pa.client.service;

import java.io.*;
import java.net.*;
import java.util.*;

public class ApiService {
  private final String host;
  private final int port;

  public ApiService(String host, int port) {
    this.host = host; this.port = port;
  }

  public List<String> send(String line) throws IOException {
    try (Socket s = new Socket(host, port);
         var in = new BufferedReader(new InputStreamReader(s.getInputStream()));
         var out = new PrintWriter(s.getOutputStream(), true)) {
      in.readLine(); // WELCOME
      out.println(line);
      List<String> resp = new ArrayList<>();
      String r;
      while ((r = in.readLine()) != null) {
        resp.add(r);
        if ("END".equals(r)) break;
        if (r.startsWith("ERR") || r.endsWith("_OK") || r.startsWith("LOGIN_OK") || r.startsWith("SIGNUP_OK")) break;
      }
      return resp;
    }
  }
}
