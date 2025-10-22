package com.pa.client.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Backward-compatible API helper.
 *
 * Keeps your original API:
 *   new ApiService(host, port).send(command)  -> List<String>
 *
 * Adds safe helpers used by some new screens:
 *   ApiService.sendAndReceive(command)        -> String (lines joined by \n)
 *   ApiService.getCurrentUserId()             -> String (best-effort)
 *   ApiService.getInstance()                  -> optional singleton using defaults
 *
 * Defaults for the singleton:
 *   host = System.getProperty("pa.host", "127.0.0.1")
 *   port = Integer.getInteger("pa.port", 5555)
 */
public class ApiService {

  // -------- original fields (kept) --------
  private final String host;
  private final int port;

  public ApiService(String host, int port) {
    this.host = host;
    this.port = port;
  }

  /**
   * ORIGINAL behavior (unchanged):
   * Opens a short-lived socket, sends command, collects lines.
   * Stops at:
   *  - "END"
   *  - any line starting with "ERR"
   *  - any line ending with "_OK"
   *  - "LOGIN_OK..." or "SIGNUP_OK..."
   *
   * Additionally (non-breaking): updates static current user when it sees LOGIN_OK/AUTH_OK.
   */
  public List<String> send(String line) throws IOException {
    try (Socket s = new Socket(host, port);
         BufferedReader in = new BufferedReader(
                 new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
         PrintWriter out = new PrintWriter(
                 new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)) {

      // Server greets
      in.readLine(); // "WELCOME" (ignored if absent)

      // Send command
      out.println(line);

      List<String> resp = new ArrayList<>();
      String r;
      while ((r = in.readLine()) != null) {
        resp.add(r);

        // Update session tracking if present
        if (r.startsWith("LOGIN_OK|")) {
          // LOGIN_OK|<userId>|<display>|<role>
          String[] p = r.split("\\|", 4);
          if (p.length >= 2) {
            currentUserId = p[1];
            // Best-effort also store name
            if (p.length >= 3) currentUserName = p[2];
          }
          break;
        }
        if (r.startsWith("AUTH_OK|")) {
          // AUTH_OK|<userId>
          String[] p = r.split("\\|", 2);
          if (p.length >= 2) currentUserId = p[1];
          break;
        }

        if ("END".equals(r)) break;
        if (r.startsWith("ERR")) break;
        if (r.endsWith("_OK")) break;
        if (r.startsWith("SIGNUP_OK")) break;
      }
      return resp;
    }
  }

  // ====== New helpers (non-breaking) ======

  // Singleton (optional) for places that expect getInstance()
  private static volatile ApiService INSTANCE;
  private static String defaultHost() { return System.getProperty("pa.host", "127.0.0.1"); }
  private static int defaultPort() {
    try { return Integer.parseInt(System.getProperty("pa.port", "5555")); }
    catch (NumberFormatException e) { return 5555; }
  }

  /** Optional singleton for convenience / legacy callers. */
  public static ApiService getInstance() {
    ApiService ref = INSTANCE;
    if (ref == null) {
      synchronized (ApiService.class) {
        ref = INSTANCE;
        if (ref == null) {
          ref = new ApiService(defaultHost(), defaultPort());
          INSTANCE = ref;
        }
      }
    }
    return ref;
  }

  /** Static convenience: send command and get whole response joined by '\n'. */
  public static String sendAndReceive(String command) {
    try {
      List<String> lines = getInstance().send(command);
      if (lines == null || lines.isEmpty()) return null;
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < lines.size(); i++) {
        if (i > 0) sb.append('\n');
        sb.append(lines.get(i));
      }
      return sb.toString();
    } catch (IOException e) {
      return null;
    }
  }

  /** Allow screens to inject a known user (e.g., right after login), if they want. */
  public static void setCurrentUser(String userId, String displayName) {
    currentUserId = userId;
    currentUserName = displayName;
  }

  /** Best-effort current user id for booking/posts/etc. */
  public static String getCurrentUserId() {
    if (currentUserId != null && !currentUserId.isBlank()) return currentUserId;

    // Fallback: try to read from AppState if your project tracks it there
    try {
      Class<?> appState = Class.forName("com.pa.client.AppState");

      // Try static method getUserId()
      try {
        Method m = appState.getDeclaredMethod("getUserId");
        Object v = m.invoke(null);
        if (v != null) return String.valueOf(v);
      } catch (NoSuchMethodException ignore) { /* continue */ }

      // Try static field "userId"
      try {
        Field f = appState.getDeclaredField("userId");
        f.setAccessible(true);
        Object v = f.get(null);
        if (v != null) return String.valueOf(v);
      } catch (NoSuchFieldException ignore) { /* continue */ }
    } catch (Exception ignore) {
      // AppState not present or inaccessible — fine, return null below.
    }

    return null;
  }

  /** Optional: expose name if someone wants to show it. */
  public static String getCurrentUserName() {
    return currentUserName;
  }

  // ====== session tracking (static, harmless) ======
  private static volatile String currentUserId;
  private static volatile String currentUserName;
}
