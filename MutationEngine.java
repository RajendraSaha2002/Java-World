import javax.swing.*;
import java.awt.*;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.*;
import java.util.*;
import java.util.List;

public class MutationEngine extends JFrame {

    // --- CONFIGURATION ---
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/viral_net_db";
    private static final String USER = "postgres";
    private static final String PASS = "varrie75"; // <--- UPDATE THIS
    private static final String PYTHON_HOST = "127.0.0.1";
    private static final int PYTHON_PORT = 8888;

    private JTextArea codePreview;
    private JLabel statusLabel;

    public MutationEngine() {
        setTitle("VIRAL NET // MUTATION ENGINE C2");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(20, 20, 30));

        initDB(); // Check connection
        setupUI();
    }

    private void initDB() {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            // Test connection
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "DB Error: " + e.getMessage());
        }
    }

    private void setupUI() {
        // Header
        JPanel header = new JPanel();
        header.setBackground(new Color(20, 20, 30));
        JLabel title = new JLabel("🧬 POLYMORPHIC PAYLOAD GENERATOR");
        title.setFont(new Font("Consolas", Font.BOLD, 24));
        title.setForeground(new Color(100, 255, 100));
        header.add(title);
        add(header, BorderLayout.NORTH);

        // Code Preview Area
        codePreview = new JTextArea();
        codePreview.setBackground(new Color(10, 10, 15));
        codePreview.setForeground(Color.GREEN);
        codePreview.setFont(new Font("Monospaced", Font.PLAIN, 12));
        codePreview.setEditable(false);
        add(new JScrollPane(codePreview), BorderLayout.CENTER);

        // Controls
        JPanel controls = new JPanel();
        controls.setBackground(new Color(40, 40, 50));

        JButton btnGenerate = new JButton("ASSEMBLE & MUTATE");
        btnGenerate.addActionListener(e -> generateVirus());

        JButton btnDeploy = new JButton("DEPLOY TO GRID");
        btnDeploy.setBackground(new Color(200, 50, 50));
        btnDeploy.setForeground(Color.WHITE);
        btnDeploy.addActionListener(e -> deployVirus());

        statusLabel = new JLabel("Status: IDLE");
        statusLabel.setForeground(Color.CYAN);

        controls.add(btnGenerate);
        controls.add(btnDeploy);
        controls.add(statusLabel);
        add(controls, BorderLayout.SOUTH);
    }

    // --- GENETIC ALGORITHM LOGIC ---
    private String currentPayload = "";
    private int currentComplexity = 0;

    private void generateVirus() {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            // 1. Fetch Random Genes
            // Get 2-4 random genes
            String sql = "SELECT gene_type, code_snippet, complexity_score FROM genes ORDER BY RANDOM() LIMIT 4";
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);

            StringBuilder rawCode = new StringBuilder("# VIRAL PAYLOAD v" + System.currentTimeMillis() + "\n");
            currentComplexity = 0;
            List<String> functions = new ArrayList<>();

            while (rs.next()) {
                String type = rs.getString("gene_type");
                String code = rs.getString("code_snippet");
                int score = rs.getInt("complexity_score");

                currentComplexity += score;
                functions.add(code); // Store for shuffling
            }

            // 2. MUTATION STEP (Obfuscation)
            // Shuffle function order
            Collections.shuffle(functions);

            for (String func : functions) {
                // Mutate Variable Names (Simple Simulation)
                String mutated = func.replace("target", "x" + new Random().nextInt(999))
                        .replace("ip", "var_" + new Random().nextInt(999));
                rawCode.append("\n").append(mutated).append("\n");
            }

            currentPayload = rawCode.toString();
            codePreview.setText(">>> VIRUS ASSEMBLED (Complexity: " + currentComplexity + ")\n\n" + currentPayload);
            statusLabel.setText("Status: READY TO DEPLOY");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void deployVirus() {
        if (currentPayload.isEmpty()) return;

        new Thread(() -> {
            try (Socket socket = new Socket(PYTHON_HOST, PYTHON_PORT)) {
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                // Protocol: "COMPLEXITY|PAYLOAD_HASH"
                // Sending just complexity for the simulation logic
                out.println(currentComplexity);

                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Status: DEPLOYED (Load: " + currentComplexity + ")");
                    codePreview.append("\n\n>>> PACKET SENT TO PATIENT ZERO.");
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Connection Error: Is Python Running?"));
            }
        }).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MutationEngine().setVisible(true));
    }
}