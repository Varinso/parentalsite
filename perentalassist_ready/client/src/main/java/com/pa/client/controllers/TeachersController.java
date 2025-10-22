package com.pa.client.controllers;

import com.pa.client.AppState;
import com.pa.client.service.ApiService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

public class TeachersController {

    @FXML
    private FlowPane teachersGrid;

    private static class Teacher {
        int id;
        String name;
        String qualification;
        int salaryMin;
        int salaryMax;
        String photoUrl;
        int userId; // may be 0 if not linked
    }

    private static final String[] DOW = { "", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun" };

    @FXML
    public void initialize() {
        if (teachersGrid == null) {
            throw new IllegalStateException("teachers.fxml must have FlowPane with fx:id=\"teachersGrid\"");
        }
        teachersGrid.setHgap(12);
        teachersGrid.setVgap(12);
        teachersGrid.setPadding(new Insets(12));

        List<Teacher> list = fetchTeachers();
        for (Teacher t : list) {
            VBox card = new VBox(8);
            card.getStyleClass().add("teacher-card");
            card.setPadding(new Insets(12));
            card.setPrefWidth(300);

            ImageView iv = new ImageView(loadTeacherImage(t.name));
            iv.setFitWidth(96);
            iv.setFitHeight(96);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);

            Label nm = new Label(t.name);
            nm.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            nm.setWrapText(true);
            nm.setMaxWidth(260);
            nm.setAlignment(javafx.geometry.Pos.CENTER);
            nm.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

            Label qf = new Label(t.qualification == null ? "" : t.qualification);
            qf.setAlignment(javafx.geometry.Pos.CENTER);
            qf.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            qf.setWrapText(true);
            Label sal = new Label("Salary: " + t.salaryMin + " - " + t.salaryMax);
            sal.setAlignment(javafx.geometry.Pos.CENTER);
            sal.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

            Label avail = new Label(fetchScheduleSummary(t.id));
            avail.setWrapText(true);
            avail.setStyle("-fx-text-fill:#555;");

            Button btn = new Button("Interested");
            btn.getStyleClass().add("primary-btn");
            btn.setOnAction(e -> onInterested(t));

            // center content inside card
            VBox centerBox = new VBox(8, iv, nm, qf, sal, new Separator(), new Label("Availability:"), avail, btn);
            centerBox.setStyle("-fx-alignment: center; -fx-spacing: 8;");
            centerBox.setPrefWidth(280);

            card.getChildren().add(centerBox);
            teachersGrid.getChildren().add(card);
        }
    }

    private List<Teacher> fetchTeachers() {
        List<Teacher> out = new ArrayList<>();
        try {
            // Get current user ID to filter out self from results
            String currentUserId = ApiService.getCurrentUserId();

            String resp = ApiService.sendAndReceive("TEACHER_LIST");
            if (resp == null)
                return out;
            for (String line : resp.split("\\R")) {
                if (line.equals("END"))
                    break;
                if (!line.startsWith("TEACHER|"))
                    continue;
                // TEACHER|id|name|qualification|salary_min|salary_max|photo_url|user_id
                String[] p = line.split("\\|", -1);
                if (p.length < 8)
                    continue;

                // Skip if this teacher is the current logged-in user
                if (currentUserId != null && currentUserId.equals(p[7])) {
                    continue;
                }

                Teacher t = new Teacher();
                t.id = parseIntSafe(p[1]);
                t.name = p[2];
                t.qualification = p[3];
                t.salaryMin = parseIntSafe(p[4]);
                t.salaryMax = parseIntSafe(p[5]);
                t.photoUrl = p[6];
                t.userId = parseIntSafe(p[7]);
                out.add(t);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private String fetchScheduleSummary(int teacherId) {
        try {
            String resp = ApiService.sendAndReceive("TEACHER_GET|" + teacherId);
            if (resp == null)
                return "(no schedule)";
            List<String> lines = new ArrayList<>();
            for (String line : resp.split("\\R")) {
                if (line.startsWith("SCHED|")) {
                    // SCHED|day|start|end
                    String[] p = line.split("\\|", -1);
                    if (p.length >= 4) {
                        int day = parseIntSafe(p[1]);
                        String dayStr = (day >= 1 && day <= 7) ? DOW[day] : ("Day " + day);
                        lines.add(dayStr + " " + p[2] + "-" + p[3]);
                    }
                }
            }
            if (lines.isEmpty())
                return "(no schedule)";
            return String.join("; ", lines);
        } catch (Exception e) {
            return "(no schedule)";
        }
    }

    private void onInterested(Teacher t) {
        String me = ApiService.getCurrentUserId();
        if (me == null || me.isBlank()) {
            alert("Please login first.");
            return;
        }
        try {
            String resp = ApiService.sendAndReceive("TEACHER_INTEREST|" + me + "|" + t.id);
            if (resp == null) {
                alert("Failed to send interest.");
                return;
            }
            for (String line : resp.split("\\R")) {
                if (line.startsWith("ERROR|")) {
                    String causeUpper = line.substring(4).toUpperCase();
                    if (causeUpper.contains("NO_USER")) {
                        alert("This teacher is not linked to a user account yet.");
                    } else {
                        alert("Save failed: Teacher Already registered");
                    }
                    return;
                }
                if (line.startsWith("INTEREST_OK|")) {
                    String[] parts = line.split("\\|");
                    if (parts.length >= 2) {
                        try {
                            // Set the conversation ID before navigating
                            AppState.selectedConversationId = Integer.parseInt(parts[1]);
                            // Navigate to chat screen; your chat page will show the new message
                            swapScene("/fxml/chat.fxml", teachersGrid);
                            return;
                        } catch (NumberFormatException e) {
                            e.printStackTrace();
                            alert("Could not parse conversation ID.");
                            return;
                        }
                    }
                    alert("Invalid response format.");
                    return;
                }
            }
            alert("Failed to send interest.");
        } catch (Exception ex) {
            alert("Failed: " + ex.getMessage());
        }
    }

    private Image loadTeacherImage(String name) {
        // /images/teachers/<normalized>.png|jpg|jpeg ; fallback /images/teacher.png ;
        // otherwise 1x1 pixel
        String base = "/images/teachers/" + normalize(name);
        String[] exts = { ".png", ".jpg", ".jpeg" };
        for (String ext : exts) {
            try (InputStream in = getClass().getResourceAsStream(base + ext)) {
                if (in != null)
                    return new Image(in);
            } catch (Exception ignored) {
            }
        }
        try (InputStream in = getClass().getResourceAsStream("/images/teacher.png")) {
            if (in != null)
                return new Image(in);
        } catch (Exception ignored) {
        }
        return new WritableImage(1, 1);
    }

    private String normalize(String s) {
        return s == null ? "teacher" : s.trim().toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    private int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    @FXML
    public void goHome() {
        try {
            swapScene("/fxml/home.fxml", teachersGrid);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void alert(String msg) {
        Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait());
    }

    /** Swap scenes preserving styles and window state. */
    private void swapScene(String fxml, Node node) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource(fxml));
        Stage stage = (Stage) node.getScene().getWindow();
        Scene prev = node.getScene();
        boolean wasMax = stage.isMaximized();
        double w = stage.getWidth(), h = stage.getHeight();
        Scene scene = new Scene(root);
        if (prev != null)
            scene.getStylesheets().addAll(prev.getStylesheets());
        stage.setScene(scene);
        if (wasMax)
            stage.setMaximized(true);
        else {
            stage.setWidth(w);
            stage.setHeight(h);
        }
    }
}
