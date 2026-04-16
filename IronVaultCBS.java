import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class IronVaultCBS extends JFrame {
    private DefaultTableModel tableModel;
    private JTextArea alertConsole;
    private static final int PORT = 7070;

    // Database configurations
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = "varrie75";

    // Anti-Fraud In-Memory Cache
    private Map<String, List<Long>> velocityTracker = new ConcurrentHashMap<>();
    private Map<String, String> locationTracker = new ConcurrentHashMap<>();

    public IronVaultCBS() {
        setTitle("Project IRON-VAULT: Core Banking & IDS");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(new Color(30, 30, 30));
        setLayout(new BorderLayout());

        // Setup Live Transaction Table
        String[] columns = {"Timestamp", "From", "To", "Amount (₹)", "Location", "Status"};
        tableModel = new DefaultTableModel(columns, 0);
        JTable txTable = new JTable(tableModel);
        txTable.setBackground(new Color(40, 40, 40));
        txTable.setForeground(Color.WHITE);

        // Custom cell renderer to flash Red/Green
        txTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String status = (String) table.getModel().getValueAt(row, 5);
                if (status.contains("BLOCKED")) c.setForeground(Color.RED);
                else if (status.equals("SUCCESS")) c.setForeground(Color.GREEN);
                else c.setForeground(Color.ORANGE);
                return c;
            }
        });

        JScrollPane tableScroll = new JScrollPane(txTable);
        tableScroll.getViewport().setBackground(new Color(30, 30, 30));
        tableScroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.CYAN), "Live Transaction Ledger"));
        add(tableScroll, BorderLayout.CENTER);

        // Alert Console
        alertConsole = new JTextArea(10, 50);
        alertConsole.setBackground(Color.BLACK);
        alertConsole.setForeground(Color.RED);
        alertConsole.setEditable(false);
        alertConsole.setFont(new Font("Monospaced", Font.BOLD, 13));
        JScrollPane alertScroll = new JScrollPane(alertConsole);
        alertScroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.RED), "Anti-Fraud Alerts"));
        add(alertScroll, BorderLayout.SOUTH);
    }

    private void logAlert(String msg) {
        SwingUtilities.invokeLater(() -> {
            alertConsole.append("[ALERT] " + msg + "\n");
            alertConsole.setCaretPosition(alertConsole.getDocument().getLength());
        });
    }

    private void addTableRow(Object[] rowData) {
        SwingUtilities.invokeLater(() -> tableModel.insertRow(0, rowData));
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

    public void startGatewayListener() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                logAlert("SYSTEM ONLINE. Listening on ATM/Gateway Port " + PORT);
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(() -> handleConnection(clientSocket)).start(); // Handle concurrent connections
                }
            } catch (IOException e) {
                logAlert("Server Error: " + e.getMessage());
            }
        }).start();
    }

    private void handleConnection(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
            String payload;
            while ((payload = in.readLine()) != null) {
                processTransaction(payload);
            }
        } catch (IOException e) {
            // Client disconnected
        }
    }

    private void processTransaction(String json) {
        String fromAcct = parseJson(json, "from_acct");
        String toAcct = parseJson(json, "toAcct");
        if (toAcct.isEmpty()) toAcct = parseJson(json, "to_acct"); // Fallback check
        double amount = Double.parseDouble(parseJson(json, "amount"));
        String location = parseJson(json, "location");

        // FIX: Explicitly use java.util.Date to resolve the ambiguous import error
        String timeStr = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());

        // --- 1. ANTI-FRAUD ENGINE ---
        String fraudReason = null;

        // Rule A: Geographic Impossible Travel
        String lastLoc = locationTracker.get(fromAcct);
        if (lastLoc != null && !lastLoc.equals(location)) {
            fraudReason = "Impossible Travel (" + lastLoc + " -> " + location + ")";
        }
        locationTracker.put(fromAcct, location);

        // Rule B: Velocity / Smurfing Check (More than 5 tx in 5 seconds)
        long now = System.currentTimeMillis();
        velocityTracker.putIfAbsent(fromAcct, new ArrayList<>());
        List<Long> times = velocityTracker.get(fromAcct);
        times.add(now);
        times.removeIf(t -> now - t > 5000); // 5-second rolling window

        if (times.size() > 5) {
            fraudReason = "Velocity/Smurfing Attack (" + times.size() + " tx/5sec)";
        }

        // --- 2. EXECUTION / DATABASE INTERACTION ---
        String status;
        if (fraudReason != null) {
            status = "BLOCKED: " + fraudReason;
            logAlert("ACCOUNT " + fromAcct + " FROZEN | REASON: " + fraudReason);
            freezeAccountAndLogFraud(fromAcct, fraudReason);
        } else {
            status = executeDbTransfer(fromAcct, toAcct, amount);
        }

        // Update UI Dashboard
        addTableRow(new Object[]{timeStr, fromAcct, toAcct, amount, location, status});
    }

    private String executeDbTransfer(String from, String to, double amt) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            // Calling the ACID Compliant function that handles row locking
            String sql = "SELECT process_transfer(?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, from);
                pstmt.setString(2, to);
                pstmt.setDouble(3, amt);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) return rs.getString(1);
            }
        } catch (SQLException e) {
            return "FAILED: DB_ERROR";
        }
        return "FAILED: UNKNOWN";
    }

    private void freezeAccountAndLogFraud(String acct, String rule) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            // Freeze Account
            try (PreparedStatement pstmt = conn.prepareStatement("UPDATE accounts SET status = 'FROZEN' WHERE acct_id = ?")) {
                pstmt.setString(1, acct);
                pstmt.executeUpdate();
            }
            // Log Incident
            try (PreparedStatement pstmt = conn.prepareStatement("INSERT INTO fraud_incidents (acct_id, rule_triggered) VALUES (?, ?)")) {
                pstmt.setString(1, acct);
                pstmt.setString(2, rule);
                pstmt.executeUpdate();
            }
        } catch (SQLException ignored) {}
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            IronVaultCBS cbs = new IronVaultCBS();
            cbs.setVisible(true);
            cbs.startGatewayListener();
        });
    }
}