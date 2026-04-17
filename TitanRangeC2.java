import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.sql.*;

public class TitanRangeC2 extends JFrame {
    private static final int PORT = 7777;
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = "varrie75";

    private JTextPane sqlVisualizer;
    private JTextArea trafficLog;
    private JToggleButton defconShield;

    public TitanRangeC2() {
        setTitle("TITAN-RANGE: C2 Defense Simulator");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(new Color(20, 20, 25));

        // --- TOP: SQL Execution Monitor ---
        sqlVisualizer = new JTextPane();
        sqlVisualizer.setBackground(Color.BLACK);
        sqlVisualizer.setForeground(Color.RED);
        sqlVisualizer.setFont(new Font("Consolas", Font.BOLD, 18));
        sqlVisualizer.setEditable(false);
        JScrollPane sqlScroll = new JScrollPane(sqlVisualizer);
        sqlScroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.RED), "LIVE SQL EXECUTION MONITOR (VULNERABILITY VIEWER)"));

        // --- BOTTOM LEFT: Live Traffic Feed ---
        trafficLog = new JTextArea();
        trafficLog.setBackground(new Color(10, 10, 10));
        trafficLog.setForeground(Color.GREEN);
        trafficLog.setEditable(false);
        trafficLog.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JScrollPane trafficScroll = new JScrollPane(trafficLog);
        trafficScroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.CYAN), "Incoming Game API Traffic"));

        // --- BOTTOM RIGHT: Control Panel ---
        JPanel controlPanel = new JPanel(new GridLayout(3, 1, 10, 10));
        controlPanel.setBackground(new Color(20, 20, 25));
        controlPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.ORANGE), "C2 Defense Controls"));

        defconShield = new JToggleButton("DEFCON SHIELD: OFF (VULNERABLE)", false);
        defconShield.setBackground(new Color(150, 0, 0));
        defconShield.setForeground(Color.WHITE);
        defconShield.setFont(new Font("Arial", Font.BOLD, 16));
        defconShield.addChangeListener(e -> {
            if (defconShield.isSelected()) {
                defconShield.setText("DEFCON SHIELD: ON (SECURE)");
                defconShield.setBackground(new Color(0, 100, 0));
                sqlVisualizer.setForeground(Color.CYAN);
            } else {
                defconShield.setText("DEFCON SHIELD: OFF (VULNERABLE)");
                defconShield.setBackground(new Color(150, 0, 0));
                sqlVisualizer.setForeground(Color.RED);
            }
        });
        controlPanel.add(new JLabel("Toggle to switch between String Concatenation and PreparedStatements:", SwingConstants.CENTER));
        controlPanel.add(defconShield);

        // --- LAYOUT ASSEMBLY ---
        JSplitPane bottomSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, trafficScroll, controlPanel);
        bottomSplit.setDividerLocation(600);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, sqlScroll, bottomSplit);
        mainSplit.setDividerLocation(250);

        add(mainSplit);
    }

    private void updateSQLVisualizer(String sql) {
        SwingUtilities.invokeLater(() -> {
            sqlVisualizer.setText("\n>> EXECUTING QUERY AGAINST POSTGRESQL:\n\n" + sql + "\n");
        });
    }

    private void logTraffic(String msg) {
        SwingUtilities.invokeLater(() -> {
            trafficLog.append(msg + "\n");
            trafficLog.setCaretPosition(trafficLog.getDocument().getLength());
        });
    }

    public void startServer() {
        new Thread(() -> {
            try (ServerSocket server = new ServerSocket(PORT)) {
                logTraffic("[SYSTEM] C2 Server Listening on Port " + PORT);
                while (true) {
                    Socket client = server.accept();
                    BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                    String payload;
                    while ((payload = in.readLine()) != null) {
                        processGameApiRequest(payload);
                    }
                }
            } catch (IOException e) { logTraffic("[ERROR] " + e.getMessage()); }
        }).start();
    }

    // Manual string parsing to strictly avoid external JSON libraries
    private String parseField(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start == -1) return "";
        start += search.length();
        int end = json.indexOf("\"", start + 1);
        if (end == -1) return "";
        start = json.indexOf("\"", start) + 1;
        return json.substring(start, end);
    }

    private void processGameApiRequest(String json) {
        logTraffic("\n[RCV] " + json);
        String action = parseField(json, "action");
        String user = parseField(json, "username");

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            boolean isSecure = defconShield.isSelected();

            if (action.equals("ADMIN_LOGIN")) {
                String pass = parseField(json, "password");

                if (!isSecure) {
                    // VULNERABLE PATH: Classic SQLi Auth Bypass
                    String sql = "SELECT * FROM fps_players WHERE username = '" + user + "' AND pass_hash = '" + pass + "'";
                    updateSQLVisualizer(sql);

                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(sql);
                    if (rs.next()) logTraffic(">>> LOGIN SUCCESS! Welcome, " + rs.getString("username"));
                    else logTraffic(">>> LOGIN FAILED.");
                } else {
                    // SECURE PATH: PreparedStatement prevents Auth Bypass
                    String sql = "SELECT * FROM fps_players WHERE username = ? AND pass_hash = ?";
                    updateSQLVisualizer("PreparedStatement: " + sql + "\nParams: [1]='" + user + "', [2]='" + pass + "'");

                    PreparedStatement pstmt = conn.prepareStatement(sql);
                    pstmt.setString(1, user);
                    pstmt.setString(2, pass);
                    ResultSet rs = pstmt.executeQuery();
                    if (rs.next()) logTraffic(">>> LOGIN SUCCESS! Welcome, " + rs.getString("username"));
                    else logTraffic(">>> LOGIN FAILED. SQLi Blocked!");
                }
            }
            else if (action.equals("UPDATE_PROFILE")) {
                String msg = parseField(json, "msg");

                if (!isSecure) {
                    // VULNERABLE PATH: Stacked Query Injection (Economy Manipulation)
                    // Note: We use .execute() to allow chained statements separated by semicolons
                    String sql = "UPDATE fps_players SET profile_msg = '" + msg + "' WHERE username = '" + user + "'";
                    updateSQLVisualizer(sql);

                    Statement stmt = conn.createStatement();
                    stmt.execute(sql);
                    logTraffic(">>> PROFILE UPDATED (WARNING: Vulnerable to Stacked Queries)");
                } else {
                    // SECURE PATH: PreparedStatement treats the entire payload as a literal string
                    String sql = "UPDATE fps_players SET profile_msg = ? WHERE username = ?";
                    updateSQLVisualizer("PreparedStatement: " + sql + "\nParams: [1]='" + msg + "', [2]='" + user + "'");

                    PreparedStatement pstmt = conn.prepareStatement(sql);
                    pstmt.setString(1, msg);
                    pstmt.setString(2, user);
                    pstmt.executeUpdate();
                    logTraffic(">>> PROFILE UPDATED SAFELY.");
                }
            }
        } catch (SQLException e) {
            logTraffic(">>> DATABASE EXCEPTION: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            TitanRangeC2 c2 = new TitanRangeC2();
            c2.setVisible(true);
            c2.startServer();
        });
    }
}