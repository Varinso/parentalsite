package com.pa.client.controllers;

import com.pa.client.AppState;
import com.pa.client.ClientApp;
import com.pa.client.service.ApiService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SessionsController {

    @FXML
    private ScrollPane scroll;
    @FXML
    private TilePane grid;

    static class Session {
        int id;
        String name;
        String shortDesc;
        String date;
        String start;
        String end;

        Session(int id, String n, String s, String d, String st, String en) {
            this.id = id;
            name = n;
            shortDesc = s;
            date = d;
            start = st;
            end = en;
        }
    }

    private final List<Session> sessions = new ArrayList<>();

    @FXML
    public void initialize() {
        grid.setHgap(12);  // Reduced spacing between cards horizontally
        grid.setVgap(12);  // Reduced spacing between cards vertically
        grid.setPadding(new Insets(12));  // Reduced outer padding
        new Thread(() -> {
            try {
                var api = new ApiService("127.0.0.1", 5555);
                var lines = api.send("SESSIONS_UPCOMING");
                for (String l : lines)
                    if (l.startsWith("SESSION|")) {
                        var p = l.split("\\|", -1);
                        sessions.add(new Session(Integer.parseInt(p[1]), p[2], p[3], p[4], p[5], p[6]));
                    }
                Platform.runLater(this::render);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void render() {
        grid.getChildren().clear();
        for (var s : sessions) {
            // Create a button that will act as the card container
            Button cardButton = new Button();
            cardButton.setPrefWidth(240);  // Made card narrower
            cardButton.setPrefHeight(160); // Set fixed height for uniform look
            cardButton.getStyleClass().add("session-card");

            // Create the content for the card
            VBox content = new VBox(6); // Tighter spacing between elements
            content.setPadding(new Insets(10)); // Smaller padding
            content.setMaxWidth(Double.MAX_VALUE);

            // Session name with compact bold font
            Label name = new Label(s.name);
            name.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1a1a1a;");
            name.setWrapText(true);
            name.setMaxHeight(40); // Limit name to 2 lines

            // Description with smaller font and limited height
            Label brief = new Label(s.shortDesc);
            brief.setStyle("-fx-text-fill: #4b5563; -fx-font-size: 12px;");
            brief.setWrapText(true);
            brief.setMaxHeight(36); // Limit description to ~2 lines

            // Date and time in single line with smaller font
            Label when = new Label("📅 " + s.date + "  ⌚ " + s.start + " - " + s.end);
            when.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 12px;");

            // Compact stats section
            HBox stats = new HBox();
            stats.setStyle("-fx-padding: 6 0 0 0; -fx-border-color: #e5e7eb; -fx-border-width: 1 0 0 0;");

            Label seats = new Label("👥 Limited Slots");
            seats.setStyle("-fx-text-fill: #374151; -fx-font-size: 12px;");

            stats.getChildren().add(seats);

            content.getChildren().addAll(name, brief, when, stats);
            cardButton.setGraphic(content);

            // Set the action for the entire card
            cardButton.setOnAction(ev -> {
                AppState.selectedSessionId = s.id;
                ClientApp.setScene("/fxml/session_details.fxml");
            });

            grid.getChildren().add(cardButton);
        }
    }

    public void goHome() {
        ClientApp.setScene("/fxml/home.fxml");
    }
}
