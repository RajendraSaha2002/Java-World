import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final int PORT = 5000;
    private static Set<ClientHandler> clientHandlers = ConcurrentHashMap.newKeySet();
    private static Map<String, ClientHandler> users = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("=== Chat Server Started ===");
        System.out.println("Listening on port " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New connection from: " + clientSocket.getInetAddress());

                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clientHandlers.add(clientHandler);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    static class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Request username
                out.println("SERVER: Enter your username:");
                username = in.readLine();

                // Check if username is taken
                while (users.containsKey(username) || username == null || username.trim().isEmpty()) {
                    out.println("SERVER: Username taken or invalid. Try another:");
                    username = in.readLine();
                }

                users.put(username, this);
                System.out.println(username + " joined the chat");

                // Send welcome message
                out.println("SERVER: Welcome " + username + "!");
                out.println("SERVER: Type /help for available commands");

                // Broadcast join message
                broadcastMessage("SERVER: " + username + " joined the chat", this);

                // Send online users list
                sendUserList();

                // Handle messages
                String message;
                while ((message = in.readLine()) != null) {
                    handleMessage(message);
                }
            } catch (IOException e) {
                System.out.println(username + " disconnected abruptly");
            } finally {
                cleanup();
            }
        }

        private void handleMessage(String message) {
            if (message.startsWith("/")) {
                handleCommand(message);
            } else {
                // Broadcast regular message
                String formattedMsg = username + ": " + message;
                System.out.println(formattedMsg);
                broadcastMessage(formattedMsg, null);
            }
        }

        private void handleCommand(String command) {
            String[] parts = command.split(" ", 2);
            String cmd = parts[0].toLowerCase();

            switch (cmd) {
                case "/help":
                    out.println("SERVER: Available commands:");
                    out.println("  /help - Show this help");
                    out.println("  /users - List online users");
                    out.println("  /msg <username> <message> - Send private message");
                    out.println("  /quit - Exit chat");
                    break;

                case "/users":
                    sendUserList();
                    break;

                case "/msg":
                    if (parts.length < 2) {
                        out.println("SERVER: Usage: /msg <username> <message>");
                        return;
                    }
                    sendPrivateMessage(parts[1]);
                    break;

                case "/quit":
                    out.println("SERVER: Goodbye!");
                    cleanup();
                    break;

                default:
                    out.println("SERVER: Unknown command. Type /help for commands");
            }
        }

        private void sendUserList() {
            out.println("SERVER: Online users (" + users.size() + "):");
            for (String user : users.keySet()) {
                out.println("  - " + user);
            }
        }

        private void sendPrivateMessage(String input) {
            String[] parts = input.split(" ", 2);
            if (parts.length < 2) {
                out.println("SERVER: Usage: /msg <username> <message>");
                return;
            }

            String targetUser = parts[0];
            String message = parts[1];

            ClientHandler target = users.get(targetUser);
            if (target != null) {
                target.out.println("[PM from " + username + "]: " + message);
                out.println("[PM to " + targetUser + "]: " + message);
                System.out.println("[PM] " + username + " -> " + targetUser + ": " + message);
            } else {
                out.println("SERVER: User '" + targetUser + "' not found");
            }
        }

        private void broadcastMessage(String message, ClientHandler excludeUser) {
            for (ClientHandler client : clientHandlers) {
                if (client != excludeUser && client.out != null) {
                    client.out.println(message);
                }
            }
        }

        private void cleanup() {
            try {
                if (username != null) {
                    users.remove(username);
                    clientHandlers.remove(this);
                    broadcastMessage("SERVER: " + username + " left the chat", this);
                    System.out.println(username + " left the chat");
                }
                if (socket != null) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void sendMessage(String message) {
            if (out != null) {
                out.println(message);
            }
        }
    }
}