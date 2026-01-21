import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.*;
import java.time.LocalDate;

public class SalesDashboard extends JFrame {

    // --- CONFIGURATION ---
    private static final String DB_URL = "jdbc:mysql://localhost:3306/sales_db";
    private static final String USER = "root";
    private static final String PASS = "varrie75"; // <--- UPDATE THIS

    // UPDATE THIS PATH (Use double backslashes \\)
    private static final String PYTHON_SCRIPT_PATH = "C:\\Users\\Rajendra Saha\\OneDrive\\Desktop\\Python Programs\\daily_report_etl.py";
    // ---------------------

    private JTable summaryTable;
    private DefaultTableModel summaryModel;
    private JTextField productField, amountField, qtyField;

    public SalesDashboard() {
        setTitle("Sales Dashboard & Daily Reporter");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JTabbedPane tabbedPane = new JTabbedPane();

        // --- TAB 1: Data Entry ---
        JPanel entryPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        productField = new JTextField(15);
        amountField = new JTextField(10);
        qtyField = new JTextField(5);
        JButton addButton = new JButton("Add Sale Entry");

        gbc.gridx=0; gbc.gridy=0; entryPanel.add(new JLabel("Product Name:"), gbc);
        gbc.gridx=1; entryPanel.add(productField, gbc);

        gbc.gridx=0; gbc.gridy=1; entryPanel.add(new JLabel("Total Amount ($):"), gbc);
        gbc.gridx=1; entryPanel.add(amountField, gbc);

        gbc.gridx=0; gbc.gridy=2; entryPanel.add(new JLabel("Quantity:"), gbc);
        gbc.gridx=1; entryPanel.add(qtyField, gbc);

        gbc.gridx=1; gbc.gridy=3; entryPanel.add(addButton, gbc);

        tabbedPane.addTab("Entry Station", entryPanel);

        // --- TAB 2: Executive Summary ---
        JPanel reportPanel = new JPanel(new BorderLayout());

        summaryModel = new DefaultTableModel(new String[]{"Date", "Total Revenue", "Total Items", "Top Product"}, 0);
        summaryTable = new JTable(summaryModel);

        JPanel controlPanel = new JPanel();
        JButton generateButton = new JButton("Generate Daily Reports (Python ETL)");
        JButton refreshButton = new JButton("Refresh View");

        controlPanel.add(generateButton);
        controlPanel.add(refreshButton);

        reportPanel.add(new JScrollPane(summaryTable), BorderLayout.CENTER);
        reportPanel.add(controlPanel, BorderLayout.SOUTH);

        tabbedPane.addTab("Executive Summary", reportPanel);

        add(tabbedPane, BorderLayout.CENTER);

        // --- LISTENERS ---
        addButton.addActionListener(e -> addSale());
        generateButton.addActionListener(e -> runPythonETL());
        refreshButton.addActionListener(e -> loadSummary());

        // Initial Load
        loadSummary();
    }

    // --- DATABASE METHODS ---

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL, USER, PASS);
    }

    private void addSale() {
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement("INSERT INTO sales_raw (product_name, amount, quantity, sale_date) VALUES (?, ?, ?, ?)")) {

            pstmt.setString(1, productField.getText());
            pstmt.setDouble(2, Double.parseDouble(amountField.getText()));
            pstmt.setInt(3, Integer.parseInt(qtyField.getText()));
            pstmt.setDate(4, java.sql.Date.valueOf(LocalDate.now()));

            pstmt.executeUpdate();
            JOptionPane.showMessageDialog(this, "Sale Recorded!");
            productField.setText(""); amountField.setText(""); qtyField.setText("");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
        }
    }

    private void loadSummary() {
        summaryModel.setRowCount(0);
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT * FROM sales_summary ORDER BY report_date DESC");
            while (rs.next()) {
                summaryModel.addRow(new Object[]{
                        rs.getDate("report_date"),
                        "$" + rs.getDouble("total_revenue"),
                        rs.getInt("total_items_sold"),
                        rs.getString("top_product")
                });
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // --- PYTHON INTEGRATION ---

    private void runPythonETL() {
        new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("python", PYTHON_SCRIPT_PATH);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("Python ETL: " + line);
                }

                int exitCode = process.waitFor();
                SwingUtilities.invokeLater(() -> {
                    if(exitCode == 0) {
                        JOptionPane.showMessageDialog(this, "ETL Complete! Reports Generated.");
                        loadSummary();
                    } else {
                        JOptionPane.showMessageDialog(this, "ETL Failed. Check Console.");
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Script Error: " + e.getMessage()));
            }
        }).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SalesDashboard().setVisible(true));
    }
}