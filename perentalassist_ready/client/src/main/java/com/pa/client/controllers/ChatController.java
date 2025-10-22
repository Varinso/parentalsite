package com.pa.client.controllers;

import com.pa.client.AppState;
import com.pa.client.ClientApp;
import com.pa.client.service.ApiService;
import com.pa.client.service.RealtimeClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class ChatController {

    @FXML
    private ListView<Conversation> convList;
    @FXML
    private VBox messagesBox;
    @FXML
    private ScrollPane messagesScroll;
    @FXML
    private TextField inputField, searchField;

    private int currentConvId = -1;
    private int lastMsgId = 0;

    private final RealtimeClient rt = RealtimeClient.get();
    private final java.util.function.Consumer<RealtimeClient.Msg> pushHandler = this::onPushedMessage;
    // texts we've sent but are awaiting server confirmation (used to avoid
    // duplicate local+push)
    private final Set<String> pendingOutgoing = Collections.synchronizedSet(new HashSet<>());

    // simple holder for conversation list entries (keeps id and display title)
    private static class Conversation {
        int id;
        String title;
        String preview;
        String time;
        int unread;

        Conversation() {
        }

        Conversation(int id, String title) {
            this.id = id;
            this.title = title;
        }

        @Override
        public String toString() {
            return title == null ? "" : title;
        }
    }

    @FXML
    public void initialize() {
        try {
            rt.ensureConnected("127.0.0.1", 5555);
            rt.authIfNeeded(ClientApp.userId);
        } catch (IOException e) {
            e.printStackTrace();
        }

        currentConvId = AppState.selectedConversationId;

        // render conversation title only (no numeric prefix)
        convList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Conversation item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.title);
                    setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
                }
            }
        });

        loadConversations();

        convList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) {
                if (currentConvId > 0) {
                    rt.removeListener(currentConvId, pushHandler);
                    rt.unsubscribe(currentConvId);
                }
                currentConvId = n.id;
                AppState.selectedConversationId = currentConvId;
                lastMsgId = 0;
                messagesBox.getChildren().clear();
                fetchMessages();
                rt.addListener(currentConvId, pushHandler);
                rt.subscribe(currentConvId);
            }
        });
    }

    private void loadConversations() {
        new Thread(() -> {
            try {
                var api = new ApiService("127.0.0.1", 5555);
                var lines = api.send("MY_CONVS|" + ClientApp.userId);
                List<Conversation> items = new ArrayList<>();
                for (String l : lines)
                    if (l.startsWith("CONV|")) {
                        var p = l.split("\\|", -1);
                        int id = Integer.parseInt(p[1]);
                        String title = p.length > 2 ? p[2] : ("Conversation " + id);
                        items.add(new Conversation(id, title));
                    }
                Platform.runLater(() -> {
                    convList.getItems().setAll(items);
                    if (currentConvId > 0) {
                        for (Conversation c : items)
                            if (c.id == currentConvId) {
                                convList.getSelectionModel().select(c);
                                break;
                            }
                    } else if (!items.isEmpty()) {
                        convList.getSelectionModel().select(0);
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void fetchMessages() {
        if (currentConvId <= 0)
            return;
        new Thread(() -> {
            try {
                var api = new ApiService("127.0.0.1", 5555);
                var lines = api.send("CHAT_FETCH|" + currentConvId + "|" + lastMsgId);
                List<Region> bubbles = new ArrayList<>();
                int maxId = lastMsgId;
                for (String l : lines)
                    if (l.startsWith("MSG|")) {
                        var p = l.split("\\|", -1);
                        int id = Integer.parseInt(p[1]);
                        int sender = Integer.parseInt(p[2]);
                        String text = p[3];
                        // if this is a message we just sent locally and it's awaiting server echo, skip
                        // adding duplicate
                        if (sender == ClientApp.userId && pendingOutgoing.remove(text)) {
                            // still log and update max id
                            logToFile(currentConvId, id, sender, text, (p.length > 4 ? p[4] : ""));
                            if (id > maxId)
                                maxId = id;
                            continue;
                        }
                        bubbles.add(buildBubble(sender == ClientApp.userId, text));
                        logToFile(currentConvId, id, sender, text, (p.length > 4 ? p[4] : ""));
                        if (id > maxId)
                            maxId = id;
                    }
                if (!bubbles.isEmpty()) {
                    int finalMaxId = maxId;
                    Platform.runLater(() -> {
                        messagesBox.getChildren().addAll(bubbles);
                        lastMsgId = finalMaxId;
                        // auto-scroll to bottom
                        messagesScroll.layout();
                        messagesScroll.setVvalue(1.0);
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private HBox buildBubble(boolean mine, String text) {
        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.getStyleClass().add(mine ? "msg-bubble-right" : "msg-bubble-left");
        bubble.setMaxWidth(420);

        HBox row = new HBox();
        row.setFillHeight(true);
        row.setAlignment(mine ? javafx.geometry.Pos.CENTER_RIGHT : javafx.geometry.Pos.CENTER_LEFT);
        row.getChildren().add(bubble);
        HBox.setMargin(bubble, new Insets(4, 6, 4, 6));
        return row;
    }

    private void onPushedMessage(RealtimeClient.Msg m) {
        if (m.convId() != currentConvId)
            return;
        Platform.runLater(() -> {
            // if this is an echo of our own recently-sent message, suppress duplicate
            if (m.sender() == ClientApp.userId && pendingOutgoing.remove(m.text())) {
                lastMsgId = Math.max(lastMsgId, m.id());
                return;
            }
            messagesBox.getChildren().add(buildBubble(m.sender() == ClientApp.userId, m.text()));
            lastMsgId = Math.max(lastMsgId, m.id());
        });
        logToFile(m.convId(), m.id(), m.sender(), m.text(), m.createdAt());
    }

    public void onSend() {
        String text = inputField.getText();
        if (text == null || text.isBlank() || currentConvId <= 0)
            return;
        inputField.clear();
        // show outgoing immediately to avoid waiting for server push
        pendingOutgoing.add(text);
        Platform.runLater(() -> {
            messagesBox.getChildren().add(buildBubble(true, text));
            messagesScroll.layout();
            messagesScroll.setVvalue(1.0);
        });
        new Thread(() -> {
            try {
                var api = new ApiService("127.0.0.1", 5555);
                api.send("CHAT_SEND|" + currentConvId + "|" + ClientApp.userId + "|" + text.replace("|", " "));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void onSearch() {
        String q = searchField.getText();
        if (q == null || q.isBlank())
            return;
        new Thread(() -> {
            try {
                var api = new ApiService("127.0.0.1", 5555);
                var lines = api.send("USER_SEARCH|" + q.replace("|", " "));
                List<String> users = new ArrayList<>();
                for (String l : lines)
                    if (l.startsWith("USER|")) {
                        var p = l.split("\\|", -1);
                        users.add(p[1] + "|" + p[2] + " (" + p[3] + ")");
                    }
                Platform.runLater(() -> {
                    ChoiceDialog<String> dlg = new ChoiceDialog<>(users.isEmpty() ? null : users.get(0), users);
                    dlg.setHeaderText("Start chat with…");
                    dlg.setContentText("Pick a user:");
                    dlg.showAndWait().ifPresent(ch -> {
                        int targetId = Integer.parseInt(ch.substring(0, ch.indexOf('|')));
                        openChatWith(targetId);
                    });
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void openChatWith(int targetUserId) {
        new Thread(() -> {
            try {
                var api = new ApiService("127.0.0.1", 5555);
                var resp = api.send("CHAT_OPEN|" + ClientApp.userId + "|" + targetUserId);
                for (String l : resp)
                    if (l.startsWith("CHAT_OK|")) {
                        int convId = Integer.parseInt(l.split("\\|")[1]);
                        Platform.runLater(() -> {
                            convList.getItems().add(0, new Conversation(convId, "New chat"));
                            convList.getSelectionModel().select(0);
                        });
                    }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void logToFile(int convId, int id, int sender, String text, String ts) {
        try {
            Path dir = Path.of(System.getProperty("user.home"), ".perentalassist", "chatlogs");
            Files.createDirectories(dir);
            Path f = dir.resolve("conv_" + convId + ".txt");
            String line = String.format("%s\t#%d\tfrom:%d\t%s%n", ts == null ? "" : ts, id, sender, text);
            Files.writeString(f, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    // <<< ADDED: for chat.fxml button onAction="#goHome"
    public void goHome() {
        ClientApp.setScene("/fxml/home.fxml");
    }
    // >>>
}
