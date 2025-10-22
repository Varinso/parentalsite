
package com.pa.client.controllers;

import com.pa.client.ClientApp;
import com.pa.client.service.ApiService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class LoginController {
  @FXML private TextField emailField;
  @FXML private PasswordField passwordField;

  public void onLogin(ActionEvent e) {
    try {
      var api = new ApiService("127.0.0.1", 5555);
      var resp = api.send("LOGIN|" + emailField.getText() + "|" + passwordField.getText());
      String first = resp.isEmpty()? "": resp.get(0);
      if (first.startsWith("LOGIN_OK")) {
        String[] p = first.split("\\|");
        ClientApp.userId = Integer.parseInt(p[1]);
        ClientApp.displayName = p[2];
        ClientApp.setScene("/fxml/home.fxml");
      } else {
        alert("Login failed: " + first);
      }
    } catch (Exception ex) {
      alert("Server not reachable");
    }
  }

  public void goSignup(ActionEvent e) {
    ClientApp.setScene("/fxml/signup.fxml");
  }

  public void onForgot(ActionEvent e) {
    alert("Reset flow not implemented yet.");
  }

  private void alert(String msg) {
    new Alert(Alert.AlertType.INFORMATION, msg).showAndWait();
  }
}
