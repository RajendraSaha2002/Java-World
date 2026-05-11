import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.sql.*;

public class OrbitDockerC2 extends JFrame {
    private static final int PORT = 9090;
    // Connects using Docker DNS hostname
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/orbit_db";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = "sharma30@";

    private OrbitTrackerPanel orbitTracker;
    private DefaultTableModel auditTableModel;
    private JTextArea cryptoConsole;

    // State for IDS
    private double lastLat = 0, lastLon = 0;

    public OrbitDockerC2() {
        setTitle("ORBIT-DOCKER: Mission Control");
        setSize(1200, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(new Color(15, 20, 25));
        setLayout(new BorderLayout());

        // --- TOP: Health & Crypto Console ---
        JPanel topPanel = new JPanel(new GridLayout(1, 2));

        JPanel cryptoPanel = new JPanel(new BorderLayout());
        cryptoPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.CYAN), "Cryptographic Command Console"));
        cryptoPanel.setBackground(new Color(15, 20, 25));

        cryptoConsole = new JTextArea();
        cryptoConsole.setBackground(Color.BLACK);
        cryptoConsole.setForeground(Color.GREEN);
        cryptoConsole.setEditable(false);
        cryptoPanel.add(new JScrollPane(cryptoConsole), BorderLayout.CENTER);

        JTextField cmdInput = new JTextField("ADCS_CALIBRATE");
        cmdInput.setBackground(Color.DARK_GRAY);
        cmdInput.setForeground(Color.WHITE);
        cmdInput.addActionListener(e -> sendCommand(cmdInput.getText()));
        cryptoPanel.add(cmdInput, BorderLayout.SOUTH);

        topPanel.add(cryptoPanel);
        add(topPanel, BorderLayout.NORTH);

        // --- CENTER: Live Orbit Tracker ---
        orbitTracker = new OrbitTrackerPanel();
        orbitTracker.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.GRAY), "Live Orbit Tracker (Graphics2D)"));
        add(orbitTracker, BorderLayout.CENTER);

        // --- BOTTOM: Security Triage Panel ---
        String[] cols = {"Timestamp", "Threat Type", "Details"};
        auditTableModel = new DefaultTableModel(cols, 0);
        JTable auditTable = new JTable(auditTableModel);
        auditTable.setBackground(Color.BLACK);
        auditTable.setForeground(Color.RED);
        JScrollPane scroll = new JScrollPane(auditTable);
        scroll.setPreferredSize(new Dimension(1200, 200));
        scroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.RED), "Security Triage Panel"));
        add(scroll, BorderLayout.SOUTH);
    }

    private void logAudit(String threat, String details) {
        SwingUtilities.invokeLater(() -> {
            String time = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
            auditTableModel.insertRow(0, new Object[]{time, threat, details});
        });
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement("INSERT INTO security_audits (threat_type, details) VALUES (?, ?)")) {
            ps.setString(1, threat); ps.setString(2, details); ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public void startServer() {
        new Thread(() -> {
            try (ServerSocket server = new ServerSocket(PORT)) {
                logAudit("SYSTEM", "C2 Listening on Docker Network Port " + PORT);
                while (true) {
                    Socket client = server.accept();
                    new Thread(() -> handleTelemetry(client)).start();
                }
            } catch (IOException e) { logAudit("ERROR", e.getMessage()); }
        }).start();
    }

    private void handleTelemetry(Socket client) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()))) {
            String payload;
            while ((payload = in.readLine()) != null) {
                double lat = Double.parseDouble(parseJson(payload, "lat"));
                double lon = Double.parseDouble(parseJson(payload, "lon"));

                // IDS: TLE Spoofing Detection (Impossible Distance Jump)
                double distance = Math.hypot(lat - lastLat, lon - lastLon);
                boolean isSpoofed = distance > 15.0 && lastLat != 0;

                if (isSpoofed) {
                    logAudit("TLE_SPOOFING", "Impossible velocity detected! Jump of " + distance + " units.");
                }

                orbitTracker.updatePosition(lat, lon, isSpoofed);
                lastLat = lat; lastLon = lon;
            }
        } catch (Exception ignored) {}
    }

    private void sendCommand(String rawCmd) {
        String encrypted = encrypt(rawCmd);
        cryptoConsole.append("RAW: " + rawCmd + "\n");
        cryptoConsole.append("ENCRYPTED: " + encrypted + "\n\n");

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement("INSERT INTO command_history (raw_command, encrypted_payload) VALUES (?, ?)")) {
            ps.setString(1, rawCmd); ps.setString(2, encrypted); ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private String encrypt(String data) {
        // Native simulated XOR crypto
        byte[] bytes = data.getBytes();
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte)(bytes[i] ^ 0x5A);
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) hex.append(String.format("%02X", b));
        return hex.toString();
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
            OrbitDockerC2 c2 = new OrbitDockerC2();
            c2.setVisible(true);
            c2.startServer();
        });
    }

    // --- LIVE ORBIT TRACKER GRAPHICS ---
    class OrbitTrackerPanel extends JPanel {
        private double satLat = 0, satLon = 0;
        private boolean underAttack = false;

        public OrbitTrackerPanel() { setBackground(new Color(10, 15, 20)); }

        public void updatePosition(double lat, double lon, boolean attacked) {
            this.satLat = lat; this.satLon = lon; this.underAttack = attacked;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;

            // Draw Equator & Prime Meridian
            g2d.setColor(Color.DARK_GRAY);
            g2d.drawLine(0, getHeight()/2, getWidth(), getHeight()/2);
            g2d.drawLine(getWidth()/2, 0, getWidth()/2, getHeight());

            // Map Lat/Lon to Screen
            int x = (int) (((satLon + 180) / 360) * getWidth());
            int y = (int) (((-satLat + 90) / 180) * getHeight());

            if (underAttack) {
                g2d.setColor(Color.ORANGE); // Neon Orange for TLE Spoofing
                g2d.fillOval(x - 15, y - 15, 30, 30);
                g2d.drawString("SPOOFING DETECTED!", x + 20, y);
            } else {
                g2d.setColor(Color.CYAN);
                g2d.fillOval(x - 5, y - 5, 10, 10);
                g2d.drawString("SAT-1", x + 15, y);
            }
        }
    }
}