import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class BlackoutC2 extends JFrame {
    private JTextArea consoleOutput;
    private PrintWriter pythonCommandStream;
    private Map<String, Double> latestVoltages = new HashMap<>();

    // DB Credentials (Update if necessary)
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = "varrie75";

    public BlackoutC2() {
        // Native Dark Theme Implementation (No FlatLaf)
        Color bgDark = new Color(43, 43, 43);
        Color textGreen = new Color(0, 255, 0);

        setTitle("Project BLACKOUT - Command & Control IDS");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(bgDark);
        setLayout(new BorderLayout());

        consoleOutput = new JTextArea();
        consoleOutput.setBackground(Color.BLACK);
        consoleOutput.setForeground(textGreen);
        consoleOutput.setFont(new Font("Monospaced", Font.PLAIN, 14));
        consoleOutput.setEditable(false);

        TitledBorder border = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.CYAN), "Live Grid Telemetry & IDS Alerts");
        border.setTitleColor(Color.CYAN);
        consoleOutput.setBorder(border);
        add(new JScrollPane(consoleOutput), BorderLayout.CENTER);

        // Control Panel
        JPanel controlPanel = new JPanel();
        controlPanel.setBackground(bgDark);
        JButton btnShutdown = new JButton("EMERGENCY SHUTDOWN");
        btnShutdown.setBackground(Color.RED);
        btnShutdown.setForeground(Color.WHITE);
        btnShutdown.setFocusPainted(false);
        btnShutdown.addActionListener(e -> sendCommandToPython("EMERGENCY_SHUTDOWN"));

        JButton btnSafeMode = new JButton("ENABLE SAFE MODE (Substation_B)");
        btnSafeMode.setBackground(Color.ORANGE);
        btnSafeMode.setFocusPainted(false);
        btnSafeMode.addActionListener(e -> sendCommandToPython("SAFE_MODE:Substation_B"));

        controlPanel.add(btnSafeMode);
        controlPanel.add(btnShutdown);
        add(controlPanel, BorderLayout.SOUTH);
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            consoleOutput.append(message + "\n");
            consoleOutput.setCaretPosition(consoleOutput.getDocument().getLength());
        });
    }

    private void sendCommandToPython(String command) {
        if (pythonCommandStream != null) {
            pythonCommandStream.println(command);
            log("\n[C2 COMMAND ISSUED] -> " + command + "\n");
        } else {
            log("[ERROR] No connection to Edge Nodes.");
        }
    }

    // Manual JSON Parsing to avoid external APIs
    private String parseJson(String json, String key, boolean isString) {
        String searchKey = "\"" + key + "\":";
        int start = json.indexOf(searchKey);
        if (start == -1) return "0";
        start += searchKey.length();

        int end;
        if (isString) {
            start = json.indexOf("\"", start) + 1;
            end = json.indexOf("\"", start);
        } else {
            end = json.indexOf(",", start);
            if (end == -1) end = json.indexOf("}", start);
        }
        return json.substring(start, end).trim();
    }

    public void startServer() {
        new Thread(() -> {
            try (ServerSocket server = new ServerSocket(5050)) {
                log("[SYSTEM] MTU Active. Listening on Port 5050...");
                Socket socket = server.accept();
                log("[NETWORK] Secure connection established with Python Edge Nodes.\n");

                // Initialize output stream to send commands BACK to Python
                pythonCommandStream = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                String payload;
                while ((payload = in.readLine()) != null) {
                    processTelemetry(payload);
                }
            } catch (IOException e) {
                log("[NETWORK ERROR] " + e.getMessage());
            }
        }).start(); // Run in background SwingWorker thread
    }

    private void processTelemetry(String json) {
        try {
            String substation = parseJson(json, "substation", true);
            double voltage = Double.parseDouble(parseJson(json, "voltage", false));
            double frequency = Double.parseDouble(parseJson(json, "frequency", false));
            double load = Double.parseDouble(parseJson(json, "load", false));
            String status = parseJson(json, "status", true);

            latestVoltages.put(substation, voltage);
            log(String.format("TELEMETRY | %s | V: %.2f | Hz: %.2f | Status: %s", substation, voltage, frequency, status));

            archiveToSQL(substation, voltage, frequency, load, status);

            // --- ADVANCED IDS PHYSICS-CHECK LOGIC ---
            // 1. Man-in-the-Middle Check: A is 220V, B is 0V, but Breaker B is CLOSED
            Double volA = latestVoltages.getOrDefault("Substation_A", 220.0);
            Double volB = latestVoltages.getOrDefault("Substation_B", 220.0);

            if (volA > 200.0 && volB < 10.0 && status.equals("CLOSED") && substation.equals("Substation_B")) {
                handleCyberAttack(substation, "MitM / False Data Injection (Physics Mismatch)");
            }
            // 2. Load Manipulation Attack (Subtle increase beyond physics limits)
            else if (load > 1200.0) {
                handleCyberAttack(substation, "Load Manipulation / DoS Overflow");
            }
        } catch (Exception e) {
            log("[PARSE ERROR] " + e.getMessage());
        }
    }

    private void handleCyberAttack(String substation, String attackType) {
        log("\n==================================================");
        log("[!] INTRUSION DETECTED: " + attackType);
        log("[!] TARGET: " + substation);
        log("[!] AUTOMATED DEFENSE: Tripping Breaker & Archiving Event");
        log("==================================================\n");

        sendCommandToPython("SAFE_MODE:" + substation);
        logIncidentToSQL(substation, attackType, "Breaker Tripped / Node Isolated");
    }

    private void archiveToSQL(String sub, double v, double f, double l, String status) {
        new Thread(() -> { // Asynchronous INSERT
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 PreparedStatement pstmt = conn.prepareStatement("INSERT INTO grid_telemetry (substation, voltage, frequency, load_kw, breaker_status) VALUES (?,?,?,?,?)")) {
                pstmt.setString(1, sub); pstmt.setDouble(2, v); pstmt.setDouble(3, f);
                pstmt.setDouble(4, l); pstmt.setString(5, status);
                pstmt.executeUpdate();
            } catch (SQLException ignored) {}
        }).start();
    }

    private void logIncidentToSQL(String sub, String attack, String action) {
        new Thread(() -> {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 PreparedStatement pstmt = conn.prepareStatement("INSERT INTO incident_reports (substation, attack_type, action_taken) VALUES (?,?,?)")) {
                pstmt.setString(1, sub); pstmt.setString(2, attack); pstmt.setString(3, action);
                pstmt.executeUpdate();
            } catch (SQLException e) { log("DB Error: " + e.getMessage()); }
        }).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            BlackoutC2 c2 = new BlackoutC2();
            c2.setVisible(true);
            c2.startServer();
        });
    }
}