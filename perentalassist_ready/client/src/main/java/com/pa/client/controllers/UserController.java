package com.pa.client.controllers;

import com.pa.client.service.ApiService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * User profile page: shows profile header + doctor appointments,
 * health-session registrations, and outcomes.
 *
 * Server protocol supported:
 *   PROFILE|id|display_name|email|role
 *   BOOKING|apptId|doctorId|doctorName|start_at|end_at|video_url
 *   APPT|id|doctorName|date|start|end|videoUrl
 *   REG|regId|sessionId|sessionName|date|start|end|locationName|childName|childAge|prev_vaccines|prev_diseases
 *   OUTCOME|regId|type|notes|taken_at
 *   END
 */
public class UserController {

    // ====== FXML ids (must match user.fxml) ======
    @FXML private Label nameLbl;
    @FXML private Label emailLbl;
    @FXML private Label roleLbl;

    @FXML private VBox bookingsBox;   // appointments list
    @FXML private VBox regsBox;       // registrations list
    @FXML private VBox outcomesBox;   // outcomes list

    @FXML private Button backBtn;
    @FXML private Button logoutBtn;

    // ====== internals ======
    private ApiService api;
    private int currentUserId = -1;

    private static final DateTimeFormatter DT_DISP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    private void initialize() {
        // Wire handlers (even if onAction is not set in FXML)
        if (backBtn != null)   backBtn.setOnAction(e -> goHome());
        if (logoutBtn != null) logoutBtn.setOnAction(e -> logout());

        api = resolveApiService();
        currentUserId = resolveCurrentUserId();
        loadProfile(currentUserId);
    }

    // ---------- Navigation (NO ClientApp usage) ----------
    @FXML
    private void goHome() {
        swapRoot("/fxml/home.fxml");
    }

    @FXML
    private void logout() {
        // Best-effort: clear your common session holders
        try {
            Class<?> as = Class.forName("com.pa.client.AppState");
            Method get = as.getMethod("get");
            Object inst = get.invoke(null);
            try {
                Method m = as.getMethod("logout");
                m.invoke(inst);
            } catch (NoSuchMethodException ignored) {}
            try {
                Field f = as.getDeclaredField("currentUserId");
                f.setAccessible(true);
                f.setInt(inst, -1);
            } catch (NoSuchFieldException ignored) {}
        } catch (Throwable ignored) {}

        try {
            Class<?> ca = Class.forName("com.pa.client.ClientApp");
            try {
                Field uf = ca.getDeclaredField("userId"); // many pages read this
                uf.setAccessible(true);
                uf.setInt(null, -1);
            } catch (NoSuchFieldException ignored) {}
            try {
                Method setCurrentUser = ca.getMethod("setCurrentUser", int.class, String.class, String.class, String.class);
                setCurrentUser.invoke(null, -1, null, null, null);
            } catch (NoSuchMethodException ignored) {}
        } catch (Throwable ignored) {}

        swapRoot("/fxml/login.fxml");
    }

