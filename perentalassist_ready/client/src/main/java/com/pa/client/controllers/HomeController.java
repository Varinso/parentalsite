package com.pa.client.controllers;

import com.pa.client.ClientApp;
import com.pa.client.service.ApiService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class HomeController {

  // --- existing navigation handlers ---
  public void onCall(ActionEvent e) { info("(219) 555-0114"); }
  public void onUserMenu(ActionEvent e) { ClientApp.setScene("/fxml/user.fxml"); }

  public void goHome(ActionEvent e) { /* already here */ }

  public void goPosts(ActionEvent e) { ClientApp.setScene("/fxml/posts.fxml"); }

  public void goExperts(ActionEvent e) { ClientApp.setScene("/fxml/doctors.fxml"); }
  public void goChat(ActionEvent e) { ClientApp.setScene("/fxml/chat.fxml"); }

  public void goSessions(ActionEvent e) { ClientApp.setScene("/fxml/sessions.fxml"); }

  public void goPreParent(javafx.event.ActionEvent e) {
    ClientApp.setScene("/fxml/preparent.fxml");
  }
  public void go0to5(javafx.event.ActionEvent e) {
    ClientApp.setScene("/fxml/age0to5.fxml");
  }
  public void go6to12(javafx.event.ActionEvent e) {
    ClientApp.setScene("/fxml/age6to12.fxml");
  }
  public void goTeen(javafx.event.ActionEvent e) {
    ClientApp.setScene("/fxml/teen.fxml");
  }

  @FXML
  public void goSpecialSupport() {
    ClientApp.setScene("/fxml/special_schools.fxml");
  }

  public void goTeachers() { ClientApp.setScene("/fxml/teachers.fxml"); }
  public void goRegisterTeacher() { ClientApp.setScene("/fxml/register_teacher.fxml"); }

  public void goHealth(ActionEvent e) { info("Health Sessions coming soon."); }

  public void goFeed(ActionEvent e) { ClientApp.setScene("/fxml/feed.fxml"); }

  private void info(String m) { new Alert(Alert.AlertType.INFORMATION, m).showAndWait(); }

  // --- UI refs already present in your file ---
  @FXML private ImageView heroImage;
  @FXML private GridPane   hero; // optional, only used for sizing
  // NEW: the button to hide when the user is already a teacher
  @FXML private Button registerTeacherBtn;

  // --- local helpers (no changes to ClientApp needed) ---
  private ApiService api;
  private int currentUserId = -1;

  @FXML
  public void initialize() {
    // hero image (null-safe)
    try {
      if (heroImage != null) {
        var url = getClass().getResource("/images/hero.jpg");
        if (url != null) {
          heroImage.setImage(new Image(url.toExternalForm()));
          // scale with parent width, keep aspect ratio
          if (heroImage.getParent() instanceof Region region) {
            heroImage.fitWidthProperty().bind(region.widthProperty());
          }
        }
      }
    } catch (Exception ignored) { }

    // init API + user id
    api = resolveApiService();
    currentUserId = resolveCurrentUserId();

    // Hide "Register as a Teacher" if already registered
    maybeHideRegisterTeacher();
  }

  /** If the signed-in user already has a teacher profile, hide the register button. */
  private void maybeHideRegisterTeacher() {
    if (registerTeacherBtn == null) return; // no button in FXML, nothing to do
    if (currentUserId <= 0) return;         // not logged in → keep visible

    try {
      // Server should respond with: TEACHER_ID|<id>|<name> ... then END
      List<String> lines = api.send("TEACHER_FIND_BY_USER|" + currentUserId);
      boolean found = false;
      for (String line : lines) {
        if (line == null) continue;
        if (line.startsWith("TEACHER_ID|")) { found = true; break; }
        if ("END".equals(line)) break;
      }
      if (found) {
        registerTeacherBtn.setVisible(false);
        registerTeacherBtn.setManaged(false);
      }
    } catch (Exception ex) {
      // On any error, do nothing (keep the button visible).
      // You can log if you want:
      System.err.println("maybeHideRegisterTeacher: " + ex.getMessage());
    }
  }

  // -------- reflection-based resolution so we don't touch ClientApp --------
  private ApiService resolveApiService() {
    try {
      Class<?> as = Class.forName("com.pa.client.AppState");
      Method get = as.getMethod("get");
      Object inst = get.invoke(null);
      try {
        Method apiM = as.getMethod("api");
        Object svc = apiM.invoke(inst);
        if (svc instanceof ApiService) return (ApiService) svc;
      } catch (NoSuchMethodException ignored) { }
    } catch (Throwable ignored) { }
    // fallback to defaults you already use
    return new ApiService("127.0.0.1", 5555);
  }

  private int resolveCurrentUserId() {
    Integer v = tryStaticFieldInt("com.pa.client.ClientApp", "userId");
    if (v == null) v = tryStaticFieldInt("com.pa.client.ClientApp", "currentUserId");
    if (v == null) {
      try {
        String prop = System.getProperty("pa.userId");
        if (prop != null) v = Integer.parseInt(prop.trim());
      } catch (Throwable ignored) { }
    }
    return v != null ? v : -1;
  }

  private Integer tryStaticFieldInt(String cls, String field) {
    try {
      Class<?> c = Class.forName(cls);
      Field f = c.getDeclaredField(field);
      f.setAccessible(true);
      Object rv = f.get(null);
      if (rv instanceof Number) return ((Number) rv).intValue();
    } catch (Throwable ignored) { }
    return null;
  }
}
