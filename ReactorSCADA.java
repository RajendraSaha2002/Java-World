import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class ReactorSCADA extends JFrame {

    // --- CONFIGURATION ---
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/cyber_nuke_db";
    private static final String USER = "postgres";
    private static final String PASS = "varrie75"; // <--- UPDATE THIS
    private static final int BACKDOOR_PORT = 6666; // The Vulnerability

    // --- PHYSICS STATE ---
    private double coreTemp = 300.0; // Celsius (Meltdown at 1000)
    private int controlRodPos = 50;  // 0-100 (100 = Full insertion/Shutdown)
    private boolean coolantValveOpen = true;
    private int realFlowRate = 100;

    // --- STUXNET VARIABLES (Spoofed State) ---
    private boolean sensorSpoofingActive = false;
    private int spoofedFlowValue = 100;

    // GUI Components
    private JLabel lblTemp, lblFlow, lblValve, lblStatus;
    private JProgressBar tempBar;

    public ReactorSCADA() {
        setTitle("SCADA HMI // REACTOR UNIT 4");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Color.BLACK);

        initDB();
        setupUI();

        // 1. Start Physics Engine Loop
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updatePhysics();
                logTelemetry();
            }
        }, 0, 500); // 500ms Tick

        // 2. Start Command Poller (Listens for Python SCRAM)
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkCommands();
            }
        }, 0, 1000);

        // 3. Start Backdoor Listener (The Vulnerability)
        new Thread(this::startBackdoorServer).start();
    }

    private void initDB() {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS reactor_logs (id SERIAL PRIMARY KEY, timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, core_temp DECIMAL(10, 2), coolant_pressure DECIMAL(10, 2), control_rod_pos INT, valve_status VARCHAR(10), coolant_flow_rate INT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS command_queue (id SERIAL PRIMARY KEY, command VARCHAR(50), priority INT DEFAULT 1, issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, is_executed BOOLEAN DEFAULT FALSE)");
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "DB Init Error: " + e.getMessage());
        }
    }

    private void setupUI() {
        JPanel dash = new JPanel(new GridLayout(4, 1));
        dash.setBackground(Color.BLACK);

        lblStatus = new JLabel("SYSTEM NORMAL");
        lblStatus.setFont(new Font("Consolas", Font.BOLD, 24));
        lblStatus.setForeground(Color.GREEN);
        lblStatus.setHorizontalAlignment(SwingConstants.CENTER);
        dash.add(lblStatus);

        lblTemp = new JLabel("CORE TEMP: 300.0 C");
        lblTemp.setFont(new Font("Consolas", Font.BOLD, 18));
        lblTemp.setForeground(Color.CYAN);
        dash.add(lblTemp);

        tempBar = new JProgressBar(0, 1200);
        tempBar.setValue(300);
        tempBar.setStringPainted(true);
        dash.add(tempBar);

        JPanel flowPanel = new JPanel(new FlowLayout());
        flowPanel.setBackground(Color.BLACK);

        lblValve = new JLabel("[VALVE: OPEN] ");
        lblValve.setForeground(Color.GREEN);
        lblFlow = new JLabel("FLOW: 100%");
        lblFlow.setForeground(Color.WHITE);

        flowPanel.add(lblValve);
        flowPanel.add(lblFlow);
        dash.add(flowPanel);

        add(dash, BorderLayout.CENTER);
    }

    // --- PHYSICS ENGINE ---
    private void updatePhysics() {
        // 1. Calculate Real Flow
        realFlowRate = coolantValveOpen ? 100 : 0;

        // 2. Calculate Heat Gen vs Cooling
        double heatGen = (100 - controlRodPos) * 2.0; // Less rods = more heat
        double cooling = realFlowRate * 1.5;

        double netChange = heatGen - cooling;
        coreTemp += netChange;

        // Clamp Temp
        if (coreTemp < 20.0) coreTemp = 20.0;

        // 3. UI Updates
        SwingUtilities.invokeLater(() -> {
            lblTemp.setText(String.format("CORE TEMP: %.1f C", coreTemp));
            tempBar.setValue((int) coreTemp);

            // Show REAL valve status, but potentially SPOOFED flow
            lblValve.setText(coolantValveOpen ? "[VALVE: OPEN] " : "[VALVE: CLOSED] ");
            lblValve.setForeground(coolantValveOpen ? Color.GREEN : Color.RED);

            int displayFlow = sensorSpoofingActive ? spoofedFlowValue : realFlowRate;
            lblFlow.setText("FLOW SENSOR: " + displayFlow + "%");

            if (coreTemp > 1000) {
                lblStatus.setText("!!! MELTDOWN IMMINENT !!!");
                lblStatus.setForeground(Color.RED);
            }
        });
    }

    private void logTelemetry() {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            String sql = "INSERT INTO reactor_logs (core_temp, coolant_pressure, control_rod_pos, valve_status, coolant_flow_rate) VALUES (?, ?, ?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setDouble(1, coreTemp);
            pstmt.setDouble(2, 100.0); // Constant for demo
            pstmt.setInt(3, controlRodPos);
            pstmt.setString(4, coolantValveOpen ? "OPEN" : "CLOSED");

            // THE VULNERABILITY: Logging the SPOOFED value to the DB audit trail
            int loggedFlow = sensorSpoofingActive ? spoofedFlowValue : realFlowRate;
            pstmt.setInt(5, loggedFlow);

            pstmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- COMMAND POLLER ---
    private void checkCommands() {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT id, command FROM command_queue WHERE is_executed = FALSE ORDER BY priority DESC LIMIT 1");

            if (rs.next()) {
                int cmdId = rs.getInt("id");
                String cmd = rs.getString("command");

                if ("SCRAM".equals(cmd)) {
                    performSCRAM();
                } else if ("RESET".equals(cmd)) {
                    resetSystem();
                }

                stmt.executeUpdate("UPDATE command_queue SET is_executed = TRUE WHERE id = " + cmdId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void performSCRAM() {
        controlRodPos = 100; // Full insertion
        coolantValveOpen = true; // Fail-safe open
        sensorSpoofingActive = false; // Override hacks
        lblStatus.setText("!!! SCRAM TRIGGERED !!!");
        lblStatus.setForeground(Color.ORANGE);
        System.out.println("SCRAM EXECUTED: Rods dropped. Valve Open.");
    }

    private void resetSystem() {
        controlRodPos = 50;
        coreTemp = 300;
        coolantValveOpen = true;
        sensorSpoofingActive = false;
        lblStatus.setText("SYSTEM NORMAL");
        lblStatus.setForeground(Color.GREEN);
    }

    // --- THE VULNERABILITY (Backdoor) ---
    private void startBackdoorServer() {
        try (ServerSocket server = new ServerSocket(BACKDOOR_PORT)) {
            System.out.println("Backdoor Listener Active on Port " + BACKDOOR_PORT);
            while (true) {
                Socket client = server.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                String command = in.readLine(); // Expecting: "SPOOF_FLOW 100" or "CLOSE_VALVE"

                if (command != null) {
                    processHackerCommand(command);
                }
                client.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processHackerCommand(String cmd) {
        System.out.println("HACKER COMMAND RECEIVED: " + cmd);
        if (cmd.startsWith("SPOOF_FLOW")) {
            sensorSpoofingActive = true;
            spoofedFlowValue = Integer.parseInt(cmd.split(" ")[1]);
        } else if ("CLOSE_VALVE".equals(cmd)) {
            coolantValveOpen = false; // Physically close it
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ReactorSCADA().setVisible(true));
    }
}