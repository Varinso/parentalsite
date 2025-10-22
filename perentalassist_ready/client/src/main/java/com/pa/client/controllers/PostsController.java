
package com.pa.client.controllers;

import com.pa.client.ClientApp;
import com.pa.client.service.ApiService;
import javafx.collections.*;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

public class PostsController {
  @FXML
  private TableView<PostRow> postTable;
  @FXML
  private TableColumn<PostRow, String> colAuthor;
  @FXML
  private TableColumn<PostRow, String> colContent;
  @FXML
  private TableColumn<PostRow, String> colTime;
  @FXML
  private ListView<String> commentList;
  @FXML
  private TextArea newPostArea;
  @FXML
  private TextField imageUrlField;
  @FXML
  private TextField newCommentField;

  private final ObservableList<PostRow> posts = FXCollections.observableArrayList();
  private int selectedPostId = -1;

  @FXML
  public void initialize() {
    colAuthor.setCellValueFactory(data -> data.getValue().authorProperty());
    colContent.setCellValueFactory(data -> data.getValue().contentProperty());
    colTime.setCellValueFactory(data -> data.getValue().timeProperty());
    // render content with optional image preview
    colContent.setCellFactory(tc -> new TableCell<>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
          setGraphic(null);
          return;
        }
        PostRow row = getTableView().getItems().get(getIndex());
        VBox box = new VBox(4);
        Label txt = new Label(item);
        txt.setWrapText(true);
        box.getChildren().add(txt);
        String img = row.imageUrlProperty().get();
        if (img != null && !img.isBlank()) {
          try {
            javafx.scene.image.Image im = new javafx.scene.image.Image(img, 360, 0, true, true);
            javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(im);
            box.getChildren().add(iv);
          } catch (Exception ignore) {
          }
        }
        setGraphic(box);
        setText(null);
      }
    });
    postTable.setItems(posts);
    postTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
      if (n != null) {
        selectedPostId = n.id();
        loadComments(n.id());
      }
    });
    loadPosts();
  }

  public void loadPosts() {
    posts.clear();
    try {
      var api = new ApiService("127.0.0.1", 5555);
      // fetch posts for the currently viewed user (personal feed)
      String uid = String.valueOf(ClientApp.userId);
      String me = String.valueOf(ClientApp.userId);
      var resp = api.send("FEED_BY_USER|" + uid + "|" + me);
      for (String r : resp) {
        if (r.startsWith("POST|")) {
          // POST|id|userId|display_name|role|content|created_at|image_url
          String[] p = r.split("\\|", 8);
          int id = Integer.parseInt(p[1]);
          String display = p.length > 3 ? p[3] : "";
          String content = p.length > 5 ? p[5] : "";
          String created = p.length > 6 ? p[6] : "";
          String img = p.length > 7 ? p[7] : "";
          posts.add(new PostRow(id, display, content, created, img));
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
      for (String r : resp) {
        if (r.startsWith("COMMENT|")) {
          String[] p = r.split("\\|", 5);
          commentList.getItems().add(p[2] + ": " + p[3] + "  (" + p[4] + ")");
        }
      }
    } catch (Exception ex) {
      alert("Failed to fetch comments");
    }
  }

  public void onCreatePost(ActionEvent e) {
    createPost(false);
  }

  public void onCreatePostAnonymous(ActionEvent e) {
    createPost(true);
  }

  private void createPost(boolean anonymous) {
    String content = newPostArea.getText().trim();
    if (content.isEmpty()) {
      alert("Write something first.");
      return;
    }
    try {
      var api = new ApiService("127.0.0.1", 5555);
      int uid = ClientApp.userId; // always send real user id
      String img = imageUrlField == null ? "" : imageUrlField.getText().trim();
      int anonFlag = anonymous ? 1 : 0;
      var resp = api.send("POST_CREATE|" + uid + "|" + content.replace("|", "/") + "|" + img + "|" + anonFlag);
      if (!resp.isEmpty() && resp.get(0).startsWith("POST_OK")) {
        newPostArea.clear();
        if (imageUrlField != null) imageUrlField.clear();
        loadPosts();
      } else
        alert("Post failed: " + (resp.isEmpty() ? "no resp" : resp.get(0)));
    } catch (Exception ex) {
      alert("Server not reachable");
    }
  }

  public void onCreateComment(ActionEvent e) {
    if (selectedPostId < 0) {
      alert("Select a post first.");
      return;
    }
    String content = newCommentField.getText().trim();
    if (content.isEmpty())
      return;
    try {
      var api = new ApiService("127.0.0.1", 5555);
      var resp = api
          .send("COMMENT_CREATE|" + selectedPostId + "|" + ClientApp.userId + "|" + content.replace("|", "/"));
      if (!resp.isEmpty() && resp.get(0).startsWith("COMMENT_OK")) {
        newCommentField.clear();
        loadComments(selectedPostId);
      } else
        alert("Comment failed: " + (resp.isEmpty() ? "no resp" : resp.get(0)));
    } catch (Exception ex) {
      alert("Server not reachable");
    }
  }

  public void goBack(ActionEvent e) {
    ClientApp.setScene("/fxml/home.fxml");
  }

  private void alert(String msg) {
    new Alert(Alert.AlertType.INFORMATION, msg).showAndWait();
  }
}