    /** Load FXML from classpath and replace current scene root. */
    private void swapRoot(String absFxmlPath) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource(absFxmlPath));
            Scene scene = (backBtn != null ? backBtn.getScene()
                    : (logoutBtn != null ? logoutBtn.getScene() : null));
            if (scene != null) {
                scene.setRoot(root);
            } else {
                // Fallback: if no button scene available, try any control we have
                if (bookingsBox != null && bookingsBox.getScene() != null) {
                    bookingsBox.getScene().setRoot(root);
                } else {
                    // last resort: create a new Scene (rarely needed)
                    // javafx.stage.Stage stage = com.pa.client.ClientApp.getStage();
                    // stage.setScene(new Scene(root, 1200, 800));
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // ---------- Data loading ----------
    private void loadProfile(int userId) {
        clearBoxes();

        if (userId <= 0) {
            setHeader("Not logged in", "", "GUEST");
            return;
        }

        List<String> lines;
        try {
            lines = api.send("USER_PROFILE|" + userId);
        } catch (Exception e) {
            setHeader("Error", e.getMessage(), "");
            return;
        }

        String disp = null, email = null, role = null;
        List<String> apptLines = new ArrayList<>();
        List<String> regLines  = new ArrayList<>();
        List<String> outLines  = new ArrayList<>();

        for (String line : lines) {
            if (line == null || line.isBlank() || "END".equals(line)) continue;

            if (line.startsWith("PROFILE|")) {
                String[] p = split(line);
                disp  = s(p,2);
                email = s(p,3);
                role  = s(p,4);
            } else if (line.startsWith("BOOKING|") || line.startsWith("APPT|")) {
                apptLines.add(line);
            } else if (line.startsWith("REG|")) {
                regLines.add(line);
            } else if (line.startsWith("OUTCOME|")) {
                outLines.add(line);
            }
        }

        setHeader(z(disp), z(email), z(role));
        renderAppointments(apptLines);
        renderRegistrations(regLines);
        renderOutcomes(outLines);
    }

    private void renderAppointments(List<String> lines) {
        bookingsBox.getChildren().clear();
        if (lines.isEmpty()) {
            bookingsBox.getChildren().add(new Label("No appointments yet."));
            return;
        }
        for (String line : lines) {
            if (line.startsWith("BOOKING|")) {
                // BOOKING|apptId|doctorId|doctorName|start_at|end_at|video_url
                String[] p = split(line);
                int apptId     = i(p,1);
                String docName = s(p,3);
                String start   = s(p,4);
                String end     = s(p,5);
                String url     = s(p,6);
                bookingsBox.getChildren().add(bookingCard(apptId, docName, start, end, url));
            } else {
                // APPT|id|doctorName|date|start|end|videoUrl
                String[] p = split(line);
                int apptId     = i(p,1);
                String docName = s(p,2);
                String date    = s(p,3);
                String start   = s(p,4);
                String end     = s(p,5);
                String url     = s(p,6);
                String startIso = z(date) + "T" + z(start);
                String endIso   = z(date) + "T" + z(end);
                bookingsBox.getChildren().add(bookingCard(apptId, docName, startIso, endIso, url));
            }
        }
        bookingsBox.getChildren().add(new Separator());
    }

    private void renderRegistrations(List<String> lines) {
        regsBox.getChildren().clear();
        if (lines.isEmpty()) {
            regsBox.getChildren().add(new Label("No health session registrations yet."));
            return;
        }
        for (String line : lines) {
            // REG|regId|sessionId|sessionName|date|start|end|locationName|childName|childAge|prev_vaccines|prev_diseases
            String[] p = split(line);
            int regId     = i(p,1);
            String name   = s(p,3);
            String date   = s(p,4);
            String st     = s(p,5);
            String en     = s(p,6);
            String place  = s(p,7);
            String child  = s(p,8);
            int age       = i(p,9);
            String vacc   = s(p,10);
            String dis    = s(p,11);
            regsBox.getChildren().add(regCard(regId, name, date, st, en, place, child, age, vacc, dis));
        }
        regsBox.getChildren().add(new Separator());
    }

    private void renderOutcomes(List<String> lines) {
        outcomesBox.getChildren().clear();
        if (lines.isEmpty()) {
            outcomesBox.getChildren().add(new Label("No recorded vaccines/tests yet."));
            return;
        }
        for (String line : lines) {
            // OUTCOME|regId|type|notes|taken_at
            String[] p = split(line);
            int regId     = i(p,1);
            String type   = s(p,2);
            String notes  = s(p,3);
            String when   = s(p,4);
            outcomesBox.getChildren().add(outcomeRow(regId, type, notes, when));
        }
    }

    private void setHeader(String name, String email, String role) {
        nameLbl.setText(z(name).isBlank() ? "Not logged in" : name);
        emailLbl.setText(z(email));
        roleLbl.setText(z(role).isBlank() ? "USER" : role);
    }

    private void clearBoxes() {
        bookingsBox.getChildren().clear();
        regsBox.getChildren().clear();
        outcomesBox.getChildren().clear();
    }

    // ---------- UI Builders ----------
    private Node bookingCard(int apptId, String doctorName, String startIso, String endIso, String videoUrl) {
        LocalDateTime start = parseIso(startIso);
        LocalDateTime end   = parseIso(endIso);
        String timeText = (start != null ? start.format(DT_DISP) : z(startIso)) +
                (end != null ? " – " + end.toLocalTime() : "");

        Label title = new Label("Appointment with " + z(doctorName));
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        Label when  = new Label(timeText);
        Label urlLb = new Label((videoUrl==null||videoUrl.isBlank()) ? "" : "Video link:");
        Hyperlink link = new Hyperlink((videoUrl==null||videoUrl.isBlank()) ? "" : videoUrl);
        link.setOnAction(e -> openUrl(videoUrl));

        HBox urlBox = new HBox(6, urlLb, link);
        urlBox.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(4, title, when, urlBox);
        box.setSpacing(2);
        box.setPadding(new Insets(8, 10, 8, 10));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setStyle("-fx-background-color: rgba(0,0,0,0.04); -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 4, 0, 0, 1);");
        return box;
    }

    private Node regCard(
            int regId, String sessionName,
            String date, String start, String end, String place,
            String childName, int childAge, String vaccines, String diseases) {

        Label title = new Label("Session: " + z(sessionName));
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        Label when  = new Label(formatDateTime(date, start, end));
        Label where = new Label("Location: " + z(place));
        Label child = new Label("Child: " + z(childName) + " (" + childAge + ")");
        Label vacc  = new Label("Vaccines (prev): " + z(vaccines));
        Label dis   = new Label("Diseases (prev): " + z(diseases));

        VBox box = new VBox(3, title, when, where, child, vacc, dis);
        box.setSpacing(2);
        box.setPadding(new Insets(8, 10, 8, 10));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setStyle("-fx-background-color: rgba(0,0,0,0.04); -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 4, 0, 0, 1);");
        return box;
    }

    private Node outcomeRow(int regId, String type, String notes, String takenAtIso) {
        Label head = new Label("Outcome (" + z(type) + ")");
        head.setStyle("-fx-font-weight: bold;");
        Label txt  = new Label(z(notes));
        Label when = new Label(z(takenAtIso));
        VBox row = new VBox(2, head, txt, when);
        row.setPadding(new Insets(6,8,6,8));
        row.setStyle("-fx-background-color: -fx-base; -fx-background-radius: 8;");
        return row;
    }

    // ---------- helpers ----------
    private ApiService resolveApiService() {
        try {
            // Prefer AppState.get().api() if your app has it
            Class<?> as = Class.forName("com.pa.client.AppState");
            Method get = as.getMethod("get");
            Object inst = get.invoke(null);
            try {
                Method apiM = as.getMethod("api");
                Object svc = apiM.invoke(inst);
                if (svc instanceof ApiService) return (ApiService) svc;
            } catch (NoSuchMethodException ignored) {}
        } catch (Throwable ignored) {}
        // Fallback: your simple constructor used elsewhere
        return new ApiService("127.0.0.1", 5555);
    }

    private int resolveCurrentUserId() {
        Integer v = null;

        // Try AppState singleton/methods if present
        v = v != null ? v : trySingletonInt("com.pa.client.AppState", "getCurrentUserId");
        v = v != null ? v : trySingletonInt("com.pa.client.AppState", "getUserId");
        v = v != null ? v : tryStaticInt("com.pa.client.AppState", "getCurrentUserId");

        // Try ClientApp helpers/fields (your project often uses ClientApp.userId)
        v = v != null ? v : tryStaticInt("com.pa.client.ClientApp", "getCurrentUserId");
        v = v != null ? v : tryStaticFieldInt("com.pa.client.ClientApp", "currentUserId");
        v = v != null ? v : tryStaticFieldInt("com.pa.client.ClientApp", "userId");

        // Try a system property fallback
        if (v == null) {
            try {
                String prop = System.getProperty("pa.userId");
                if (prop != null) v = Integer.parseInt(prop.trim());
            } catch (Throwable ignored) {}
        }

        return v != null ? v : -1;
    }

    private Integer trySingletonInt(String cls, String method) {
        try {
            Class<?> c = Class.forName(cls);
            Method get = c.getMethod("get");
            Object inst = get.invoke(null);
            Method mm = c.getMethod(method);
            Object rv = mm.invoke(inst);
            if (rv instanceof Number) return ((Number) rv).intValue();
        } catch (Throwable ignored) {}
        return null;
    }

    private Integer tryStaticInt(String cls, String method) {
        try {
            Class<?> c = Class.forName(cls);
            Method m = c.getMethod(method);
            Object rv = m.invoke(null);
            if (rv instanceof Number) return ((Number) rv).intValue();
        } catch (Throwable ignored) {}
        return null;
    }

    private Integer tryStaticFieldInt(String cls, String field) {
        try {
            Class<?> c = Class.forName(cls);
            Field f = c.getDeclaredField(field);
            f.setAccessible(true);
            Object rv = f.get(null);
            if (rv instanceof Number) return ((Number) rv).intValue();
        } catch (Throwable ignored) {}
        return null;
    }

    private void openUrl(String url) {
        if (url == null || url.isBlank()) return;
        try {
            java.awt.Desktop.getDesktop().browse(new URI(url));
        } catch (Exception ignored) {}
    }

    private LocalDateTime parseIso(String iso) {
        try { return LocalDateTime.parse(iso); } catch (Exception e) { return null; }
    }

    private String formatDateTime(String date, String start, String end) {
        try {
            String s = (date + "T" + start);
            LocalDateTime dt = LocalDateTime.parse(s);
            String disp = dt.format(DT_DISP);
            if (end != null && end.length() >= 5) {
                LocalTime t2 = LocalTime.parse(end);
                disp += " – " + t2.toString();
            }
            return disp;
        } catch (Exception e) {
            return (z(date) + " " + z(start) + (end!=null && !end.isBlank() ? (" – " + end) : ""));
        }
    }

    private String[] split(String line) { return line.split("\\|", -1); }
    private String s(String[] p, int idx) { return idx >= 0 && idx < p.length ? p[idx] : ""; }
    private int i(String[] p, int idx) { try { return Integer.parseInt(s(p, idx)); } catch (Exception e) { return 0; } }
    private String z(String v) { return v == null ? "" : v; }
}
    
