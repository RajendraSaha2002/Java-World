import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class IronGateDashboard extends JFrame {

    // Database Configuration
    private static final String URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String USER = "postgres";
    private static final String PASS = "varrie75";

    // UI Components
    private JPanel gridPanel;
    private JLabel alertIcon;
    private JTable logTable;
    private DefaultTableModel logModel;
    private Timer pulseTimer; // For the flashing effect
    private boolean isRedAlert = false;

    public IronGateDashboard() {
        super("IRON-GATE: SOVEREIGN GRID MONITOR");
        setSize(1000, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 1. Header (Threat Status)
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 10));
        header.setBackground(new Color(20, 20, 25));

        alertIcon = new JLabel("🛡️ SYSTEM SECURE");
        alertIcon.setFont(new Font("Segoe UI", Font.BOLD, 20));
        alertIcon.setForeground(Color.GREEN);
        header.add(alertIcon);

        add(header, BorderLayout.NORTH);

        // 2. Communication Grid (Center)
        // This panel holds the boxes for each communication channel
        gridPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        gridPanel.setBackground(Color.BLACK);
        gridPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(gridPanel, BorderLayout.CENTER);

        // 3. Audit Log (Bottom)
        String[] cols = {"Time", "User", "Action", "Status"};
        logModel = new DefaultTableModel(cols, 0);
        logTable = new JTable(logModel);
        logTable.setBackground(new Color(30, 30, 30));
        logTable.setForeground(Color.CYAN);
        add(new JScrollPane(logTable), BorderLayout.SOUTH);

        // 4. Initial Load
        updateDashboard();

        // 5. Start Polling Service (Replaces PGNotification)
        // Checks the database every 2 seconds for status changes
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::updateDashboard, 0, 2, TimeUnit.SECONDS);

        // Pulse Timer for Visual Alerts (UI Thread animation)
        pulseTimer = new Timer(500, e -> {
            if (isRedAlert) {
                Color c = alertIcon.getForeground();
                // Toggle between RED and BLACK
                alertIcon.setForeground(c == Color.RED ? Color.BLACK : Color.RED);
            }
        });

        setVisible(true);
    }

    // --- LOGIC: Polling the DB ---
    private void updateDashboard() {
        // Run UI updates on the Swing Event Thread
        SwingUtilities.invokeLater(() -> {
            refreshChannels();
            refreshLogs();
        });
    }

    private void refreshChannels() {
        gridPanel.removeAll();
        boolean breachFound = false;

        try {
            Connection conn = DriverManager.getConnection(URL, USER, PASS);
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT name, signal_strength, status FROM comm_channels ORDER BY channel_id");

            while (rs.next()) {
                String name = rs.getString("name");
                int signal = rs.getInt("signal_strength");
                String status = rs.getString("status");

                // Check if any channel is compromised
                if ("COMPROMISED".equals(status)) {
                    breachFound = true;
                }

                // Add widget to grid
                gridPanel.add(createChannelWidget(name, signal, status));
            }
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Logic to trigger RED ALERT if a breach was found
        if (breachFound && !isRedAlert) {
            isRedAlert = true;
            alertIcon.setText("⚠️ BREACH DETECTED");
            alertIcon.setForeground(Color.RED);
            pulseTimer.start(); // Start flashing
        } else if (!breachFound && isRedAlert) {
            // Reset to Normal
            isRedAlert = false;
            pulseTimer.stop();
            alertIcon.setText("🛡️ SYSTEM SECURE");
            alertIcon.setForeground(Color.GREEN);
        }

        gridPanel.revalidate();
        gridPanel.repaint();
    }

    private void refreshLogs() {
        try {
            Connection conn = DriverManager.getConnection(URL, USER, PASS);
            Statement stmt = conn.createStatement();
            // Get last 10 logs
            ResultSet rs = stmt.executeQuery("SELECT timestamp, user_identity, action_type, status FROM access_logs ORDER BY log_id DESC LIMIT 10");

            logModel.setRowCount(0); // Clear table

            while(rs.next()) {
                logModel.addRow(new Object[]{
                        rs.getTimestamp("timestamp"),
                        rs.getString("user_identity"),
                        rs.getString("action_type"),
                        rs.getString("status")
                });
            }
            conn.close();
        } catch (Exception e) {}
    }

    // --- UI HELPER: Create Grid Item ---
    private JPanel createChannelWidget(String name, int signal, String status) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(40, 40, 45));
        p.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));

        JLabel nameLbl = new JLabel(" " + name);
        nameLbl.setForeground(Color.WHITE);
        nameLbl.setFont(new Font("Arial", Font.BOLD, 14));
        p.add(nameLbl, BorderLayout.NORTH);

        // Signal Bar (Custom Paint Component)
        JPanel signalBar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(Color.BLACK);
                g.fillRect(10, 10, 200, 20); // Background Bar

                // Set Color based on status
                if (status.equals("COMPROMISED")) g.setColor(Color.RED);
                else if (signal < 50) g.setColor(Color.YELLOW);
                else g.setColor(Color.GREEN);

                // Draw Signal Strength
                int width = (int) (200 * (signal / 100.0));
                g.fillRect(10, 10, width, 20); // Fill

                g.setColor(Color.WHITE);
                g.drawString("Signal: " + signal + "%", 220, 25);
            }
        };
        signalBar.setOpaque(false);
        p.add(signalBar, BorderLayout.CENTER);

        JLabel statusLbl = new JLabel(" STATUS: " + status + " ");
        statusLbl.setHorizontalAlignment(SwingConstants.RIGHT);
        if(status.equals("COMPROMISED")) statusLbl.setForeground(Color.RED);
        else statusLbl.setForeground(Color.GREEN);
        p.add(statusLbl, BorderLayout.SOUTH);

        return p;
    }

    public static void main(String[] args) {
        // Set Dark Theme
        try { UIManager.setLookAndFeel(new FlatDarkLaf()); } catch (Exception ex) {}
        SwingUtilities.invokeLater(IronGateDashboard::new);
    }
}