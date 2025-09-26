
package com.pa.client.controllers;

import com.pa.client.ClientApp;
import com.pa.client.service.ApiService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class SignupController {
  @FXML private TextField nameField;
  @FXML private TextField emailField;
  @FXML private PasswordField passwordField;
  @FXML private PasswordField confirmField;

  public void onSignup(ActionEvent e) {
    if (!passwordField.getText().equals(confirmField.getText())) {
      alert("Passwords do not match.");
      return;
    }
    try {
      var api = new ApiService("127.0.0.1", 5555);
      var resp = api.send("SIGNUP|" + emailField.getText() + "|" + passwordField.getText() + "|" + nameField.getText());
      String first = resp.isEmpty()? "": resp.get(0);
      if (first.startsWith("SIGNUP_OK")) {
        alert("Account created! Please sign in.");
        ClientApp.setScene("/fxml/login.fxml", 440, 560);
      } else {
        alert("Signup error: " + first);
      }
    } catch (Exception ex) {
      alert("Server not reachable");
    }
  }

  public void goLogin(ActionEvent e) {
    ClientApp.setScene("/fxml/login.fxml", 440, 560);
  }
  private void alert(String msg) { new Alert(Alert.AlertType.INFORMATION, msg).showAndWait(); }
}
