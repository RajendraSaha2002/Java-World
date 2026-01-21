import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.*;

public class BankingSystem extends JFrame {

    // --- CONFIGURATION ---
    private static final String DB_URL = "jdbc:mysql://localhost:3306/banking_db";
    private static final String USER = "root";
    private static final String PASS = "varrie75"; // <--- UPDATE THIS

    // UPDATE PATH (Use double backslashes)
    private static final String PYTHON_SCRIPT_PATH = "C:\\Users\\Rajendra Saha\\OneDrive\\Desktop\\Python Programs\\fraud_detector.py";
    // ---------------------

    private JComboBox<CustomerItem> customerBox;
    private JTextField amountField;
    private JTable alertsTable, historyTable;
    private DefaultTableModel alertsModel, historyModel;

    public BankingSystem() {
        setTitle("Banking System & Fraud Detector");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JTabbedPane tabbedPane = new JTabbedPane();

        // --- TAB 1: Transaction Terminal ---
        JPanel transPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        customerBox = new JComboBox<>();
        amountField = new JTextField(10);
        JButton depositBtn = new JButton("Deposit");
        JButton withdrawBtn = new JButton("Withdraw");

        gbc.gridx=0; gbc.gridy=0; transPanel.add(new JLabel("Select Customer:"), gbc);
        gbc.gridx=1; transPanel.add(customerBox, gbc);

        gbc.gridx=0; gbc.gridy=1; transPanel.add(new JLabel("Amount ($):"), gbc);
        gbc.gridx=1; transPanel.add(amountField, gbc);

        gbc.gridx=0; gbc.gridy=2; transPanel.add(depositBtn, gbc);
        gbc.gridx=1; transPanel.add(withdrawBtn, gbc);

        tabbedPane.addTab("Transactions", transPanel);

        // --- TAB 2: Monitoring & Alerts ---
        JPanel monitorPanel = new JPanel(new BorderLayout());

        // Split Pane: Top (History), Bottom (Alerts)
        historyModel = new DefaultTableModel(new String[]{"ID", "Customer", "Amount", "Type"}, 0);
        historyTable = new JTable(historyModel);

        alertsModel = new DefaultTableModel(new String[]{"Trans ID", "Risk Reason", "Detected At"}, 0);
        alertsTable = new JTable(alertsModel);
        alertsTable.setForeground(Color.RED);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(historyTable), new JScrollPane(alertsTable));
        splitPane.setDividerLocation(250);

        JPanel controlPanel = new JPanel();
        JButton runFraudCheckBtn = new JButton("RUN FRAUD DETECTION (Python ML)");
        JButton refreshBtn = new JButton("Refresh Data");
        controlPanel.add(runFraudCheckBtn);
        controlPanel.add(refreshBtn);

        monitorPanel.add(splitPane, BorderLayout.CENTER);
        monitorPanel.add(controlPanel, BorderLayout.SOUTH);

        tabbedPane.addTab("Fraud Monitor", monitorPanel);

        add(tabbedPane, BorderLayout.CENTER);

        // --- LISTENERS ---
        depositBtn.addActionListener(e -> processTransaction("DEPOSIT"));
        withdrawBtn.addActionListener(e -> processTransaction("WITHDRAWAL"));
        runFraudCheckBtn.addActionListener(e -> runPythonML());
        refreshBtn.addActionListener(e -> loadData());

        // Init
        loadCustomers();
        loadData();
    }

    // --- DATABASE METHODS ---
    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL, USER, PASS);
    }

    private void loadCustomers() {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT id, name FROM customers");
            while (rs.next()) customerBox.addItem(new CustomerItem(rs.getInt("id"), rs.getString("name")));
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void processTransaction(String type) {
        CustomerItem cust = (CustomerItem) customerBox.getSelectedItem();
        if (cust == null) return;

        try (Connection conn = connect()) {
            double amount = Double.parseDouble(amountField.getText());

            // Insert Transaction
            String sql = "INSERT INTO transactions (customer_id, amount, type) VALUES (?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, cust.id);
            pstmt.setDouble(2, amount);
            pstmt.setString(3, type);
            pstmt.executeUpdate();

            // Update Balance
            String balSql = type.equals("DEPOSIT") ?
                    "UPDATE customers SET balance = balance + ? WHERE id = ?" :
                    "UPDATE customers SET balance = balance - ? WHERE id = ?";
            PreparedStatement balStmt = conn.prepareStatement(balSql);
            balStmt.setDouble(1, amount);
            balStmt.setInt(2, cust.id);
            balStmt.executeUpdate();

            JOptionPane.showMessageDialog(this, "Transaction Successful!");
            loadData(); // Refresh table to see new transaction
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
        }
    }

    private void loadData() {
        historyModel.setRowCount(0);
        alertsModel.setRowCount(0);
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            // Load History
            ResultSet rs = stmt.executeQuery("SELECT t.id, c.name, t.amount, t.type FROM transactions t JOIN customers c ON t.customer_id = c.id ORDER BY t.id DESC LIMIT 50");
            while (rs.next()) {
                historyModel.addRow(new Object[]{rs.getInt("id"), rs.getString("name"), "$" + rs.getDouble("amount"), rs.getString("type")});
            }

            // Load Alerts
            ResultSet rs2 = stmt.executeQuery("SELECT transaction_id, reason, detected_at FROM fraud_alerts ORDER BY id DESC");
            while (rs2.next()) {
                alertsModel.addRow(new Object[]{rs2.getInt("transaction_id"), rs2.getString("reason"), rs2.getTimestamp("detected_at")});
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- PYTHON INTEGRATION ---
    private void runPythonML() {
        new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("python", PYTHON_SCRIPT_PATH);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) System.out.println("Python ML: " + line);

                int exitCode = process.waitFor();
                SwingUtilities.invokeLater(() -> {
                    if (exitCode == 0) {
                        JOptionPane.showMessageDialog(this, "Fraud Scan Complete!");
                        loadData();
                    } else {
                        JOptionPane.showMessageDialog(this, "ML Script Failed.");
                    }
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    static class CustomerItem {
        int id; String name;
        public CustomerItem(int id, String name) { this.id = id; this.name = name; }
        public String toString() { return name; }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BankingSystem().setVisible(true));
    }
}