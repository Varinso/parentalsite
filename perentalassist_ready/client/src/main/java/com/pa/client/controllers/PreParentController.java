package com.pa.client.controllers;

import com.pa.client.ClientApp;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PreParentController {

    @FXML private ImageView heroImage;
    @FXML private FlowPane videoGrid;

    private static class VideoItem {
        final String title;
        final String url;      // YouTube watch / share / embed url
        VideoItem(String t, String u){ title=t; url=u; }
    }

    @FXML
    public void initialize() {
        // Hero image (falls back to hero.jpg if preparent.jpg missing)
        URL heroUrl = getClass().getResource("/images/preparent.jpg");
        if (heroUrl == null) heroUrl = getClass().getResource("/images/hero.jpg");
        heroImage.setImage(new Image(heroUrl.toExternalForm()));
        heroImage.fitWidthProperty().bind(((Region) heroImage.getParent()).widthProperty());

        // --- Add your videos here (copy/paste more lines anytime) ---
        List<VideoItem> items = new ArrayList<>();
        items.add(new VideoItem("9 Tips for Parenting Pre-Teens",
                "https://youtu.be/MxBkXCTrh8s?si=VEbsIkfl3zi9YCr3"));
        items.add(new VideoItem("The Single Most Important Parenting Strategy | Becky Kennedy | TED",
                "https://youtu.be/PHpPtdk9rco?si=XP0wSL4bv_yGHDtV"));
        // TODO: Add more VideoItem(title, "YouTube URL");

        buildGallery(items);
    }

    public void goBack(ActionEvent e){
        ClientApp.setScene("/fxml/home.fxml");
    }

    // ---------- gallery + popup ----------
    private void buildGallery(List<VideoItem> vids) {
        videoGrid.getChildren().clear();
        for (VideoItem v : vids) {
            var id = extractVideoId(v.url);
            if (id == null) continue;

            String thumb = "https://img.youtube.com/vi/" + id + "/hqdefault.jpg";

            VBox card = new VBox(8);
            card.getStyleClass().add("video-card");
            card.setPadding(new Insets(8));
            card.setPrefWidth(320);

            ImageView iv = new ImageView(new Image(thumb, 320, 180, true, true));
            iv.setFitWidth(320);
            iv.setFitHeight(180);
            iv.setPreserveRatio(true);

            Label title = new Label(v.title);
            title.getStyleClass().add("video-title");
            title.setWrapText(true);

            card.getChildren().addAll(iv, title);

            // click to open lightbox
            card.setOnMouseClicked(ev -> {
                if (ev.getButton() == MouseButton.PRIMARY) openLightbox(id);
            });

            videoGrid.getChildren().add(card);
        }
    }

    private void openLightbox(String videoId) {
        Stage popup = new Stage(StageStyle.TRANSPARENT);
        popup.initOwner(ClientApp.primaryStage);
        popup.initModality(Modality.WINDOW_MODAL);

        StackPane backdrop = new StackPane();
        backdrop.setStyle("-fx-background-color: rgba(0,0,0,0.55);");

        BorderPane content = new BorderPane();
        content.setPadding(new Insets(8));
        content.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 20, 0.3, 0, 6);");

        WebView web = new WebView();
        web.setPrefSize(900, 506);                 // <= this is fine
        String embed = "https://www.youtube.com/embed/" + videoId + "?autoplay=1&rel=0";
        web.getEngine().load(embed);               // <= and this is fine
        content.setCenter(web);

        StackPane.setMargin(content, new Insets(40));
        backdrop.getChildren().add(content);

        Scene scene = new Scene(backdrop, ClientApp.primaryStage.getWidth(), ClientApp.primaryStage.getHeight());
        scene.setFill(null);

        backdrop.setOnMouseClicked(e -> {
            if (!content.isHover()) {
                web.getEngine().load("about:blank");   // stop the video
                popup.close();
            }
        });

        popup.setScene(scene);
        popup.show();
    }


    // supports watch, share, embed formats
    private String extractVideoId(String url) {
        Pattern[] patterns = new Pattern[] {
                Pattern.compile("v=([A-Za-z0-9_-]{11})"),                    // ...watch?v=ID
                Pattern.compile("youtu\\.be/([A-Za-z0-9_-]{11})"),           // youtu.be/ID
                Pattern.compile("/embed/([A-Za-z0-9_-]{11})")                // .../embed/ID
        };
        for (Pattern p: patterns) {
            Matcher m = p.matcher(url);
            if (m.find()) return m.group(1);
        }
        return null;
    }
}
