package com.pa.client.controllers;

import com.pa.client.service.ApiService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class RegisterTeacherController {

    @FXML private TextField tfName;
    @FXML private TextField tfQualification;
    @FXML private TextField tfSalaryMin;
    @FXML private TextField tfSalaryMax;

    @FXML private CheckBox cb1; @FXML private TextField s1; @FXML private TextField e1; // Mon
    @FXML private CheckBox cb2; @FXML private TextField s2; @FXML private TextField e2; // Tue
    @FXML private CheckBox cb3; @FXML private TextField s3; @FXML private TextField e3; // Wed
    @FXML private CheckBox cb4; @FXML private TextField s4; @FXML private TextField e4; // Thu
    @FXML private CheckBox cb5; @FXML private TextField s5; @FXML private TextField e5; // Fri
    @FXML private CheckBox cb6; @FXML private TextField s6; @FXML private TextField e6; // Sat
    @FXML private CheckBox cb7; @FXML private TextField s7; @FXML private TextField e7; // Sun

    /** Auto-fill the Name with the logged-in user's username and lock the field. */
    @FXML
    public void initialize() {
        try {
            String me = ApiService.getCurrentUserId();
            if (me != null && !me.isBlank()) {
                String resp = ApiService.sendAndReceive("USER_GET|" + me);
                if (resp != null) {
                    for (String line : resp.split("\\R")) {
                        if (line.startsWith("USER|")) {
                            String[] parts = line.split("\\|", -1);
                            if (parts.length >= 3) {
                                tfName.setText(parts[2]);         // display_name
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignore) { }
        // Make sure it's read-only regardless
        tfName.setEditable(false);
        tfName.setFocusTraversable(false);
    }

    @FXML
    public void onSave() {
        String me = ApiService.getCurrentUserId();
        if (me == null || me.isBlank()) { alert("Please login first."); return; }

        String name = safe(tfName.getText());             // fixed to your username
        String q    = safe(tfQualification.getText());
        String sMin = tfSalaryMin.getText() == null ? "" : tfSalaryMin.getText().trim();
        String sMax = tfSalaryMax.getText() == null ? "" : tfSalaryMax.getText().trim();

        if (name.isBlank()) { alert("Your username could not be loaded. Please re-login."); return; }

        int min = parseIntSafe(sMin);
        int max = parseIntSafe(sMax);
        if (min < 0 || max < 0 || (max > 0 && min > max)) { alert("Salary range is invalid."); return; }

        String sched = buildScheduleCsv();
        String cmd = "TEACHER_REGISTER|" + me + "|" + name + "|" + q + "|" + min + "|" + max + "|" + sched;

        try {
            String resp = ApiService.sendAndReceive(cmd);
            if (resp == null) { alert("Save failed."); return; }
            for (String line : resp.split("\\R")) {
                if (line.startsWith("ERR|")) {
                    alert("Save failed: " + line);
                    return;
                }
                if (line.startsWith("TEACHER_REG_OK|")) {
                    info("Profile saved successfully.");
                    swapScene("/fxml/teachers.fxml", tfName);
                    return;
                }
            }
            alert("Save failed.");
        } catch (Exception ex) {
            alert("Save failed: " + ex.getMessage());
        }
    }

    @FXML
    public void goBack() {
        try {
            swapScene("/fxml/teachers.fxml", tfName);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private String buildScheduleCsv() {
        StringBuilder sb = new StringBuilder();
        appendIfChecked(sb, 1, cb1, s1, e1);
        appendIfChecked(sb, 2, cb2, s2, e2);
        appendIfChecked(sb, 3, cb3, s3, e3);
        appendIfChecked(sb, 4, cb4, s4, e4);
        appendIfChecked(sb, 5, cb5, s5, e5);
        appendIfChecked(sb, 6, cb6, s6, e6);
        appendIfChecked(sb, 7, cb7, s7, e7);
        return sb.toString();
    }

    private void appendIfChecked(StringBuilder sb, int day, CheckBox cb, TextField start, TextField end) {
        if (cb.isSelected()) {
            String s = (start.getText()==null?"":start.getText().trim());
            String e = (end.getText()==null?"":end.getText().trim());
            if (validTime(s) && validTime(e)) {
                if (sb.length() > 0) sb.append(';');
                sb.append(day).append(',').append(s).append(',').append(e);
            }
        }
    }

    // ---- helpers ----
    private String safe(String s) { return s == null ? "" : s.replace("|"," ").trim(); }
    private int parseIntSafe(String s) { try { return Integer.parseInt(s); } catch (Exception e) { return 0; } }

    private boolean validTime(String t) {
        if (t == null || t.length()!=5 || t.charAt(2)!=':') return false;
        try {
            int h = Integer.parseInt(t.substring(0,2));
            int m = Integer.parseInt(t.substring(3,5));
            return h>=0 && h<=23 && m>=0 && m<=59;
        } catch (Exception e) { return false; }
    }

    private void alert(String msg) {
        Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait());
    }
    private void info(String msg) {
        Platform.runLater(() -> new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait());
    }

    /** Swap scenes while preserving stylesheets and window size/maximize state. */
    private void swapScene(String fxml, Node node) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource(fxml));
        Stage stage = (Stage) node.getScene().getWindow();
        Scene prev = node.getScene();
        boolean wasMax = stage.isMaximized();
        double w = stage.getWidth(), h = stage.getHeight();
        Scene scene = new Scene(root);
        if (prev != null) scene.getStylesheets().addAll(prev.getStylesheets());
        stage.setScene(scene);
        if (wasMax) stage.setMaximized(true); else { stage.setWidth(w); stage.setHeight(h); }
    }
}
