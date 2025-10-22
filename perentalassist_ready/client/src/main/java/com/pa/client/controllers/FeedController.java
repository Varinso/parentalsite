  package com.pa.client.controllers;

import com.pa.client.AppState;
import com.pa.client.ClientApp;
import com.pa.client.service.ApiService;
import com.pa.client.service.PushBus;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

public class FeedController {

    @FXML
    private ScrollPane scroll;
    @FXML
    private VBox feedBox;
    @FXML
    private TextArea newPostArea;
    @FXML
    private Button attachPostBtn;
    @FXML
    private ImageView postPreview;

    static class Post {
        int id, userId;
        String name, role, content, created, imageUrl;

        Post(int id, int uid, String n, String r, String c, String t, String img) {
            this.id = id;
            userId = uid;
            name = n;
            role = r;
            content = c;
            created = t;
            imageUrl = img;
        }
    }

    static class Comment {
        int id, userId;
        String name, role, content, created, imageUrl;

        Comment(int id, int uid, String n, String r, String c, String t, String img) {
            this.id = id;
            userId = uid;
            name = n;
            role = r;
            content = c;
            created = t;
            imageUrl = img;
        }
    }

    private final List<Post> posts = new ArrayList<>();
    private final Map<Integer, VBox> openCommentBoxes = new HashMap<>();
    private final Map<Integer, Consumer<String[]>> liveHandlers = new HashMap<>();
    private final Map<Integer, String> commentImageForPost = new HashMap<>();
    private String postImageUrl = "";

    @FXML
    public void initialize() {
        feedBox.setSpacing(14);
        feedBox.setPadding(new Insets(16));
        if (postPreview != null)
            postPreview.setVisible(false);
        loadFeed();
    }

