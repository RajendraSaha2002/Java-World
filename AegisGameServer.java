import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Observer Pattern Interface
interface ServerObserver {
    void onLog(String message, boolean isThreat);
    void onThreatSpike(int level);
}

public class AegisGameServer extends JFrame implements ServerObserver {
    private JTextArea console;
    private JProgressBar threatMeter;
    private int currentThreatLevel = 0;
    private static final int PORT = 9999;

    private static final String DB_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = "varrie75";

    // In-Memory Game State & Rate Limiting
    private Map<String, Long> lastActionTime = new ConcurrentHashMap<>();
    private Map<String, Point.Double> playerPositions = new ConcurrentHashMap<>();

    public AegisGameServer() {
        setTitle("AEGIS-GAMENET Authoritative Server");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(new Color(20, 20, 20));
        setLayout(new BorderLayout());

        console = new JTextArea();
        console.setBackground(Color.BLACK);
        console.setForeground(Color.GREEN);
        console.setEditable(false);
        console.setFont(new Font("Monospaced", Font.PLAIN, 13));
        add(new JScrollPane(console), BorderLayout.CENTER);

        // Native Threat Meter
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(new Color(20, 20, 20));
        threatMeter = new JProgressBar(0, 100);
        threatMeter.setValue(0);
        threatMeter.setStringPainted(true);
        threatMeter.setForeground(Color.RED);
        TitledBorder border = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.RED), "DDoS & Attack Threat Level");
        border.setTitleColor(Color.RED);
        topPanel.setBorder(border);
        topPanel.add(threatMeter, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        // Control Panel
        JPanel bottomPanel = new JPanel();
        bottomPanel.setBackground(new Color(20, 20, 20));
        JButton btnShadowBan = new JButton("MANUAL SHADOW BAN (Hacker)");
        btnShadowBan.setBackground(Color.DARK_GRAY);
        btnShadowBan.setForeground(Color.WHITE);
        btnShadowBan.addActionListener(e -> applyShadowBan("Player_Hacker", "Manual Admin Override"));
        bottomPanel.add(btnShadowBan);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    // Observer Pattern Implementation
    @Override
    public void onLog(String message, boolean isThreat) {
        SwingUtilities.invokeLater(() -> {
            if (isThreat) console.append("[!] " + message + "\n");
            else console.append(message + "\n");
            console.setCaretPosition(console.getDocument().getLength());
        });
    }

    @Override
    public void onThreatSpike(int level) {
        SwingUtilities.invokeLater(() -> {
            currentThreatLevel = Math.min(100, currentThreatLevel + level);
            threatMeter.setValue(currentThreatLevel);
            if (currentThreatLevel > 75) threatMeter.setString("CRITICAL THREAT DETECTED");
            else threatMeter.setString("Threat Level: " + currentThreatLevel + "%");
        });
    }

    // Network Engine
    public void startServer() {
        new Thread(() -> {
            // Decay threat level slowly over time
            new Thread(() -> {
                while (true) {
                    try { Thread.sleep(2000); } catch (Exception e) {}
                    if (currentThreatLevel > 0) onThreatSpike(-5);
                }
            }).start();

            try (ServerSocket server = new ServerSocket(PORT)) {
                onLog("AEGIS SERVER ONLINE. Port: " + PORT, false);
                while (true) {
                    Socket client = server.accept();
                    String ip = client.getInetAddress().getHostAddress();

                    if (isIpBlacklisted(ip)) {
                        onLog("Connection dropped from Blacklisted IP: " + ip, true);
                        client.close();
                        continue;
                    }
                    new Thread(() -> handlePacket(client, ip)).start();
                }
            } catch (IOException e) {
                onLog("Server Crash: " + e.getMessage(), true);
            }
        }).start();
    }

    private boolean isIpBlacklisted(String ip) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM ip_blacklist WHERE ip_address = ?")) {
            ps.setString(1, ip);
            return ps.executeQuery().next();
        } catch (SQLException e) { return false; }
    }

    private void handlePacket(Socket client, String ip) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()))) {
            String payload = in.readLine();
            if (payload == null) return;

            // DDoS Flood detection hook
            if (payload.contains("HANDSHAKE_FLOOD")) {
                onThreatSpike(10);
                return;
            }

            // Manual JSON parsing constraint
            String playerId = parseJson(payload, "player_id");
            String action = parseJson(payload, "action");

            // --- ANTI-CHEAT AUTHORITY LOGIC ---
            long now = System.currentTimeMillis();
            long lastTime = lastActionTime.getOrDefault(playerId, 0L);

            // 1. Botting/Rate Limit Check (Actions too fast)
            if (now - lastTime < 100) {
                onLog("BOTTING DETECTED: " + playerId + " exceeding rate limits.", true);
                onThreatSpike(5);
                blacklistIp(ip, "Botting/Spamming API");
                return;
            }
            lastActionTime.put(playerId, now);

            // 2. Physics/Teleport Hack Check
            if (action.equals("MOVE")) {
                double newX = Double.parseDouble(parseJson(payload, "x"));
                double newY = Double.parseDouble(parseJson(payload, "y"));

                Point.Double oldPos = playerPositions.getOrDefault(playerId, new Point.Double(0, 0));
                double distance = Math.hypot(newX - oldPos.x, newY - oldPos.y);

                if (distance > 20.0) { // Max speed is 20 units
                    onLog("TELEPORT HACK BLOCKED: " + playerId + " moved " + String.format("%.2f", distance) + " units!", true);
                    onThreatSpike(15);
                    applyShadowBan(playerId, "Physics Violation - Teleporting");
                    return; // Reject packet
                }
                playerPositions.put(playerId, new Point.Double(newX, newY));
                updatePlayerPositionInDb(playerId, newX, newY);
                onLog("Player " + playerId + " moved to (" + newX + ", " + newY + ")", false);
            }

            // 3. Economy Hack (Memory Injection simulation)
            if (action.equals("INJECT_GEMS")) {
                int gems = Integer.parseInt(parseJson(payload, "amount"));
                injectGemsDb(playerId, gems); // The PostgreSQL trigger will catch this!
                onLog("Player " + playerId + " attempted to inject " + gems + " gems.", true);
            }

        } catch (Exception e) {
            // Ignore broken sockets
        }
    }

    private String parseJson(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start == -1) return "";
        start += search.length();
        int end = json.indexOf(",", start);
        if (end == -1) end = json.indexOf("}", start);
        return json.substring(start, end).replace("\"", "").trim();
    }

    private void applyShadowBan(String playerId, String reason) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement("UPDATE players SET status = 'SHADOW_BANNED' WHERE player_id = ?")) {
            ps.setString(1, playerId);
            ps.executeUpdate();
            onLog(">> SHADOW BAN APPLIED to " + playerId + " (" + reason + ")", true);
        } catch (SQLException ignored) {}
    }

    private void blacklistIp(String ip, String reason) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement("INSERT INTO ip_blacklist (ip_address, reason) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
            ps.setString(1, ip); ps.setString(2, reason);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private void updatePlayerPositionInDb(String pId, double x, double y) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement("UPDATE players SET x_pos=?, y_pos=? WHERE player_id=? AND status='ACTIVE'")) {
            ps.setDouble(1, x); ps.setDouble(2, y); ps.setString(3, pId);
            ps.executeUpdate(); // Won't update if SHADOW_BANNED
        } catch (SQLException ignored) {}
    }

    private void injectGemsDb(String pId, int amt) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement("UPDATE players SET gems = gems + ? WHERE player_id=?")) {
            ps.setInt(1, amt); ps.setString(2, pId);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            AegisGameServer server = new AegisGameServer();
            server.setVisible(true);
            server.startServer();
        });
    }
}