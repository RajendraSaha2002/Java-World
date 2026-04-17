import javax.swing.SwingUtilities;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServerEngine {
    private static final int TELEMETRY_PORT = 7001;

    private static final String DB_URL = "jdbc:postgresql://127.0.0.1:5432/titan_shield";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = "varrie75";

    private static final Map<String, ClientSession> CLIENTS = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        startTelemetryListener();
        SwingUtilities.invokeLater(() -> {
            C2Dashboard dashboard = new C2Dashboard();
            dashboard.setVisible(true);
        });
    }

    private static void startTelemetryListener() {
        Thread serverThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(TELEMETRY_PORT)) {
                System.out.println("ServerEngine listening on " + TELEMETRY_PORT);
                while (true) {
                    Socket socket = serverSocket.accept();
                    ClientSession session = new ClientSession(socket);
                    new Thread(() -> handleClient(session), "client-" + socket.getRemoteSocketAddress()).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, "telemetry-listener");

        serverThread.setDaemon(true);
        serverThread.start();
    }

    private static void handleClient(ClientSession session) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(session.socket.getInputStream()));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(session.socket.getOutputStream()), true)) {

            session.writer = out;

            String line;
            while ((line = in.readLine()) != null) {
                processMessage(session, line);
            }
        } catch (IOException e) {
            System.out.println("Client disconnected: " + session.source);
        } finally {
            if (session.source != null) {
                CLIENTS.remove(session.source);
            }
            try {
                session.socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void processMessage(ClientSession session, String json) {
        String type = extractString(json, "type");
        String source = extractString(json, "source");
        String assetKey = extractString(json, "asset_key");

        if (source != null && !source.isBlank()) {
            session.source = source;
            CLIENTS.put(source, session);
        }

        if ("hello".equalsIgnoreCase(type)) {
            System.out.println("HELLO from " + source + " / " + assetKey);
            return;
        }

        if ("telemetry".equalsIgnoreCase(type)) {
            Double tempC = extractDouble(json, "temp_c");
            Double humidity = extractDouble(json, "humidity");
            Double voltageV = extractDouble(json, "voltage_v");
            Double loadPct = extractDouble(json, "load_pct");

            String status = SecurityLogic.assessTelemetry(assetKey, tempC, humidity, voltageV, loadPct);
            if ("spoofed".equalsIgnoreCase(extractString(json, "mode"))) {
                status = "SPOOFED";
            }

            insertTelemetry(assetKey, tempC, humidity, voltageV, loadPct, status, json);

            if (!"OK".equals(status)) {
                System.out.println("ALERT: " + assetKey + " -> " + status);
            }
            return;
        }

        if ("auth_attempt".equalsIgnoreCase(type)) {
            String username = extractString(json, "username");
            boolean flagged = SecurityLogic.isSuspiciousInput(username) || SecurityLogic.isSuspiciousInput(json);
            insertAuthEvent(username == null ? "unknown" : username, source, "login_probe", flagged, json);

            if (flagged) {
                System.out.println("ALERT: suspicious auth input from " + source + " username=" + username);
            }
        }
    }

    private static void insertTelemetry(String assetKey, Double tempC, Double humidity, Double voltageV,
                                        Double loadPct, String status, String rawJson) {
        String sql = """
                INSERT INTO telemetry_logs(asset_key, temp_c, humidity, voltage_v, load_pct, status, raw_json)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, assetKey);
            if (tempC == null) ps.setNull(2, Types.NUMERIC); else ps.setDouble(2, tempC);
            if (humidity == null) ps.setNull(3, Types.NUMERIC); else ps.setDouble(3, humidity);
            if (voltageV == null) ps.setNull(4, Types.NUMERIC); else ps.setDouble(4, voltageV);
            if (loadPct == null) ps.setNull(5, Types.NUMERIC); else ps.setDouble(5, loadPct);
            ps.setString(6, status);
            ps.setString(7, rawJson);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void insertAuthEvent(String username, String source, String eventType, boolean flagged, String rawJson) {
        String sql = """
                INSERT INTO auth_events(username, source, event_type, flagged, raw_json)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, source);
            ps.setString(3, eventType);
            ps.setBoolean(4, flagged);
            ps.setString(5, rawJson);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void broadcastCommand(String action) {
        String json = String.format("{\"type\":\"command\",\"action\":\"%s\"}", action);
        for (ClientSession session : CLIENTS.values()) {
            try {
                if (session.writer != null) {
                    session.writer.println(json);
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static ResultSetSnapshot fetchLatestSnapshot() {
        String sql = """
                SELECT h.asset_key, h.asset_type, h.rack_label,
                       t.temp_c, t.humidity, t.voltage_v, t.load_pct, t.status, t.received_at
                FROM hardware_assets h
                LEFT JOIN LATERAL (
                    SELECT temp_c, humidity, voltage_v, load_pct, status, received_at
                    FROM telemetry_logs tl
                    WHERE tl.asset_key = h.asset_key
                    ORDER BY tl.received_at DESC
                    LIMIT 1
                ) t ON TRUE
                ORDER BY h.rack_label, h.asset_key
                """;

        ResultSetSnapshot snapshot = new ResultSetSnapshot();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                snapshot.add(new RackRow(
                        rs.getString("asset_key"),
                        rs.getString("asset_type"),
                        rs.getString("rack_label"),
                        rs.getObject("temp_c"),
                        rs.getObject("humidity"),
                        rs.getObject("voltage_v"),
                        rs.getObject("load_pct"),
                        rs.getString("status")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return snapshot;
    }

    private static String extractString(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"(.*?)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static Double extractDouble(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
        Matcher m = p.matcher(json);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    static class ClientSession {
        final Socket socket;
        volatile String source;
        volatile PrintWriter writer;

        ClientSession(Socket socket) {
            this.socket = socket;
        }
    }

    public static class RackRow {
        public final String assetKey;
        public final String assetType;
        public final String rackLabel;
        public final Object tempC;
        public final Object humidity;
        public final Object voltageV;
        public final Object loadPct;
        public final String status;

        public RackRow(String assetKey, String assetType, String rackLabel,
                       Object tempC, Object humidity, Object voltageV, Object loadPct, String status) {
            this.assetKey = assetKey;
            this.assetType = assetType;
            this.rackLabel = rackLabel;
            this.tempC = tempC;
            this.humidity = humidity;
            this.voltageV = voltageV;
            this.loadPct = loadPct;
            this.status = status;
        }
    }

    public static class ResultSetSnapshot {
        private final java.util.List<RackRow> rows = new java.util.ArrayList<>();
        public void add(RackRow row) { rows.add(row); }
        public java.util.List<RackRow> rows() { return rows; }
    }
}