    // ===== Data =====
    private void loadFeed() {
        posts.clear();
        feedBox.getChildren().clear();
        new Thread(() -> {
            try {
                var api = new ApiService("127.0.0.1", 5555);
                var lines = api.send("FEED_HOME");
                for (String l : lines)
                    if (l.startsWith("POST|")) {
                        var p = l.split("\\|", -1);
                        posts.add(new Post(Integer.parseInt(p[1]), Integer.parseInt(p[2]), p[3], p[4], p[5], p[6],
                                p.length >= 8 ? p[7] : ""));
                    }
                Platform.runLater(this::renderFeed);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void renderFeed() {
        feedBox.getChildren().clear();
        for (var p : posts)
            feedBox.getChildren().add(buildPostCard(p));
    }

    // ===== UI =====
    private Pane buildPostCard(Post p) {
        VBox card = new VBox(8);
        card.getStyleClass().add("feed-card");

        // header
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        var avatar = new ImageView(makeInitialsAvatar(p.name, 34));
        avatar.setFitWidth(34);
        avatar.setFitHeight(34);
        Label name = new Label(p.name);
        name.setStyle("-fx-font-weight:bold;");
        name.setOnMouseClicked(ev -> openProfile(p.userId, p.name, p.role));
        Label badge = new Label(p.role != null && p.role.equalsIgnoreCase("DOCTOR") ? "Professional" : "");
        if (!badge.getText().isBlank())
            badge.getStyleClass().add("post-badge");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label time = new Label(p.created);

        // Chat button — only if not my own post
        Button chatBtn = null;
        if (p.userId != ClientApp.userId) {
            chatBtn = new Button("Chat");
            chatBtn.setOnAction(ev -> startChatWith(p.userId));
        }

        // Delete button — only for my own post
        Button delBtn = null;
        if (p.userId == ClientApp.userId) {
            delBtn = new Button("Delete");
            delBtn.setOnAction(ev -> deletePost(p, card));
            delBtn.setStyle("-fx-background-color:#ffe3e3;");
        }

        header.getChildren().addAll(avatar, name);
        if (!badge.getText().isBlank())
            header.getChildren().add(badge);
        header.getChildren().addAll(spacer, time);
        if (chatBtn != null)
            header.getChildren().add(chatBtn);
        if (delBtn != null)
            header.getChildren().add(delBtn);

        // content + image
        Label body = new Label(p.content);
        body.setWrapText(true);
        body.getStyleClass().add("post-body");
        VBox contentBox = new VBox(6);
        if (p.content != null && !p.content.isBlank())
            contentBox.getChildren().add(body);
        if (p.imageUrl != null && !p.imageUrl.isBlank()) {
            Image img = loadImageFlexible(p.imageUrl);
            if (img != null && !img.isError()) {
                ImageView imgView = new ImageView(img);
                imgView.getStyleClass().add("post-image");
                imgView.setFitWidth(680);
                imgView.setPreserveRatio(true);
                imgView.setSmooth(true);
                contentBox.getChildren().add(imgView);
            }
        }

        // actions + comments
        HBox actions = new HBox(10);
        actions.getStyleClass().add("post-actions");
        Button commentToggle = new Button("Comments");
        commentToggle.getStyleClass().add("secondary-btn");
        actions.getChildren().add(commentToggle);

        VBox commentsBox = new VBox(8);
        commentsBox.setPadding(new Insets(8, 0, 0, 0));
        commentsBox.setVisible(false);
        commentsBox.setManaged(false);

        commentToggle.setOnAction(ev -> {
            boolean show = !commentsBox.isVisible();
            commentsBox.setVisible(show);
            commentsBox.setManaged(show);
            if (show) {
                openCommentBoxes.put(p.id, commentsBox);
                if (commentsBox.getChildren().isEmpty())
                    loadComments(p.id, commentsBox);
                liveSubscribe(p.id);
            } else {
                liveUnsubscribe(p.id);
                openCommentBoxes.remove(p.id);
            }
        });

        // new comment
        HBox newC = new HBox(8);
        TextField cField = new TextField();
        cField.setPromptText("Write a comment… (or attach only)");
        Button attachC = new Button("Attach Image");
        attachC.setOnAction(ev -> {
            String url = pickImageFile();
            if (url != null)
                commentImageForPost.put(p.id, url);
        });
        Button send = new Button("Post");
        send.setOnAction(ev -> postComment(p.id, cField, commentsBox));
        newC.getChildren().addAll(cField, attachC, send);
        HBox.setHgrow(cField, Priority.ALWAYS);

        card.getChildren().addAll(header, contentBox, actions, commentsBox, newC);
        return card;
    }

    private void loadComments(int postId, VBox into) {
        into.getChildren().clear();
        into.getChildren().add(new Label("Loading…"));
        new Thread(() -> {
            try {
                var api = new ApiService("127.0.0.1", 5555);
                var lines = api.send("COMMENTS_WITH_USERS|" + postId);
                List<Comment> cs = new ArrayList<>();
                for (String l : lines)
                    if (l.startsWith("COMMENT|")) {
                        var p = l.split("\\|", -1);
                        cs.add(new Comment(Integer.parseInt(p[1]), Integer.parseInt(p[2]), p[3], p[4], p[5], p[6],
                                p.length >= 8 ? p[7] : ""));
                    }
                Platform.runLater(() -> {
                    into.getChildren().clear();
                    if (cs.isEmpty()) {
                        Label none = new Label("No comments yet.");
                        none.setStyle("-fx-text-fill:#666;");
                        into.getChildren().add(none);
                    } else {
                        for (var c : cs)
                            into.getChildren().add(buildCommentRow(c, into));
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private Pane buildCommentRow(Comment c, VBox parentBox) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.TOP_LEFT);

        var avatar = new ImageView(makeInitialsAvatar(c.name, 22));
        avatar.setFitWidth(22);
        avatar.setFitHeight(22);
        Label name = new Label(c.name);
        name.setStyle("-fx-font-weight:bold; -fx-font-size:12px;");
        name.setOnMouseClicked(ev -> openProfile(c.userId, c.name, c.role));
        Label badge = new Label(c.role != null && c.role.equalsIgnoreCase("DOCTOR") ? "Professional" : "");
        if (!badge.getText().isBlank())
            badge.setStyle(
                    "-fx-background-color:#e4f0ff; -fx-text-fill:#0b70d5; -fx-padding:1 5; -fx-background-radius:10; -fx-font-size:11px;");

        VBox bubble = new VBox(4, new HBox(6, name, badge));
        bubble.getStyleClass().add("comment-bubble");
        if (c.content != null && !c.content.isBlank()) {
            Label text = new Label(c.content);
            text.setWrapText(true);
            bubble.getChildren().add(text);
        }
        if (c.imageUrl != null && !c.imageUrl.isBlank()) {
            Image img = loadImageFlexible(c.imageUrl);
            if (img != null && !img.isError()) {
                ImageView iv = new ImageView(img);
                iv.setFitWidth(520);
                iv.setPreserveRatio(true);
                iv.setSmooth(true);
                bubble.getChildren().add(iv);
            }
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button chatBtn = null;
        if (c.userId != ClientApp.userId) {
            chatBtn = new Button("Chat");
            int target = c.userId;
            chatBtn.setOnAction(ev -> startChatWith(target));
        }

        Button delBtn = null;
        if (c.userId == ClientApp.userId) {
            delBtn = new Button("Delete");
            delBtn.setStyle("-fx-background-color:#ffe3e3;");
            int cid = c.id;
            delBtn.setOnAction(ev -> deleteComment(cid, parentBox, row));
        }

        List<javafx.scene.Node> right = new ArrayList<>();
        if (chatBtn != null)
            right.add(chatBtn);
        if (delBtn != null)
            right.add(delBtn);
        if (right.isEmpty())
            row.getChildren().addAll(avatar, bubble);
        else
            row.getChildren().addAll(avatar, bubble, spacer, new HBox(6, right.toArray(new javafx.scene.Node[0])));
        return row;
    }

    // ===== New/Attach =====
    public void onPostAttach() {
        String url = pickImageFile();
        if (url != null) {
            postImageUrl = url;
            if (postPreview != null) {
                postPreview.setImage(loadImageFlexible(url));
                postPreview.setFitHeight(80);
                postPreview.setPreserveRatio(true);
                postPreview.setVisible(true);
            }
        }
    }

    public void onPostCreate() {
        sendPost(false);
    }

    public void onPostCreateAnonymous() {
        sendPost(true);
    }

    private void sendPost(boolean anonymous) {
        String text = (newPostArea.getText() == null) ? "" : newPostArea.getText();
        String img = postImageUrl == null ? "" : postImageUrl;

        if (text.isBlank() && img.isBlank()) {
            alert("Please write something or attach an image.");
            return;
        }
        newPostArea.clear();
        postImageUrl = "";
        if (postPreview != null) {
            postPreview.setImage(null);
            postPreview.setVisible(false);
        }

        new Thread(() -> {
            try {
                var api = new ApiService("127.0.0.1", 5555);
                // send real userid and anonymous flag (1/0)
                api.send("POST_CREATE|" + ClientApp.userId + "|" + text.replace("|", " ") + "|" + img + "|" + (anonymous ? 1 : 0));
                var lines = api.send("FEED_HOME");
                posts.clear();
                for (String l : lines)
                    if (l.startsWith("POST|")) {
                        var p = l.split("\\|", -1);
                        posts.add(new Post(Integer.parseInt(p[1]), Integer.parseInt(p[2]), p[3], p[4], p[5], p[6],
                                p.length >= 8 ? p[7] : ""));
                    }
                Platform.runLater(this::renderFeed);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void postComment(int postId, TextField field, VBox into) {
        String text = field.getText() == null ? "" : field.getText();
        String img = commentImageForPost.getOrDefault(postId, "");

        if (text.isBlank() && img.isBlank()) {
            alert("Write a comment or attach an image.");
            return;
        }

        field.clear();
        commentImageForPost.remove(postId);
        new Thread(() -> {
            try {
                var api = new ApiService("127.0.0.1", 5555);
                api.send(
                        "COMMENT_CREATE|" + postId + "|" + ClientApp.userId + "|" + text.replace("|", " ") + "|" + img);
                // live push will append
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    // ===== Delete actions =====
    private void deletePost(Post p, VBox card) {
        if (!confirm("Delete this post?"))
            return;
        new Thread(() -> {
            try {
                var api = new ApiService("127.0.0.1", 5555);
                var resp = api.send("POST_DELETE|" + p.id + "|" + ClientApp.userId);
                for (String l : resp)
                    if (l.startsWith("POST_DEL_OK|")) {
                        Platform.runLater(() -> feedBox.getChildren().remove(card));
                        return;
                    }
                Platform.runLater(() -> alert("Delete failed."));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void deleteComment(int commentId, VBox commentsBox, HBox row) {
        if (!confirm("Delete this comment?"))
            return;
        new Thread(() -> {
            try {
                var api = new ApiService("127.0.0.1", 5555);
                var resp = api.send("COMMENT_DELETE|" + commentId + "|" + ClientApp.userId);
                for (String l : resp)
                    if (l.startsWith("COMMENT_DEL_OK|")) {
                        Platform.runLater(() -> commentsBox.getChildren().remove(row));
                        return;
                    }
                Platform.runLater(() -> alert("Delete failed."));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    // ===== Live comments =====
    private void liveSubscribe(int postId) {
        if (liveHandlers.containsKey(postId))
            return;
        Consumer<String[]> cb = parts -> {
            // COMMENT_NEW|postId|id|userId|name|role|content|created|imageUrl
            int pid = Integer.parseInt(parts[1]);
            VBox box = openCommentBoxes.get(pid);
            if (box == null)
                return;
            if (!box.getChildren().isEmpty() && box.getChildren().get(0) instanceof Label lbl
                    && "No comments yet.".equals(lbl.getText()))
                box.getChildren().clear();
            Comment c = new Comment(Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), parts[4], parts[5],
                    parts[6], parts[7], parts.length >= 9 ? parts[8] : "");
            box.getChildren().add(buildCommentRow(c, box));
        };
        liveHandlers.put(postId, cb);
        PushBus.get().subscribeComments(postId, cb);
    }

    private void liveUnsubscribe(int postId) {
        var cb = liveHandlers.remove(postId);
        if (cb != null)
            PushBus.get().unsubscribeComments(postId, cb);
    }

    // ===== Helpers =====
    private void startChatWith(int targetUserId) {
        new Thread(() -> {
            try {
                var api = new ApiService("127.0.0.1", 5555);
                var resp = api.send("CHAT_OPEN|" + ClientApp.userId + "|" + targetUserId);
                for (String l : resp)
                    if (l.startsWith("CHAT_OK|")) {
                        int convId = Integer.parseInt(l.split("\\|")[1]);
                        Platform.runLater(() -> {
                            AppState.selectedConversationId = convId;
                            ClientApp.setScene("/fxml/chat.fxml");
                        });
                        return;
                    }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private String pickImageFile() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"));
        File f = fc.showOpenDialog(null);
        if (f == null)
            return null;
        return f.toURI().toString(); // file:///
    }

    private Image loadImageFlexible(String url) {
        try {
            if (url == null || url.isBlank())
                return null;
            if (url.startsWith("http") || url.startsWith("file:"))
                return new Image(url, true);
            if (url.startsWith("res:")) {
                String p = url.substring(4);
                var u = getClass().getResource(p.startsWith("/") ? p : "/" + p);
                if (u != null)
                    return new Image(u.toExternalForm(), true);
            }
            if (url.startsWith("/")) {
                var u = getClass().getResource(url);
                if (u != null)
                    return new Image(u.toExternalForm(), true);
            }
            return new Image(new File(url).toURI().toString(), true);
        } catch (Exception e) {
            return null;
        }
    }

    private Image makeInitialsAvatar(String name, int sizePx) {
        String initials = calcInitials(name);
        String[] colors = { "#7FB3D5", "#76D7C4", "#F7DC6F", "#F5B7B1", "#D7BDE2", "#A3E4D7", "#F8C471", "#AED6F1" };
        int idx = Math.abs(name == null ? 0 : name.hashCode()) % colors.length;
        Color bg = Color.web(colors[idx]);
        var circle = new Circle(sizePx / 2.0, bg);
        var text = new Label(initials);
        text.setTextFill(Color.WHITE);
        text.setStyle("-fx-font-weight:bold; -fx-font-size:" + Math.max(12, (int) (sizePx * 0.42)) + "px;");
        var pane = new StackPane(circle, text);
        pane.setPrefSize(sizePx, sizePx);
        var sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        return pane.snapshot(sp, new WritableImage(sizePx, sizePx));
    }

    private String calcInitials(String name) {
        if (name == null || name.isBlank())
            return "U";
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty())
                sb.append(Character.toUpperCase(p.charAt(0)));
            if (sb.length() == 2)
                break;
        }
        return sb.toString();
    }

    private void alert(String m) {
        new Alert(Alert.AlertType.INFORMATION, m, ButtonType.OK).showAndWait();
    }

    private boolean confirm(String m) {
        var a = new Alert(Alert.AlertType.CONFIRMATION, m, ButtonType.OK, ButtonType.CANCEL);
        a.showAndWait();
        return a.getResult() == ButtonType.OK;
    }

    public void goHome() {
        ClientApp.setScene("/fxml/home.fxml");
    }

    private void openProfile(int userId, String name, String role) {
        AppState.profileUserId = userId;
        AppState.profileDisplayName = name;
        AppState.profileRole = role == null ? "USER" : role;
        ClientApp.setScene("/fxml/profile.fxml");
    }
}
