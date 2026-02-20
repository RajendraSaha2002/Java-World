import com.formdev.flatlaf.FlatDarkLaf;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class StrategicHQ extends JFrame {

    // DB Config
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = "varrie75";

    // UI Components
    private JTextArea intelLog;
    private JLabel statusLabel;
    private JPanel mapPanel;

    public StrategicHQ() {
        super("VAJRA-NET: JOINT THEATRE COMMAND [STRATEGIC LEVEL]");
        setSize(1000, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 1. Top Bar (Status)
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topBar.setBackground(new Color(40, 40, 40));
        statusLabel = new JLabel("DEFCON 4: SURVEILLANCE MODE");
        statusLabel.setForeground(Color.CYAN);
        statusLabel.setFont(new Font("Impact", Font.PLAIN, 24));
        topBar.add(statusLabel);
        add(topBar, BorderLayout.NORTH);

        // 2. Main Split (Map vs Intel)
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(700);

        // Left: Map (Placeholder)
        mapPanel = new JPanel();
        mapPanel.setBackground(new Color(20, 30, 40));
        mapPanel.setBorder(new TitledBorder("COMMON OPERATING PICTURE (COP)"));
        mapPanel.add(new JLabel("<html><center><h2>THEATRE MAP ACTIVE</h2><br>[Waiting for Radar Feeds...]</center></html>"));
        splitPane.setLeftComponent(mapPanel);

        // Right: Intel Feed & Orders
        JPanel rightPanel = new JPanel(new BorderLayout());

        intelLog = new JTextArea();
        intelLog.setBackground(Color.BLACK);
        intelLog.setForeground(Color.GREEN);
        intelLog.setFont(new Font("Monospaced", Font.PLAIN, 12));
        rightPanel.add(new JScrollPane(intelLog), BorderLayout.CENTER);

        JButton btnStrike = new JButton("AUTHORIZE STRIKE (SQN-18)");
        btnStrike.setBackground(new Color(150, 0, 0));
        btnStrike.setForeground(Color.WHITE);
        btnStrike.addActionListener(e -> issueStrikeOrder());
        rightPanel.add(btnStrike, BorderLayout.SOUTH);

        splitPane.setRightComponent(rightPanel);
        add(splitPane, BorderLayout.CENTER);

        // Start ZeroMQ Listener Thread
        new Thread(this::listenToTacticalFeed).start();

        setVisible(true);
    }

    // --- ZEROMQ LISTENER (The Fast Lane) ---
    private void listenToTacticalFeed() {
        try (ZContext context = new ZContext()) {
            // Subscribe to Python Publisher
            ZMQ.Socket subscriber = context.createSocket(SocketType.SUB);
            subscriber.connect("tcp://localhost:5555");
            subscriber.subscribe("RADAR_CONTACT".getBytes()); // Filter topic

            log("System: Connected to Tactical Data Link (ZMQ/5555)...");

            while (!Thread.currentThread().isInterrupted()) {
                // Block until message received
                String msg = subscriber.recvStr(0);
                if (msg != null) {
                    // Msg Format: "RADAR_CONTACT BOGEY 120.5 50.2"
                    processRadarData(msg);
                }
            }
        }
    }

    private void processRadarData(String msg) {
        String[] parts = msg.split(" ");
        String type = parts[1];
        String x = parts[2];
        String y = parts[3];

        SwingUtilities.invokeLater(() -> {
            log("TRACKING: " + type + " at Sector " + x + "," + y);

            if (type.equals("BOGEY")) {
                statusLabel.setText("DEFCON 1: THREAT DETECTED!");
                statusLabel.setForeground(Color.RED);

                // Visual Alert
                mapPanel.setBackground(new Color(50, 10, 10)); // Flash Red Background
            } else {
                statusLabel.setText("DEFCON 4: SURVEILLANCE MODE");
                statusLabel.setForeground(Color.CYAN);
                mapPanel.setBackground(new Color(20, 30, 40)); // Reset
            }
        });
    }

    // --- STRIKE ORDER LOGIC (PostgreSQL Write) ---
    private void issueStrikeOrder() {
        try {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
            String sql = "INSERT INTO strike_orders (authorized_by, asset_assigned, status) VALUES (?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, "GEN. RAWAT (CDS)");
            pstmt.setString(2, "SQN-18");
            pstmt.setString(3, "AUTHORIZED");

            pstmt.executeUpdate();
            conn.close();

            JOptionPane.showMessageDialog(this, "STRIKE ORDER ISSUED & LOGGED TO DB.");
            log("COMMAND: Strike Authorized for Asset SQN-18");

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Order Failed: " + e.getMessage());
        }
    }

    private void log(String text) {
        SwingUtilities.invokeLater(() -> {
            intelLog.append(text + "\n");
            intelLog.setCaretPosition(intelLog.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception ex) {}
        SwingUtilities.invokeLater(StrategicHQ::new);
    }
}