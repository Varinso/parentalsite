
package com.pa.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ClientApp extends Application {
  public static Stage primaryStage;
  public static int userId = -1;
  public static String displayName = "";

  @Override
  public void start(Stage stage) throws Exception {
    primaryStage = stage;
    setScene("/fxml/login.fxml", 440, 560);
    stage.setTitle("perentalassist");
    stage.show();
  }

  public static void setScene(String fxml, double w, double h) {
    try {
      Parent root = FXMLLoader.load(ClientApp.class.getResource(fxml));
      Scene scene = new Scene(root, w, h);
      scene.getStylesheets().add(ClientApp.class.getResource("/css/app.css").toExternalForm());
      primaryStage.setScene(scene);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void main(String[] args) { launch(args); }
}
