import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.*;

public class HealthMonitor extends JFrame {

    // --- CONFIGURATION ---
    private static final String DB_URL = "jdbc:mysql://localhost:3306/health_db";
    private static final String USER = "root";
    private static final String PASS = "varrie75"; // <--- UPDATE THIS

    // UPDATE THIS PATH to your Python file location
    private static final String PYTHON_SCRIPT_PATH = "C:\\Users\\Rajendra Saha\\OneDrive\\Desktop\\Python Programs\\risk_predictor.py";
    // ---------------------

    private JTextField nameField, ageField, bmiField, hrField;
    private JTable healthTable;
    private DefaultTableModel tableModel;

    public HealthMonitor() {
        setTitle("Health Monitor & Risk Predictor");
        setSize(800, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // --- TOP: Input Form ---
        JPanel inputPanel = new JPanel(new GridLayout(5, 2, 10, 10));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 50, 10, 50));

        nameField = new JTextField();
        ageField = new JTextField();
        bmiField = new JTextField();
        hrField = new JTextField();
        JButton saveBtn = new JButton("Log Health Data");

        inputPanel.add(new JLabel("Name:")); inputPanel.add(nameField);
        inputPanel.add(new JLabel("Age:")); inputPanel.add(ageField);
        inputPanel.add(new JLabel("BMI (e.g., 22.5):")); inputPanel.add(bmiField);
        inputPanel.add(new JLabel("Heart Rate (BPM):")); inputPanel.add(hrField);
        inputPanel.add(new JLabel("")); inputPanel.add(saveBtn);

        add(inputPanel, BorderLayout.NORTH);

        // --- CENTER: Data Table ---
        tableModel = new DefaultTableModel(new String[]{"ID", "Name", "Age", "BMI", "HR", "Risk Level"}, 0);
        healthTable = new JTable(tableModel);
        add(new JScrollPane(healthTable), BorderLayout.CENTER);

        // --- BOTTOM: Action Panel ---
        JPanel actionPanel = new JPanel();
        JButton predictBtn = new JButton("Calculate Risk (Python AI)");
        JButton refreshBtn = new JButton("Refresh Table");

        predictBtn.setBackground(new Color(255, 100, 100)); // Red tint
        predictBtn.setForeground(Color.WHITE);

        actionPanel.add(predictBtn);
        actionPanel.add(refreshBtn);
        add(actionPanel, BorderLayout.SOUTH);

        // --- LISTENERS ---
        saveBtn.addActionListener(e -> saveLog());
        refreshBtn.addActionListener(e -> loadLogs());
        predictBtn.addActionListener(e -> runPythonPrediction());

        loadLogs();
    }

    // --- DATABASE METHODS ---
    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL, USER, PASS);
    }

    private void saveLog() {
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement("INSERT INTO health_logs (user_name, age, bmi, heart_rate) VALUES (?, ?, ?, ?)")) {

            pstmt.setString(1, nameField.getText());
            pstmt.setInt(2, Integer.parseInt(ageField.getText()));
            pstmt.setDouble(3, Double.parseDouble(bmiField.getText()));
            pstmt.setInt(4, Integer.parseInt(hrField.getText()));

            pstmt.executeUpdate();
            JOptionPane.showMessageDialog(this, "Health Data Logged!");
            loadLogs();
            // Clear fields
            nameField.setText(""); ageField.setText(""); bmiField.setText(""); hrField.setText("");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
        }
    }

    private void loadLogs() {
        tableModel.setRowCount(0);
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT * FROM health_logs ORDER BY id DESC");
            while (rs.next()) {
                tableModel.addRow(new Object[]{
                        rs.getInt("id"),
                        rs.getString("user_name"),
                        rs.getInt("age"),
                        rs.getDouble("bmi"),
                        rs.getInt("heart_rate"),
                        rs.getString("risk_level")
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- PYTHON INTEGRATION ---
    private void runPythonPrediction() {
        new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("python", PYTHON_SCRIPT_PATH);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("Python: " + line);
                }

                int exitCode = process.waitFor();
                SwingUtilities.invokeLater(() -> {
                    if (exitCode == 0) {
                        JOptionPane.showMessageDialog(this, "Risk Analysis Complete!");
                        loadLogs();
                    } else {
                        JOptionPane.showMessageDialog(this, "Error running prediction script.");
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new HealthMonitor().setVisible(true));
    }
}