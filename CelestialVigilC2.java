import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.net.*;
import java.sql.*;
import java.util.Arrays;

public class CelestialVigilC2 extends JFrame {
    private static final int GCS_PORT = 4444; // Ground Station listening port
    private static final int SAT_PORT = 5555; // Satellite listening port
    private static final String SAT_IP = "127.0.0.1";
    private static final byte[] CRYPTO_KEY = "AEROSPACE_VIGIL_KEY".getBytes();

    // Database
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = "varrie75";

    // UI Components
    private JProgressBar cpuBar, ramBar, latencyBar;
    private JToggleButton encryptionToggle;
    private VectorMapPanel mapPanel;
    private DefaultListModel<AlertRecord> alertModel;
    private DatagramSocket udpSocket;

    // IDS State
    private int expectedSequence = -1;

    public CelestialVigilC2() {
        setTitle("CELESTIAL-VIGIL: Orbital C2 Station");
        setSize(1200, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(new Color(10, 15, 20));
        setLayout(new BorderLayout());

        // --- LEFT COLUMN: GCS Health & Controls ---
        JPanel leftPanel = new JPanel(new GridLayout(6, 1, 10, 10));
        leftPanel.setBackground(new Color(10, 15, 20));
        leftPanel.setBorder(createDarkBorder("Ground Station Health"));

        cpuBar = createHealthBar("CPU Usage");
        ramBar = createHealthBar("RAM Allocation");
        latencyBar = createHealthBar("RF Latency (ms)");

        encryptionToggle = new JToggleButton("ENCRYPTION: ON", true);
        encryptionToggle.setBackground(new Color(0, 100, 0));
        encryptionToggle.setForeground(Color.WHITE);
        encryptionToggle.addChangeListener(e -> {
            if (encryptionToggle.isSelected()) {
                encryptionToggle.setText("ENCRYPTION: ON");
                encryptionToggle.setBackground(new Color(0, 100, 0));
            } else {
                encryptionToggle.setText("ENCRYPTION: OFF (DANGER)");
                encryptionToggle.setBackground(Color.RED);
            }
        });

        leftPanel.add(cpuBar); leftPanel.add(ramBar); leftPanel.add(latencyBar);
        leftPanel.add(new JLabel("")); // Spacer
        leftPanel.add(encryptionToggle);
        add(leftPanel, BorderLayout.WEST);

        // --- CENTER COLUMN: Vector Map ---
        mapPanel = new VectorMapPanel();
        mapPanel.setBorder(createDarkBorder("Orbital Track (Live)"));
        add(mapPanel, BorderLayout.CENTER);

        // --- RIGHT COLUMN: Tactical Alerts & Command ---
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setPreferredSize(new Dimension(350, 700));
        rightPanel.setBackground(new Color(10, 15, 20));
        rightPanel.setBorder(createDarkBorder("Tactical Alerts & Uplink"));

        alertModel = new DefaultListModel<>();
        JList<AlertRecord> alertList = new JList<>(alertModel);
        alertList.setBackground(Color.BLACK);
        alertList.setCellRenderer(new AlertCellRenderer());
        rightPanel.add(new JScrollPane(alertList), BorderLayout.CENTER);

        JPanel cmdPanel = new JPanel(new BorderLayout());
        JTextField cmdInput = new JTextField("DEPLOY_SOLAR_ARRAYS");
        JButton sendBtn = new JButton("UPLINK");
        sendBtn.addActionListener(e -> sendUplinkCommand(cmdInput.getText()));
        cmdPanel.add(cmdInput, BorderLayout.CENTER);
        cmdPanel.add(sendBtn, BorderLayout.EAST);
        rightPanel.add(cmdPanel, BorderLayout.SOUTH);

        add(rightPanel, BorderLayout.EAST);

        simulateHealthBars();
    }

    private JProgressBar createHealthBar(String title) {
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setStringPainted(true);
        bar.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(), title, 0, 0, null, Color.CYAN));
        bar.setForeground(Color.CYAN);
        bar.setBackground(Color.DARK_GRAY);
        return bar;
    }

    private TitledBorder createDarkBorder(String title) {
        TitledBorder b = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.CYAN), title);
        b.setTitleColor(Color.CYAN);
        return b;
    }

    private void addAlert(String msg, int severity) {
        SwingUtilities.invokeLater(() -> {
            alertModel.add(0, new AlertRecord(msg, severity));
            if (alertModel.size() > 50) alertModel.removeElementAt(50);
        });
    }

    // --- NETWORKING & IDS LOGIC (UDP) ---
    public void startRFLink() {
        new Thread(() -> {
            try {
                udpSocket = new DatagramSocket(GCS_PORT);
                addAlert("UDP RF Link Established on Port 4444", 0);
                byte[] buffer = new byte[1024];

                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpSocket.receive(packet);
                    String payload = new String(packet.getData(), 0, packet.getLength());
                    processDownlink(payload, packet.getAddress().getHostAddress());
                }
            } catch (Exception e) {
                addAlert("RF LINK FAILURE: " + e.getMessage(), 2);
            }
        }).start();
    }

    private void processDownlink(String json, String sourceIp) {
        try {
            // 1. Bit-Flip / Corruption IDS
            if (!json.startsWith("{") || !json.endsWith("}")) {
                throw new Exception("Malformed JSON structure");
            }

            int seq = Integer.parseInt(parseJson(json, "seq"));
            double lat = Double.parseDouble(parseJson(json, "lat"));
            double lon = Double.parseDouble(parseJson(json, "lon"));
            double alt = Double.parseDouble(parseJson(json, "alt"));

            // 2. Signal Jamming / Packet Drop IDS
            if (expectedSequence != -1 && seq > expectedSequence) {
                int dropped = seq - expectedSequence;
                addAlert("SIGNAL JAMMING: " + dropped + " packets dropped in RF link!", 1);
            }
            expectedSequence = seq + 1;

            // Update UI Map
            mapPanel.updateSatellitePos(lat, lon);
            archiveTelemetry(seq, lat, lon, alt, 95.0, -50);

        } catch (Exception e) {
            // Fired by Adversary Bit-Flip Injection or Rogue UDP Packets
            addAlert("BIT-FLIP / MALFORMED PACKET DETECTED!", 2);
            String hexDump = bytesToHex(json.getBytes());
            archiveHexDump("RF_CORRUPTION_OR_INJECTION", hexDump, sourceIp);
        }
    }

    private void sendUplinkCommand(String cmd) {
        new Thread(() -> {
            try {
                boolean isEncrypted = encryptionToggle.isSelected();
                String payload = "{\"cmd\":\"" + cmd + "\",\"crypto\":\"" + (isEncrypted ? "XOR" : "NONE") + "\"}";
                byte[] data = payload.getBytes();

                // Custom XOR Stream Cipher
                if (isEncrypted) {
                    for (int i = 0; i < data.length; i++) {
                        data[i] = (byte) (data[i] ^ CRYPTO_KEY[i % CRYPTO_KEY.length]);
                    }
                }

                DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(SAT_IP), SAT_PORT);
                udpSocket.send(packet);

                logUplink(cmd, isEncrypted ? "ENCRYPTED" : "PLAINTEXT", "SENT");
                addAlert("Uplink Sent: " + cmd, 0);

            } catch (Exception e) { addAlert("Uplink Failed", 2); }
        }).start();
    }

    // --- UTILS & DB ---
    private String parseJson(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start == -1) return "0";
        start += search.length();
        int end = json.indexOf(",", start);
        if (end == -1) end = json.indexOf("}", start);
        return json.substring(start, end).trim().replace("\"", "");
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X ", b));
        return sb.toString();
    }

    private void archiveTelemetry(int seq, double lat, double lon, double alt, double batt, int rssi) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement("INSERT INTO celestial_telemetry (sequence_num, latitude, longitude, altitude_km, battery_pct, signal_rssi) VALUES (?,?,?,?,?,?)")) {
            ps.setInt(1, seq); ps.setDouble(2, lat); ps.setDouble(3, lon); ps.setDouble(4, alt); ps.setDouble(5, batt); ps.setInt(6, rssi);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private void archiveHexDump(String attack, String hex, String ip) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement("INSERT INTO anomaly_hex_archive (attack_classification, raw_hex_dump, source_ip) VALUES (?,?,?)")) {
            ps.setString(1, attack); ps.setString(2, hex); ps.setString(3, ip);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private void logUplink(String cmd, String crypto, String res) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement("INSERT INTO c2_uplink_ledger (command_raw, crypto_status, satellite_response) VALUES (?,?,?)")) {
            ps.setString(1, cmd); ps.setString(2, crypto); ps.setString(3, res);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private void simulateHealthBars() {
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(1000); } catch (Exception e) {}
                SwingUtilities.invokeLater(() -> {
                    cpuBar.setValue((int)(Math.random() * 20) + 10);
                    ramBar.setValue(45);
                    latencyBar.setValue((int)(Math.random() * 50) + 150);
                });
            }
        }).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            CelestialVigilC2 c2 = new CelestialVigilC2();
            c2.setVisible(true);
            c2.startRFLink();
        });
    }

    // --- ADVANCED UI CLASSES ---
    class VectorMapPanel extends JPanel {
        private double satLat = 0, satLon = 0;
        public void updateSatellitePos(double lat, double lon) {
            this.satLat = lat; this.satLon = lon;
            repaint();
        }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setColor(new Color(0, 20, 50)); // Ocean
            g2d.fillRect(0, 0, getWidth(), getHeight());

            g2d.setColor(new Color(0, 80, 0)); // Continents (Abstract)
            g2d.fillRoundRect(50, 50, 150, 100, 20, 20); // NA
            g2d.fillRoundRect(300, 80, 200, 150, 30, 30); // Eurasia

            g2d.setColor(new Color(100, 100, 100, 100)); // Orbit Path (Sine Wave)
            for(int i=0; i<getWidth(); i+=5) g2d.drawOval(i, (int)(getHeight()/2 + Math.sin(i*0.02)*100), 2, 2);

            // Map lat/lon (-90 to 90, -180 to 180) to screen X/Y
            int x = (int) (((satLon + 180.0) / 360.0) * getWidth());
            int y = (int) (((-satLat + 90.0) / 180.0) * getHeight());

            g2d.setColor(Color.RED);
            g2d.fillOval(x-5, y-5, 10, 10);
            g2d.setColor(Color.WHITE);
            g2d.drawString("SAT-1", x+10, y+5);
        }
    }

    class AlertRecord { String msg; int severity; AlertRecord(String m, int s) {msg=m; severity=s;} }

    class AlertCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            AlertRecord rec = (AlertRecord) value;
            setText(" >> " + rec.msg);
            if (rec.severity == 2) setForeground(Color.RED);
            else if (rec.severity == 1) setForeground(Color.ORANGE);
            else setForeground(Color.CYAN);
            return c;
        }
    }
}