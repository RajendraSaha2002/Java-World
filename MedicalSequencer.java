import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.*;
import java.util.Timer;
import java.util.TimerTask;

public class MedicalSequencer extends JFrame {

    // --- CONFIGURATION ---
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/gene_bank_db";
    private static final String USER = "postgres";
    private static final String PASS = "varrie75"; // <--- UPDATE THIS

    // UI Components
    private JTable soldierTable;
    private DefaultTableModel tableModel;
    private JPanel dnaPanel;
    private JLabel statusLabel;
    private JLabel selectedSoldierLabel;

    // State
    private int selectedId = -1;
    private String currentDna = "";

    public MedicalSequencer() {
        setTitle("MED-CORPS // GENETIC SEQUENCER DASHBOARD");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Color.WHITE);

        initDB(); // Self-healing check
        setupUI();

        // Start Auto-Refresh Loop (To detect the virus attacks in real-time)
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                refreshTableData();
                if (selectedId != -1) {
                    fetchDnaDetails(selectedId);
                }
            }
        }, 0, 2000); // 2 seconds
    }

    private void initDB() {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            // Check connectivity
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "DB Error: " + e.getMessage());
        }
    }

    private void setupUI() {
        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(0, 102, 204)); // Medical Blue
        header.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("🧬 TACTICAL MEDICAL SEQUENCER");
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.WEST);

        statusLabel = new JLabel("SYSTEM OPTIMAL");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        statusLabel.setForeground(Color.GREEN);
        header.add(statusLabel, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        // Split Pane (List vs DNA View)
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(400);
        add(splitPane, BorderLayout.CENTER);

        // Left: Soldier List
        String[] cols = {"ID", "NAME", "RANK", "BLOOD"};
        tableModel = new DefaultTableModel(cols, 0);
        soldierTable = new JTable(tableModel);
        soldierTable.setRowHeight(25);
        soldierTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = soldierTable.getSelectedRow();
                if (row != -1) {
                    selectedId = Integer.parseInt(tableModel.getValueAt(row, 0).toString());
                    fetchDnaDetails(selectedId);
                }
            }
        });
        splitPane.setLeftComponent(new JScrollPane(soldierTable));

        // Right: DNA Visualization
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(Color.BLACK);

        selectedSoldierLabel = new JLabel("SELECT A SOLDIER TO ANALYZE");
        selectedSoldierLabel.setForeground(Color.WHITE);
        selectedSoldierLabel.setFont(new Font("Monospaced", Font.BOLD, 16));
        selectedSoldierLabel.setHorizontalAlignment(SwingConstants.CENTER);
        rightPanel.add(selectedSoldierLabel, BorderLayout.NORTH);

        dnaPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawDNA(g);
            }
        };
        dnaPanel.setBackground(Color.BLACK);
        rightPanel.add(dnaPanel, BorderLayout.CENTER);

        splitPane.setRightComponent(rightPanel);
    }

    private void refreshTableData() {
        // Save selection
        int savedRow = soldierTable.getSelectedRow();

        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT id, name, rank, blood_type FROM soldiers ORDER BY id");

            SwingUtilities.invokeLater(() -> {
                tableModel.setRowCount(0);
                while (true) {
                    try {
                        if (!rs.next()) break;
                        tableModel.addRow(new Object[]{
                                rs.getInt("id"), rs.getString("name"),
                                rs.getString("rank"), rs.getString("blood_type")
                        });
                    } catch (SQLException e) { e.printStackTrace(); }
                }
                // Restore selection if valid
                if (savedRow != -1 && savedRow < tableModel.getRowCount()) {
                    soldierTable.setRowSelectionInterval(savedRow, savedRow);
                }
            });
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void fetchDnaDetails(int id) {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            PreparedStatement pstmt = conn.prepareStatement("SELECT name, dna_sequence FROM soldiers WHERE id = ?");
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String name = rs.getString("name");
                currentDna = rs.getString("dna_sequence");

                SwingUtilities.invokeLater(() -> {
                    selectedSoldierLabel.setText("ANALYZING: " + name);
                    analyzeIntegrity(); // Check for virus signature
                    dnaPanel.repaint();
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void analyzeIntegrity() {
        // LOGIC: Healthy DNA must contain "AAA". If replaced by "TTT", synthesis fails.
        if (currentDna.contains("TTT")) {
            statusLabel.setText("CRITICAL ERROR: SYNTHESIS FAILED");
            statusLabel.setForeground(Color.RED);
        } else {
            statusLabel.setText("SYNTHESIS OPTIMAL");
            statusLabel.setForeground(Color.GREEN);
        }
    }

    private void drawDNA(Graphics g) {
        if (currentDna.isEmpty()) return;

        int x = 20;
        int y = 50;
        int size = 20;
        int gap = 5;

        // Draw blocks for DNA bases
        for (char base : currentDna.toCharArray()) {
            switch (base) {
                case 'A': g.setColor(Color.GREEN); break;
                case 'T': g.setColor(Color.RED); break;
                case 'C': g.setColor(Color.BLUE); break;
                case 'G': g.setColor(Color.YELLOW); break;
                default: g.setColor(Color.GRAY);
            }

            g.fillRect(x, y, size, size);
            g.setColor(Color.BLACK);
            g.drawRect(x, y, size, size);
            g.drawString(String.valueOf(base), x + 5, y + 15);

            x += size + gap;
            if (x > dnaPanel.getWidth() - 30) {
                x = 20;
                y += size + gap;
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MedicalSequencer().setVisible(true));
    }
}