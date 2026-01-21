import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.sql.*;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class HiveMindController extends JFrame {

    // --- CONFIGURATION ---
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/drone_ops_db";
    private static final String USER = "postgres";
    private static final String PASS = "varrie75"; // <--- UPDATE THIS
    private static final String PY_HOST = "127.0.0.1";
    private static final int PY_PORT = 7000;

    private JTable droneTable;
    private DefaultTableModel tableModel;
    private JTextArea logArea;
    private Random random = new Random();

    public HiveMindController() {
        setTitle("HIVE MIND // DRONE SWARM COMMAND");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        initDB(); // Self-healing check
        setupUI();

        // Start Telemetry Loop (Updates battery/status)
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateSwarmTelemetry();
                refreshTable();
            }
        }, 0, 2000); // Every 2 seconds
    }

    private void initDB() {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            // Just connectivity check
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "DB Connection Error: " + e.getMessage());
        }
    }

    private void setupUI() {
        // Header
        JPanel header = new JPanel(new FlowLayout(FlowLayout.CENTER));
        header.setBackground(new Color(20, 20, 20));
        JLabel title = new JLabel("DRONE SWARM TELEMETRY");
        title.setFont(new Font("Consolas", Font.BOLD, 24));
        title.setForeground(Color.CYAN);
        header.add(title);
        add(header, BorderLayout.NORTH);

        // Center: Grid of Drones
        String[] cols = {"DRONE ID", "STATUS", "BATTERY %", "SECTOR"};
        tableModel = new DefaultTableModel(cols, 0);
        droneTable = new JTable(tableModel);
        droneTable.setBackground(Color.BLACK);
        droneTable.setForeground(Color.GREEN);
        droneTable.setRowHeight(30);
        droneTable.setFont(new Font("Arial", Font.BOLD, 14));

        // Color renderer based on Status
        droneTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String status = (String) table.getModel().getValueAt(row, 1);

                if ("ERROR".equals(status)) c.setForeground(Color.RED);
                else if ("FLYING".equals(status)) c.setForeground(Color.CYAN);
                else if ("CHARGING".equals(status)) c.setForeground(Color.YELLOW);
                else c.setForeground(Color.GREEN);

                c.setBackground(Color.BLACK);
                return c;
            }
        });

        add(new JScrollPane(droneTable), BorderLayout.CENTER);

        // Bottom: Controls & Logs
        JPanel bottomPanel = new JPanel(new BorderLayout());

        // Controls
        JPanel controls = new JPanel();
        controls.setBackground(Color.DARK_GRAY);

        JButton btnDeploy = new JButton("DEPLOY SWARM (PATROL)");
        btnDeploy.setBackground(new Color(0, 100, 0));
        btnDeploy.setForeground(Color.WHITE);
        btnDeploy.addActionListener(e -> sendCommandToAll("FLYING"));

        JButton btnRecall = new JButton("RECALL SWARM (RTB)");
        btnRecall.setBackground(new Color(150, 100, 0));
        btnRecall.setForeground(Color.WHITE);
        btnRecall.addActionListener(e -> sendCommandToAll("CHARGING"));

        JButton btnSimulateCam = new JButton("SIMULATE CAMERA (VISION AI)");
        btnSimulateCam.setBackground(new Color(0, 0, 150));
        btnSimulateCam.setForeground(Color.WHITE);
        btnSimulateCam.addActionListener(e -> simulateCameraEvent());

        controls.add(btnDeploy);
        controls.add(btnRecall);
        controls.add(btnSimulateCam);
        bottomPanel.add(controls, BorderLayout.NORTH);

        // Logs
        logArea = new JTextArea(8, 50);
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(Color.LIGHT_GRAY);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        bottomPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    // --- LOGIC ---

    private void updateSwarmTelemetry() {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            // Logic: Decrease battery if FLYING, Increase if CHARGING
            String sql = "UPDATE drone_telemetry SET battery_level = CASE " +
                    "WHEN status = 'FLYING' THEN GREATEST(0, battery_level - 2) " +
                    "WHEN status = 'CHARGING' THEN LEAST(100, battery_level + 5) " +
                    "ELSE battery_level END";
            Statement stmt = conn.createStatement();
            stmt.executeUpdate(sql);

            // Auto-Recall if battery low
            stmt.executeUpdate("UPDATE drone_telemetry SET status = 'CHARGING' WHERE battery_level < 20 AND status = 'FLYING'");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void refreshTable() {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT drone_id, status, battery_level, current_sector FROM drone_telemetry ORDER BY drone_id");

            tableModel.setRowCount(0);
            while (rs.next()) {
                tableModel.addRow(new Object[]{
                        rs.getString(1), rs.getString(2), rs.getInt(3) + "%", rs.getString(4)
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendCommandToAll(String newStatus) {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            String sql = "UPDATE drone_telemetry SET status = ? WHERE status != 'ERROR'";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, newStatus);
            pstmt.executeUpdate();
            log("COMMAND SENT: Swarm set to " + newStatus);
            refreshTable();
        } catch (Exception e) {
            log("Error sending command: " + e.getMessage());
        }
    }

    // --- VISION AI INTEGRATION ---
    private void simulateCameraEvent() {
        // 1. Pick a random drone
        int row = droneTable.getSelectedRow();
        String droneId = (row != -1) ? (String) droneTable.getValueAt(row, 0) : "DRONE-01";

        // 2. Generate a Dummy Image
        // 50% chance of Red (Fire), 50% chance of Green (Forest)
        boolean createFire = random.nextBoolean();
        String filename = "drone_view_" + System.currentTimeMillis() + ".png";
        File imgFile = new File(filename);

        try {
            BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            if (createFire) {
                g.setColor(Color.RED); // FIRE COLOR
            } else {
                g.setColor(new Color(34, 139, 34)); // FOREST GREEN
            }
            g.fillRect(0, 0, 100, 100);
            g.dispose();
            ImageIO.write(img, "png", imgFile);

            log("SNAPSHOT: " + droneId + " captured image. Sending to Python Vision...");

            // 3. Send Absolute Path to Python via Socket
            try (Socket socket = new Socket(PY_HOST, PY_PORT)) {
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Protocol: "DRONE_ID|IMAGE_PATH"
                out.println(droneId + "|" + imgFile.getAbsolutePath());

                // Read Result
                String result = in.readLine();
                log("AI ANALYSIS RESULT: " + result);

                // If Fire, Popup Alert
                if (result.contains("FIRE")) {
                    JOptionPane.showMessageDialog(this, "⚠️ " + result, "EMERGENCY ALERT", JOptionPane.WARNING_MESSAGE);
                }

            } catch (Exception ex) {
                log("Python Vision Service Offline.");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> logArea.append("> " + msg + "\n"));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new HiveMindController().setVisible(true));
    }
}