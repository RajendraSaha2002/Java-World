import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.sql.*;

public class CommandCenter extends JFrame {
    private JTextArea logArea;
    private static final int PORT = 9090;

    // Database configuration (Update with your PostgreSQL credentials)
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = "varrie75";

    public CommandCenter() {
        setTitle("WATCHTOWER Command & Control");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(Color.GREEN);
        logArea.setFont(new Font("Monospaced", Font.BOLD, 14));
        add(new JScrollPane(logArea), BorderLayout.CENTER);

        JLabel header = new JLabel("BLUE TEAM TACTICAL DASHBOARD", SwingConstants.CENTER);
        header.setFont(new Font("Arial", Font.BOLD, 20));
        header.setForeground(Color.RED);
        header.setOpaque(true);
        header.setBackground(Color.DARK_GRAY);
        add(header, BorderLayout.NORTH);
    }

    public void logEvent(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public void saveToDatabase(String jsonAlert) {
        try {
            // Manual JSON extraction to avoid external API dependencies like GSON/Jackson
            int nodeId = Integer.parseInt(extractJsonValue(jsonAlert, "node_id"));
            String threatType = extractJsonValue(jsonAlert, "threat_type");
            String severity = extractJsonValue(jsonAlert, "severity");
            String sourceIp = extractJsonValue(jsonAlert, "source_ip");

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                String sql = "INSERT INTO threat_logs (node_id, threat_type, severity, source_ip) VALUES (?, ?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setInt(1, nodeId);
                    pstmt.setString(2, threatType);
                    pstmt.setString(3, severity);
                    pstmt.setString(4, sourceIp);
                    pstmt.executeUpdate();
                }
            }
        } catch (Exception e) {
            logEvent("[!] DB Error: " + e.getMessage());
        }
    }

    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int start = json.indexOf(searchKey);
        if (start == -1) return "1"; // Default fallback
        start += searchKey.length();
        int end = json.indexOf(",", start);
        if (end == -1) end = json.indexOf("}", start);
        return json.substring(start, end).trim().replace("\"", "");
    }

    public void startServer() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                logEvent(">> WATCHTOWER System Online. Listening on port " + PORT + "...");
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    String inputLine = in.readLine();

                    if (inputLine != null) {
                        logEvent("[ALERT RECEIVED] " + inputLine);

                        // Automated Defense Logic
                        if (inputLine.contains("Brute Force")) {
                            logEvent(">> [DEFENSE PROTOCOL] AUTOMATED LOCKDOWN TRIGGERED ON COMPROMISED NODE!");
                        }

                        saveToDatabase(inputLine);
                    }
                    clientSocket.close();
                }
            } catch (IOException e) {
                logEvent("Server Error: " + e.getMessage());
            }
        }).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            CommandCenter dashboard = new CommandCenter();
            dashboard.setVisible(true);
            dashboard.startServer();
        });
    }
}