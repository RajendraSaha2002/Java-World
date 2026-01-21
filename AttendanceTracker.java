import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.*;
import java.time.LocalDate;

public class AttendanceTracker extends JFrame {

    // --- CONFIGURATION ---
    private static final String DB_URL = "jdbc:mysql://localhost:3306/school_tracker";
    private static final String USER = "root";
    private static final String PASS = "varrie75"; // Update this!

    // UPDATE THIS PATH to where you save the python file
    // I have updated this based on your previous screenshot.
    // If you saved the file elsewhere, right-click the file -> "Copy as Path" and paste it here (use double backslashes \\)
    private static final String PYTHON_SCRIPT_PATH = "C:\\Users\\Rajendra Saha\\OneDrive\\Desktop\\Python Programs\\pattern_detector.py";
    // ---------------------

    private JTable studentsTable, alertsTable;
    private DefaultTableModel studentModel, alertModel;
    private JComboBox<String> statusBox;
    private JTextField studentIdField;

    public AttendanceTracker() {
        setTitle("School Attendance & Pattern Detector");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JTabbedPane tabbedPane = new JTabbedPane();

        // --- TAB 1: Mark Attendance ---
        JPanel markPanel = new JPanel(new BorderLayout());

        // Top: Input Form
        JPanel inputPanel = new JPanel(new FlowLayout());
        studentIdField = new JTextField(5);
        statusBox = new JComboBox<>(new String[]{"Present", "Absent"});
        JButton submitButton = new JButton("Mark Attendance");

        inputPanel.add(new JLabel("Student ID:"));
        inputPanel.add(studentIdField);
        inputPanel.add(new JLabel("Status:"));
        inputPanel.add(statusBox);
        inputPanel.add(submitButton);

        // Center: Student List (Reference)
        studentModel = new DefaultTableModel(new String[]{"ID", "Name", "Grade"}, 0);
        studentsTable = new JTable(studentModel);

        markPanel.add(inputPanel, BorderLayout.NORTH);
        markPanel.add(new JScrollPane(studentsTable), BorderLayout.CENTER);

        tabbedPane.addTab("Daily Entry", markPanel);

        // --- TAB 2: Risk Analysis ---
        JPanel riskPanel = new JPanel(new BorderLayout());

        alertModel = new DefaultTableModel(new String[]{"Student Name", "Risk Score (%)", "Pattern Warning"}, 0);
        alertsTable = new JTable(alertModel);
        alertsTable.setForeground(Color.RED);

        JPanel actionPanel = new JPanel();
        JButton analyzeButton = new JButton("Run Python Pattern Detector");
        JButton refreshButton = new JButton("Refresh Alerts");

        actionPanel.add(analyzeButton);
        actionPanel.add(refreshButton);

        riskPanel.add(new JScrollPane(alertsTable), BorderLayout.CENTER);
        riskPanel.add(actionPanel, BorderLayout.SOUTH);

        tabbedPane.addTab("At-Risk Students", riskPanel);

        add(tabbedPane, BorderLayout.CENTER);

        // --- LISTENERS ---
        submitButton.addActionListener(e -> markAttendance());
        analyzeButton.addActionListener(e -> runPythonAnalysis());
        refreshButton.addActionListener(e -> loadAlerts());

        // Initial Load
        loadStudents();
        loadAlerts();
    }

    // --- DATABASE METHODS ---

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL, USER, PASS);
    }

    private void loadStudents() {
        studentModel.setRowCount(0);
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT * FROM students");
            while (rs.next()) {
                studentModel.addRow(new Object[]{rs.getInt("id"), rs.getString("name"), rs.getInt("grade_level")});
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void markAttendance() {
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement("INSERT INTO attendance (student_id, date, status) VALUES (?, ?, ?)")) {

            pstmt.setInt(1, Integer.parseInt(studentIdField.getText()));
            pstmt.setDate(2, java.sql.Date.valueOf(LocalDate.now()));
            pstmt.setString(3, (String) statusBox.getSelectedItem());

            pstmt.executeUpdate();
            JOptionPane.showMessageDialog(this, "Attendance Recorded!");
            studentIdField.setText("");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
        }
    }

    private void loadAlerts() {
        alertModel.setRowCount(0);
        String query = "SELECT s.name, r.risk_score, r.message FROM risk_alerts r JOIN students s ON r.student_id = s.id ORDER BY r.risk_score DESC";
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(query);
            while (rs.next()) {
                alertModel.addRow(new Object[]{
                        rs.getString("name"),
                        String.format("%.1f%%", rs.getDouble("risk_score")),
                        rs.getString("message")
                });
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // --- PYTHON INTEGRATION ---

    private void runPythonAnalysis() {
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
                    if(exitCode == 0) {
                        JOptionPane.showMessageDialog(this, "Analysis Complete. Checking for patterns...");
                        loadAlerts();
                    } else {
                        JOptionPane.showMessageDialog(this, "Error running analysis script.");
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AttendanceTracker().setVisible(true));
    }
}