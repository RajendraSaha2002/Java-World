import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SkyShieldCommand extends JFrame {

    // --- CONFIGURATION ---
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/skyshield_db";
    private static final String USER = "postgres";
    private static final String PASS = "varrie75"; // <--- UPDATE THIS

    private static final int MAP_WIDTH = 800;
    private static final int MAP_HEIGHT = 600;
    private static final int CITY_X = MAP_WIDTH / 2;
    private static final int CITY_Y = MAP_HEIGHT / 2;

    // Components
    private TacticalMapPanel mapPanel;
    private JTextArea eventLog;

    // Data State
    private List<Track> localTracks = new ArrayList<>();
    private final Object lock = new Object(); // For thread safety

    // Thread Pool
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

    public SkyShieldCommand() {
        setTitle("SKYSHIELD IADS // CENTRAL COMMAND (SWING)");
        setSize(1100, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Color.BLACK);

        setupUI();
        initDB();

        // --- START SERVICES ---

        // 1. Radar Simulator (Generates Data)
        scheduler.scheduleAtFixedRate(this::radarSimulationService, 0, 1, TimeUnit.SECONDS);

        // 2. Data Fetcher (Reads DB for UI)
        scheduler.scheduleAtFixedRate(this::fetchDataService, 0, 500, TimeUnit.MILLISECONDS);
    }

    private void setupUI() {
        // Map Area
        mapPanel = new TacticalMapPanel();
        mapPanel.setPreferredSize(new Dimension(MAP_WIDTH, MAP_HEIGHT));
        add(mapPanel, BorderLayout.CENTER);

        // Sidebar / Controls
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(250, MAP_HEIGHT));
        sidebar.setBackground(new Color(20, 20, 20));
        sidebar.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("SYSTEM LOGS");
        title.setForeground(Color.GREEN);
        title.setFont(new Font("Consolas", Font.BOLD, 16));
        sidebar.add(title, BorderLayout.NORTH);

        eventLog = new JTextArea();
        eventLog.setEditable(false);
        eventLog.setBackground(Color.BLACK);
        eventLog.setForeground(Color.GREEN);
        eventLog.setFont(new Font("Consolas", Font.PLAIN, 12));
        eventLog.setLineWrap(true);
        sidebar.add(new JScrollPane(eventLog), BorderLayout.CENTER);

        JButton btnSim = new JButton("GENERATE CONTACT");
        btnSim.setBackground(new Color(0, 100, 0));
        btnSim.setForeground(Color.WHITE);
        btnSim.setFocusPainted(false);
        btnSim.addActionListener(e -> generateSingleTrack());
        sidebar.add(btnSim, BorderLayout.SOUTH);

        add(sidebar, BorderLayout.EAST);
    }

    private void initDB() {
        // Optional: Add table creation logic here if needed
        // Assuming schema.sql was run or Python script initialized it
    }

    // --- SERVICE 1: RADAR SIMULATOR ---
    private void radarSimulationService() {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            // 1. Move Existing Tracks
            String updateSql = "UPDATE tracks SET x_pos = x_pos + (CASE WHEN x_pos < ? THEN 5 ELSE -5 END), " +
                    "y_pos = y_pos + (CASE WHEN y_pos < ? THEN 5 ELSE -5 END) " +
                    "WHERE status = 'LIVE'";
            try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                pstmt.setInt(1, CITY_X);
                pstmt.setInt(2, CITY_Y);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void generateSingleTrack() {
        new Thread(() -> {
            try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
                Random rand = new Random();
                String uuid = "T-" + (1000 + rand.nextInt(9000));
                int x = rand.nextBoolean() ? 0 : MAP_WIDTH;
                int y = rand.nextInt(MAP_HEIGHT);
                int speed = 200 + rand.nextInt(1500);
                int heading = rand.nextInt(360);

                String sql = "INSERT INTO tracks (track_uuid, x_pos, y_pos, speed_knots, heading_deg, altitude_ft, status) VALUES (?, ?, ?, ?, ?, ?, 'LIVE')";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, uuid);
                    pstmt.setInt(2, x);
                    pstmt.setInt(3, y);
                    pstmt.setInt(4, speed);
                    pstmt.setInt(5, heading);
                    pstmt.setInt(6, rand.nextInt(40000));
                    pstmt.executeUpdate();
                    log("RADAR: New Contact " + uuid + " (" + speed + " kts)");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // --- SERVICE 2: DATA FETCHER ---
    private void fetchDataService() {
        List<Track> freshTracks = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT id, track_uuid, x_pos, y_pos, threat_score, iff_status, status FROM tracks WHERE status IN ('LIVE', 'ENGAGED')");

            while (rs.next()) {
                freshTracks.add(new Track(
                        rs.getInt("id"), rs.getString("track_uuid"),
                        rs.getInt("x_pos"), rs.getInt("y_pos"),
                        rs.getInt("threat_score"), rs.getString("status")
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Update UI Data safely
        synchronized (lock) {
            localTracks = freshTracks;
        }
        SwingUtilities.invokeLater(() -> mapPanel.repaint());
    }

    // --- MANUAL OVERRIDE ---
    private void handleOverride(int x, int y) {
        synchronized (lock) {
            for (Track t : localTracks) {
                if (Math.abs(t.x - x) < 20 && Math.abs(t.y - y) < 20) {
                    log("COMMAND: Manual Override on " + t.uuid);
                    new Thread(() -> forceHostile(t.id)).start();
                    return;
                }
            }
        }
    }

    private void forceHostile(int trackId) {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            String sql = "UPDATE tracks SET threat_score = 100, iff_status = 'HOSTILE' WHERE id = ?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, trackId);
            pstmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> eventLog.append("> " + msg + "\n"));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SkyShieldCommand app = new SkyShieldCommand();
            app.setVisible(true);
        });
    }

    // --- INNER CLASS: MAP VISUALIZER ---
    class TacticalMapPanel extends JPanel {
        public TacticalMapPanel() {
            setBackground(new Color(5, 5, 5));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        handleOverride(e.getX(), e.getY());
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Draw Grid
            g2.setColor(new Color(0, 50, 0));
            for(int i=0; i<getWidth(); i+=50) g2.drawLine(i, 0, i, getHeight());
            for(int i=0; i<getHeight(); i+=50) g2.drawLine(0, i, getWidth(), i);

            // Draw City
            g2.setColor(Color.BLUE);
            g2.fillOval(CITY_X - 5, CITY_Y - 5, 10, 10);
            g2.drawOval(CITY_X - 100, CITY_Y - 100, 200, 200);

            // Draw Tracks
            synchronized (lock) {
                for (Track t : localTracks) {
                    Color c = Color.GREEN;
                    if (t.score > 50) c = Color.ORANGE;
                    if (t.score > 90) c = Color.RED;
                    if (t.status.equals("ENGAGED")) c = Color.MAGENTA;

                    g2.setColor(c);
                    g2.fillOval(t.x, t.y, 10, 10);

                    g2.setFont(new Font("Arial", Font.PLAIN, 10));
                    g2.drawString(t.uuid + " [" + t.score + "]", t.x + 12, t.y);

                    if (t.status.equals("ENGAGED")) {
                        g2.setColor(Color.RED);
                        g2.drawLine(CITY_X, CITY_Y, t.x, t.y);
                    }
                }
            }
        }
    }

    // Data Class
    static class Track {
        int id, x, y, score;
        String uuid, status;
        public Track(int id, String uuid, int x, int y, int score, String status) {
            this.id = id; this.uuid = uuid; this.x = x; this.y = y; this.score = score; this.status = status;
        }
    }
}