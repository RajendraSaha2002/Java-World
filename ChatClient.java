import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ChatClient {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private volatile boolean running = true;

    public ChatClient() {
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            System.out.println("=== Connected to Chat Server ===");

            // Start thread to receive messages
            new Thread(new MessageReceiver()).start();

            // Send messages from console
            sendMessages();

        } catch (UnknownHostException e) {
            System.err.println("Server not found: " + SERVER_ADDRESS);
        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
        }
    }

    private void sendMessages() {
        Scanner scanner = new Scanner(System.in);
        try {
            while (running && scanner.hasNextLine()) {
                String message = scanner.nextLine();
                if (message.equalsIgnoreCase("/quit")) {
                    out.println(message);
                    running = false;
                    break;
                }
                out.println(message);
            }
        } finally {
            cleanup();
            scanner.close();
        }
    }

    private void cleanup() {
        running = false;
        try {
            if (socket != null) socket.close();
            if (in != null) in.close();
            if (out != null) out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("\n=== Disconnected from server ===");
    }

    class MessageReceiver implements Runnable {
        @Override
        public void run() {
            try {
                String message;
                while (running && (message = in.readLine()) != null) {
                    System.out.println(message);
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("Connection lost: " + e.getMessage());
                }
            } finally {
                running = false;
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("=== Chat Client ===");
        System.out.println("Connecting to " + SERVER_ADDRESS + ":" + SERVER_PORT);
        new ChatClient();
    }
}