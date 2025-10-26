package com.pa.server;

import java.net.*;
import java.io.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import com.pa.server.dao.Db;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private volatile PrintWriter out;
    private volatile Integer authedUserId = null;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public void sendLine(String line) {
        var o = this.out;
        if (o != null)
            synchronized (o) {
                o.println(line);
            }
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter pout = new PrintWriter(socket.getOutputStream(), true)) {
            this.out = pout;
            sendLine("WELCOME");

            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank())
                    continue;
                String[] p = line.split("\\|", -1);
                String cmd = p[0].trim();

                try {
                    switch (cmd) {
                        case "PING" -> sendLine("PONG");

                        // ===== auth & subs =====
                        case "AUTH" -> { // AUTH|userId
                            authedUserId = Integer.parseInt(p[1]);
                            ClientHub.get().registerUser(authedUserId, this);
                            sendLine("AUTH_OK|" + authedUserId);
                        }
                        case "CHAT_SUB" -> {
                            ClientHub.get().subscribe(Integer.parseInt(p[1]), this);
                            sendLine("SUB_OK|" + p[1]);
                        }
                        case "CHAT_UNSUB" -> {
                            ClientHub.get().unsubscribe(Integer.parseInt(p[1]), this);
                            sendLine("UNSUB_OK|" + p[1]);
                        }
                        case "COMMENT_SUB" -> {
                            ClientHub.get().subscribeComment(Integer.parseInt(p[1]), this);
                            sendLine("CSUB_OK|" + p[1]);
                        }
                        case "COMMENT_UNSUB" -> {
                            ClientHub.get().unsubscribeComment(Integer.parseInt(p[1]), this);
                            sendLine("CUNSUB_OK|" + p[1]);
                        }

                        // ===== users =====
                        case "LOGIN" -> handleLogin(p);
                        case "SIGNUP" -> handleSignup(p);
                        case "USER_GET" -> handleUserGet(p); // END
                        case "USER_PROFILE" -> handleUserProfile(p); // END

                        // ===== posts & comments (images + delete) =====
                        case "POST_CREATE" -> handlePostCreate(p); // END
                        case "POST_DELETE" -> handlePostDelete(p); // END
                        case "COMMENT_CREATE" -> handleCommentCreate(p); // END (+push)
                        case "COMMENT_DELETE" -> handleCommentDelete(p); // END
                        case "FEED_HOME" -> handleFeedHome(); // END
                        case "FEED_BY_USER" -> handleFeedByUser(p); // END
                        case "COMMENTS_WITH_USERS" -> handleCommentsWithUsers(p);// END
                        case "FETCH_POSTS" -> handleFetchPosts(); // END (legacy)
                        case "FETCH_COMMENTS" -> handleFetchComments(p); // END (legacy)

                        // ===== doctors / appts =====
                        case "DOCTOR_LIST" -> handleDoctorList(); // END
                        case "DOCTOR_GET" -> handleDoctorGet(p); // END
                        case "DOCTOR_FIND_BY_USER" -> handleDoctorFindByUser(p); // END
                        case "APPT_SLOTS" -> handleApptSlots(p); // END
                        case "APPT_BOOK" -> handleApptBook(p); // END

                        // ===== teachers (Search Private Teacher) =====
                        case "TEACHER_LIST" -> handleTeacherList(); // END
                        case "TEACHER_GET" -> handleTeacherGet(p); // END
                        case "TEACHER_INTEREST" -> handleTeacherInterest(p); // END
                        case "TEACHER_REGISTER" -> handleTeacherRegister(p); // END

                        // ===== chat =====
                        case "MY_CONVS" -> handleMyConvs(p); // END
                        case "USER_SEARCH" -> handleUserSearch(p); // END
                        case "CHAT_OPEN" -> handleChatOpen(p); // END
                        case "CHAT_SEND" -> handleChatSendAndBroadcast(p); // END (+push)
                        case "CHAT_FETCH" -> handleChatFetch(p); // END

                        // ===== health sessions =====
                        case "SESSIONS_UPCOMING" -> handleSessionsUpcoming(); // END
                        case "SESSION_GET" -> handleSessionGet(p); // END
                        case "SESSION_REGISTER" -> handleSessionRegister(p); // END

                        case "QUIT" -> {
                            return;
                        }
                        default -> sendLine("ERR|UNKNOWN|" + cmd);
                    }
                } catch (Exception ex) {
                    sendLine("ERR|EX|" + ex.getMessage());
                    sendLine("END");
                }
            }
        } catch (IOException ignored) {
        } finally {
            ClientHub.get().unregisterHandler(this, authedUserId);
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    // ===== users =====

    private void handleLogin(String[] p) throws Exception {
        if (p.length < 3) {
            sendLine("ERR|LOGIN|ARGS");
            return;
        }
        try (Connection c = Db.get();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT id,display_name,IFNULL(role,'USER') FROM users WHERE email=? AND password_hash=?")) {
            ps.setString(1, p[1]);
            ps.setString(2, p[2]);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    sendLine("LOGIN_OK|" + rs.getInt(1) + "|" + rs.getString(2) + "|" + rs.getString(3));
                else
                    sendLine("ERR|LOGIN|Invalid");
            }
        }
    }

    private void handleSignup(String[] p) throws Exception {
        if (p.length < 4) {
            sendLine("ERR|SIGNUP|ARGS");
            return;
        }
        try (Connection c = Db.get();
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO users(email,password_hash,display_name,role) VALUES (?,?,?,'USER')")) {
            ps.setString(1, p[1]);
            ps.setString(2, p[2]);
            ps.setString(3, p[3]);
            ps.executeUpdate();
            sendLine("SIGNUP_OK");
        } catch (SQLException e) {
            sendLine("ERR|SIGNUP|" + e.getMessage());
        }
    }

    private void handleUserGet(String[] p) throws Exception { // USER_GET|userId
        if (p.length < 2) {
            sendLine("ERR|USER_GET|ARGS");
            sendLine("END");
            return;
        }
        int uid = Integer.parseInt(p[1]);
        try (Connection c = Db.get();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT id,display_name,IFNULL(role,'USER') FROM users WHERE id=?")) {
            ps.setInt(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    sendLine("USER|" + rs.getInt(1) + "|" + rs.getString(2) + "|" + rs.getString(3));
                else
                    sendLine("ERR|USER_GET|NOT_FOUND");
            }
            sendLine("END");
        }
    }

    /**
     * USER_PROFILE|userId
     * Emits:
     * PROFILE|id|display_name|email|role
     * BOOKING|apptId|doctorId|doctorName|start_at|end_at|video_url
     * REG|regId|sessionId|sessionName|date|start|end|locationName|childName|childAge|prev_vaccines|prev_diseases
     * OUTCOME|regId|type|notes|taken_at
     * Then END
     */
    private void handleUserProfile(String[] p) throws Exception {
        if (p.length < 2) {
            sendLine("ERR|USER_PROFILE|ARGS");
            sendLine("END");
            return;
        }
        int uid = Integer.parseInt(p[1]);

        try (Connection c = Db.get()) {
            // header
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id,display_name,email,IFNULL(role,'USER') FROM users WHERE id=?")) {
                ps.setInt(1, uid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        sendLine("PROFILE|" + rs.getInt(1) + "|" +
                                safe(rs.getString(2)) + "|" + safe(rs.getString(3)) + "|" + safe(rs.getString(4)));
                    } else {
                        sendLine("ERR|USER_PROFILE|NOT_FOUND");
                        sendLine("END");
                        return;
                    }
                }
            }

            // appointments
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT a.id,a.doctor_id,COALESCE(d.name,'Doctor'),a.start_at,a.end_at,COALESCE(a.video_url,'') " +
                            "FROM appointments a LEFT JOIN doctors d ON d.id=a.doctor_id " +
                            "WHERE a.user_id=? ORDER BY a.start_at DESC")) {
                ps.setInt(1, uid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        sendLine("BOOKING|" + rs.getInt(1) + "|" + rs.getInt(2) + "|" +
                                safe(rs.getString(3)) + "|" + safe(rs.getString(4)) + "|" +
                                safe(rs.getString(5)) + "|" + safe(rs.getString(6)));
                    }
                }
            }

            // health session registrations
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT r.id, r.session_id, s.name, s.date, s.start_time, s.end_time, " +
                            "COALESCE(l.place,''), r.child_name, r.child_age, " +
                            "COALESCE(r.prev_vaccines,''), COALESCE(r.prev_diseases,'') " +
                            "FROM health_session_registrations r " +
                            "JOIN health_sessions s ON s.id=r.session_id " +
                            "LEFT JOIN health_session_locations l ON l.id=r.location_id " +
                            "WHERE r.user_id=? ORDER BY r.id DESC")) {
                ps.setInt(1, uid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        sendLine("REG|" + rs.getInt(1) + "|" + rs.getInt(2) + "|" +
                                safe(rs.getString(3)) + "|" + safe(rs.getString(4)) + "|" +
                                safe(rs.getString(5)) + "|" + safe(rs.getString(6)) + "|" +
                                safe(rs.getString(7)) + "|" + rs.getInt(8) + "|" +
                                safe(rs.getString(9)) + "|" + safe(rs.getString(10)));
                    }
                }
            }

            // optional outcomes
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT o.registration_id,o.type,COALESCE(o.notes,''),o.taken_at " +
                            "FROM health_session_outcomes o " +
                            "JOIN health_session_registrations r ON r.id=o.registration_id " +
                            "WHERE r.user_id=? ORDER BY o.taken_at DESC")) {
                ps.setInt(1, uid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        sendLine("OUTCOME|" + rs.getInt(1) + "|" +
                                safe(rs.getString(2)) + "|" + safe(rs.getString(3)) + "|" + safe(rs.getString(4)));
                    }
                }
            }

            sendLine("END");
        }
    }

    // ===== posts & comments =====

    private void handlePostCreate(String[] p) throws Exception {
        // POST_CREATE|userId|content[|imageUrl|anonymous]
        if (p.length < 3) {
            sendLine("ERR|POST|ARGS");
            sendLine("END");
            return;
        }
        int userId = Integer.parseInt(p[1]);
        String content = p[2] == null ? "" : p[2].replace("\n", " ").replace("|", " ");
        String imageUrl = (p.length >= 4) ? p[3].trim() : "";
        int anonymous = 0;
        if (p.length >= 5) {
            try { anonymous = Integer.parseInt(p[4]); } catch (Exception ignored) { anonymous = 0; }
        }
        if (content.isBlank() && imageUrl.isBlank()) {
            sendLine("ERR|POST|EMPTY");
            sendLine("END");
            return;
        }
        try (Connection c = Db.get();
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO posts(user_id,content,image_url,anonymous) VALUES (?,?,?,?)")) {
            ps.setInt(1, userId);
            ps.setString(2, content);
            ps.setString(3, imageUrl);
            ps.setInt(4, anonymous);
            ps.executeUpdate();
            sendLine("POST_OK");
            sendLine("END");
        }
    }

    private void handlePostDelete(String[] p) throws Exception {
        // POST_DELETE|postId|userId
        if (p.length < 3) {
            sendLine("ERR|POST_DEL|ARGS");
            sendLine("END");
            return;
        }
        int postId = Integer.parseInt(p[1]);
        int userId = Integer.parseInt(p[2]);
        try (Connection c = Db.get()) {
            try (PreparedStatement ck = c.prepareStatement("SELECT 1 FROM posts WHERE id=? AND user_id=?")) {
                ck.setInt(1, postId);
                ck.setInt(2, userId);
                try (ResultSet rs = ck.executeQuery()) {
                    if (!rs.next()) {
                        sendLine("ERR|POST_DEL|FORBIDDEN");
                        sendLine("END");
                        return;
                    }
                }
            }
            try (PreparedStatement d1 = c.prepareStatement("DELETE FROM comments WHERE post_id=?")) {
                d1.setInt(1, postId);
                d1.executeUpdate();
            }
            try (PreparedStatement d2 = c.prepareStatement("DELETE FROM posts WHERE id=?")) {
                d2.setInt(1, postId);
                d2.executeUpdate();
            }
            sendLine("POST_DEL_OK|" + postId);
            sendLine("END");
        }
    }

    private void handleCommentCreate(String[] p) throws Exception {
        // COMMENT_CREATE|postId|userId|content[|imageUrl]
        if (p.length < 4) {
            sendLine("ERR|COMMENT|ARGS");
            sendLine("END");
            return;
        }
        int postId = Integer.parseInt(p[1]);
        int userId = Integer.parseInt(p[2]);
        String content = p[3] == null ? "" : p[3].replace("\n", " ").replace("|", " ");
        String imageUrl = (p.length >= 5) ? p[4].trim() : "";
        if (content.isBlank() && imageUrl.isBlank()) {
            sendLine("ERR|COMMENT|EMPTY");
            sendLine("END");
            return;
        }

        int id;
        String created;
        String name;
        String role;
        try (Connection c = Db.get()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO comments(post_id,user_id,content,image_url) VALUES (?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, postId);
                ps.setInt(2, userId);
                ps.setString(3, content);
                ps.setString(4, imageUrl);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    id = rs.getInt(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT created_at FROM comments WHERE id=?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    created = rs.next() ? rs.getString(1) : "";
                }
            }
            try (PreparedStatement ps = c
                    .prepareStatement("SELECT display_name,IFNULL(role,'USER') FROM users WHERE id=?")) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        name = rs.getString(1);
                        role = rs.getString(2);
                    } else {
                        name = "User";
                        role = "USER";
                    }
                }
            }
        }
        ClientHub.get().broadcastComment(postId,
                "COMMENT_NEW|" + postId + "|" + id + "|" + userId + "|" + name + "|" + role + "|" + content + "|"
                        + created + "|" + imageUrl);
        sendLine("COMMENT_OK");
        sendLine("END");
    }

    private void handleCommentDelete(String[] p) throws Exception {
        // COMMENT_DELETE|commentId|userId
        if (p.length < 3) {
            sendLine("ERR|COMMENT_DEL|ARGS");
            sendLine("END");
            return;
        }
        int cid = Integer.parseInt(p[1]);
        int uid = Integer.parseInt(p[2]);
        try (Connection c = Db.get()) {
            try (PreparedStatement ck = c.prepareStatement("SELECT 1 FROM comments WHERE id=? AND user_id=?")) {
                ck.setInt(1, cid);
                ck.setInt(2, uid);
                try (ResultSet rs = ck.executeQuery()) {
                    if (!rs.next()) {
                        sendLine("ERR|COMMENT_DEL|FORBIDDEN");
                        sendLine("END");
                        return;
                    }
                }
            }
            try (PreparedStatement d = c.prepareStatement("DELETE FROM comments WHERE id=?")) {
                d.setInt(1, cid);
                d.executeUpdate();
            }
            sendLine("COMMENT_DEL_OK|" + cid);
            sendLine("END");
        }
    }

    private void handleFeedHome() throws Exception {
        try (Connection c = Db.get();
                Statement st = c.createStatement();
        ResultSet rs = st.executeQuery(
            "SELECT p.id,p.user_id,COALESCE(u.display_name,'Anonymous'),COALESCE(u.role,'USER'),p.content,p.created_at,COALESCE(p.image_url,''),IFNULL(p.anonymous,0) "
                +
                "FROM posts p LEFT JOIN users u ON u.id=p.user_id ORDER BY p.id DESC LIMIT 100")) {
        while (rs.next()) {
        int postId = rs.getInt(1);
        int userId = rs.getInt(2);
        String display = rs.getString(3);
        String role = rs.getString(4);
        String content = rs.getString(5).replace("\n", " ");
        String created = rs.getString(6);
        String image = rs.getString(7);
        int anon = rs.getInt(8);
        String outName = (anon == 1) ? "Anonymous" : display;
        sendLine("POST|" + postId + "|" + userId + "|" + outName + "|" + role + "|" + content + "|" + created + "|" + image);
        }
            sendLine("END");
        }
    }

    private void handleFeedByUser(String[] p) throws Exception { // FEED_BY_USER|viewedUserId[|viewerUserId]
        if (p.length < 2) {
            sendLine("ERR|FEED_BY_USER|ARGS");
            sendLine("END");
            return;
        }
        int viewed = Integer.parseInt(p[1]);
        int viewer = -1;
        if (p.length >= 3) {
            try { viewer = Integer.parseInt(p[2]); } catch (Exception ignored) { viewer = -1; }
        }
        try (Connection c = Db.get();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT p.id,p.user_id,COALESCE(u.display_name,'Anonymous'),COALESCE(u.role,'USER'),p.content,p.created_at,COALESCE(p.image_url,''),IFNULL(p.anonymous,0) "
                                +
                                "FROM posts p LEFT JOIN users u ON u.id=p.user_id WHERE p.user_id=? ORDER BY p.id DESC")) {
            ps.setInt(1, viewed);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int postId = rs.getInt(1);
                    int userId = rs.getInt(2);
                    String display = rs.getString(3);
                    String role = rs.getString(4);
                    String content = rs.getString(5).replace("\n", " ");
                    String created = rs.getString(6);
                    String image = rs.getString(7);
                    int anon = rs.getInt(8);
                    // if anonymous and the viewer is not the owner, show Anonymous
                    String outName = (anon == 1 && viewer != userId) ? "Anonymous" : display;
                    sendLine("POST|" + postId + "|" + userId + "|" + outName + "|" + role + "|" + content + "|" + created + "|" + image);
                }
            }
            sendLine("END");
        }
    }

    private void handleCommentsWithUsers(String[] p) throws Exception { // COMMENTS_WITH_USERS|postId
        if (p.length < 2) {
            sendLine("ERR|COMMENTS|ARGS");
            sendLine("END");
            return;
        }
        int postId = Integer.parseInt(p[1]);
        try (Connection c = Db.get();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT c.id,u.id,u.display_name,IFNULL(u.role,'USER'),c.content,c.created_at,IFNULL(c.image_url,'') "
                                +
                                "FROM comments c JOIN users u ON u.id=c.user_id WHERE c.post_id=? ORDER BY c.id ASC")) {
            ps.setInt(1, postId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sendLine("COMMENT|" + rs.getInt(1) + "|" + rs.getInt(2) + "|" + rs.getString(3) + "|" +
                            rs.getString(4) + "|" + rs.getString(5).replace("\n", " ") + "|" + rs.getString(6) + "|"
                            + rs.getString(7));
                }
            }
            sendLine("END");
        }
    }

    // legacy (not used by new UI, but kept)
    private void handleFetchPosts() throws Exception {
        try (Connection c = Db.get();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT p.id, COALESCE(u.display_name,'Anonymous'), p.content, p.created_at, COALESCE(p.image_url,'') "
                                +
                                "FROM posts p LEFT JOIN users u ON u.id=p.user_id ORDER BY p.id DESC LIMIT 100")) {
            while (rs.next()) {
                sendLine("POST|" + rs.getInt(1) + "|" + rs.getString(2) + "|" + rs.getString(3).replace("\n", " ") + "|"
                        + rs.getString(4) + "|" + rs.getString(5));
            }
            sendLine("END");
        }
    }

    private void handleFetchComments(String[] p) throws Exception {
        if (p.length < 2) {
            sendLine("ERR|COMMENTS|ARGS");
            sendLine("END");
            return;
        }
        int postId = Integer.parseInt(p[1]);
        try (Connection c = Db.get();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT c.id,u.display_name,c.content,c.created_at " +
                                "FROM comments c JOIN users u ON u.id=c.user_id WHERE c.post_id=? ORDER BY c.id ASC")) {
            ps.setInt(1, postId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sendLine("COMMENT|" + rs.getInt(1) + "|" + rs.getString(2) + "|" +
                            rs.getString(3).replace("\n", " ") + "|" + rs.getString(4));
                }
            }
            sendLine("END");
        }
    }

    // ===== doctors / appointments =====

    private void handleDoctorList() throws Exception {
        try (Connection c = Db.get();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT id,name,specialty,IFNULL(photo_url,'') FROM doctors ORDER BY name")) {
            while (rs.next()) {
                sendLine("DOCTOR|" + rs.getInt(1) + "|" + rs.getString(2) + "|" +
                        rs.getString(3) + "|" + rs.getString(4));
            }
            sendLine("END");
        }
    }

    private void handleDoctorGet(String[] p) throws Exception {
        if (p.length < 2) {
            sendLine("ERR|DOCTOR_GET|ARGS");
            sendLine("END");
            return;
        }
        int id = Integer.parseInt(p[1]);
        try (Connection c = Db.get()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id,name,specialty,IFNULL(bio,''),IFNULL(photo_url,''),IFNULL(user_id,0) FROM doctors WHERE id=?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        sendLine("ERR|DOCTOR_GET|NOT_FOUND");
                        sendLine("END");
                        return;
                    }
                    sendLine("DOCTOR|" + rs.getInt(1) + "|" + rs.getString(2) + "|" + rs.getString(3) + "|" +
                            rs.getString(4).replace("\n", " ") + "|" + rs.getString(5) + "|" + rs.getInt(6));
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT day_of_week,start_time,end_time FROM doctor_schedules WHERE doctor_id=? ORDER BY day_of_week,start_time")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        sendLine("SCHED|" + rs.getInt(1) + "|" + rs.getString(2) + "|" + rs.getString(3));
                }
            }
            sendLine("END");
        }
    }

    private void handleDoctorFindByUser(String[] p) throws Exception { // DOCTOR_FIND_BY_USER|userId
        if (p.length < 2) {
            sendLine("ERR|DOC_FIND|ARGS");
            sendLine("END");
            return;
        }
        int uid = Integer.parseInt(p[1]);
        try (Connection c = Db.get();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT id,name FROM doctors WHERE user_id=?")) {
            ps.setInt(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    sendLine("DOCTOR_ID|" + rs.getInt(1) + "|" + rs.getString(2));
                else
                    sendLine("ERR|DOC_FIND|NOT_FOUND");
            }
            sendLine("END");
        }
    }

    /** APPT_SLOTS|doctorId|YYYY-MM-DD -> SLOTS|HH:mm,HH:mm,... then END */
    private void handleApptSlots(String[] p) throws Exception {
        if (p.length < 3) {
            sendLine("ERR|SLOTS|ARGS");
            sendLine("END");
            return;
        }
        int doctorId = Integer.parseInt(p[1]);
        LocalDate date = LocalDate.parse(p[2]);

        int dow = date.getDayOfWeek().getValue(); // 1..7

        List<String> windows = new ArrayList<>();
        try (Connection c = Db.get();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT start_time,end_time FROM doctor_schedules WHERE doctor_id=? AND day_of_week=? ORDER BY start_time")) {
            ps.setInt(1, doctorId);
            ps.setInt(2, dow);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    windows.add(rs.getString(1) + "-" + rs.getString(2));
            }
        }

        if (windows.isEmpty()) {
            sendLine("SLOTS|");
            sendLine("END");
            return;
        }

        // booked times (same date)
        Set<String> booked = new HashSet<>();
        try (Connection c = Db.get();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT substr(start_at,12,5) AS HHMM FROM appointments WHERE doctor_id=? AND date(start_at)=?")) {
            ps.setInt(1, doctorId);
            ps.setString(2, date.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    booked.add(rs.getString(1));
            }
        }

        LocalTime nowTime = LocalTime.now();
        LocalDate today = LocalDate.now();

        List<String> out = new ArrayList<>();
        for (String w : windows) {
            String[] se = w.split("-", -1);
            if (se.length < 2)
                continue;
            LocalTime start = LocalTime.parse(se[0]);
            LocalTime end = LocalTime.parse(se[1]);
            for (LocalTime t = start; !t.plusMinutes(30).isAfter(end); t = t.plusMinutes(30)) {
                String hhmm = String.format("%02d:%02d", t.getHour(), t.getMinute());
                if (booked.contains(hhmm))
                    continue;
                if (date.isEqual(today) && !t.isAfter(nowTime))
                    continue; // no past times today
                out.add(hhmm);
            }
        }

        sendLine("SLOTS|" + String.join(",", out));
        sendLine("END");
    }

    private void handleApptBook(String[] p) throws Exception { // APPT_BOOK|userId|doctorId|startISO|endISO
        if (p.length < 5) {
            sendLine("ERR|APPT|ARGS");
            sendLine("END");
            return;
        }
        int userId = Integer.parseInt(p[1]);
        int doctorId = Integer.parseInt(p[2]);
        LocalDateTime start = LocalDateTime.parse(p[3]);
        LocalDateTime end = LocalDateTime.parse(p[4]);

        if (start.isBefore(LocalDateTime.now())) {
            sendLine("ERR|APPT|PAST");
            sendLine("END");
            return;
        }

        int dow = start.getDayOfWeek().getValue();
        boolean inside = false;
        try (Connection c = Db.get();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT start_time,end_time FROM doctor_schedules WHERE doctor_id=? AND day_of_week=?")) {
            ps.setInt(1, doctorId);
            ps.setInt(2, dow);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalTime s = LocalTime.parse(rs.getString(1));
                    LocalTime e = LocalTime.parse(rs.getString(2));
                    LocalTime t = start.toLocalTime();
                    if (!t.isBefore(s) && !t.plusMinutes(30).isAfter(e)) {
                        inside = true;
                        break;
                    }
                }
            }
        }
        if (!inside) {
            sendLine("ERR|APPT|OUT_OF_SCHEDULE");
            sendLine("END");
            return;
        }

        // already booked?
        try (Connection c = Db.get();
                PreparedStatement ck = c.prepareStatement(
                        "SELECT 1 FROM appointments WHERE doctor_id=? AND start_at=? LIMIT 1")) {
            ck.setInt(1, doctorId);
            ck.setString(2, start.toString());
            try (ResultSet rs = ck.executeQuery()) {
                if (rs.next()) {
                    sendLine("ERR|APPT|ALREADY_BOOKED");
                    sendLine("END");
                    return;
                }
            }
        }

        String url;
        int apptId;
        try (Connection c = Db.get();
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO appointments(user_id,doctor_id,start_at,end_at,video_url) VALUES (?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            url = "https://meet.jit.si/perentalassist_" + System.currentTimeMillis();
            ps.setInt(1, userId);
            ps.setInt(2, doctorId);
            ps.setString(3, start.toString());
            ps.setString(4, end.toString());
            ps.setString(5, url);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                apptId = rs.getInt(1);
            }
        }
        url = "https://meet.jit.si/perentalassist_" + apptId;
        try (Connection c = Db.get();
                PreparedStatement ups = c.prepareStatement("UPDATE appointments SET video_url=? WHERE id=?")) {
            ups.setString(1, url);
            ups.setInt(2, apptId);
            ups.executeUpdate();
        }

        // send booking message from doctor to user (if doctor linked to a user account)
        try (Connection c = Db.get();
                PreparedStatement ps = c.prepareStatement("SELECT user_id,name FROM doctors WHERE id=?")) {
            ps.setInt(1, doctorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int doctorUserId = rs.getInt(1);
                    String docName = rs.getString(2);
                    if (doctorUserId > 0) {
                        Integer convId = findExistingConversation(c, userId, doctorUserId);
                        if (convId == null)
                            convId = createConversation(c, userId, doctorUserId);
                        String msg = "Appointment confirmed with " + docName +
                                " on " + start.toLocalDate() + " at "
                                + String.format("%02d:%02d", start.getHour(), start.getMinute()) +
                                ". Video link: " + url;
                        try (PreparedStatement ins = c.prepareStatement(
                                "INSERT INTO chat_messages(conversation_id,sender_user_id,content) VALUES (?,?,?)")) {
                            ins.setInt(1, convId);
                            ins.setInt(2, doctorUserId);
                            ins.setString(3, msg);
                            ins.executeUpdate();
                        }
                        ClientHub.get().broadcast(convId,
                                "MSG|" + convId + "|0|" + doctorUserId + "|" + msg + "|" + LocalDateTime.now());
                    }
                }
            }
        }

        sendLine("APPT_OK|" + apptId + "|" + url);
        sendLine("END");
    }

    // ===== teachers =====

    private void handleTeacherList() throws Exception {
        try (Connection c = Db.get();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT id,name,IFNULL(qualification,''),IFNULL(salary_min,0),IFNULL(salary_max,0),IFNULL(photo_url,''),IFNULL(user_id,0) "
                                +
                                "FROM teachers ORDER BY id DESC")) {
            while (rs.next()) {
                sendLine("TEACHER|" + rs.getInt(1) + "|" + rs.getString(2) + "|" + rs.getString(3) + "|" +
                        rs.getInt(4) + "|" + rs.getInt(5) + "|" + rs.getString(6) + "|" + rs.getInt(7));
            }
            sendLine("END");
        }
    }

    private void handleTeacherGet(String[] p) throws Exception { // TEACHER_GET|teacherId
        if (p.length < 2) {
            sendLine("ERR|TEACHER_GET|ARGS");
            sendLine("END");
            return;
        }
        int id = Integer.parseInt(p[1]);
        try (Connection c = Db.get()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id,name,IFNULL(qualification,''),IFNULL(salary_min,0),IFNULL(salary_max,0),IFNULL(photo_url,''),IFNULL(user_id,0) "
                            +
                            "FROM teachers WHERE id=?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        sendLine("ERR|TEACHER_GET|NOT_FOUND");
                        sendLine("END");
                        return;
                    }
                    sendLine("TEACHER|" + rs.getInt(1) + "|" + rs.getString(2) + "|" + rs.getString(3) + "|" +
                            rs.getInt(4) + "|" + rs.getInt(5) + "|" + rs.getString(6) + "|" + rs.getInt(7));
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT day_of_week,start_time,end_time FROM teacher_schedules WHERE teacher_id=? ORDER BY day_of_week,start_time")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        sendLine("SCHED|" + rs.getInt(1) + "|" + rs.getString(2) + "|" + rs.getString(3));
                    }
                }
            }
            sendLine("END");
        }
    }

    private void handleTeacherRegister(String[] p) throws Exception {
        // TEACHER_REGISTER|userId|name|qualification|min|max|day,start,end;day,start,end;...
        if (p.length < 7) {
            sendLine("ERR|TEACHER_REG|ARGS");
            sendLine("END");
            return;
        }
        int userId = Integer.parseInt(p[1]);
        String name = safe(p[2]);
        String q = safe(p[3]);
        int min = parseIntSafe(p[4]);
        int max = parseIntSafe(p[5]);
        String schedCsv = p[6] == null ? "" : p[6].trim();

        try (Connection c = Db.get()) {
            // allow register once
            try (PreparedStatement ck = c.prepareStatement("SELECT id FROM teachers WHERE user_id=? LIMIT 1")) {
                ck.setInt(1, userId);
                try (ResultSet rs = ck.executeQuery()) {
                    if (rs.next()) {
                        sendLine("ERR|TEACHER_REG|ALREADY");
                        sendLine("END");
                        return;
                    }
                }
            }

            int teacherId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO teachers(user_id,name,qualification,salary_min,salary_max,photo_url) VALUES (?,?,?,?,?,NULL)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, userId);
                ps.setString(2, name);
                ps.setString(3, q);
                ps.setInt(4, min);
                ps.setInt(5, max);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    teacherId = rs.getInt(1);
                }
            }

            if (!schedCsv.isBlank()) {
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO teacher_schedules(teacher_id,day_of_week,start_time,end_time) VALUES (?,?,?,?)")) {
                    for (String token : schedCsv.split(";", -1)) {
                        if (token.isBlank())
                            continue;
                        String[] t = token.split(",", -1);
                        if (t.length < 3)
                            continue;
                        int day = parseIntSafe(t[0]);
                        String st = t[1];
                        String et = t[2];
                        if (validTime(st) && validTime(et) && day >= 1 && day <= 7) {
                            ins.setInt(1, teacherId);
                            ins.setInt(2, day);
                            ins.setString(3, st);
                            ins.setString(4, et);
                            ins.addBatch();
                        }
                    }
                    ins.executeBatch();
                }
            }

            sendLine("TEACHER_REG_OK|" + teacherId);
            sendLine("END");
        }
    }

    private void handleTeacherInterest(String[] p) throws Exception {
        // TEACHER_INTEREST|meUserId|teacherId
        if (p.length < 3) {
            sendLine("ERR|TEACHER_INT|ARGS");
            sendLine("END");
            return;
        }
        int me = Integer.parseInt(p[1]);
        int teacherId = Integer.parseInt(p[2]);

        int teacherUserId = 0;
        try (Connection c = Db.get();
                PreparedStatement ps = c.prepareStatement("SELECT IFNULL(user_id,0) FROM teachers WHERE id=?")) {
            ps.setInt(1, teacherId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    teacherUserId = rs.getInt(1);
            }
        }
        if (teacherUserId <= 0) {
            sendLine("ERR|TEACHER_INT|NO_USER");
            sendLine("END");
            return;
        }

        int convId;
        try (Connection c = Db.get()) {
            Integer existing = findExistingConversation(c, me, teacherUserId);
            if (existing == null)
                existing = createConversation(c, me, teacherUserId);
            convId = existing;

            String msg = "Hello there! I am interested to hire you as a teacher for my child. So I want to talk in details.";
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO chat_messages(conversation_id,sender_user_id,content) VALUES (?,?,?)")) {
                ps.setInt(1, convId);
                ps.setInt(2, me);
                ps.setString(3, msg);
                ps.executeUpdate();
            }
        }

        sendLine("INTEREST_OK|" + convId);
        sendLine("END");
    }

    // ===== chat =====

    private void handleMyConvs(String[] p) throws Exception { // MY_CONVS|me
        if (p.length < 2) {
            sendLine("ERR|MY_CONVS|ARGS");
            sendLine("END");
            return;
        }
        int userId = Integer.parseInt(p[1]);
        String sql = "SELECT cc.id, COALESCE(cc.title, (SELECT GROUP_CONCAT(u.display_name, ', ') " +
                " FROM chat_members m JOIN users u ON u.id=m.user_id " +
                " WHERE m.conversation_id=cc.id AND u.id<>?)) " +
                "FROM chat_conversations cc " +
                "WHERE EXISTS (SELECT 1 FROM chat_members m WHERE m.conversation_id=cc.id AND m.user_id=?) " +
                "ORDER BY cc.id DESC";
        try (Connection c = Db.get();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String title = rs.getString(2);
                    if (title == null)
                        title = "Chat";
                    sendLine("CONV|" + rs.getInt(1) + "|" + title);
                }
            }
            sendLine("END");
        }
    }

    private void handleUserSearch(String[] p) throws Exception { // USER_SEARCH|term
        if (p.length < 2) {
            sendLine("ERR|USER_SEARCH|ARGS");
            sendLine("END");
            return;
        }
        String q = "%" + p[1] + "%";
        try (Connection c = Db.get();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT id,display_name,IFNULL(role,'USER') FROM users WHERE display_name LIKE ? ORDER BY display_name LIMIT 20")) {
            ps.setString(1, q);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sendLine("USER|" + rs.getInt(1) + "|" + rs.getString(2) + "|" + rs.getString(3));
                }
            }
            sendLine("END");
        }
    }

    private void handleChatOpen(String[] p) throws Exception { // CHAT_OPEN|me|other
        if (p.length < 3) {
            sendLine("ERR|CHAT_OPEN|ARGS");
            sendLine("END");
            return;
        }
        int me = Integer.parseInt(p[1]);
        int other = Integer.parseInt(p[2]);
        try (Connection c = Db.get()) {
            Integer convId = findExistingConversation(c, me, other);
            if (convId == null)
                convId = createConversation(c, me, other);
            sendLine("CHAT_OK|" + convId);
            sendLine("END");
        }
    }

    private Integer findExistingConversation(Connection c, int a, int b) throws SQLException {
        String sql = "SELECT cc.id FROM chat_conversations cc " +
                "WHERE EXISTS (SELECT 1 FROM chat_members m WHERE m.conversation_id=cc.id AND m.user_id=?) " +
                "AND EXISTS (SELECT 1 FROM chat_members m WHERE m.conversation_id=cc.id AND m.user_id=?) " +
                "ORDER BY cc.id DESC LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, a);
            ps.setInt(2, b);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getInt(1);
            }
        }
        return null;
    }

    private int createConversation(Connection c, int a, int b) throws SQLException {
        int id;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO chat_conversations(title) VALUES (NULL)", Statement.RETURN_GENERATED_KEYS)) {
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                id = rs.getInt(1);
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO chat_members(conversation_id,user_id) VALUES (?,?),(?,?)")) {
            ps.setInt(1, id);
            ps.setInt(2, a);
            ps.setInt(3, id);
            ps.setInt(4, b);
            ps.executeUpdate();
        }
        return id;
    }

    private void handleChatSendAndBroadcast(String[] p) throws Exception { // CHAT_SEND|convId|sender|text
        if (p.length < 4) {
            sendLine("ERR|CHAT_SEND|ARGS");
            sendLine("END");
            return;
        }
        int convId = Integer.parseInt(p[1]);
        int sender = Integer.parseInt(p[2]);
        String msg = p[3];

        int id;
        String created;
        try (Connection c = Db.get();
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO chat_messages(conversation_id,sender_user_id,content) VALUES (?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, convId);
            ps.setInt(2, sender);
            ps.setString(3, msg);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) {
                    sendLine("ERR|CHAT_SEND|NO_ID");
                    sendLine("END");
                    return;
                }
                id = rs.getInt(1);
            }
            try (PreparedStatement ts = c.prepareStatement("SELECT created_at FROM chat_messages WHERE id=?")) {
                ts.setInt(1, id);
                try (ResultSet trs = ts.executeQuery()) {
                    created = trs.next() ? trs.getString(1) : "";
                }
            }
        }

        ClientHub.get().broadcast(convId,
                "MSG|" + convId + "|" + id + "|" + sender + "|" + msg.replace("\n", " ") + "|" + created);
        sendLine("SEND_OK|" + id);
        sendLine("END");
    }

    private void handleChatFetch(String[] p) throws Exception { // CHAT_FETCH|convId|afterId
        if (p.length < 3) {
            sendLine("ERR|CHAT_FETCH|ARGS");
            sendLine("END");
            return;
        }
        int convId = Integer.parseInt(p[1]);
        int afterId = Integer.parseInt(p[2]);
        try (Connection c = Db.get();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT id,sender_user_id,content,created_at FROM chat_messages WHERE conversation_id=? AND id>? ORDER BY id ASC")) {
            ps.setInt(1, convId);
            ps.setInt(2, afterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sendLine("MSG|" + rs.getInt(1) + "|" + rs.getInt(2) + "|" +
                            rs.getString(3).replace("\n", " ") + "|" + rs.getString(4));
                }
            }
            sendLine("END");
        }
    }

    // ===== health sessions =====

    private void handleSessionsUpcoming() throws Exception {
        try (Connection c = Db.get();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT id,name,IFNULL(short_desc,''),date,start_time,end_time " +
                                "FROM health_sessions WHERE date >= DATE('now') ORDER BY date ASC, id ASC")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sendLine("SESSION|" + rs.getInt(1) + "|" + rs.getString(2) + "|" +
                            rs.getString(3).replace("\n", " ") + "|" + rs.getString(4) + "|" +
                            rs.getString(5) + "|" + rs.getString(6));
                }
            }
            sendLine("END");
        }
    }

    private void handleSessionGet(String[] p) throws Exception { // SESSION_GET|sessionId
        if (p.length < 2) {
            sendLine("ERR|SESSION_GET|ARGS");
            sendLine("END");
            return;
        }
        int sid = Integer.parseInt(p[1]);
        try (Connection c = Db.get()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id,name,IFNULL(short_desc,''),date,start_time,end_time FROM health_sessions WHERE id=?")) {
                ps.setInt(1, sid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        sendLine("ERR|SESSION_GET|NOT_FOUND");
                        sendLine("END");
                        return;
                    }
                    sendLine("SESSION|" + rs.getInt(1) + "|" + rs.getString(2) + "|" +
                            rs.getString(3).replace("\n", " ") + "|" + rs.getString(4) + "|" +
                            rs.getString(5) + "|" + rs.getString(6));
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id,place FROM health_session_locations WHERE session_id=? ORDER BY id ASC")) {
                ps.setInt(1, sid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        sendLine("LOC|" + sid + "|" + rs.getInt(1) + "|" + rs.getString(2));
                }
            }
            sendLine("END");
        }
    }

    private void handleSessionRegister(String[] p) throws Exception {
        // SESSION_REGISTER|userId|sessionId|locationId|childName|childAge|vaccCSV|vaccOther|disCSV|disOther
        if (p.length < 10) {
            sendLine("ERR|REG|ARGS");
            sendLine("END");
            return;
        }
        int userId = Integer.parseInt(p[1]);
        int sessionId = Integer.parseInt(p[2]);
        int locationId = Integer.parseInt(p[3]);
        String childName = p[4].replace("|", " ").trim();
        int childAge = Integer.parseInt(p[5]);
        String vaccCsv = p[6].replace("|", " ").trim();
        String vaccOther = p[7].replace("|", " ").trim();
        String disCsv = p[8].replace("|", " ").trim();
        String disOther = p[9].replace("|", " ").trim();

        String vaccines = vaccCsv.isEmpty() ? vaccOther : (vaccOther.isEmpty() ? vaccCsv : (vaccCsv + "," + vaccOther));
        String diseases = disCsv.isEmpty() ? disOther : (disOther.isEmpty() ? disCsv : (disCsv + "," + disOther));

        try (Connection c = Db.get();
                PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO health_session_registrations(session_id,location_id,user_id,child_name,child_age,prev_vaccines,prev_diseases) "
                                +
                                "VALUES (?,?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, sessionId);
            ps.setInt(2, locationId);
            ps.setInt(3, userId);
            ps.setString(4, childName);
            ps.setInt(5, childAge);
            ps.setString(6, vaccines);
            ps.setString(7, diseases);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    sendLine("REG_OK|" + rs.getInt(1));
                    sendLine("END");
                    return;
                }
            }
            sendLine("ERR|REG|NO_ID");
            sendLine("END");
        }
    }

    // ===== helpers =====

    private String safe(String s) {
        return s == null ? "" : s.replace("|", " ").replace("\n", " ").trim();
    }

    private int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean validTime(String t) {
        if (t == null || t.length() != 5 || t.charAt(2) != ':')
            return false;
        try {
            int h = Integer.parseInt(t.substring(0, 2));
            int m = Integer.parseInt(t.substring(3, 5));
            return h >= 0 && h <= 23 && m >= 0 && m <= 59;
        } catch (Exception e) {
            return false;
        }
    }
}
