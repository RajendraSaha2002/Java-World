import com.formdev.flatlaf.FlatDarkLaf;
import com.itextpdf.text.Document;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.FileOutputStream;
import java.sql.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PMOSecureTerminal extends JFrame {

    // DB Config
    private static final String URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String USER = "postgres";
    private static final String PASS = "varrie75";

    // UI Components
    private CardLayout cardLayout = new CardLayout();
    private JPanel mainContentPanel = new JPanel(cardLayout);
    private DefaultListModel<String> alertModel = new DefaultListModel<>();

    // Track the last seen log ID to avoid duplicate alerts
    private long lastLogId = 0;

    public PMOSecureTerminal() {
        super("PROJECT AKSH [PMO EYES ONLY]");
        setSize(1100, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 1. Sidebar (Simple Dark Style)
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(new Color(33, 33, 33));
        sidebar.setPreferredSize(new Dimension(100, getHeight()));

        sidebar.add(createSidebarButton("📡 Live", "PANEL_LIVE"));
        sidebar.add(Box.createVerticalStrut(20));
        sidebar.add(createSidebarButton("📄 PDF", "PANEL_REPORT"));

        add(sidebar, BorderLayout.WEST);

        // 2. Main Panels
        mainContentPanel.add(createLivePanel(), "PANEL_LIVE");
        mainContentPanel.add(createReportPanel(), "PANEL_REPORT");

        add(mainContentPanel, BorderLayout.CENTER);

        // Start DB Polling (Alternative to Listen/Notify)
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(this::pollForThreats);

        setVisible(true);
    }

    private JButton createSidebarButton(String text, String panelName) {
        JButton btn = new JButton(text);
        btn.setForeground(Color.WHITE);
        btn.setBackground(new Color(50, 50, 50));
        btn.setFocusPainted(false);
        btn.setMaximumSize(new Dimension(90, 50));
        btn.addActionListener(e -> cardLayout.show(mainContentPanel, panelName));
        return btn;
    }

    // --- PANEL 1: LIVE ALERTS ---
    private JPanel createLivePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(45, 45, 45));

        JLabel title = new JLabel("INCOMING THREAT STREAM (REGION N-01)");
        title.setFont(new Font("Arial", Font.BOLD, 18));
        title.setForeground(Color.RED);
        title.setBorder(new EmptyBorder(20, 20, 20, 20));
        panel.add(title, BorderLayout.NORTH);

        JList<String> alertList = new JList<>(alertModel);
        alertList.setBackground(new Color(20, 20, 20));
        alertList.setForeground(Color.GREEN);
        alertList.setFont(new Font("Monospaced", Font.PLAIN, 14));
        panel.add(new JScrollPane(alertList), BorderLayout.CENTER);

        return panel;
    }

    // --- PANEL 2: PDF REPORTER ---
    private JPanel createReportPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(60, 60, 60));

        JButton btnGen = new JButton("GENERATE DAILY INTEL SUMMARY (PDF)");
        btnGen.setFont(new Font("Arial", Font.BOLD, 16));
        btnGen.setBackground(new Color(0, 100, 200));
        btnGen.setForeground(Color.WHITE);

        btnGen.addActionListener(e -> generatePDF());

        panel.add(btnGen);
        return panel;
    }

    // --- LOGIC: PDF GENERATION ---
    private void generatePDF() {
        try {
            String filename = "Intel_Summary_" + System.currentTimeMillis() + ".pdf";
            Document document = new Document();
            PdfWriter.getInstance(document, new FileOutputStream(filename));
            document.open();

            document.add(new Paragraph("TOP SECRET // PROJECT AKSH"));
            document.add(new Paragraph("DAILY INTELLIGENCE SUMMARY"));
            document.add(new Paragraph("--------------------------------------------------"));

            Connection conn = DriverManager.getConnection(URL, USER, PASS);
            Statement stmt = conn.createStatement();
            // Get last 10 records
            ResultSet rs = stmt.executeQuery("SELECT * FROM uav_feeds ORDER BY timestamp DESC LIMIT 10");

            while(rs.next()) {
                String line = String.format("[%s] Region: %s | Threat: %s | Obj: %s",
                        rs.getTimestamp("timestamp"),
                        rs.getString("region_id"),
                        rs.getString("threat_level"),
                        rs.getString("detected_object"));
                document.add(new Paragraph(line));
            }

            document.close();
            conn.close();
            JOptionPane.showMessageDialog(this, "Report Generated: " + filename);

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
        }
    }

    // --- LOGIC: DB POLLING (Replaces PGNotification) ---
    private void pollForThreats() {
        try {
            // First, get the current max ID so we don't alert on old history
            Connection conn = DriverManager.getConnection(URL, USER, PASS);
            Statement initStmt = conn.createStatement();
            ResultSet initRs = initStmt.executeQuery("SELECT MAX(log_id) FROM uav_feeds");
            if(initRs.next()) {
                lastLogId = initRs.getLong(1);
            }
            conn.close();

            // Loop forever checking for new rows
            while (true) {
                Connection pollConn = DriverManager.getConnection(URL, USER, PASS);
                PreparedStatement pstmt = pollConn.prepareStatement(
                        "SELECT log_id, detected_object, latitude, longitude FROM uav_feeds WHERE log_id > ? AND threat_level = 'CRITICAL' ORDER BY log_id ASC"
                );
                pstmt.setLong(1, lastLogId);

                ResultSet rs = pstmt.executeQuery();
                while(rs.next()) {
                    long id = rs.getLong("log_id");
                    String obj = rs.getString("detected_object");
                    double lat = rs.getDouble("latitude");
                    double lon = rs.getDouble("longitude");

                    // Update UI
                    SwingUtilities.invokeLater(() ->
                            alertModel.addElement("⚠️ CRITICAL THREAT: " + obj + " AT " + lat + ", " + lon)
                    );

                    // Update tracker
                    lastLogId = id;
                }

                pollConn.close();
                Thread.sleep(2000); // Check every 2 seconds
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception ex) {}
        SwingUtilities.invokeLater(PMOSecureTerminal::new);
    }
}