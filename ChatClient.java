package com.securefile.task3;

import java.io.*;
import java.net.*;
import java.util.Scanner;

/**
 * ChatClient - simple console client for ChatServer
 * Usage: run main() -> connects to localhost:5000 by default.
 * Commands:
 *  - /quit or /exit -> disconnect
 *  - /w username message -> whisper (private message)
 */
public class ChatClient {

    private final String host;
    private final int port;
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private volatile boolean running = true;

    public ChatClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() {
        try {
            socket = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));

            // Thread to read server messages
            Thread reader = new Thread(this::readLoop);
            reader.setDaemon(true);
            reader.start();

            // Main thread reads console input and sends
            Scanner scanner = new Scanner(System.in, "UTF-8");
            while (running) {
                if (!scanner.hasNextLine()) break;
                String line = scanner.nextLine();
                send(line);
                if (line.equalsIgnoreCase("/quit") || line.equalsIgnoreCase("/exit")) {
                    running = false;
                }
            }

            close();
        } catch (IOException e) {
            System.err.println("Unable to connect to server: " + e.getMessage());
        }
    }

    private void readLoop() {
        try {
            String msg;
            while ((msg = in.readLine()) != null) {
                System.out.println(msg);
            }
        } catch (IOException ignored) {
        } finally {
            close();
        }
    }

    private void send(String msg) {
        try {
            out.write(msg);
            out.newLine();
            out.flush();
        } catch (IOException e) {
            System.err.println("Failed to send: " + e.getMessage());
        }
    }

    private void close() {
        running = false;
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }

    public static void main(String[] args) {
        String host = "localhost";
        int port = 5000;
        if (args.length >= 1) host = args[0];
        if (args.length >= 2) {
            try { port = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
        }
        ChatClient client = new ChatClient(host, port);
        client.start();
    }
}
