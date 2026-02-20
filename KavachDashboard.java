import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class KavachDashboard extends JFrame {

    private static final String URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String USER = "postgres";
    private static final String PASS = "varrie75";

    // UI Components
    private JPanel gridPanel;
    private JTable ledgerTable;
    private DefaultTableModel ledgerModel;
    private Map<String, JPanel> nodeWidgets = new HashMap<>();

    public KavachDashboard() {
        super("KAVACH-C2: SYSTEM INTEGRITY MONITOR");
        setSize(1000, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 1. Header
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT));
        header.setBackground(new Color(20, 20, 20));
        JLabel title = new JLabel("CDS TACTICAL COMMAND // INTEGRITY STATUS");
        title.setForeground(Color.CYAN);
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        header.add(title);
        add(header, BorderLayout.NORTH);

        // 2. The Grid (3D-style boxes)
        gridPanel = new JPanel(new GridLayout(1, 3, 20, 20));
        gridPanel.setBackground(Color.BLACK);
        gridPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        add(gridPanel, BorderLayout.CENTER);

        // 3. WORM Ledger (Bottom)
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(new TitledBorder("IMMUTABLE MISSION LEDGER (WORM)"));

        String[] cols = {"ID", "Commander", "Order Details", "Timestamp"};
        ledgerModel = new DefaultTableModel(cols, 0);
        ledgerTable = new JTable(ledgerModel);
        ledgerTable.setBackground(new Color(30, 30, 30));
        ledgerTable.setForeground(Color.WHITE);
        bottomPanel.add(new JScrollPane(ledgerTable), BorderLayout.CENTER);

        bottomPanel.setPreferredSize(new Dimension(getWidth(), 200));
        add(bottomPanel, BorderLayout.SOUTH);

        // Initial Load
        loadNodes();

        // Start Polling (Checks health every 2 seconds)
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::checkSystemHealth, 0, 2, TimeUnit.SECONDS);

        setVisible(true);
    }

    private void loadNodes() {
        // Create placeholders for the 3 known nodes
        createNodeWidget("AIR-CMD-ALPHA", "NORTHERN SECTOR");
        createNodeWidget("NAVAL-HQ-WEST", "WESTERN FLEET");
        createNodeWidget("GROUND-CORPS-1", "BORDER CONTROL");
    }

    private void createNodeWidget(String id, String sector) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(0, 100, 0)); // Default Green
        p.setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));

        JLabel lblId = new JLabel(id, SwingConstants.CENTER);
        lblId.setFont(new Font("Arial", Font.BOLD, 16));
        lblId.setForeground(Color.WHITE);

        JLabel lblSec = new JLabel(sector, SwingConstants.CENTER);
        lblSec.setForeground(Color.LIGHT_GRAY);

        JLabel lblStatus = new JLabel("SECURE", SwingConstants.CENTER);
        lblStatus.setFont(new Font("Impact", Font.PLAIN, 24));
        lblStatus.setForeground(Color.WHITE);

        p.add(lblId, BorderLayout.NORTH);
        p.add(lblStatus, BorderLayout.CENTER);
        p.add(lblSec, BorderLayout.SOUTH);

        nodeWidgets.put(id, p);
        gridPanel.add(p);
    }

    // --- POLLING LOGIC ---
    private void checkSystemHealth() {
        SwingUtilities.invokeLater(() -> {
            updateNodeStatus();
            refreshLedger();
        });
    }

    private void updateNodeStatus() {
        try {
            Connection conn = DriverManager.getConnection(URL, USER, PASS);
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT node_id, status FROM connected_nodes");

            while (rs.next()) {
                String id = rs.getString("node_id");
                String status = rs.getString("status");

                if (nodeWidgets.containsKey(id)) {
                    JPanel p = nodeWidgets.get(id);
                    JLabel statusLbl = (JLabel) p.getComponent(1); // Center component

                    if ("INFECTED".equals(status)) {
                        p.setBackground(new Color(150, 0, 0)); // RED
                        statusLbl.setText("⚠ COMPROMISED");
                    } else {
                        p.setBackground(new Color(0, 100, 0)); // GREEN
                        statusLbl.setText("SECURE");
                    }
                }
            }
            conn.close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void refreshLedger() {
        try {
            Connection conn = DriverManager.getConnection(URL, USER, PASS);
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM mission_ledger ORDER BY ledger_id DESC LIMIT 10");

            ledgerModel.setRowCount(0);
            while(rs.next()) {
                ledgerModel.addRow(new Object[]{
                        rs.getLong("ledger_id"),
                        rs.getString("command_issued_by"),
                        rs.getString("order_details"),
                        rs.getTimestamp("timestamp")
                });
            }
            conn.close();
        } catch (Exception e) {}
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(new FlatDarkLaf()); } catch (Exception ex) {}
        SwingUtilities.invokeLater(KavachDashboard::new);
    }
}