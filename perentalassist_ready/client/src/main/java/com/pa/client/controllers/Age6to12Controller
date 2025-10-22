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

public class Age6to12Controller {
    @FXML private ImageView heroImage;
    @FXML private FlowPane videoGrid;

    private static class VideoItem { final String title, url; VideoItem(String t,String u){title=t;url=u;} }

    @FXML
    public void initialize() {
        URL heroUrl = getClass().getResource("/images/age6to12.jpg");
        if (heroUrl == null) heroUrl = getClass().getResource("/images/hero.jpg");
        heroImage.setImage(new Image(heroUrl.toExternalForm()));
        heroImage.fitWidthProperty().bind(((Region) heroImage.getParent()).widthProperty());

        var items = new ArrayList<VideoItem>();
        items.add(new VideoItem("Parents of Children Aged 6-12 Years Must Watch This Video !", "https://youtu.be/f78XAC1lnXA?si=BNGvahmAa8dd-zl6"));
        items.add(new VideoItem("School-Age Growth and Developmental Milestones Pediatric Nursing NCLEX Review", "https://youtu.be/3rzT_hfSqyI?si=J7RnkCJXXBLxM5tN"));
        items.add(new VideoItem("Developmental Milestones, Ages 6-12", "https://youtu.be/Am7Zk6U4-Yo?si=UTxFSo3HS5Q7zcpl"));
        buildGallery(items);
    }

    public void goBack(ActionEvent e){ ClientApp.setScene("/fxml/home.fxml"); }

    private void buildGallery(List<VideoItem> vids){
        videoGrid.getChildren().clear();
        for (var v: vids){
            String id = extractId(v.url); if (id==null) continue;
            String thumb = "https://img.youtube.com/vi/" + id + "/hqdefault.jpg";

            VBox card = new VBox(8); card.setPadding(new Insets(8)); card.setPrefWidth(320);
            card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e5e7eb; -fx-border-radius: 12; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 14, 0.2, 0, 6);");

            ImageView iv = new ImageView(new Image(thumb, 320, 180, true, true));
            iv.setFitWidth(320); iv.setFitHeight(180); iv.setPreserveRatio(true);

            Label title = new Label(v.title); title.setWrapText(true); title.getStyleClass().add("video-title");
            card.getChildren().addAll(iv, title);

            card.setOnMouseClicked(ev -> { if (ev.getButton()==MouseButton.PRIMARY) openLightbox(id); });
            videoGrid.getChildren().add(card);
        }
    }

    private void openLightbox(String id){
        Stage popup = new Stage(StageStyle.TRANSPARENT);
        popup.initOwner(ClientApp.primaryStage); popup.initModality(Modality.WINDOW_MODAL);
        StackPane backdrop = new StackPane(); backdrop.setStyle("-fx-background-color: rgba(0,0,0,0.55);");
        BorderPane box = new BorderPane(); box.setPadding(new Insets(8));
        box.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 20, 0.3, 0, 6);");

        WebView w = new WebView(); w.setPrefSize(900, 506);
        w.getEngine().load("https://www.youtube.com/embed/"+id+"?autoplay=1&rel=0");
        box.setCenter(w); StackPane.setMargin(box, new Insets(40)); backdrop.getChildren().add(box);

        backdrop.setOnMouseClicked(e -> { if (!box.isHover()) { w.getEngine().load("about:blank"); popup.close(); } });
        Scene scene = new Scene(backdrop, ClientApp.primaryStage.getWidth(), ClientApp.primaryStage.getHeight()); scene.setFill(null);
        popup.setScene(scene); popup.show();
    }

    private String extractId(String url){
        Pattern[] ps = new Pattern[]{ Pattern.compile("v=([A-Za-z0-9_-]{11})"),
                Pattern.compile("youtu\\.be/([A-Za-z0-9_-]{11})"), Pattern.compile("/embed/([A-Za-z0-9_-]{11})")};
        for (Pattern p: ps){ Matcher m=p.matcher(url); if(m.find()) return m.group(1); }
        return null;
    }
}
