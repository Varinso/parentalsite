
package com.pa.client.controllers;

import com.pa.client.ClientApp;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;

public class HomeController {

  public void onCall(ActionEvent e) { info("(219) 555-0114"); }
  public void onUserMenu(ActionEvent e) { info("User: " + ClientApp.displayName); }

  public void goHome(ActionEvent e) { /* already here */ }

  public void goPosts(ActionEvent e) { ClientApp.setScene("/fxml/posts.fxml", 1200, 800); }

  public void goChat(ActionEvent e) { info("Chat page coming soon."); }

  public void goExperts(ActionEvent e) { info("Experts page coming soon."); }

  public void goPreParent(javafx.event.ActionEvent e) {
    com.pa.client.ClientApp.setScene("/fxml/preparent.fxml", 1300, 900);
  }
  public void go0to5(javafx.event.ActionEvent e) {
    com.pa.client.ClientApp.setScene("/fxml/age0to5.fxml", 1200, 800);
  }
  public void go6to12(javafx.event.ActionEvent e) {
    com.pa.client.ClientApp.setScene("/fxml/age6to12.fxml", 1200, 800);
  }
  public void goTeen(javafx.event.ActionEvent e) {
    com.pa.client.ClientApp.setScene("/fxml/teen.fxml", 1200, 800);
  }


  public void goSCSExpert(ActionEvent e) { info("Search Expert coming soon."); }
  public void goSCSSchools(ActionEvent e) { info("Search Schools coming soon."); }
  public void goSCSTeachers(ActionEvent e) { info("Search Private Teachers coming soon."); }

  public void goHealth(ActionEvent e) { info("Health Sessions coming soon."); }

  private void info(String m) { new Alert(Alert.AlertType.INFORMATION, m).showAndWait(); }

    // add at top of class:
    @FXML private javafx.scene.image.ImageView heroImage;
    @FXML private javafx.scene.layout.GridPane hero; // optional if you want

    @FXML
    public void initialize() {
        // load hero image from resources
        String url = getClass().getResource("/images/hero.jpg").toExternalForm();
        heroImage.setImage(new javafx.scene.image.Image(url));

        // Make image scale with the right cell width while keeping aspect ratio
        heroImage.fitWidthProperty().bind(
                ((javafx.scene.layout.Region) heroImage.getParent()).widthProperty()
        );
        // Already set fitHeight="420" in FXML. If you want it taller, change there.
    }
}
