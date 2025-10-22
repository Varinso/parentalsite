
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
  private static final double DEFAULT_WIDTH = 1024;
  private static final double DEFAULT_HEIGHT = 768;
  private static boolean wasFullScreen = false;
  private static double lastWidth = DEFAULT_WIDTH;
  private static double lastHeight = DEFAULT_HEIGHT;

  @Override
  public void start(Stage stage) throws Exception {
    primaryStage = stage;
    // Set minimum window size
    stage.setMinWidth(800);
    stage.setMinHeight(600);
    
    // Set initial scene
    setScene("/fxml/login.fxml");
    stage.setTitle("perentalassist");
    
    // Handle window state preservation
    stage.fullScreenProperty().addListener((obs, oldValue, newValue) -> {
      wasFullScreen = newValue;
      if (!newValue) {
        // Restore last window size when exiting fullscreen
        stage.setWidth(lastWidth);
        stage.setHeight(lastHeight);
      }
    });

    // Store window dimensions when not in fullscreen
    stage.widthProperty().addListener((obs, oldValue, newValue) -> {
      if (!stage.isFullScreen()) {
        lastWidth = newValue.doubleValue();
      }
    });
    
    stage.heightProperty().addListener((obs, oldValue, newValue) -> {
      if (!stage.isFullScreen()) {
        lastHeight = newValue.doubleValue();
      }
    });
    
    stage.show();
  }

  public static void setScene(String fxml) {
    try {
      boolean isFullScreen = primaryStage.isFullScreen();
      Parent root = FXMLLoader.load(ClientApp.class.getResource(fxml));
      Scene scene = new Scene(root);
      scene.getStylesheets().add(ClientApp.class.getResource("/css/app.css").toExternalForm());
      
      if (!isFullScreen) {
        // Only adjust window size if not in fullscreen
        primaryStage.setWidth(lastWidth);
        primaryStage.setHeight(lastHeight);
      }
      
      primaryStage.setScene(scene);
      // Restore fullscreen state if it was fullscreen
      primaryStage.setFullScreen(isFullScreen);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void main(String[] args) { launch(args); }
}
