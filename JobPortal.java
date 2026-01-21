import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.*;
import java.util.Vector;

public class JobPortal extends JFrame {

    // --- CONFIGURATION ---
    private static final String DB_URL = "jdbc:mysql://localhost:3306/job_portal_db";
    private static final String USER = "root";
    private static final String PASS = "varrie75"; // <--- UPDATE THIS

    // PATH TO YOUR PYTHON SCRIPT
    private static final String PYTHON_SCRIPT_PATH = "C:\\Users\\Rajendra Saha\\OneDrive\\Desktop\\Python Programs\\resume_analyzer.py";
    // ---------------------

    private JTable rankingsTable;
    private DefaultTableModel rankingModel;
    private JTextField jobTitleField, skillsField, candNameField;
    private JTextArea resumeArea;
    private JComboBox<JobItem> jobSelector;

    public JobPortal() {
        setTitle("Mini Job Portal & Resume Analyzer");
        setSize(900, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JTabbedPane tabbedPane = new JTabbedPane();

        // --- TAB 1: Recruiter (Post Job) ---
        JPanel recruiterPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        jobTitleField = new JTextField(20);
        skillsField = new JTextField(20);
        JButton postJobBtn = new JButton("Post Job");

        gbc.gridx=0; gbc.gridy=0; recruiterPanel.add(new JLabel("Job Title:"), gbc);
        gbc.gridx=1; recruiterPanel.add(jobTitleField, gbc);

        gbc.gridx=0; gbc.gridy=1; recruiterPanel.add(new JLabel("Required Skills (comma-sep):"), gbc);
        gbc.gridx=1; recruiterPanel.add(skillsField, gbc);

        gbc.gridx=1; gbc.gridy=2; recruiterPanel.add(postJobBtn, gbc);

        tabbedPane.addTab("Recruiter", recruiterPanel);

        // --- TAB 2: Candidate (Apply) ---
        JPanel candidatePanel = new JPanel(new BorderLayout(10, 10));
        JPanel formPanel = new JPanel(new GridLayout(4, 2, 10, 10));
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 50, 20, 50));

        jobSelector = new JComboBox<>();
        candNameField = new JTextField();
        resumeArea = new JTextArea(5, 20);
        JButton applyBtn = new JButton("Submit Application");

        formPanel.add(new JLabel("Select Job:"));
        formPanel.add(jobSelector);
        formPanel.add(new JLabel("Full Name:"));
        formPanel.add(candNameField);
        formPanel.add(new JLabel("Paste Resume Text:"));
        formPanel.add(new JScrollPane(resumeArea));
        formPanel.add(new JLabel("")); // Spacer
        formPanel.add(applyBtn);

        candidatePanel.add(formPanel, BorderLayout.CENTER);
        tabbedPane.addTab("Candidate Application", candidatePanel);

        // --- TAB 3: Admin (Rankings) ---
        JPanel rankPanel = new JPanel(new BorderLayout());
        rankingModel = new DefaultTableModel(new String[]{"Job Title", "Candidate", "Resume Snippet", "Match Score"}, 0);
        rankingsTable = new JTable(rankingModel);

        JPanel buttonPanel = new JPanel();
        JButton analyzeBtn = new JButton("Run Python AI Analysis");
        JButton refreshBtn = new JButton("Refresh Rankings");

        buttonPanel.add(analyzeBtn);
        buttonPanel.add(refreshBtn);

        rankPanel.add(new JScrollPane(rankingsTable), BorderLayout.CENTER);
        rankPanel.add(buttonPanel, BorderLayout.SOUTH);

        tabbedPane.addTab("Rankings & AI", rankPanel);

        add(tabbedPane, BorderLayout.CENTER);

        // --- LISTENERS ---
        postJobBtn.addActionListener(e -> postJob());
        applyBtn.addActionListener(e -> applyForJob());
        analyzeBtn.addActionListener(e -> runPythonAnalysis());
        refreshBtn.addActionListener(e -> loadRankings());

        // Init
        loadJobsIntoDropdown();
        loadRankings();
    }

    // --- DATABASE METHODS ---
    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL, USER, PASS);
    }

    private void postJob() {
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement("INSERT INTO jobs (title, required_skills) VALUES (?, ?)")) {
            pstmt.setString(1, jobTitleField.getText());
            pstmt.setString(2, skillsField.getText());
            pstmt.executeUpdate();
            JOptionPane.showMessageDialog(this, "Job Posted!");
            loadJobsIntoDropdown();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void applyForJob() {
        JobItem selectedJob = (JobItem) jobSelector.getSelectedItem();
        if (selectedJob == null) return;

        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement("INSERT INTO candidates (job_id, name, resume_text) VALUES (?, ?, ?)")) {
            pstmt.setInt(1, selectedJob.id);
            pstmt.setString(2, candNameField.getText());
            pstmt.setString(3, resumeArea.getText());
            pstmt.executeUpdate();
            JOptionPane.showMessageDialog(this, "Application Submitted!");
            loadRankings();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadJobsIntoDropdown() {
        jobSelector.removeAllItems();
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT id, title FROM jobs");
            while (rs.next()) {
                jobSelector.addItem(new JobItem(rs.getInt("id"), rs.getString("title")));
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadRankings() {
        rankingModel.setRowCount(0);
        String sql = "SELECT j.title, c.name, c.resume_text, c.match_score FROM candidates c JOIN jobs j ON c.job_id = j.id ORDER BY c.match_score DESC";
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                String resume = rs.getString("resume_text");
                String snippet = resume.length() > 50 ? resume.substring(0, 47) + "..." : resume;
                rankingModel.addRow(new Object[]{rs.getString("title"), rs.getString("name"), snippet, rs.getDouble("match_score") + "%"});
            }
        } catch (Exception e) { e.printStackTrace(); }
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
                while ((line = reader.readLine()) != null) System.out.println("Python: " + line);

                int exitCode = process.waitFor();
                SwingUtilities.invokeLater(() -> {
                    if(exitCode == 0) {
                        JOptionPane.showMessageDialog(this, "Analysis Complete! Rankings updated.");
                        loadRankings();
                    } else {
                        JOptionPane.showMessageDialog(this, "Error running Python script.");
                    }
                });
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    // Helper Class for Dropdown
    static class JobItem {
        int id; String title;
        public JobItem(int id, String title) { this.id = id; this.title = title; }
        public String toString() { return title; }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new JobPortal().setVisible(true));
    }
}