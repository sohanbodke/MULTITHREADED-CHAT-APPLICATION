package com.securefile.task3;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * ChatServer - multithreaded chat server
 * Usage: run main() -> server listens on port 5000 by default.
 *
 * Features:
 * - Accepts multiple clients
 * - Each client registers a username on connect
 * - Broadcasts messages to all connected clients
 * - Supports /quit to disconnect
 */
public class ChatServer {

    private final int port;
    private final ExecutorService clientPool;
    // map username -> ClientHandler
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();

    public ChatServer(int port) {
        this.port = port;
        this.clientPool = Executors.newCachedThreadPool();
    }

    public void start() {
        System.out.println("ChatServer starting on port " + port + " ...");
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started. Waiting for clients...");
            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket);
                clientPool.submit(handler);
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            shutdown();
        }
    }

    public void broadcast(String fromUser, String message) {
        String full = String.format("[%s] %s", fromUser, message);
        clients.values().forEach(h -> h.send(full));
    }

    public void addClient(String username, ClientHandler handler) {
        clients.put(username, handler);
        broadcast("SERVER", username + " joined the chat. Users: " + usersList());
    }

    public void removeClient(String username) {
        clients.remove(username);
        broadcast("SERVER", username + " left the chat. Users: " + usersList());
    }

    public String usersList() {
        return String.join(", ", clients.keySet());
    }

    public void shutdown() {
        try {
            clientPool.shutdownNow();
        } catch (Exception ignored) {}
        System.out.println("Server shutdown.");
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private String username;
        private BufferedReader in;
        private BufferedWriter out;
        private volatile boolean running = true;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));

                // ask for username
                send("Welcome! Please enter your username:");
                username = in.readLine();
                if (username == null || username.isBlank()) {
                    send("Invalid username. Closing connection.");
                    close();
                    return;
                }

                // if username exists, append a number to avoid collision
                synchronized (clients) {
                    String base = username;
                    int i = 1;
                    while (clients.containsKey(username)) {
                        username = base + "(" + i++ + ")";
                    }
                }

                addClient(username, this);
                send("Connected as: " + username);
                String line;
                while (running && (line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.equalsIgnoreCase("/quit") || line.equalsIgnoreCase("/exit")) {
                        send("Goodbye!");
                        break;
                    }
                    if (line.startsWith("/w ")) { // private message: /w target message...
                        String[] parts = line.split("\\s+", 3);
                        if (parts.length >= 3) {
                            String target = parts[1];
                            String msg = parts[2];
                            ClientHandler targetHandler = clients.get(target);
                            if (targetHandler != null) {
                                targetHandler.send("[whisper from " + username + "] " + msg);
                                send("[whisper to " + target + "] " + msg);
                            } else {
                                send("User '" + target + "' not found.");
                            }
                        } else {
                            send("Invalid whisper format. Use: /w username message");
                        }
                        continue;
                    }
                    broadcast(username, line);
                }
            } catch (IOException e) {
                System.err.println("Connection error for " + username + ": " + e.getMessage());
            } finally {
                close();
            }
        }

        void send(String msg) {
            try {
                out.write(msg);
                out.newLine();
                out.flush();
            } catch (IOException e) {
                // ignore send failures
            }
        }

        private void close() {
            running = false;
            try {
                if (username != null && clients.containsKey(username)) {
                    removeClient(username);
                }
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException ignored) {}
        }
    }

    public static void main(String[] args) {
        int port = 5000;
        if (args.length >= 1) {
            try { port = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }
        ChatServer server = new ChatServer(port);
        server.start();
    }
}
