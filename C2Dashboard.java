import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.Map;

public class C2Dashboard extends JFrame {
    private final DefaultTableModel model;
    private final HeatmapPanel heatmapPanel;
    private final JLabel statusLabel;

    public C2Dashboard() {
        super("TITAN-SHIELD C2 Dashboard");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);

        model = new DefaultTableModel(
                new Object[]{"Asset Key", "Type", "Rack", "Temp C", "Humidity", "Voltage V", "Load %", "Status"}, 0
        );

        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);

        heatmapPanel = new HeatmapPanel();
        heatmapPanel.setPreferredSize(new Dimension(500, 500));

        JButton lockdown = new JButton(new AbstractAction("LOCKDOWN MODE") {
            @Override
            public void actionPerformed(ActionEvent e) {
                int confirm = JOptionPane.showConfirmDialog(
                        C2Dashboard.this,
                        "Send SAFE_MODE to all active nodes?",
                        "Confirm Lockdown",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );
                if (confirm == JOptionPane.YES_OPTION) {
                    ServerEngine.broadcastCommand("SAFE_MODE");
                    statusLabel.setText("Lockdown command sent.");
                }
            }
        });

        JButton normal = new JButton(new AbstractAction("NORMAL MODE") {
            @Override
            public void actionPerformed(ActionEvent e) {
                ServerEngine.broadcastCommand("NORMAL");
                statusLabel.setText("Normal-mode command sent.");
            }
        });

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topBar.add(lockdown);
        topBar.add(normal);

        statusLabel = new JLabel("System ready.");

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(heatmapPanel),
                new JScrollPane(table));
        splitPane.setResizeWeight(0.45);

        add(topBar, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        Timer timer = new Timer(1000, e -> refresh());
        timer.start();
        refresh();
    }

    private void refresh() {
        model.setRowCount(0);

        ServerEngine.ResultSetSnapshot snapshot = ServerEngine.fetchLatestSnapshot();
        Map<String, HeatmapPanel.RackVisual> visuals = new HashMap<>();

        for (ServerEngine.RackRow row : snapshot.rows()) {
            model.addRow(new Object[]{
                    row.assetKey, row.assetType, row.rackLabel, row.tempC, row.humidity, row.voltageV, row.loadPct, row.status
            });

            double temp = row.tempC instanceof Number ? ((Number) row.tempC).doubleValue() : 0.0;
            String status = row.status == null ? "UNKNOWN" : row.status;
            visuals.put(row.assetKey, new HeatmapPanel.RackVisual(row.assetKey, row.rackLabel, temp, status));
        }

        heatmapPanel.setVisuals(visuals);
        heatmapPanel.repaint();
    }

    static class HeatmapPanel extends JPanel {
        static class RackVisual {
            final String assetKey;
            final String rackLabel;
            final double tempC;
            final String status;

            RackVisual(String assetKey, String rackLabel, double tempC, String status) {
                this.assetKey = assetKey;
                this.rackLabel = rackLabel;
                this.tempC = tempC;
                this.status = status;
            }
        }

        private Map<String, RackVisual> visuals = new HashMap<>();

        public void setVisuals(Map<String, RackVisual> visuals) {
            this.visuals = visuals;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int x = 30;
            int y = 30;
            int w = 120;
            int h = 80;
            int gapX = 150;
            int gapY = 120;

            int index = 0;
            for (RackVisual v : visuals.values()) {
                int col = index % 3;
                int row = index / 3;
                int rx = x + col * gapX;
                int ry = y + row * gapY;

                Color fill;
                if ("OVERHEAT".equals(v.status) || "SPOOFED".equals(v.status)) fill = Color.RED;
                else if (v.tempC > 28.0) fill = Color.ORANGE;
                else fill = new Color(80, 160, 255);

                g2.setColor(fill);
                g2.fillRoundRect(rx, ry, w, h, 18, 18);
                g2.setColor(Color.DARK_GRAY);
                g2.drawRoundRect(rx, ry, w, h, 18, 18);

                g2.setColor(Color.WHITE);
                g2.drawString(v.assetKey, rx + 10, ry + 20);
                g2.drawString(v.rackLabel + " | " + v.status, rx + 10, ry + 40);
                g2.drawString(String.format("T=%.1fC", v.tempC), rx + 10, ry + 60);

                index++;
            }
        }
    }
}