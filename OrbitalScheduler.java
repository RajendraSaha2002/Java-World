import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Vector;

public class OrbitalScheduler extends JFrame {

    // --- CONFIGURATION ---
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/orbital_db";
    private static final String USER = "postgres";
    private static final String PASS = "varrie75"; // <--- UPDATE THIS

    private JComboBox<String> satCombo;
    private JComboBox<String> priorityCombo;
    private JTextField targetField, startField, durationField;
    private JTable scheduleTable, requestTable;
    private DefaultTableModel schedModel, reqModel;

    public OrbitalScheduler() {
        setTitle("ORBITAL GUARD // CONSTELLATION MANAGER");
        setSize(1200, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Color.DARK_GRAY);

        initDB(); // Self-healing check
        setupUI();
        refreshData();
    }

    private void initDB() {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            // Simple connectivity check
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "DB Error: " + e.getMessage());
        }
    }

    private void setupUI() {
        // --- HEADER ---
        JPanel header = new JPanel();
        header.setBackground(new Color(20, 20, 40));
        JLabel title = new JLabel("🛰️ ORBITAL TASKING STATION");
        title.setFont(new Font("Consolas", Font.BOLD, 24));
        title.setForeground(Color.CYAN);
        header.add(title);
        add(header, BorderLayout.NORTH);

        // --- INPUT PANEL (Left) ---
        JPanel inputPanel = new JPanel(new GridLayout(7, 2, 10, 10));
        inputPanel.setBorder(BorderFactory.createTitledBorder(null, "NEW TASKING REQUEST", 0, 0, null, Color.WHITE));
        inputPanel.setBackground(Color.DARK_GRAY);
        inputPanel.setPreferredSize(new Dimension(350, 0));

        inputPanel.add(new JLabel("<html><font color='white'>Target Name:</font></html>"));
        targetField = new JTextField("Silo-42");
        inputPanel.add(targetField);

        inputPanel.add(new JLabel("<html><font color='white'>Priority:</font></html>"));
        priorityCombo = new JComboBox<>(new String[]{"1 - PRESIDENTIAL", "2 - STRATEGIC", "3 - ROUTINE"});
        inputPanel.add(priorityCombo);

        inputPanel.add(new JLabel("<html><font color='white'>Satellite Asset:</font></html>"));
        satCombo = new JComboBox<>();
        loadSatellites();
        inputPanel.add(satCombo);

        inputPanel.add(new JLabel("<html><font color='white'>Start (YYYY-MM-DD HH:MM):</font></html>"));
        startField = new JTextField(LocalDateTime.now().plusHours(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        inputPanel.add(startField);

        inputPanel.add(new JLabel("<html><font color='white'>Duration (Minutes):</font></html>"));
        durationField = new JTextField("30");
        inputPanel.add(durationField);

        JButton btnSubmit = new JButton("SUBMIT REQUEST");
        btnSubmit.setBackground(new Color(0, 100, 200));
        btnSubmit.setForeground(Color.WHITE);
        btnSubmit.addActionListener(e -> submitRequest());
        inputPanel.add(new JLabel("")); // Spacer
        inputPanel.add(btnSubmit);

        add(inputPanel, BorderLayout.WEST);

        // --- TABLES PANEL (Center) ---
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

        // Top Table: Active Schedule
        JPanel schedPanel = new JPanel(new BorderLayout());
        schedPanel.setBorder(BorderFactory.createTitledBorder(null, "CONFIRMED SCHEDULE (GANTT DATA)", 0, 0, null, Color.GREEN));

        String[] schedCols = {"SAT", "TARGET", "START TIME", "END TIME", "WINDOW"};
        schedModel = new DefaultTableModel(schedCols, 0);
        scheduleTable = new JTable(schedModel);
        scheduleTable.setBackground(Color.BLACK);
        scheduleTable.setForeground(Color.GREEN);
        schedPanel.add(new JScrollPane(scheduleTable), BorderLayout.CENTER);

        // Bottom Table: Pending/Conflict Requests
        JPanel reqPanel = new JPanel(new BorderLayout());
        reqPanel.setBorder(BorderFactory.createTitledBorder(null, "REQUEST QUEUE STATUS", 0, 0, null, Color.ORANGE));

        String[] reqCols = {"ID", "TARGET", "PRIORITY", "SAT", "STATUS"};
        reqModel = new DefaultTableModel(reqCols, 0);
        requestTable = new JTable(reqModel);

        // Color renderer for Status
        requestTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String status = (String) table.getModel().getValueAt(row, 4);
                if ("CONFLICT".equals(status)) {
                    c.setForeground(Color.RED);
                    c.setBackground(new Color(50, 0, 0));
                } else if ("SCHEDULED".equals(status)) {
                    c.setForeground(Color.GREEN);
                    c.setBackground(Color.BLACK);
                } else {
                    c.setForeground(Color.YELLOW); // Pending
                    c.setBackground(Color.BLACK);
                }
                return c;
            }
        });

        reqPanel.add(new JScrollPane(requestTable), BorderLayout.CENTER);

        JButton btnRefresh = new JButton("REFRESH VIEWS");
        btnRefresh.addActionListener(e -> refreshData());
        reqPanel.add(btnRefresh, BorderLayout.SOUTH);

        splitPane.setTopComponent(schedPanel);
        splitPane.setBottomComponent(reqPanel);
        splitPane.setDividerLocation(400);

        add(splitPane, BorderLayout.CENTER);
    }

    private void loadSatellites() {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT name FROM satellites");
            while (rs.next()) satCombo.addItem(rs.getString("name"));
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void submitRequest() {
        String target = targetField.getText();
        String priorityStr = (String) priorityCombo.getSelectedItem();
        int priority = Integer.parseInt(priorityStr.split(" ")[0]);
        String satName = (String) satCombo.getSelectedItem();
        String startStr = startField.getText();
        int duration = Integer.parseInt(durationField.getText());

        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            // Get Sat ID
            PreparedStatement pSat = conn.prepareStatement("SELECT id FROM satellites WHERE name = ?");
            pSat.setString(1, satName);
            ResultSet rsSat = pSat.executeQuery();
            rsSat.next();
            int satId = rsSat.getInt(1);

            // Calc End Time
            LocalDateTime start = LocalDateTime.parse(startStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            LocalDateTime end = start.plusMinutes(duration);

            // Insert Request
            String sql = "INSERT INTO requests (target_name, priority, satellite_id, start_time, end_time) VALUES (?, ?, ?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, target);
            pstmt.setInt(2, priority);
            pstmt.setInt(3, satId);
            pstmt.setTimestamp(4, Timestamp.valueOf(start));
            pstmt.setTimestamp(5, Timestamp.valueOf(end));

            pstmt.executeUpdate();
            refreshData();
            JOptionPane.showMessageDialog(this, "Request Queued. Run Python Optimizer to Schedule.");

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
        }
    }

    private void refreshData() {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            // 1. Load Requests
            reqModel.setRowCount(0);
            Statement stmtReq = conn.createStatement();
            ResultSet rsReq = stmtReq.executeQuery("SELECT r.id, r.target_name, r.priority, s.name, r.status FROM requests r JOIN satellites s ON r.satellite_id = s.id ORDER BY r.status DESC, r.priority ASC");
            while (rsReq.next()) {
                reqModel.addRow(new Object[]{
                        rsReq.getInt("id"), rsReq.getString("target_name"),
                        rsReq.getInt("priority"), rsReq.getString("name"), rsReq.getString("status")
                });
            }

            // 2. Load Schedule
            schedModel.setRowCount(0);
            Statement stmtSched = conn.createStatement();
            String schedSql = "SELECT s.name, r.target_name, r.start_time, r.end_time, sc.mission_window " +
                    "FROM schedule sc " +
                    "JOIN requests r ON sc.request_id = r.id " +
                    "JOIN satellites s ON sc.satellite_id = s.id " +
                    "ORDER BY r.start_time ASC";
            ResultSet rsSched = stmtSched.executeQuery(schedSql);
            while (rsSched.next()) {
                schedModel.addRow(new Object[]{
                        rsSched.getString("name"), rsSched.getString("target_name"),
                        rsSched.getTimestamp("start_time"), rsSched.getTimestamp("end_time"),
                        rsSched.getString("mission_window")
                });
            }

        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new OrbitalScheduler().setVisible(true));
    }
}