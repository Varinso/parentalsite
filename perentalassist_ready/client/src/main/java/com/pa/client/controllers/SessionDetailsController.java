package com.pa.client.controllers;

import com.pa.client.AppState;
import com.pa.client.ClientApp;
import com.pa.client.service.ApiService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class SessionDetailsController {

    @FXML private Label titleLbl, dateLbl, timeLbl;
    @FXML private TextArea descArea;
    @FXML private ComboBox<String> locationBox;

    @FXML private TextField childNameField, childAgeField, vaxOtherField, disOtherField;
    @FXML private VBox vaxBox, disBox;

    private static class Loc { int id; String name; Loc(int i,String n){id=i;name=n;} }
    private final List<Loc> locations = new ArrayList<>();

    private final String[] VACCINES = {"BCG","Polio (OPV/IPV)","Hepatitis B","DTP","MMR","Varicella","Pentavalent"};
    private final String[] DISEASES = {"Asthma","Diabetes","Thalassemia","Epilepsy","Heart disease","Allergies","Other chronic"};

    @FXML
    public void initialize() {
        int sid = AppState.selectedSessionId;
        if (sid <= 0) {
            new Alert(Alert.AlertType.ERROR, "No session selected").showAndWait();
            goBack();
            return;
        }
        // load session & locations
        new Thread(() -> {
            try {
                var api = new ApiService("127.0.0.1", 5555);
                var lines = api.send("SESSION_GET|" + sid);
                String name="", desc="", date="", start="", end="";
                List<String> locs = new ArrayList<>();
                for (String l : lines) {
                    if (l.startsWith("SESSION|")) {
                        var p = l.split("\\|", -1);
                        name = p[2]; desc = p[3]; date = p[4]; start = p[5]; end = p[6];
                    } else if (l.startsWith("LOC|")) {
                        var p = l.split("\\|", -1);
                        locations.add(new Loc(Integer.parseInt(p[2]), p[3]));
                        locs.add(p[3]);
                    }
                }
                final String fname=name,fdesc=desc,fdate=date,fstart=start,fend=end;
                Platform.runLater(() -> {
                    titleLbl.setText(fname);
                    descArea.setText(fdesc);
                    dateLbl.setText(fdate);
                    timeLbl.setText(fstart + " – " + fend);
                    locationBox.getItems().setAll(locs);
                    if (!locs.isEmpty()) locationBox.getSelectionModel().select(0);
                    // render checkboxes
                    renderChecks(vaxBox, VACCINES);
                    renderChecks(disBox, DISEASES);
                });
            } catch (IOException e) { e.printStackTrace(); }
        }).start();
    }

    private void renderChecks(VBox box, String[] options) {
        box.getChildren().clear();
        FlowPane pane = new FlowPane();
        pane.setHgap(10); pane.setVgap(6); pane.setPadding(new Insets(6));
        for (String opt : options) {
            CheckBox cb = new CheckBox(opt);
            pane.getChildren().add(cb);
        }
        box.getChildren().add(pane);
    }

    public void onRegister() {
        String childName = safe(childNameField.getText());
        String ageStr = childAgeField.getText()==null ? "" : childAgeField.getText().trim();
        if (childName.isBlank() || ageStr.isBlank()) {
            new Alert(Alert.AlertType.WARNING, "Child name and age are required.").showAndWait();
            return;
        }
        int age;
        try { age = Integer.parseInt(ageStr); }
        catch (NumberFormatException ex) { new Alert(Alert.AlertType.WARNING, "Child age must be a number.").showAndWait(); return; }

        int locIdx = locationBox.getSelectionModel().getSelectedIndex();
        if (locIdx < 0) { new Alert(Alert.AlertType.WARNING, "Please select a location.").showAndWait(); return; }
        int locationId = locations.get(locIdx).id;

        String vaccCsv = selectedFrom(vaxBox);
        String vaccOther = safe(vaxOtherField.getText());
        String disCsv = selectedFrom(disBox);
        String disOther = safe(disOtherField.getText());

        int sid = AppState.selectedSessionId;
        new Thread(() -> {
            try {
                var api = new ApiService("127.0.0.1", 5555);
                String cmd = "SESSION_REGISTER|" + ClientApp.userId + "|" + sid + "|" + locationId + "|" +
                        childName.replace("|"," ") + "|" + age + "|" +
                        vaccCsv.replace("|"," ") + "|" + vaccOther.replace("|"," ") + "|" +
                        disCsv.replace("|"," ") + "|" + disOther.replace("|"," ");
                var resp = api.send(cmd);
                Platform.runLater(() -> {
                    for (String l: resp) {
                        if (l.startsWith("REG_OK|")) {
                            new Alert(Alert.AlertType.INFORMATION, "Registration complete!\nYour registration id: " + l.split("\\|")[1]).showAndWait();
                            goBack();
                            return;
                        }
                    }
                    new Alert(Alert.AlertType.ERROR, "Registration failed. Please try again.").showAndWait();
                });
            } catch (IOException e) { e.printStackTrace(); }
        }).start();
    }

    private String selectedFrom(VBox box) {
        if (box.getChildren().isEmpty()) return "";
        FlowPane pane = (FlowPane) box.getChildren().get(0);
        List<String> sel = new ArrayList<>();
        pane.getChildren().forEach(n -> {
            if (n instanceof CheckBox cb && cb.isSelected()) sel.add(cb.getText());
        });
        return sel.stream().collect(Collectors.joining(","));
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }

    public void goBack() { ClientApp.setScene("/fxml/sessions.fxml"); }
}
