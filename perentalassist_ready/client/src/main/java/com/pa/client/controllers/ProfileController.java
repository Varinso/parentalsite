package com.pa.client.controllers;

import com.pa.client.AppState;
import com.pa.client.ClientApp;
import com.pa.client.service.ApiService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ProfileController {

    @FXML private Label nameLbl, badgeLbl;
    @FXML private Button chatBtn, bookBtn, doctorsBtn;
    @FXML private VBox postsBox;

    static class Post {
        int id, userId; String name, role, content, created;
        Post(int id, int uid, String n, String r, String c, String t){ this.id=id; userId=uid; name=n; role=r; content=c; created=t; }
    }
    private final List<Post> posts = new ArrayList<>();

    @FXML
    public void initialize() {
        nameLbl.setText(AppState.profileDisplayName);
        if ("DOCTOR".equalsIgnoreCase(AppState.profileRole)) {
            badgeLbl.setText("Professional");
            badgeLbl.setStyle("-fx-background-color:#e4f0ff; -fx-text-fill:#0b70d5; -fx-padding:2 6; -fx-background-radius:10;");
            bookBtn.setVisible(true); bookBtn.setManaged(true);
        } else {
            badgeLbl.setText("");
            bookBtn.setVisible(false); bookBtn.setManaged(false);
        }

        postsBox.setSpacing(12);
        postsBox.setPadding(new Insets(12));

        loadUserFeed();
    }

    private void loadUserFeed() {
        posts.clear(); postsBox.getChildren().clear();
        int uid = AppState.profileUserId;
        new Thread(() -> {
            try {
                var api = new ApiService("127.0.0.1", 5555);
                var lines = api.send("FEED_BY_USER|" + uid);
                for (String l: lines) if (l.startsWith("POST|")) {
                    var p = l.split("\\|", -1);
                    posts.add(new Post(
                            Integer.parseInt(p[1]), Integer.parseInt(p[2]), p[3], p[4], p[5], p[6]
                    ));
                }
                Platform.runLater(this::renderPosts);
            } catch (IOException e) { e.printStackTrace(); }
        }).start();
    }

    private void renderPosts() {
        postsBox.getChildren().clear();
        if (posts.isEmpty()) {
            Label none = new Label("No posts yet.");
            none.setStyle("-fx-text-fill:#666;");
            postsBox.getChildren().add(none);
            return;
        }
        for (var p: posts) {
            VBox card = new VBox(6);
            card.setPadding(new Insets(10));
            card.setStyle("-fx-background-color:white; -fx-background-radius:12; -fx-effect:dropshadow(gaussian, rgba(0,0,0,0.08), 12, 0, 0, 2);");
            Label time = new Label(p.created); time.setStyle("-fx-text-fill:#666; -fx-font-size:11px;");
            Label body = new Label(p.content); body.setWrapText(true);
            card.getChildren().addAll(time, body);
            postsBox.getChildren().add(card);
        }
    }

    public void onChat() {
        int target = AppState.profileUserId;
        new Thread(() -> {
            try {
                var api = new ApiService("127.0.0.1", 5555);
                var resp = api.send("CHAT_OPEN|" + ClientApp.userId + "|" + target);
                for (String l: resp) if (l.startsWith("CHAT_OK|")) {
                    int convId = Integer.parseInt(l.split("\\|")[1]);
                    Platform.runLater(() -> {
                        AppState.selectedConversationId = convId;
                        ClientApp.setScene("/fxml/chat.fxml");
                    });
                    return;
                }
            } catch (IOException e) { e.printStackTrace(); }
        }).start();
    }

    // If this profile is a doctor, try to jump directly to their doctor page, else open doctors list.
    public void onBook() {
        int uid = AppState.profileUserId;
        new Thread(() -> {
            try {
                var api = new ApiService("127.0.0.1", 5555);
                var lines = api.send("DOCTOR_FIND_BY_USER|" + uid);
                int doctorId = -1;
                for (String l: lines) if (l.startsWith("DOCTOR_ID|")) {
                    doctorId = Integer.parseInt(l.split("\\|")[1]);
                }
                int finalDoctorId = doctorId;
                Platform.runLater(() -> {
                    if (finalDoctorId > 0) {
                        AppState.selectedDoctorId = finalDoctorId;
                        ClientApp.setScene("/fxml/doctor_details.fxml");
                    } else {
                        ClientApp.setScene("/fxml/doctors.fxml");
                    }
                });
            } catch (IOException e) { e.printStackTrace(); }
        }).start();
    }

    public void goDoctors() { ClientApp.setScene("/fxml/doctors.fxml"); }
    public void goHome() { ClientApp.setScene("/fxml/home.fxml"); }
}
