import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.Vector;

public class BlueTeamSIEM extends JFrame {

    // --- CONFIGURATION ---
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/cyber_war_db";
    private static final String USER = "postgres";
    private static final String PASS = "varrie75"; // <--- UPDATE THIS
    private static final int LISTEN_PORT = 5000;

    private JTable logTable;
    private DefaultTableModel tableModel;
    private JLabel statusLabel;

    public BlueTeamSIEM() {
        setTitle("BLUE TEAM SIEM // NETWORK DEFENSE DASHBOARD");
        setSize(1200, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 1. Initialize Database (Self-Healing)
        initDB();

        setupUI();

        // Start Background Services
        new Thread(this::startLogServer).start();   // Listens for Python attacks
        new Thread(this::dashboardRefresher).start(); // Updates UI
    }

    private void initDB() {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
             Statement stmt = conn.createStatement()) {

            // FIX: Create the table automatically if it doesn't exist
            String sql = "CREATE TABLE IF NOT EXISTS network_logs (" +
                    "id SERIAL PRIMARY KEY, " +
                    "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "source_ip VARCHAR(50), " +
                    "target_port INT, " +
                    "payload VARCHAR(255), " +
                    "classification VARCHAR(50) DEFAULT 'Analyzing...', " +
                    "severity_level INT DEFAULT 0" +
                    ")";
            stmt.execute(sql);

            // Create Index for performance
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_time ON network_logs(timestamp)");

            System.out.println("Database initialized successfully.");

        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "DB Init Error: " + e.getMessage());
        }
    }

    private void setupUI() {
        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(10, 10, 20));
        header.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("CYBER THREAT MONITOR");
        title.setFont(new Font("Consolas", Font.BOLD, 24));
        title.setForeground(Color.CYAN);
        header.add(title, BorderLayout.WEST);

        statusLabel = new JLabel("SYSTEM SECURE");
        statusLabel.setFont(new Font("Consolas", Font.BOLD, 24));
        statusLabel.setForeground(Color.GREEN);
        header.add(statusLabel, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        // Table
        String[] cols = {"ID", "TIME", "SOURCE IP", "PORT", "PAYLOAD", "CLASSIFICATION"};
        tableModel = new DefaultTableModel(cols, 0);
        logTable = new JTable(tableModel);
        logTable.setBackground(Color.BLACK);
        logTable.setForeground(Color.GREEN);
        logTable.setFont(new Font("Consolas", Font.PLAIN, 12));
        logTable.setRowHeight(25);

        // Custom Renderer for Red Rows
        logTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                // Safety check to prevent index errors during refresh
                if (table.getModel().getRowCount() > row) {
                    String status = (String) table.getModel().getValueAt(row, 5); // Classification column

                    if (status != null && (status.contains("ATTACK") || status.contains("BRUTE"))) {
                        c.setBackground(new Color(50, 0, 0)); // Dark Red
                        c.setForeground(Color.RED);
                    } else {
                        c.setBackground(Color.BLACK);
                        c.setForeground(Color.GREEN);
                    }
                }
                return c;
            }
        });

        add(new JScrollPane(logTable), BorderLayout.CENTER);
    }

    // --- SERVICE 1: LOG INGESTION SERVER ---
    private void startLogServer() {
        try (ServerSocket serverSocket = new ServerSocket(LISTEN_PORT)) {
            System.out.println("SIEM Ingestion Engine listening on port " + LISTEN_PORT);
            while (true) {
                Socket client = serverSocket.accept();
                new Thread(() -> processLog(client)).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processLog(Socket client) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {

            String line = in.readLine(); // Expecting: "IP|PORT|PAYLOAD"
            if (line != null) {
                String[] parts = line.split("\\|");
                if (parts.length == 3) {
                    String sql = "INSERT INTO network_logs (source_ip, target_port, payload) VALUES (?, ?, ?)";
                    PreparedStatement pstmt = conn.prepareStatement(sql);
                    pstmt.setString(1, parts[0]);
                    pstmt.setInt(2, Integer.parseInt(parts[1]));
                    pstmt.setString(3, parts[2]);
                    pstmt.executeUpdate();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- SERVICE 2: DASHBOARD REFRESHER ---
    private void dashboardRefresher() {
        while (true) {
            try {
                Thread.sleep(1000); // 1-second poll rate
                refreshTable();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void refreshTable() {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            Statement stmt = conn.createStatement();

            // Check for Alerts
            ResultSet rsAlert = stmt.executeQuery("SELECT count(*) FROM network_logs WHERE classification LIKE '%ATTACK%' OR classification LIKE '%BRUTE%'");
            rsAlert.next();
            int attacks = rsAlert.getInt(1);

            SwingUtilities.invokeLater(() -> {
                if (attacks > 0) {
                    statusLabel.setText("!!! " + attacks + " THREATS DETECTED !!!");
                    statusLabel.setForeground(Color.RED);
                } else {
                    statusLabel.setText("SYSTEM SECURE");
                    statusLabel.setForeground(Color.GREEN);
                }
            });

            // Load Data
            ResultSet rs = stmt.executeQuery("SELECT id, to_char(timestamp, 'HH24:MI:SS'), source_ip, target_port, payload, classification FROM network_logs ORDER BY id DESC LIMIT 50");

            Vector<Vector<Object>> data = new Vector<>();
            while (rs.next()) {
                Vector<Object> row = new Vector<>();
                row.add(rs.getInt(1));
                row.add(rs.getString(2));
                row.add(rs.getString(3));
                row.add(rs.getInt(4));
                row.add(rs.getString(5));
                row.add(rs.getString(6));
                data.add(row);
            }

            SwingUtilities.invokeLater(() -> {
                tableModel.setRowCount(0);
                for (Vector<Object> row : data) {
                    tableModel.addRow(row);
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BlueTeamSIEM().setVisible(true));
    }
}