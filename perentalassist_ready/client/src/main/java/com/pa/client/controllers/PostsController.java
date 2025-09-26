
package com.pa.client.controllers;

import com.pa.client.ClientApp;
import com.pa.client.service.ApiService;
import javafx.collections.*;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class PostsController {
  @FXML private TableView<PostRow> postTable;
  @FXML private TableColumn<PostRow, String> colAuthor;
  @FXML private TableColumn<PostRow, String> colContent;
  @FXML private TableColumn<PostRow, String> colTime;
  @FXML private ListView<String> commentList;
  @FXML private TextArea newPostArea;
  @FXML private TextField newCommentField;

  private final ObservableList<PostRow> posts = FXCollections.observableArrayList();
  private int selectedPostId = -1;

  @FXML
  public void initialize() {
    colAuthor.setCellValueFactory(data -> data.getValue().authorProperty());
    colContent.setCellValueFactory(data -> data.getValue().contentProperty());
    colTime.setCellValueFactory(data -> data.getValue().timeProperty());
    postTable.setItems(posts);
    postTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
      if (n != null) { selectedPostId = n.id(); loadComments(n.id()); }
    });
    loadPosts();
  }

  public void loadPosts() {
    posts.clear();
    try {
      var api = new ApiService("127.0.0.1", 5555);
      var resp = api.send("FETCH_POSTS");
      for (String r: resp) {
        if (r.startsWith("POST|")) {
          String[] p = r.split("\\|",5);
          int id = Integer.parseInt(p[1]);
          posts.add(new PostRow(id, p[2], p[3], p[4]));
        }
      }
    } catch (Exception ex) {
      alert("Failed to fetch posts");
    }
  }

  public void loadComments(int postId) {
    commentList.getItems().clear();
    try {
      var api = new ApiService("127.0.0.1", 5555);
      var resp = api.send("FETCH_COMMENTS|" + postId);
      for (String r: resp) {
        if (r.startsWith("COMMENT|")) {
          String[] p = r.split("\\|",5);
          commentList.getItems().add(p[2] + ": " + p[3] + "  (" + p[4] + ")");
        }
      }
    } catch (Exception ex) {
      alert("Failed to fetch comments");
    }
  }

  public void onCreatePost(ActionEvent e) {
    String content = newPostArea.getText().trim();
    if (content.isEmpty()) { alert("Write something first."); return; }
    try {
      var api = new ApiService("127.0.0.1", 5555);
      var resp = api.send("POST_CREATE|" + ClientApp.userId + "|" + content.replace("|","/"));
      if (!resp.isEmpty() && resp.get(0).startsWith("POST_OK")) {
        newPostArea.clear();
        loadPosts();
      } else alert("Post failed: " + (resp.isEmpty()? "no resp" : resp.get(0)));
    } catch (Exception ex) { alert("Server not reachable"); }
  }

  public void onCreateComment(ActionEvent e) {
    if (selectedPostId < 0) { alert("Select a post first."); return; }
    String content = newCommentField.getText().trim();
    if (content.isEmpty()) return;
    try {
      var api = new ApiService("127.0.0.1", 5555);
      var resp = api.send("COMMENT_CREATE|" + selectedPostId + "|" + ClientApp.userId + "|" + content.replace("|","/"));
      if (!resp.isEmpty() && resp.get(0).startsWith("COMMENT_OK")) {
        newCommentField.clear();
        loadComments(selectedPostId);
      } else alert("Comment failed: " + (resp.isEmpty()? "no resp" : resp.get(0)));
    } catch (Exception ex) { alert("Server not reachable"); }
  }

  public void goBack(ActionEvent e) {
    ClientApp.setScene("/fxml/home.fxml", 1300, 900);
  }

  private void alert(String msg) { new Alert(Alert.AlertType.INFORMATION, msg).showAndWait(); }
}
