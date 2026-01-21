import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.Random;

public class SigintInterceptor extends JFrame {

    // --- CONFIGURATION ---
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/sigint_db";
    private static final String USER = "postgres";
    private static final String PASS = "varrie75"; // <--- UPDATE THIS

    private JTextArea logArea;
    private JTextField searchField;
    private JTable resultsTable;
    private DefaultTableModel tableModel;

    public SigintInterceptor() {
        setTitle("SIGINT // INTERCEPT & ANALYSIS STATION");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 1. Init DB (Self-Healing)
        initDB();

        // --- TABS ---
        JTabbedPane tabbedPane = new JTabbedPane();

        // TAB 1: INGESTION ENGINE
        JPanel ingestPanel = new JPanel(new BorderLayout());

        JPanel controlPanel = new JPanel();
        JButton btnSimulate = new JButton("START INTERCEPT STREAM");
        btnSimulate.setBackground(new Color(0, 100, 0));
        btnSimulate.setForeground(Color.WHITE);
        btnSimulate.addActionListener(e -> simulateTraffic());
        controlPanel.add(btnSimulate);

        logArea = new JTextArea();
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(Color.GREEN);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        ingestPanel.add(controlPanel, BorderLayout.NORTH);
        ingestPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);

        tabbedPane.addTab("RAW DATA FEED", ingestPanel);

        // TAB 2: INTEL SEARCH
        JPanel searchPanel = new JPanel(new BorderLayout());

        JPanel searchBar = new JPanel();
        searchField = new JTextField(30);
        JButton btnSearch = new JButton("QUERY INTEL DATABASE");
        btnSearch.addActionListener(e -> performSearch());

        searchBar.add(new JLabel("SEARCH ENTITIES:"));
        searchBar.add(searchField);
        searchBar.add(btnSearch);

        // Results Table
        String[] cols = {"ID", "TYPE", "EXTRACTED VALUE", "THREAT", "ORIGINAL MESSAGE"};
        tableModel = new DefaultTableModel(cols, 0);
        resultsTable = new JTable(tableModel);

        searchPanel.add(searchBar, BorderLayout.NORTH);
        searchPanel.add(new JScrollPane(resultsTable), BorderLayout.CENTER);

        tabbedPane.addTab("ANALYST SEARCH", searchPanel);

        add(tabbedPane, BorderLayout.CENTER);
    }

    private void initDB() {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            Statement stmt = conn.createStatement();
            // Basic table check logic would go here, effectively covered by SQL script
            // This ensures connection is valid
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "DB Connection Failed: " + e.getMessage());
        }
    }

    private void simulateTraffic() {
        new Thread(() -> {
            String[] senders = {"Viper", "Cobra", "Shadow", "Echo", "Bravo"};
            String[] contents = {
                    "Moving payload to grid 34.55, 69.20 tonight.",
                    "Weather is clear for transport.",
                    "Urgent: Nuclear assets require immediate extraction.",
                    "Rendezvous at safehouse alpha.",
                    "Target location confirmed at 12.00, 77.00."
            };
            Random rand = new Random();

            try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
                for (int i = 0; i < 5; i++) {
                    String sender = senders[rand.nextInt(senders.length)];
                    String content = contents[rand.nextInt(contents.length)];

                    String sql = "INSERT INTO raw_intercepts (sender, receiver, message_content) VALUES (?, ?, ?)";
                    PreparedStatement pstmt = conn.prepareStatement(sql);
                    pstmt.setString(1, sender);
                    pstmt.setString(2, "HQ");
                    pstmt.setString(3, content);
                    pstmt.executeUpdate();

                    SwingUtilities.invokeLater(() -> logArea.append("[INTERCEPT] From: " + sender + " | Msg: " + content + "\n"));
                    Thread.sleep(800); // Simulate network delay
                }
                SwingUtilities.invokeLater(() -> logArea.append("--- BATCH COMPLETE. RUN PYTHON ANALYZER NOW ---\n"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void performSearch() {
        String query = searchField.getText().trim();
        tableModel.setRowCount(0);

        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            String sql = "SELECT r.id, p.entity_type, p.extracted_value, p.threat_score, r.message_content " +
                    "FROM processed_intel p " +
                    "JOIN raw_intercepts r ON p.intercept_id = r.id " +
                    "WHERE p.extracted_value ILIKE ? OR p.entity_type ILIKE ?";

            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, "%" + query + "%");
            pstmt.setString(2, "%" + query + "%");

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                tableModel.addRow(new Object[]{
                        rs.getInt("id"),
                        rs.getString("entity_type"),
                        rs.getString("extracted_value"),
                        rs.getInt("threat_score"),
                        rs.getString("message_content")
                });
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Search Error: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SigintInterceptor().setVisible(true));
    }
}