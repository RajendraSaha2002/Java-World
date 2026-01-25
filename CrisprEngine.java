import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CrisprEngine extends JFrame {

    // --- CONFIGURATION ---
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/genome_db";
    private static final String USER = "postgres";
    private static final String PASS = "varrie75"; // <--- UPDATE THIS

    private JTextField targetField, replaceField;
    private JTextArea logArea;

    public CrisprEngine() {
        setTitle("GENOME HACKER // CRISPR-Cas9 EDITOR");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(20, 20, 20));

        initDB();
        setupUI();
    }

    private void initDB() {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            // Simple check
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "DB Connection Error: " + e.getMessage());
        }
    }

    private void setupUI() {
        // Header
        JPanel header = new JPanel();
        header.setBackground(new Color(0, 50, 50));
        JLabel title = new JLabel("🧬 GENETIC SEQUENCE EDITOR");
        title.setFont(new Font("Consolas", Font.BOLD, 24));
        title.setForeground(Color.CYAN);
        header.add(title);
        add(header, BorderLayout.NORTH);

        // Controls
        JPanel controlPanel = new JPanel(new GridLayout(5, 1, 10, 10));
        controlPanel.setBackground(new Color(30, 30, 30));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(20, 50, 20, 50));

        JLabel lblInfo = new JLabel("GUIDE RNA CONFIGURATION:");
        lblInfo.setForeground(Color.WHITE);
        controlPanel.add(lblInfo);

        // Inputs
        JPanel inputGrid = new JPanel(new GridLayout(2, 2, 5, 5));
        inputGrid.setBackground(new Color(30, 30, 30));

        JLabel lblTarget = new JLabel("Target Sequence (Find):");
        lblTarget.setForeground(Color.LIGHT_GRAY);
        targetField = new JTextField("TCG");

        JLabel lblReplace = new JLabel("Mutation Sequence (Inject):");
        lblReplace.setForeground(Color.ORANGE);
        replaceField = new JTextField("AAA"); // A benign mutation

        inputGrid.add(lblTarget);
        inputGrid.add(targetField);
        inputGrid.add(lblReplace);
        inputGrid.add(replaceField);

        controlPanel.add(inputGrid);

        JButton btnSplice = new JButton("INITIATE CRISPR SPLICE");
        btnSplice.setBackground(new Color(200, 0, 0));
        btnSplice.setForeground(Color.WHITE);
        btnSplice.setFont(new Font("Arial", Font.BOLD, 14));
        btnSplice.addActionListener(e -> executeMutation());
        controlPanel.add(btnSplice);

        JButton btnReset = new JButton("RESET GENOME (RESTORE BACKUP)");
        btnReset.setBackground(Color.GRAY);
        btnReset.addActionListener(e -> resetGenome());
        controlPanel.add(btnReset);

        add(controlPanel, BorderLayout.CENTER);

        // Logs
        logArea = new JTextArea(8, 40);
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(Color.GREEN);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        add(new JScrollPane(logArea), BorderLayout.SOUTH);
    }

    private void executeMutation() {
        String targetSeq = targetField.getText().trim().toUpperCase();
        String newSeq = replaceField.getText().trim().toUpperCase();

        if (targetSeq.length() != newSeq.length()) {
            log("ERROR: Cas9 Limitation - Replacement length must match Target length for this simulation.");
            return;
        }

        new Thread(() -> {
            try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
                conn.setAutoCommit(false); // Start Transaction

                // 1. Fetch entire genome string to find index
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT position_index, base_pair FROM genome_sequence ORDER BY position_index");

                StringBuilder genomeBuilder = new StringBuilder();
                List<Integer> indices = new ArrayList<>();

                while (rs.next()) {
                    genomeBuilder.append(rs.getString("base_pair"));
                    indices.add(rs.getInt("position_index"));
                }

                String fullGenome = genomeBuilder.toString();
                int matchIndex = fullGenome.indexOf(targetSeq);

                if (matchIndex == -1) {
                    log("SEARCH FAILED: Guide RNA could not find pattern '" + targetSeq + "'.");
                    return;
                }

                log("MATCH FOUND at Index " + (matchIndex + 1));
                log("Injecting Mutation...");

                // 2. Perform the Cut & Splice (Update specific rows)
                String updateSql = "UPDATE genome_sequence SET base_pair = ? WHERE position_index = ?";
                PreparedStatement pstmt = conn.prepareStatement(updateSql);

                for (int i = 0; i < newSeq.length(); i++) {
                    char base = newSeq.charAt(i);
                    int dbIndex = indices.get(matchIndex + i); // Map string index to DB ID

                    pstmt.setString(1, String.valueOf(base));
                    pstmt.setInt(2, dbIndex);
                    pstmt.addBatch();
                }

                pstmt.executeBatch();
                conn.commit();

                log("SUCCESS: Genome Mutated. Sequence '" + targetSeq + "' replaced with '" + newSeq + "'.");
                log("Visualizer should detect phenotype change momentarily.");

            } catch (Exception e) {
                log("CRITICAL FAILURE: " + e.getMessage());
            }
        }).start();
    }

    private void resetGenome() {
        // Simple reset for demo purposes
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            Statement stmt = conn.createStatement();
            stmt.executeUpdate("TRUNCATE TABLE genome_sequence");
            String seed = "INSERT INTO genome_sequence (position_index, base_pair, gene_marker) VALUES " +
                    "(1, 'A', 'Rep'), (2, 'T', 'Rep'), (3, 'G', 'Rep'), " +
                    "(4, 'C', 'Enz'), (5, 'C', 'Enz'), (6, 'T', 'Enz'), " +
                    "(7, 'A', 'Enz'), (8, 'A', 'Enz'), (9, 'G', 'Enz'), " +
                    "(10, 'T', 'Spk'), (11, 'C', 'Spk'), (12, 'G', 'Spk'), " +
                    "(13, 'A', 'Spk'), (14, 'T', 'Spk'), (15, 'C', 'Spk'), " +
                    "(16, 'G', 'Trm'), (17, 'G', 'Trm'), (18, 'A', 'Trm'), " +
                    "(19, 'T', 'Trm'), (20, 'C', 'Trm')";
            stmt.executeUpdate(seed);
            log("SYSTEM RESTORE: Genome reset to factory defaults.");
        } catch (Exception e) {
            log("Error resetting: " + e.getMessage());
        }
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> logArea.append("> " + msg + "\n"));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new CrisprEngine().setVisible(true));
    }
}