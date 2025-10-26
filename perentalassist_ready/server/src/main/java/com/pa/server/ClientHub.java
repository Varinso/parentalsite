package com.pa.server;

import java.util.concurrent.*;
import java.util.concurrent.CopyOnWriteArraySet;

/** In-memory pub/sub hub for sockets. Supports chat and comments topics. */
public final class ClientHub {
    private static final ClientHub INSTANCE = new ClientHub();
    public static ClientHub get() { return INSTANCE; }

    private final ConcurrentMap<Integer, CopyOnWriteArraySet<ClientHandler>> chatSubs = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, CopyOnWriteArraySet<ClientHandler>> commentSubs = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, CopyOnWriteArraySet<ClientHandler>> userHandlers = new ConcurrentHashMap<>();

    private ClientHub() {}

    // ---- user presence
    public void registerUser(Integer userId, ClientHandler h) {
        if (userId == null) return;
        userHandlers.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(h);
    }
    public void unregisterHandler(ClientHandler h, Integer userId) {
        if (userId != null) {
            var set = userHandlers.get(userId);
            if (set != null) set.remove(h);
        }
        chatSubs.values().forEach(s -> s.remove(h));
        commentSubs.values().forEach(s -> s.remove(h));
    }

    // ---- chat topics
    public void subscribe(int convId, ClientHandler h) {
        chatSubs.computeIfAbsent(convId, k -> new CopyOnWriteArraySet<>()).add(h);
    }
    public void unsubscribe(int convId, ClientHandler h) {
        var set = chatSubs.get(convId);
        if (set != null) set.remove(h);
    }
    public void broadcast(int convId, String line) {
        var set = chatSubs.get(convId);
        if (set != null) set.forEach(h -> h.sendLine(line));
    }

    // ---- comment topics (per post)
    public void subscribeComment(int postId, ClientHandler h) {
        commentSubs.computeIfAbsent(postId, k -> new CopyOnWriteArraySet<>()).add(h);
    }
    public void unsubscribeComment(int postId, ClientHandler h) {
        var set = commentSubs.get(postId);
        if (set != null) set.remove(h);
    }
    public void broadcastComment(int postId, String line) {
        var set = commentSubs.get(postId);
        if (set != null) set.forEach(h -> h.sendLine(line));
    }
}
