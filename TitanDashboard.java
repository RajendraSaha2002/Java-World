import javax.swing.*;
import java.awt.*;
import java.util.*;

public class TitanDashboard extends JFrame {
    private JTextArea logConsole;
    private TacticalGridPanel gridPanel;
    private ThermalGraphPanel thermalPanel;
    private ClusterManager manager;

    public TitanDashboard() {
        setTitle("TITAN-WATCH: HPC C2 Suite");
        setSize(1300, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(15, 20, 25));

        // Center: Tactical Grid
        gridPanel = new TacticalGridPanel();
        gridPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.CYAN), "HPC Cluster Tactical Map"));
        add(gridPanel, BorderLayout.CENTER);

        // Right Panel (Logs & Controls)
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setPreferredSize(new Dimension(400, 800));
        rightPanel.setBackground(new Color(15, 20, 25));

        logConsole = new JTextArea();
        logConsole.setBackground(Color.BLACK);
        logConsole.setForeground(Color.GREEN);
        JScrollPane scroll = new JScrollPane(logConsole);
        scroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.GRAY), "Security Engine Logs"));
        rightPanel.add(scroll, BorderLayout.CENTER);

        JButton killBtn = new JButton("TERMINATE UNAUTHORIZED NODES");
        killBtn.setBackground(Color.RED);
        killBtn.setForeground(Color.WHITE);
        killBtn.addActionListener(e -> {
            manager.killNode("NODE_02"); // Target the attacker
            log(">> KILL COMMAND ISSUED TO NODE_02");
        });
        rightPanel.add(killBtn, BorderLayout.SOUTH);
        add(rightPanel, BorderLayout.EAST);

        // Bottom: Thermal Graph
        thermalPanel = new ThermalGraphPanel();
        thermalPanel.setPreferredSize(new Dimension(1300, 200));
        add(thermalPanel, BorderLayout.SOUTH);

        // Init Engine
        TitanDB db = new TitanDB();
        IntrusionEngine engine = new IntrusionEngine(this, db);
        manager = new ClusterManager(engine);
        manager.startNetwork();
        log("TITAN-WATCH Online. Listening on Port 7070...");
    }

    public void log(String msg) {
        SwingUtilities.invokeLater(() -> logConsole.append(msg + "\n"));
    }

    public void updateNode(String nodeId, int state, double temp, String procId) {
        SwingUtilities.invokeLater(() -> {
            gridPanel.updateState(nodeId, state, temp, procId);
            thermalPanel.addTemp(temp);
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TitanDashboard().setVisible(true));
    }

    // --- NATIVE GRAPHICS2D GRID ---
    class TacticalGridPanel extends JPanel {
        private Map<String, Integer> nodeStates = new HashMap<>();

        public TacticalGridPanel() { setBackground(new Color(10, 15, 20)); }

        public void updateState(String node, int state, double t, String p) {
            nodeStates.put(node, state);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            int size = 100, padding = 20;

            String[] nodes = {"NODE_01", "NODE_02", "NODE_03", "NODE_04"};
            int x = 50;
            for (String node : nodes) {
                int state = nodeStates.getOrDefault(node, 0);
                if (state == 0) g2d.setColor(Color.DARK_GRAY); // Idle
                else if (state == 1) g2d.setColor(Color.BLUE); // Authorized Run
                else g2d.setColor(Color.RED); // ALARM

                g2d.fillRect(x, 50, size, size);
                g2d.setColor(Color.WHITE);
                g2d.drawRect(x, 50, size, size);
                g2d.drawString(node, x + 20, 100);
                x += size + padding;
            }
        }
    }

    // --- NATIVE GRAPHICS2D THERMAL GRAPH ---
    class ThermalGraphPanel extends JPanel {
        private java.util.LinkedList<Double> temps = new java.util.LinkedList<>();

        public ThermalGraphPanel() {
            setBackground(Color.BLACK);
            setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.ORANGE), "Cluster Thermal Heatmap"));
        }

        public void addTemp(double t) {
            temps.add(t);
            if (temps.size() > 100) temps.removeFirst();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            if (temps.size() < 2) return;

            // Alarm Threshold Line
            g2d.setColor(Color.RED);
            g2d.drawLine(0, 50, getWidth(), 50);
            g2d.drawString("CRITICAL THERMAL THRESHOLD (85C)", 10, 45);

            g2d.setColor(Color.ORANGE);
            int step = getWidth() / 100;
            for (int i = 0; i < temps.size() - 1; i++) {
                int x1 = i * step;
                int y1 = getHeight() - (int)(temps.get(i) * 1.5);
                int x2 = (i + 1) * step;
                int y2 = getHeight() - (int)(temps.get(i+1) * 1.5);
                g2d.drawLine(x1, y1, x2, y2);
            }
        }
    }
}