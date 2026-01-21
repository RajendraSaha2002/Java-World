import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.*;
import java.util.Vector;

public class EcommerceApp extends JFrame {

    // --- CONFIGURATION ---
    private static final String DB_URL = "jdbc:mysql://localhost:3306/ecommerce_db";
    private static final String USER = "root";
    private static final String PASS = "varrie75"; // <--- UPDATE THIS

    // UPDATE THIS PATH to your Python file
    private static final String PYTHON_SCRIPT_PATH = "C:\\Users\\Rajendra Saha\\OneDrive\\Desktop\\Python Programs\\rcommender.py";
    // ---------------------

    private JComboBox<UserItem> userSelector;
    private JComboBox<ProductItem> productSelector;
    private JTable recTable;
    private DefaultTableModel recModel;

    public EcommerceApp() {
        setTitle("Smart E-Commerce Recommendation Engine");
        setSize(800, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JTabbedPane tabbedPane = new JTabbedPane();

        // --- TAB 1: Shopping (Simulate Orders) ---
        JPanel shopPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        userSelector = new JComboBox<>();
        productSelector = new JComboBox<>();
        JButton buyBtn = new JButton("Buy Product");
        buyBtn.setBackground(new Color(100, 200, 100));

        gbc.gridx=0; gbc.gridy=0; shopPanel.add(new JLabel("Current User:"), gbc);
        gbc.gridx=1; shopPanel.add(userSelector, gbc);

        gbc.gridx=0; gbc.gridy=1; shopPanel.add(new JLabel("Product:"), gbc);
        gbc.gridx=1; shopPanel.add(productSelector, gbc);

        gbc.gridx=1; gbc.gridy=2; shopPanel.add(buyBtn, gbc);

        tabbedPane.addTab("Shop", shopPanel);

        // --- TAB 2: Recommendations ---
        JPanel recPanel = new JPanel(new BorderLayout());

        recModel = new DefaultTableModel(new String[]{"Recommended Product", "Category", "Confidence Score"}, 0);
        recTable = new JTable(recModel);

        JPanel controlPanel = new JPanel();
        JButton trainBtn = new JButton("Train Model (Python)");
        JButton refreshBtn = new JButton("Show My Recommendations");

        controlPanel.add(trainBtn);
        controlPanel.add(refreshBtn);

        recPanel.add(new JScrollPane(recTable), BorderLayout.CENTER);
        recPanel.add(controlPanel, BorderLayout.SOUTH);

        tabbedPane.addTab("Recommended for You", recPanel);

        add(tabbedPane, BorderLayout.CENTER);

        // --- LISTENERS ---
        buyBtn.addActionListener(e -> placeOrder());
        trainBtn.addActionListener(e -> runPythonRecommender());
        refreshBtn.addActionListener(e -> loadRecommendations());

        // Init
        loadDropdowns();
    }

    // --- DATABASE METHODS ---
    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL, USER, PASS);
    }

    private void loadDropdowns() {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            // Load Users
            ResultSet rs = stmt.executeQuery("SELECT id, name FROM users");
            while (rs.next()) userSelector.addItem(new UserItem(rs.getInt("id"), rs.getString("name")));

            // Load Products
            ResultSet rs2 = stmt.executeQuery("SELECT id, name FROM products");
            while (rs2.next()) productSelector.addItem(new ProductItem(rs2.getInt("id"), rs2.getString("name")));

        } catch (Exception e) { e.printStackTrace(); }
    }

    private void placeOrder() {
        UserItem user = (UserItem) userSelector.getSelectedItem();
        ProductItem prod = (ProductItem) productSelector.getSelectedItem();
        if (user == null || prod == null) return;

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement("INSERT INTO orders (user_id, product_id) VALUES (?, ?)")) {
            pstmt.setInt(1, user.id);
            pstmt.setInt(2, prod.id);
            pstmt.executeUpdate();
            JOptionPane.showMessageDialog(this, "Order Placed! The system will learn from this.");
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadRecommendations() {
        recModel.setRowCount(0);
        UserItem user = (UserItem) userSelector.getSelectedItem();
        if (user == null) return;

        String query = "SELECT p.name, p.category, r.score FROM recommendations r " +
                "JOIN products p ON r.product_id = p.id " +
                "WHERE r.user_id = ? ORDER BY r.score DESC";

        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, user.id);
            ResultSet rs = pstmt.executeQuery();
            boolean hasRecs = false;
            while (rs.next()) {
                hasRecs = true;
                recModel.addRow(new Object[]{rs.getString("name"), rs.getString("category"), String.format("%.2f", rs.getDouble("score"))});
            }
            if (!hasRecs) JOptionPane.showMessageDialog(this, "No recommendations yet. Try buying something or running the Python model!");
        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- PYTHON INTEGRATION ---
    private void runPythonRecommender() {
        new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("python", PYTHON_SCRIPT_PATH);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) System.out.println("Python: " + line);

                int exitCode = process.waitFor();
                SwingUtilities.invokeLater(() -> {
                    if (exitCode == 0) {
                        JOptionPane.showMessageDialog(this, "Model Trained & Recommendations Updated!");
                        loadRecommendations();
                    } else {
                        JOptionPane.showMessageDialog(this, "Error running Python script.");
                    }
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    // Helpers
    static class UserItem {
        int id; String name;
        public UserItem(int id, String name) { this.id = id; this.name = name; }
        public String toString() { return name; }
    }
    static class ProductItem {
        int id; String name;
        public ProductItem(int id, String name) { this.id = id; this.name = name; }
        public String toString() { return name; }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new EcommerceApp().setVisible(true));
    }
}