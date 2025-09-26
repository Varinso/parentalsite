
package com.pa.server.dao;

import java.nio.file.*;
import java.sql.*;

public class Db {
  private static final String DIR = System.getProperty("user.home") + "/.perentalassist";
  private static final String DB = DIR + "/app.db";

  public static void init() throws Exception {
    Files.createDirectories(Path.of(DIR));
    try (Connection c = get(); Statement st = c.createStatement()) {
      st.execute("PRAGMA foreign_keys=ON");
      st.execute("""
        CREATE TABLE IF NOT EXISTS users (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          email TEXT UNIQUE NOT NULL,
          password_hash TEXT NOT NULL,
          display_name TEXT NOT NULL,
          role TEXT NOT NULL DEFAULT 'parent',
          created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        )""");
      st.execute("""
        CREATE TABLE IF NOT EXISTS posts (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          user_id INTEGER NOT NULL,
          content TEXT NOT NULL,
          created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
          FOREIGN KEY(user_id) REFERENCES users(id)
        )""");
      st.execute("""
        CREATE TABLE IF NOT EXISTS comments (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          post_id INTEGER NOT NULL,
          user_id INTEGER NOT NULL,
          content TEXT NOT NULL,
          created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
          FOREIGN KEY(post_id) REFERENCES posts(id),
          FOREIGN KEY(user_id) REFERENCES users(id)
        )""");
    }
  }

  public static Connection get() throws SQLException {
    return DriverManager.getConnection("jdbc:sqlite:" + DB);
  }
}
