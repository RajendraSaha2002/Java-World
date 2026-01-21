import javax.swing.*;
import java.awt.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDateTime;

public class LogisticsNode extends JFrame {

    // --- CONFIGURATION ---
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/supply_chain_db";
    private static final String USER = "postgres";
    private static final String PASS = "varrie75"; // <--- UPDATE THIS

    private JTextField itemField, fromField, toField, officerField;
    private JComboBox<String> typeBox;
    private JTextArea logArea;

    public LogisticsNode() {
        setTitle("SECURE LOGISTICS TRANSACTION NODE");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 1. Initialize Database (Self-Healing)
        initDB();

        // Header
        JPanel header = new JPanel();
        header.setBackground(new Color(30, 30, 30));
        JLabel title = new JLabel("SUPPLY CHAIN LEDGER v4.0");
        title.setForeground(Color.CYAN);
        title.setFont(new Font("Monospaced", Font.BOLD, 24));
        header.add(title);
        add(header, BorderLayout.NORTH);

        // Input Form
        JPanel formPanel = new JPanel(new GridLayout(6, 2, 10, 10));
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 50, 20, 50));

        formPanel.add(new JLabel("OFFICER ID:"));
        officerField = new JTextField("MAJ-Smith");
        formPanel.add(officerField);

        formPanel.add(new JLabel("ITEM BARCODE:"));
        itemField = new JTextField("AMMO-556-BOX");
        formPanel.add(itemField);

        formPanel.add(new JLabel("ITEM TYPE:"));
        typeBox = new JComboBox<>(new String[]{"STANDARD", "SENSITIVE", "HAZMAT"});
        formPanel.add(typeBox);

        formPanel.add(new JLabel("FROM LOCATION:"));
        fromField = new JTextField("Central Armory");
        formPanel.add(fromField);

        formPanel.add(new JLabel("TO LOCATION:"));
        toField = new JTextField("FOB Alpha");
        formPanel.add(toField);

        JButton btnSubmit = new JButton("EXECUTE TRANSFER");
        btnSubmit.setBackground(new Color(0, 100, 0));
        btnSubmit.setForeground(Color.WHITE);
        btnSubmit.addActionListener(e -> processTransfer());

        formPanel.add(new JLabel("")); // Spacer
        formPanel.add(btnSubmit);

        add(formPanel, BorderLayout.CENTER);

        // Log Area
        logArea = new JTextArea(10, 50);
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(Color.GREEN);
        logArea.setEditable(false);
        add(new JScrollPane(logArea), BorderLayout.SOUTH);
    }

    private void initDB() {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            Statement stmt = conn.createStatement();
            stmt.execute("CREATE TABLE IF NOT EXISTS ledger_immutable (" +
                    "transaction_hash VARCHAR(64) PRIMARY KEY, " +
                    "item_id VARCHAR(50) NOT NULL, " +
                    "item_type VARCHAR(20) DEFAULT 'STANDARD', " +
                    "from_loc VARCHAR(50) NOT NULL, " +
                    "to_loc VARCHAR(50) NOT NULL, " +
                    "officer_id VARCHAR(50) NOT NULL, " +
                    "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "DB Init Failed: " + e.getMessage());
        }
    }

    private void processTransfer() {
        String officer = officerField.getText();
        String item = itemField.getText();
        String type = (String) typeBox.getSelectedItem();
        String from = fromField.getText();
        String to = toField.getText();

        // RBAC CHECK
        if (type.equals("SENSITIVE") && !officer.startsWith("MAJ") && !officer.startsWith("GEN")) {
            log("ACCESS DENIED: Officer rank insufficient for SENSITIVE transfer.");
            JOptionPane.showMessageDialog(this, "RBAC VIOLATION: Authorization Failed.", "Security Alert", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // GENERATE HASH (Simulating Blockchain Link)
        String rawData = item + from + to + officer + LocalDateTime.now();
        String hash = generateHash(rawData);

        // WRITE TO DB
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            String sql = "INSERT INTO ledger_immutable (transaction_hash, item_id, item_type, from_loc, to_loc, officer_id) VALUES (?, ?, ?, ?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, hash);
            pstmt.setString(2, item);
            pstmt.setString(3, type);
            pstmt.setString(4, from);
            pstmt.setString(5, to);
            pstmt.setString(6, officer);

            pstmt.executeUpdate();
            log("SUCCESS: Transaction Recorded.\nHASH: " + hash);
            JOptionPane.showMessageDialog(this, "Transfer Approved & Logged.");

        } catch (SQLException e) {
            log("DB ERROR: " + e.getMessage());
        }
    }

    private String generateHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "ERROR";
        }
    }

    private void log(String msg) {
        logArea.append("> " + msg + "\n");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LogisticsNode().setVisible(true));
    }
}