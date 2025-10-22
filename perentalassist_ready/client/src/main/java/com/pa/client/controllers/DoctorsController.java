package com.pa.client.controllers;

import com.pa.client.service.ApiService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class DoctorsController {

    @FXML
    private FlowPane doctorsGrid;

    private static class Doc {
        int id;
        String name;
        String spec;
        String photoUrl;
    }

    @FXML
    public void initialize() {
        if (doctorsGrid == null) {
            throw new IllegalStateException("doctors.fxml must have FlowPane with fx:id=\"doctorsGrid\"");
        }

        doctorsGrid.setHgap(12);
        doctorsGrid.setVgap(12);
        doctorsGrid.setPadding(new Insets(12));

        List<Doc> list = fetchDoctors();
        for (Doc d : list) {
            Button card = new Button();
            card.setPrefWidth(240);  // Match health session card width
            card.setPrefHeight(160); // Match health session card height
            card.getStyleClass().add("doctor-card-button");

            VBox cardContent = new VBox(8);  // Slightly more spacing between elements
            cardContent.setAlignment(javafx.geometry.Pos.CENTER); // Center everything
            cardContent.getStyleClass().add("doctor-card");
            cardContent.setPadding(new Insets(12));
            cardContent.setMaxWidth(Double.MAX_VALUE);

            Image img = loadDoctorImage(d.name);
            ImageView iv = new ImageView(img);
            iv.setFitWidth(80);  // Slightly smaller image
            iv.setFitHeight(80);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);

            Label nm = new Label(d.name);
            nm.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-alignment: center;");
            nm.setWrapText(true);
            nm.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            nm.setAlignment(javafx.geometry.Pos.CENTER);

            Label sp = new Label(d.spec);
            sp.setStyle("-fx-font-size: 13px; -fx-text-fill: #666;");
            sp.setWrapText(true);
            sp.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            sp.setAlignment(javafx.geometry.Pos.CENTER);

            cardContent.getChildren().addAll(iv, nm, sp);
            card.setGraphic(cardContent);
            card.setOnAction(e -> openDoctorDetails(d));

            doctorsGrid.getChildren().add(card);
        }
    }

    private List<Doc> fetchDoctors() {
        List<Doc> out = new ArrayList<>();
        try {
            String resp = ApiService.sendAndReceive("DOCTOR_LIST");
            if (resp == null)
                return out;
            String[] lines = resp.split("\\R");
            for (String line : lines) {
                if (line.equals("END"))
                    break;
                if (!line.startsWith("DOCTOR|"))
                    continue;
                String[] p = line.split("\\|", -1);
                if (p.length < 5)
                    continue;
                Doc d = new Doc();
                d.id = Integer.parseInt(p[1]);
                d.name = p[2];
                d.spec = p[3];
                d.photoUrl = p[4];
                out.add(d);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void openDoctorDetails(Doc d) {
        try {
            DoctorDetailsController.setContext(d.id, d.name, d.spec);
            swapScene("/fxml/doctor_details.fxml", doctorsGrid);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private Image loadDoctorImage(String name) {
        // Try: /images/doctors/<normalized>.png|jpg|jpeg
        String base = "/images/doctors/" + normalize(name);
        String[] exts = { ".png", ".jpg", ".jpeg" };
        for (String ext : exts) {
            String path = base + ext;
            try (InputStream in = getClass().getResourceAsStream(path)) {
                if (in != null)
                    return new Image(in);
            } catch (Exception ignored) {
            }
        }
        // Fallback: /images/doctor.png
        try (InputStream in = getClass().getResourceAsStream("/images/doctor.png")) {
            if (in != null)
                return new Image(in);
        } catch (Exception ignored) {
        }

        // Last resort: 1x1 pixel to avoid invalid URL crashes
        System.err.println("[DoctorsController] Missing doctor image for '" + name
                + "' and fallback '/images/doctor.png'. Using placeholder.");
        return new WritableImage(1, 1);
    }

    private String normalize(String s) {
        return s == null ? "doctor"
                : s.trim().toLowerCase()
                        .replaceAll("[^a-z0-9]+", "_")
                        .replaceAll("^_+|_+$", "");
    }

    @FXML
    public void goHome() {
        try {
            swapScene("/fxml/home.fxml", doctorsGrid);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
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

        // carry over all stylesheets from the current scene
        if (prev != null)
            scene.getStylesheets().addAll(prev.getStylesheets());

        stage.setScene(scene);

        if (wasMax) {
            stage.setMaximized(true);
        } else {
            stage.setWidth(w);
            stage.setHeight(h);
        }
    }
}
