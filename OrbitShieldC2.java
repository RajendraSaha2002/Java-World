import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class OrbitShieldC2 extends JFrame {
    private JTextArea telemetryConsole;
    private JTextArea alertConsole;
    private PrintWriter uplinkStream;

    private static final int PORT = 8888;
    private static final String SECRET_KEY = "ORBIT_TOP_SECRET_HMAC_KEY";

    // DB Credentials
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = "varrie75";

    // Leaky Bucket for DoS Mitigation
    private AtomicInteger packetBucket = new AtomicInteger(0);
    private static final int BUCKET_LIMIT = 20;

    public OrbitShieldC2() {
        setTitle("ORBIT-SHIELD: Mission Control C2");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(new Color(15, 20, 25));
        setLayout(new GridLayout(2, 1));

        // Telemetry Panel
        telemetryConsole = createConsole(Color.CYAN, "Live Satellite Downlink (Telemetry)");
        add(new JScrollPane(telemetryConsole));

        // Alert & Forensics Panel
        alertConsole = createConsole(Color.RED, "Intrusion Detection & Forensics");
        add(new JScrollPane(alertConsole));

        // Start Leaky Bucket Drainer (Drains 5 packets per second)
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(200); } catch (Exception e) {}
                if (packetBucket.get() > 0) packetBucket.decrementAndGet();
            }
        }).start();
    }

    private JTextArea createConsole(Color textColor, String title) {
        JTextArea area = new JTextArea();
        area.setBackground(Color.BLACK);
        area.setForeground(textColor);
        area.setEditable(false);
        area.setFont(new Font("Monospaced", Font.BOLD, 13));
        TitledBorder border = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(textColor), title);
        border.setTitleColor(textColor);
        area.setBorder(border);
        return area;
    }

    private void log(JTextArea console, String msg) {
        SwingUtilities.invokeLater(() -> {
            console.append(msg + "\n");
            console.setCaretPosition(console.getDocument().getLength());
        });
    }

    public void startGroundStation() {
        new Thread(() -> {
            try (ServerSocket server = new ServerSocket(PORT)) {
                log(alertConsole, "[SYSTEM] Ground Station Listening on RF Band (Port " + PORT + ")");
                Socket satellite = server.accept();
                log(alertConsole, "[NETWORK] Orbital Link Established.");

                uplinkStream = new PrintWriter(satellite.getOutputStream(), true);
                BufferedReader downlinkStream = new BufferedReader(new InputStreamReader(satellite.getInputStream()));

                String packet;
                while ((packet = downlinkStream.readLine()) != null) {
                    processDownlink(packet);
                }
            } catch (IOException e) {
                log(alertConsole, "[!] RF LINK LOST: " + e.getMessage());
            }
        }).start();
    }

    private void processDownlink(String json) {
        // 1. SIGNAL DENIAL (DoS) DEFENSE: Leaky Bucket Algorithm
        if (packetBucket.incrementAndGet() > BUCKET_LIMIT) {
            if (packetBucket.get() == BUCKET_LIMIT + 1) { // Log once per spike
                log(alertConsole, "[DEFENSE] SIGNAL DENIAL DETECTED! Dropping flood packets...");
            }
            return; // Drop packet
        }

        String type = parseJson(json, "type");

        if (type.equals("TELEMETRY")) {
            double temp = Double.parseDouble(parseJson(json, "temp"));
            double sun = Double.parseDouble(parseJson(json, "sun"));
            double batt = Double.parseDouble(parseJson(json, "battery"));

            log(telemetryConsole, String.format("Orbit: 1 | Batt: %.1f%% | Temp: %.1fC | Sun: %.1f%%", batt, temp, sun));

            // 2. ANOMALY DETECTION: Sensor Manipulation
            if (temp > 50.0 && sun < 10.0) {
                log(alertConsole, "[!] ANOMALY: High Temp but Low Sun Exposure. Sensor Spoofing Detected!");
            }
            // 3. TELEMETRY HIJACKING: Battery suddenly 0%
            if (batt <= 0.0) {
                log(alertConsole, "[!] CRITICAL: Battery reported at 0%. Verifying... Spoofing suspected.");
            }

            archiveTelemetry(1, 12.0, temp, sun);

        } else if (type.equals("INJECT_CMD")) {
            // 4. PHANTOM COMMAND INJECTION DEFENSE
            String cmd = parseJson(json, "cmd");
            log(alertConsole, "\n[ALERT] Received external request to relay command: " + cmd);

            if (isCommandAuthorized(cmd)) {
                log(alertConsole, "[AUTH] Command verified in SQL Ledger. Generating Cryptographic Signature...");
                sendEncryptedUplink(cmd);
            } else {
                log(alertConsole, "[DEFENSE] PHANTOM COMMAND BLOCKED: '" + cmd + "' is not in the Authorized Mission Plan!");
                logForensic(json, "UNAUTHORIZED_COMMAND");
            }
        }
    }

    private void sendEncryptedUplink(String cmd) {
        try {
            // Cryptographic Command Processor (HMAC-SHA256)
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(cmd.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) hexString.append(String.format("%02x", b));

            String payload = String.format("{\"cmd\":\"%s\", \"signature\":\"%s\"}", cmd, hexString.toString());
            uplinkStream.println(payload);
            log(telemetryConsole, ">> UPLINK SENT: " + cmd + " [SIG: " + hexString.substring(0, 8) + "...]");
        } catch (Exception e) { log(alertConsole, "Crypto Error: " + e.getMessage()); }
    }

    private boolean isCommandAuthorized(String cmd) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM authorized_mission_plan WHERE command_name = ?")) {
            ps.setString(1, cmd);
            return ps.executeQuery().next();
        } catch (SQLException e) { return false; }
    }

    private void archiveTelemetry(int orbit, double v, double t, double s) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement("INSERT INTO telemetry_logs (orbit_num, voltage, temperature, sun_exposure) VALUES (?,?,?,?)")) {
            ps.setInt(1, orbit); ps.setDouble(2, v); ps.setDouble(3, t); ps.setDouble(4, s);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private void logForensic(String packet, String reason) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement("INSERT INTO forensic_logs (packet_data, rejection_reason) VALUES (?,?)")) {
            ps.setString(1, packet); ps.setString(2, reason);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private String parseJson(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start == -1) return "0";
        start += search.length();
        int end = json.indexOf(",", start);
        if (end == -1) end = json.indexOf("}", start);
        return json.substring(start, end).replace("\"", "").trim();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            OrbitShieldC2 c2 = new OrbitShieldC2();
            c2.setVisible(true);
            c2.startGroundStation();
        });
    }
}