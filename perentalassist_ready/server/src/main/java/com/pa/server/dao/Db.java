package com.pa.server.dao;

import java.sql.*;

public final class Db {
    private static Connection SINGLETON;

    public static synchronized Connection get() throws SQLException {
        if (SINGLETON == null || SINGLETON.isClosed()) {
            // NOTE: path kept as in your project to avoid breaking existing DB
            String url = "jdbc:sqlite:" + System.getProperty("user.home") + "/.perentalassist/app.db";
            SINGLETON = DriverManager.getConnection(url);
            try (Statement st = SINGLETON.createStatement()) { st.execute("PRAGMA foreign_keys=ON"); }
            init(SINGLETON);
        }
        return SINGLETON;
    }

    private static void init(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            // users
            st.executeUpdate("""
              CREATE TABLE IF NOT EXISTS users(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                email TEXT UNIQUE,
                password_hash TEXT,
                display_name TEXT,
                role TEXT DEFAULT 'USER'
              )
            """);

            // posts (with image_url and anonymous flag)
            st.executeUpdate("""
              CREATE TABLE IF NOT EXISTS posts(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER,
                content TEXT,
                image_url TEXT,
                anonymous INTEGER DEFAULT 0,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
              )
            """);

            // comments (with image_url)
            st.executeUpdate("""
              CREATE TABLE IF NOT EXISTS comments(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                post_id INTEGER,
                user_id INTEGER,
                content TEXT,
                image_url TEXT,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
              )
            """);

            // chat
            st.executeUpdate("""
              CREATE TABLE IF NOT EXISTS chat_conversations(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT
              )
            """);
            st.executeUpdate("""
              CREATE TABLE IF NOT EXISTS chat_members(
                conversation_id INTEGER,
                user_id INTEGER
              )
            """);
            st.executeUpdate("""
              CREATE TABLE IF NOT EXISTS chat_messages(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                conversation_id INTEGER,
                sender_user_id INTEGER,
                content TEXT,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
              )
            """);

            // doctors / schedules / appts
            st.executeUpdate("""
              CREATE TABLE IF NOT EXISTS doctors(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER,
                name TEXT,
                specialty TEXT,
                bio TEXT,
                photo_url TEXT
              )
            """);
            st.executeUpdate("""
              CREATE TABLE IF NOT EXISTS doctor_schedules(
                doctor_id INTEGER,
                day_of_week INTEGER,
                start_time TEXT,
                end_time TEXT
              )
            """);
            st.executeUpdate("""
              CREATE TABLE IF NOT EXISTS appointments(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER,
                doctor_id INTEGER,
                start_at TEXT,
                end_at TEXT,
                video_url TEXT
              )
            """);

            // health sessions
            st.executeUpdate("""
              CREATE TABLE IF NOT EXISTS health_sessions(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT,
                short_desc TEXT,
                date TEXT,
                start_time TEXT,
                end_time TEXT
              )
            """);
            st.executeUpdate("""
              CREATE TABLE IF NOT EXISTS health_session_locations(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id INTEGER,
                place TEXT
              )
            """);
            st.executeUpdate("""
              CREATE TABLE IF NOT EXISTS health_session_registrations(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id INTEGER,
                location_id INTEGER,
                user_id INTEGER,
                child_name TEXT,
                child_age INTEGER,
                prev_vaccines TEXT,
                prev_diseases TEXT,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
              )
            """);
            // optional outcomes table (for tests/vaccines actually taken)
            st.executeUpdate("""
              CREATE TABLE IF NOT EXISTS health_session_outcomes(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                registration_id INTEGER,
                type TEXT,
                notes TEXT,
                taken_at DATETIME DEFAULT CURRENT_TIMESTAMP
              )
            """);

            // teachers
            st.executeUpdate("""
              CREATE TABLE IF NOT EXISTS teachers(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER,
                name TEXT,
                qualification TEXT,
                salary_min INTEGER,
                salary_max INTEGER,
                photo_url TEXT
              )
            """);
            st.executeUpdate("""
              CREATE TABLE IF NOT EXISTS teacher_schedules(
                teacher_id INTEGER,
                day_of_week INTEGER,
                start_time TEXT,
                end_time TEXT
              )
            """);
        }

        // Backfill columns if older DB existed without image columns
  addColumnIfMissing(c, "posts", "image_url", "TEXT");
  addColumnIfMissing(c, "posts", "anonymous", "INTEGER");
        addColumnIfMissing(c, "comments", "image_url", "TEXT");
    }

    private static void addColumnIfMissing(Connection c, String table, String col, String type) throws SQLException {
        boolean exists = false;
        try (PreparedStatement ps = c.prepareStatement("PRAGMA table_info(" + table + ")")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (col.equalsIgnoreCase(rs.getString("name"))) { exists = true; break; }
                }
            }
        }
        if (!exists) {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + col + " " + type);
            }
        }
    }
}
