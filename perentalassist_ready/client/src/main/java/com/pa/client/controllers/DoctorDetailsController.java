package com.pa.client.controllers;

import com.pa.client.AppState;
import com.pa.client.service.ApiService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.Node;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.awt.Desktop;
import java.net.URI;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class DoctorDetailsController {

    @FXML
    private Label lblName;
    @FXML
    private Label lblSpec;
    @FXML
    private DatePicker datePicker;
    @FXML
    private ComboBox<String> timeBox; // HH:mm options
    @FXML
    private Button btnBook;
    @FXML
    private Hyperlink videoLink;
    @FXML
    private ListView<String> listSchedule;

    private static int doctorId;
    private static String doctorName;
    private static String doctorSpec;

    private Integer doctorUserId; // for chat

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final String[] DOW = { "", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday",
            "Sunday" };

    public static void setContext(int id, String name, String spec) {
        doctorId = id;
        doctorName = name;
        doctorSpec = spec;
    }

    @FXML
    public void initialize() {
        lblName.setText(doctorName != null ? doctorName : "");
        lblSpec.setText(doctorSpec != null ? doctorSpec : "");

        // Load doctor details (userId + weekly schedule)
        loadDoctorDetails();

        // Disable past dates
        datePicker.setDayCellFactory(disablePastDates());
        datePicker.setValue(LocalDate.now());

        // Load initial slots
        refreshSlots();

        datePicker.valueProperty().addListener((obs, o, n) -> refreshSlots());

        // Video link open
        videoLink.setVisible(false);
        videoLink.setOnAction(e -> {
            Object ud = videoLink.getUserData();
            if (ud instanceof String url && !url.isBlank()) {
                try {
                    Desktop.getDesktop().browse(new URI(url));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    // ---- FXML handlers ----

    @FXML
    public void onBook() {
        LocalDate date = datePicker.getValue();
        String timeStr = timeBox.getValue();
        if (date == null || timeStr == null || timeStr.startsWith("(")) {
            alert("Please select a valid date and time.");
            return;
        }

        LocalTime t = LocalTime.parse(timeStr, TIME_FMT);
        LocalDateTime start = LocalDateTime.of(date, t);
        LocalDateTime end = start.plusMinutes(30);

        String userId = ApiService.getCurrentUserId();
        if (userId == null || userId.isBlank()) {
            alert("Please login first.");
            return;
        }

        try {
            String cmd = "APPT_BOOK|" + userId + "|" + doctorId + "|" + DT_FMT.format(start) + "|" + DT_FMT.format(end);
            String resp = ApiService.sendAndReceive(cmd);
            if (resp == null) {
                alert("Booking failed.");
                return;
            }

            String bookedUrl = null;
            String err = null;
            for (String line : resp.split("\\R")) {
                if (line.startsWith("ERR|")) {
                    err = line;
                    break;
                }
                if (line.startsWith("APPT_OK|")) {
                    String[] parts = line.split("\\|", 3);
                    if (parts.length >= 3)
                        bookedUrl = parts[2];
                }
            }

            if (err != null) {
                if (err.contains("|PAST"))
                    alert("You cannot book a past date/time.");
                else if (err.contains("|ALREADY_BOOKED"))
                    alert("That time slot is already booked.");
                else if (err.contains("|OUT_OF_SCHEDULE"))
                    alert("Selected time is outside the doctor's schedule.");
                else
                    alert("Booking failed: " + err);
                refreshSlots();
                return;
            }

            if (bookedUrl != null && !bookedUrl.isBlank()) {
                videoLink.setText("Join video");
                videoLink.setVisible(true);
                videoLink.setUserData(bookedUrl);
            }
            info("Appointment booked.\nDoctor: " + doctorName +
                    "\nWhen: " + DATE_FMT.format(date) + " " + timeStr +
                    (bookedUrl != null ? "\nLink: " + bookedUrl : ""));
            refreshSlots();

        } catch (Exception ex) {
            alert("Booking failed: " + ex.getMessage());
        }
    }

    @FXML
    public void onChat() {
        try {
            String me = ApiService.getCurrentUserId();
            if (me == null || me.isBlank()) {
                alert("Please login first.");
                return;
            }
            if (doctorUserId == null || doctorUserId <= 0) {
                alert("This doctor is not linked to a user account.");
                return;
            }

            // Ensure a conversation exists and get its ID
            String resp = ApiService.sendAndReceive("CHAT_OPEN|" + me + "|" + doctorUserId);
            if (resp == null) {
                alert("Could not open chat.");
                return;
            }

            // Parse CHAT_OK response to get conversation ID
            for (String line : resp.split("\\R")) {
                if (line.startsWith("CHAT_OK|")) {
                    String[] parts = line.split("\\|");
                    if (parts.length >= 2) {
                        try {
                            AppState.selectedConversationId = Integer.parseInt(parts[1]);
                            // Go to chat, preserving styles and window state
                            swapScene("/fxml/chat.fxml", lblName);
                            return;
                        } catch (NumberFormatException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            alert("Could not open chat - invalid response.");
        } catch (Exception ex) {
            ex.printStackTrace();
            alert("Could not open chat: " + ex.getMessage());
        }
    }

    @FXML
    public void goBack() {
        try {
            swapScene("/fxml/doctors.fxml", lblName);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // ---- helpers ----

    private void loadDoctorDetails() {
        listSchedule.getItems().clear();
        try {
            String resp = ApiService.sendAndReceive("DOCTOR_GET|" + doctorId);
            if (resp == null)
                return;
            for (String line : resp.split("\\R")) {
                if (line.startsWith("DOCTOR|")) {
                    // DOCTOR|id|name|specialty|bio|photo_url|user_id
                    String[] p = line.split("\\|", -1);
                    if (p.length >= 7) {
                        if (doctorName == null || doctorName.isBlank())
                            lblName.setText(p[2]);
                        if (doctorSpec == null || doctorSpec.isBlank())
                            lblSpec.setText(p[3]);
                        try {
                            int uid = Integer.parseInt(p[6]);
                            if (uid > 0)
                                doctorUserId = uid;
                        } catch (NumberFormatException ignore) {
                        }
                    }
                } else if (line.startsWith("SCHED|")) {
                    // SCHED|day_of_week|start|end
                    String[] p = line.split("\\|", -1);
                    if (p.length >= 4) {
                        try {
                            int dow = Integer.parseInt(p[1]); // 1..7
                            String start = p[2];
                            String end = p[3];
                            String day = (dow >= 1 && dow <= 7) ? DOW[dow] : "Day " + dow;
                            listSchedule.getItems().add(day + "  " + start + " – " + end);
                        } catch (NumberFormatException ignore) {
                        }
                    }
                }
            }
            if (listSchedule.getItems().isEmpty()) {
                listSchedule.getItems().add("(No weekly schedule found)");
            }
        } catch (Exception ignore) {
        }
    }

    private Callback<DatePicker, DateCell> disablePastDates() {
        return dp -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date.isBefore(LocalDate.now())) {
                    setDisable(true);
                    setStyle("-fx-opacity: 0.5;");
                }
            }
        };
    }

    private void refreshSlots() {
        timeBox.getItems().clear();
        LocalDate date = datePicker.getValue();
        if (date == null)
            return;

        List<String> slots = fetchSlots(doctorId, date);
        if (slots.isEmpty()) {
            timeBox.getItems().add("(No slots)");
            timeBox.getSelectionModel().select(0);
            timeBox.setDisable(true);
        } else {
            timeBox.getItems().addAll(slots);
            timeBox.getSelectionModel().select(0);
            timeBox.setDisable(false);
        }
    }

    private List<String> fetchSlots(int docId, LocalDate date) {
        try {
            String resp = ApiService.sendAndReceive("APPT_SLOTS|" + docId + "|" + DATE_FMT.format(date));
            if (resp == null)
                return List.of();
            for (String line : resp.split("\\R")) {
                if (line.startsWith("SLOTS|")) {
                    String rest = line.substring("SLOTS|".length());
                    if (rest.isBlank())
                        return List.of();
                    return Arrays.stream(rest.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                }
            }
        } catch (Exception ignored) {
        }
        return List.of();
    }

    private void alert(String msg) {
        Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait());
    }

    private void info(String msg) {
        Platform.runLater(() -> new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait());
    }

    /**
     * Swap scenes while preserving stylesheets and window state (size/maximized).
     */
    private void swapScene(String fxml, Node anyNodeInCurrentScene) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource(fxml));
        Stage stage = (Stage) anyNodeInCurrentScene.getScene().getWindow();

        Scene prev = anyNodeInCurrentScene.getScene();
        boolean wasMax = stage.isMaximized();
        double w = stage.getWidth();
        double h = stage.getHeight();

        Scene scene = new Scene(root);

        // Carry over all stylesheets from the current scene to keep the same look
        if (prev != null)
            scene.getStylesheets().addAll(prev.getStylesheets());

        stage.setScene(scene);

        if (wasMax) {
            stage.setMaximized(true);
        } else {
            // restore window size
            stage.setWidth(w);
            stage.setHeight(h);
        }
    }
}
