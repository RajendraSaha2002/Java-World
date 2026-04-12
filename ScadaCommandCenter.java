import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.sql.*;

public class ScadaCommandCenter extends JFrame {
    private JTextArea telemetryArea;
    private JTextArea alertArea;
    private static final int PORT = 8080;

    // Database configuration
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = "varrie75";

    public ScadaCommandCenter() {
        setTitle("Project BLACKOUT: MTU SCADA Dashboard");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridLayout(1, 2));

        telemetryArea = new JTextArea();
        telemetryArea.setEditable(false);
        telemetryArea.setBackground(Color.BLACK);
        telemetryArea.setForeground(Color.CYAN);
        telemetryArea.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.CYAN), "Live Grid Telemetry"));

        alertArea = new JTextArea();
        alertArea.setEditable(false);
        alertArea.setBackground(Color.BLACK);
        alertArea.setForeground(Color.RED);
        alertArea.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.RED), "Intrusion Detection System (IDS)"));

        add(new JScrollPane(telemetryArea));
        add(new JScrollPane(alertArea));
    }

    public void logTelemetry(String msg) {
        SwingUtilities.invokeLater(() -> {
            telemetryArea.append(msg + "\n");
            telemetryArea.setCaretPosition(telemetryArea.getDocument().getLength());
        });
    }

    public void logAlert(String msg) {
        SwingUtilities.invokeLater(() -> {
            alertArea.append(msg + "\n");
            alertArea.setCaretPosition(alertArea.getDocument().getLength());
        });
    }

    // Manual JSON Parsing (No external API)
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int start = json.indexOf(searchKey);
        if (start == -1) return "0";
        start += searchKey.length();
        int end = json.indexOf(",", start);
        if (end == -1) end = json.indexOf("}", start);
        return json.substring(start, end).trim().replace("\"", "");
    }

    public void startScadaServer() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                logAlert("[SYSTEM] SCADA Master Terminal listening on port " + PORT);

                while (true) {
                    Socket edgeNode = serverSocket.accept();
                    BufferedReader in = new BufferedReader(new InputStreamReader(edgeNode.getInputStream()));
                    PrintWriter out = new PrintWriter(edgeNode.getOutputStream(), true); // Send commands back to Python

                    String payload;
                    while ((payload = in.readLine()) != null) {
                        int nodeId = Integer.parseInt(extractJsonValue(payload, "node_id"));
                        double voltage = Double.parseDouble(extractJsonValue(payload, "voltage"));
                        double frequency = Double.parseDouble(extractJsonValue(payload, "frequency"));
                        double loadKw = Double.parseDouble(extractJsonValue(payload, "load_kw"));

                        logTelemetry(String.format("Node %d | V: %.2f | Hz: %.2f | Load: %.2fkW", nodeId, voltage, frequency, loadKw));

                        // Physics-Based Detection Logic
                        // Normal voltage is ~230V. A sudden drop without extreme load indicates False Data Injection.
                        if (voltage < 200.0) {
                            String threat = "FDI Attack: Voltage Anomaly";
                            logAlert("[!] CRITICAL: " + threat + " at Node " + nodeId);
                            logAlert(">> AUTOMATED RESPONSE: Isolating Node " + nodeId + " from Main Grid.");

                            // 1. Send Kill Command to Python Edge Node
                            out.println("TRIP_BREAKER:" + nodeId);

                            // 2. Log Forensics securely to PostgreSQL
                            logIncidentToHistorian(nodeId, threat, "CRITICAL", "Tripped Breaker / Islanded Node");
                        } else {
                            logTelemetryToHistorian(nodeId, voltage, frequency, loadKw);
                        }
                    }
                }
            } catch (IOException e) {
                logAlert("[ERROR] Network failure: " + e.getMessage());
            }
        }).start();
    }

    private void logTelemetryToHistorian(int nodeId, double v, double f, double l) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            String sql = "INSERT INTO node_telemetry (node_id, voltage, frequency, load_kw) VALUES (?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, nodeId); pstmt.setDouble(2, v); pstmt.setDouble(3, f); pstmt.setDouble(4, l);
                pstmt.executeUpdate();
            }
        } catch (SQLException ignored) {} // Ignored for console cleanliness during high-frequency ingestion
    }

    private void logIncidentToHistorian(int nodeId, String threat, String severity, String response) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            String sql = "INSERT INTO incident_logs (node_id, threat_vector, severity, response_taken) VALUES (?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, nodeId); pstmt.setString(2, threat);
                pstmt.setString(3, severity); pstmt.setString(4, response);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            logAlert("DB Forensic Error: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ScadaCommandCenter scada = new ScadaCommandCenter();
            scada.setVisible(true);
            scada.startScadaServer();
        });
    }
}