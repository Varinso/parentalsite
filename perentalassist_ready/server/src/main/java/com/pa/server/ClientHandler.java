
package com.pa.server;

import java.net.*;
import java.io.*;
import java.sql.*;
import com.pa.server.dao.Db;

public class ClientHandler implements Runnable {
  private final Socket socket;
  public ClientHandler(Socket socket) { this.socket = socket; }

  @Override public void run() {
    try (var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
         var out = new PrintWriter(socket.getOutputStream(), true)) {
      out.println("WELCOME");
      String line;
      while ((line = in.readLine()) != null) {
        try {
          String[] parts = line.split("\\|", 4);
          String cmd = parts[0];
          switch (cmd) {
            case "PING" -> out.println("PONG");
            case "SIGNUP" -> {
              String email = parts[1], pass = parts[2], name = parts[3];
              try (var c = Db.get(); var st = c.prepareStatement(
                "INSERT INTO users(email,password_hash,display_name) VALUES(?,?,?)")) {
                st.setString(1, email);
                st.setString(2, pass); // NOTE: hash in real app
                st.setString(3, name);
                st.executeUpdate();
                out.println("SIGNUP_OK");
              } catch (SQLException ex) {
                out.println("ERR|SIGNUP|"+ex.getMessage());
              }
            }
            case "LOGIN" -> {
              String email = parts[1], pass = parts[2];
              try (var c = Db.get(); var st = c.prepareStatement(
                "SELECT id,display_name FROM users WHERE email=? AND password_hash=?")) {
                st.setString(1, email);
                st.setString(2, pass);
                try (var rs = st.executeQuery()) {
                  if (rs.next()) out.println("LOGIN_OK|"+rs.getInt(1)+"|"+rs.getString(2));
                  else out.println("ERR|LOGIN|Invalid");
                }
              }
            }
            case "POST_CREATE" -> {
              int userId = Integer.parseInt(parts[1]);
              String content = parts[2];
              try (var c = Db.get(); var st = c.prepareStatement(
                "INSERT INTO posts(user_id,content) VALUES(?,?)")) {
                st.setInt(1, userId);
                st.setString(2, content);
                st.executeUpdate();
                out.println("POST_OK");
              }
            }
            case "FETCH_POSTS" -> {
              try (var c = Db.get(); var st = c.createStatement();
                   var rs = st.executeQuery("""
                      SELECT p.id,u.display_name,p.content,p.created_at
                      FROM posts p JOIN users u ON u.id=p.user_id
                      ORDER BY p.id DESC LIMIT 100
                   """)) {
                while (rs.next()) {
                  out.println("POST|"+rs.getInt(1)+"|"+rs.getString(2)+"|"+rs.getString(3).replace("\n"," ")+"|"+rs.getString(4));
                }
                out.println("END");
              }
            }
            case "COMMENT_CREATE" -> {
              String[] ps = line.split("\\|",5);
              int postId = Integer.parseInt(ps[1]);
              int userId = Integer.parseInt(ps[2]);
              String content = ps[3];
              try (var c = Db.get(); var st = c.prepareStatement(
                "INSERT INTO comments(post_id,user_id,content) VALUES(?,?,?)")) {
                st.setInt(1, postId);
                st.setInt(2, userId);
                st.setString(3, content);
                st.executeUpdate();
                out.println("COMMENT_OK");
              }
            }
            case "FETCH_COMMENTS" -> {
              int postId = Integer.parseInt(parts[1]);
              try (var c = Db.get(); var st = c.prepareStatement("""
                  SELECT c.id,u.display_name,c.content,c.created_at
                  FROM comments c JOIN users u ON u.id=c.user_id
                  WHERE c.post_id=? ORDER BY c.id ASC
              """)) {
                st.setInt(1, postId);
                try (var rs = st.executeQuery()) {
                  while (rs.next()) {
                    out.println("COMMENT|"+rs.getInt(1)+"|"+rs.getString(2)+"|"+rs.getString(3).replace("\n"," ")+"|"+rs.getString(4));
                  }
                }
                out.println("END");
              }
            }
            default -> out.println("ERR|UNKNOWN");
          }
        } catch (Exception ex) {
          out.println("ERR|EX|" + ex.getMessage());
        }
      }
    } catch (IOException e) {
      // client disconnected
    }
  }
}
