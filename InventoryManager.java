import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.*;
import java.time.LocalDate;

public class InventoryManager extends JFrame {

    // DB CONFIGURATION - CHANGE THESE TO MATCH YOUR MYSQL
    private static final String DB_URL = "jdbc:mysql://localhost:3306/inventory_ml";
    private static final String USER = "root";
    private static final String PASS = "varrie75";

    private JTable productTable;
    private DefaultTableModel tableModel;
    private JTextField nameField, priceField, idFieldForHistory, historyPriceField;

    public InventoryManager() {
        setTitle("Inventory & Price Prediction System");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // --- TABS ---
        JTabbedPane tabbedPane = new JTabbedPane();

        // TAB 1: Product Dashboard
        JPanel dashboardPanel = new JPanel(new BorderLayout());
        tableModel = new DefaultTableModel(new String[]{"ID", "Name", "Current Price", "ML Predicted Price"}, 0);
        productTable = new JTable(tableModel);
        dashboardPanel.add(new JScrollPane(productTable), BorderLayout.CENTER);

        JPanel controlPanel = new JPanel();
        nameField = new JTextField(10);
        priceField = new JTextField(8);
        JButton addButton = new JButton("Add Product");
        JButton refreshButton = new JButton("Refresh Data");
        JButton predictButton = new JButton("Run Python ML Model");

        controlPanel.add(new JLabel("Name:"));
        controlPanel.add(nameField);
        controlPanel.add(new JLabel("Price:"));
        controlPanel.add(priceField);
        controlPanel.add(addButton);
        controlPanel.add(refreshButton);
        controlPanel.add(predictButton);

        dashboardPanel.add(controlPanel, BorderLayout.SOUTH);
        tabbedPane.addTab("Dashboard", dashboardPanel);

        // TAB 2: Add History
        JPanel historyPanel = new JPanel(new FlowLayout());
        idFieldForHistory = new JTextField(5);
        historyPriceField = new JTextField(8);
        JButton addHistoryButton = new JButton("Log Historical Price");

        historyPanel.add(new JLabel("Product ID:"));
        historyPanel.add(idFieldForHistory);
        historyPanel.add(new JLabel("Historical Price:"));
        historyPanel.add(historyPriceField);
        historyPanel.add(addHistoryButton);
        tabbedPane.addTab("Log History", historyPanel);

        add(tabbedPane, BorderLayout.CENTER);

        // --- ACTION LISTENERS ---

        addButton.addActionListener(e -> addProduct());
        refreshButton.addActionListener(e -> loadProducts());
        addHistoryButton.addActionListener(e -> addHistory());

        predictButton.addActionListener(e -> {
            predictButton.setEnabled(false);
            predictButton.setText("Processing...");
            new Thread(this::runPythonScript).start(); // Run in background
        });

        // Load initial data
        loadProducts();
    }

    // --- DATABASE OPERATIONS ---

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL, USER, PASS);
    }

    private void loadProducts() {
        tableModel.setRowCount(0);
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT * FROM products");
            while (rs.next()) {
                tableModel.addRow(new Object[]{
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getDouble("current_price"),
                        rs.getDouble("predicted_next_price")
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading data: " + e.getMessage());
        }
    }

    private void addProduct() {
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement("INSERT INTO products (name, current_price) VALUES (?, ?)")) {
            pstmt.setString(1, nameField.getText());
            pstmt.setDouble(2, Double.parseDouble(priceField.getText()));
            pstmt.executeUpdate();
            loadProducts();
            nameField.setText("");
            priceField.setText("");
            JOptionPane.showMessageDialog(this, "Product Added!");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error adding product: " + e.getMessage());
        }
    }

    private void addHistory() {
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement("INSERT INTO price_history (product_id, price, recorded_date) VALUES (?, ?, ?)")) {
            pstmt.setInt(1, Integer.parseInt(idFieldForHistory.getText()));
            pstmt.setDouble(2, Double.parseDouble(historyPriceField.getText()));
            pstmt.setDate(3, java.sql.Date.valueOf(LocalDate.now())); // Defaults to today for simplicity
            pstmt.executeUpdate();
            JOptionPane.showMessageDialog(this, "History Logged!");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error logging history: " + e.getMessage());
        }
    }

    // --- PYTHON INTEGRATION ---

    private void runPythonScript() {
        try {
            // Command to run python script. Ensure 'python' is in your system PATH
            // Or replace "python" with the full path to python.exe
            ProcessBuilder pb = new ProcessBuilder("python", "price_predictor.py");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read Python output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("Python: " + line);
            }

            int exitCode = process.waitFor();
            SwingUtilities.invokeLater(() -> {
                if (exitCode == 0) {
                    JOptionPane.showMessageDialog(this, "Prediction Complete! Reloading table.");
                    loadProducts();
                } else {
                    JOptionPane.showMessageDialog(this, "Error running Python script.");
                }
                // Reset button text - finding the button in a real app would be cleaner,
                // but this works for the example structure.
                this.repaint();
            });

        } catch (Exception e) {
            e.printStackTrace();
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Script Failed: " + e.getMessage()));
        }
    }

    public static void main(String[] args) {
        // Ensure UI looks consistent
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new InventoryManager().setVisible(true));
    }
}