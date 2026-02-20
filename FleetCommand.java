import com.formdev.flatlaf.FlatDarkLaf;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.general.DefaultPieDataset;

import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FleetCommand extends JFrame {

    // DB Config
    private static final String URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String USER = "postgres";
    private static final String PASS = "varrie75";

    // UI Components
    private JLabel shipStatusLabel;
    private JPanel statusPanel;
    private DefaultPieDataset fuelDataset;
    private JTextArea logArea;

    // Track last checked log to avoid duplicate alerts
    private long lastLogId = 0;

    public FleetCommand() {
        super("SAGAR-SURAKSHA: FLEET COMMAND");
        setSize(1000, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 1. Header (Ship Status)
        statusPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        statusPanel.setBackground(new Color(0, 100, 0)); // Default Green
        statusPanel.setPreferredSize(new Dimension(getWidth(), 80));

        shipStatusLabel = new JLabel("INS-VIKRANT: PATROLLING (GREEN)");
        shipStatusLabel.setFont(new Font("Impact", Font.BOLD, 30));
        shipStatusLabel.setForeground(Color.WHITE);
        statusPanel.add(shipStatusLabel);

        add(statusPanel, BorderLayout.NORTH);

        // 2. Logistics Charts (Center)
        JPanel chartsPanel = new JPanel(new GridLayout(1, 2));

        // Chart A: Fuel
        fuelDataset = new DefaultPieDataset();
        fuelDataset.setValue("Available", 85);
        fuelDataset.setValue("Used", 15);

        JFreeChart fuelChart = ChartFactory.createPieChart(
                "Fuel Reserves", fuelDataset, true, true, false);
        fuelChart.setBackgroundPaint(new Color(40, 40, 40));
        fuelChart.getPlot().setBackgroundPaint(new Color(40, 40, 40));
        chartsPanel.add(new ChartPanel(fuelChart));

        // Chart B: Ammo
        DefaultPieDataset ammoDataset = new DefaultPieDataset();
        ammoDataset.setValue("Missiles", 60);
        ammoDataset.setValue("Torpedoes", 30);
        ammoDataset.setValue("Empty Slots", 10);

        JFreeChart ammoChart = ChartFactory.createPieChart(
                "Ammunition Loadout", ammoDataset, true, true, false);
        ammoChart.setBackgroundPaint(new Color(40, 40, 40));
        ammoChart.getPlot().setBackgroundPaint(new Color(40, 40, 40));
        chartsPanel.add(new ChartPanel(ammoChart));

        add(chartsPanel, BorderLayout.CENTER);

        // 3. Log Panel
        logArea = new JTextArea(5, 20);
        logArea.setText("System Initialized. Monitoring Sonar Array...\n");
        add(new JScrollPane(logArea), BorderLayout.SOUTH);

        // Start Database Polling
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(this::pollForThreats);

        setVisible(true);
    }

    private void pollForThreats() {
        try {
            // First, get current max ID to ignore old history
            Connection initConn = DriverManager.getConnection(URL, USER, PASS);
            Statement initStmt = initConn.createStatement();
            ResultSet initRs = initStmt.executeQuery("SELECT MAX(log_id) FROM sonar_logs");
            if(initRs.next()) {
                lastLogId = initRs.getLong(1);
            }
            initConn.close();

            // Loop to check for new Mechanical threats
            while (true) {
                Connection conn = DriverManager.getConnection(URL, USER, PASS);
                PreparedStatement pstmt = conn.prepareStatement(
                        "SELECT log_id, classification, detected_freq_hz FROM sonar_logs WHERE log_id > ? AND classification = 'MECHANICAL' ORDER BY log_id ASC"
                );
                pstmt.setLong(1, lastLogId);

                ResultSet rs = pstmt.executeQuery();
                while(rs.next()) {
                    long id = rs.getLong("log_id");
                    int freq = rs.getInt("detected_freq_hz");

                    // Update tracker
                    lastLogId = id;

                    // Trigger Alert
                    triggerRedAlert(freq);
                }

                conn.close();
                Thread.sleep(2000); // Check every 2 seconds
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void triggerRedAlert(int freq) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("!!! ALERT: MECHANICAL SIGNATURE DETECTED (" + freq + "Hz) !!!\n");

            // Trigger Red Alert UI
            statusPanel.setBackground(Color.RED);
            shipStatusLabel.setText("!!! GENERAL QUARTERS - ENEMY SUB DETECTED !!!");

            // Flash Effect
            Timer timer = new Timer(500, e -> {
                Color c = statusPanel.getBackground();
                if (c == Color.RED) statusPanel.setBackground(Color.BLACK);
                else statusPanel.setBackground(Color.RED);
            });
            timer.start();
        });
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception ex) {}
        SwingUtilities.invokeLater(FleetCommand::new);
    }
}