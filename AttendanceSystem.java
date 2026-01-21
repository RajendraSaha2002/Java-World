import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Vector;

public class AttendanceSystem extends JFrame {

    // --- CONFIGURATION ---
    private static final String DB_URL = "jdbc:mysql://localhost:3306/attendance_db";
    private static final String USER = "root";
    private static final String PASS = "varrie75"; // <--- UPDATE THIS

    // PATH TO YOUR PYTHON SCRIPT (Use double backslashes)
    private static final String PYTHON_SCRIPT_PATH = "C:\\Users\\Rajendra Saha\\OneDrive\\Desktop\\Python Programs\\report_generator.py";
    // ---------------------

    private JComboBox<EmployeeItem> empSelector;
    private JLabel statusLabel;

    public AttendanceSystem() {
        setTitle("Employee Attendance & Report System");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JTabbedPane tabbedPane = new JTabbedPane();

        // --- TAB 1: Daily Attendance ---
        JPanel punchPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        empSelector = new JComboBox<>();
        JButton inBtn = new JButton("CLOCK IN");
        JButton outBtn = new JButton("CLOCK OUT");
        statusLabel = new JLabel("Ready...");

        inBtn.setBackground(new Color(144, 238, 144)); // Light Green
        outBtn.setBackground(new Color(255, 182, 193)); // Light Pink

        gbc.gridx=0; gbc.gridy=0; punchPanel.add(new JLabel("Select Employee:"), gbc);
        gbc.gridx=1; punchPanel.add(empSelector, gbc);
        gbc.gridx=0; gbc.gridy=1; punchPanel.add(inBtn, gbc);
        gbc.gridx=1; gbc.gridy=1; punchPanel.add(outBtn, gbc);
        gbc.gridx=0; gbc.gridy=2; gbc.gridwidth=2; punchPanel.add(statusLabel, gbc);

        tabbedPane.addTab("Daily Attendance", punchPanel);

        // --- TAB 2: Reports ---
        JPanel reportPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 50, 50));
        JButton reportBtn = new JButton("Generate Monthly PDF Report");
        reportBtn.setFont(new Font("Arial", Font.BOLD, 16));
        reportPanel.add(reportBtn);

        tabbedPane.addTab("Reports (PDF)", reportPanel);

        add(tabbedPane, BorderLayout.CENTER);

        // --- LISTENERS ---
        inBtn.addActionListener(e -> markAttendance("IN"));
        outBtn.addActionListener(e -> markAttendance("OUT"));
        reportBtn.addActionListener(e -> generateAndOpenPDF());

        loadEmployees();
    }

    // --- DATABASE METHODS ---
    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL, USER, PASS);
    }

    private void loadEmployees() {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT id, name FROM employees");
            while (rs.next()) {
                empSelector.addItem(new EmployeeItem(rs.getInt("id"), rs.getString("name")));
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void markAttendance(String type) {
        EmployeeItem emp = (EmployeeItem) empSelector.getSelectedItem();
        if (emp == null) return;

        try (Connection conn = connect()) {
            if (type.equals("IN")) {
                // Logic: Late if after 9:30 AM
                String status = LocalTime.now().isAfter(LocalTime.of(9, 30)) ? "Late" : "Present";
                String sql = "INSERT INTO attendance (employee_id, date, in_time, status) VALUES (?, ?, ?, ?)";
                PreparedStatement pstmt = conn.prepareStatement(sql);
                pstmt.setInt(1, emp.id);
                pstmt.setDate(2, java.sql.Date.valueOf(LocalDate.now()));
                pstmt.setTime(3, java.sql.Time.valueOf(LocalTime.now()));
                pstmt.setString(4, status);
                pstmt.executeUpdate();
                statusLabel.setText("Clocked IN at " + LocalTime.now().withNano(0));
            } else {
                String sql = "UPDATE attendance SET out_time = ? WHERE employee_id = ? AND date = ?";
                PreparedStatement pstmt = conn.prepareStatement(sql);
                pstmt.setTime(1, java.sql.Time.valueOf(LocalTime.now()));
                pstmt.setInt(2, emp.id);
                pstmt.setDate(3, java.sql.Date.valueOf(LocalDate.now()));
                int rows = pstmt.executeUpdate();
                if (rows > 0) statusLabel.setText("Clocked OUT at " + LocalTime.now().withNano(0));
                else statusLabel.setText("Error: You haven't clocked IN today.");
            }
        } catch (SQLIntegrityConstraintViolationException ex) {
            statusLabel.setText("Error: Already clocked IN today.");
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Error: " + e.getMessage());
        }
    }

    // --- PYTHON & PDF LOGIC ---
    private void generateAndOpenPDF() {
        new Thread(() -> {
            try {
                statusLabel.setText("Generating PDF...");
                ProcessBuilder pb = new ProcessBuilder("python", PYTHON_SCRIPT_PATH);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                // Capture Python Output (filename of the PDF)
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                String pdfPath = null;
                while ((line = reader.readLine()) != null) {
                    System.out.println("Python: " + line);
                    if (line.trim().endsWith(".pdf")) {
                        pdfPath = line.trim();
                    }
                }

                int exitCode = process.waitFor();
                if (exitCode == 0 && pdfPath != null) {
                    // OPEN THE PDF
                    File pdfFile = new File(pdfPath);
                    if (pdfFile.exists()) {
                        Desktop.getDesktop().open(pdfFile);
                        SwingUtilities.invokeLater(() -> statusLabel.setText("PDF Opened!"));
                    } else {
                        SwingUtilities.invokeLater(() -> statusLabel.setText("Error: PDF file not found."));
                    }
                } else {
                    SwingUtilities.invokeLater(() -> statusLabel.setText("Error running Python script."));
                }
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Error: " + e.getMessage()));
            }
        }).start();
    }

    // Helper for Dropdown
    static class EmployeeItem {
        int id; String name;
        public EmployeeItem(int id, String name) { this.id = id; this.name = name; }
        public String toString() { return name; }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AttendanceSystem().setVisible(true));
    }
}