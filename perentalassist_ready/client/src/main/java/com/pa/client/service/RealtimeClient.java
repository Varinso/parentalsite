package com.pa.client.service;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** One persistent TCP connection; reads server-pushed lines on a thread. */
public class RealtimeClient {
    private static final RealtimeClient INSTANCE = new RealtimeClient();
    public static RealtimeClient get() { return INSTANCE; }

    private Socket socket;
    private PrintWriter out;
    private Thread readerThread;
    private volatile boolean running = false;
    private volatile Integer authedUserId = null;

    // convId -> listeners
    private final ConcurrentMap<Integer, CopyOnWriteArrayList<Consumer<Msg>>> listeners = new ConcurrentHashMap<>();

    public static record Msg(int convId, int id, int sender, String text, String createdAt) {}

    public synchronized void ensureConnected(String host, int port) throws IOException {
        if (running) return;
        socket = new Socket(host, port);
        out = new PrintWriter(socket.getOutputStream(), true);
        running = true;
        readerThread = new Thread(this::readLoop, "RealtimeClient-Reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public synchronized void close() {
        running = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    private void readLoop() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String line;
            while (running && (line = in.readLine()) != null) {
                if (line.startsWith("MSG|")) {
                    // MSG|convId|id|sender|content|created_at
                    String[] p = line.split("\\|", -1);
                    if (p.length >= 6) {
                        int convId = Integer.parseInt(p[1]);
                        int id = Integer.parseInt(p[2]);
                        int sender = Integer.parseInt(p[3]);
                        String text = p[4];
                        String created = p[5];
                        dispatch(convId, new Msg(convId, id, sender, text, created));
                    }
                }
                // ignore other lines; controller can still call one-shot ApiService if needed
            }
        } catch (IOException ignored) {
            running = false;
        }
    }

    private void dispatch(int convId, Msg msg) {
        var ls = listeners.get(convId);
        if (ls == null) return;
        for (var l : ls) l.accept(msg);
    }

    public void addListener(int convId, Consumer<Msg> l) {
        listeners.computeIfAbsent(convId, k -> new CopyOnWriteArrayList<>()).add(l);
    }
    public void removeListener(int convId, Consumer<Msg> l) {
        var list = listeners.get(convId);
        if (list != null) list.remove(l);
    }

    public synchronized void send(String raw) {
        if (out != null) out.println(raw);
    }

    public void authIfNeeded(int userId) {
        if (authedUserId != null && authedUserId == userId) return;
        send("AUTH|" + userId);
        authedUserId = userId;
    }

    public void subscribe(int convId) { send("CHAT_SUB|" + convId); }
    public void unsubscribe(int convId) { send("CHAT_UNSUB|" + convId); }
}
