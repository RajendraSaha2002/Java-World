import javax.swing.*;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.concurrent.ConcurrentHashMap;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;

public class AetherisDashboard extends JFrame {

    // 1. DATA CLASS (Moved to top so the IDE finds it immediately)
    static class Asset {
        String id, type;
        double lat, lon;
        Asset(String id, String type, double lat, double lon) {
            this.id = id; this.type = type; this.lat = lat; this.lon = lon;
        }
    }

    // 2. CLASS VARIABLES (This creates the liveAssets map)
    private RadarPanel radarPanel;
    private TickerPanel tickerPanel;
    private ConcurrentHashMap<String, Asset> liveAssets = new ConcurrentHashMap<>();
    private boolean isRedAlert = false;

    // 3. DATABASE CREDENTIALS
    private final String DB_URL = "jdbc:postgresql://localhost:5432/aetheris_db";
    private final String DB_USER = "postgres";
    private final String DB_PASS = "varrie75"; // Put your DB password here

    public AetherisDashboard() {
        setTitle("AETHERIS - Advanced Tactical Dashboard");
        setSize(1200, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Color.BLACK);

        JPanel sidebar = createSidebar();
        add(sidebar, BorderLayout.WEST);

        radarPanel = new RadarPanel();
        add(radarPanel, BorderLayout.CENTER);

        tickerPanel = new TickerPanel();
        add(tickerPanel, BorderLayout.SOUTH);

        startDatabaseListener();
    }

    private JPanel createSidebar() {
        JPanel panel = new JPanel(new GridLayout(6, 1, 10, 10));
        panel.setBackground(new Color(20, 30, 40));
        panel.setPreferredSize(new Dimension(200, getHeight()));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 10, 20, 10));

        JButton btnMap = styleButton("Theater Map");
        JButton btnPMO = styleButton("PMO Secure Link");
        JButton btnAnalytics = styleButton("Jointness Analytics");
        JButton btnRedAlert = styleButton("RED ALERT");

        btnRedAlert.setBackground(new Color(150, 0, 0));
        btnRedAlert.addActionListener(e -> toggleRedAlert());

        panel.add(btnMap);
        panel.add(btnPMO);
        panel.add(btnAnalytics);
        panel.add(new JLabel("")); // Spacer
        panel.add(btnRedAlert);

        return panel;
    }

    private JButton styleButton(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(new Color(40, 60, 80));
        btn.setForeground(Color.CYAN);
        btn.setFocusPainted(false);
        btn.setFont(new Font("Monospaced", Font.BOLD, 14));
        return btn;
    }

    private void toggleRedAlert() {
        isRedAlert = !isRedAlert;
        Color alertColor = isRedAlert ? new Color(50, 0, 0) : Color.BLACK;
        radarPanel.setBackground(alertColor);
    }

    private void startDatabaseListener() {
        new Thread(() -> {
            try {
                Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                PGConnection pgConn = conn.unwrap(PGConnection.class);

                Statement stmt = conn.createStatement();
                stmt.execute("LISTEN tactical_channel");
                stmt.execute("LISTEN pmo_channel");

                while (true) {
                    stmt.executeQuery("SELECT 1").close();
                    PGNotification[] notifications = pgConn.getNotifications();
                    if (notifications != null) {
                        for (PGNotification notif : notifications) {
                            if (notif.getName().equals("tactical_channel")) {
                                processTacticalData(notif.getParameter());
                            } else if (notif.getName().equals("pmo_channel")) {
                                tickerPanel.addMessage(notif.getParameter());
                            }
                        }
                    }
                    Thread.sleep(200);
                }
            } catch (Exception e) {
                System.out.println("DB Error: " + e.getMessage());
            }
        }).start();
    }

    private void processTacticalData(String payload) {
        try {
            String[] parts = payload.split(",");
            if (parts.length == 4) {
                String id = parts[0];
                String type = parts[1];
                double lat = Double.parseDouble(parts[2]);
                double lon = Double.parseDouble(parts[3]);

                // NO MORE ERROR HERE!
                liveAssets.put(id, new Asset(id, type, lat, lon));
            }
        } catch (Exception e) {
            System.out.println("Parse Error: " + e.getMessage());
        }
    }

    class RadarPanel extends JPanel {
        private int sweepAngle = 0;

        public RadarPanel() {
            setBackground(Color.BLACK);
            Timer timer = new Timer(16, e -> {
                sweepAngle = (sweepAngle + 2) % 360;
                repaint();
            });
            timer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();
            int cx = width / 2;
            int cy = height / 2;
            int radius = Math.min(width, height) / 2 - 20;

            g2d.setColor(isRedAlert ? new Color(255, 0, 0, 50) : new Color(0, 255, 0, 50));
            g2d.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);
            g2d.drawOval(cx - radius/2, cy - radius/2, radius, radius);
            g2d.drawLine(cx - radius, cy, cx + radius, cy);
            g2d.drawLine(cx, cy - radius, cx, cy + radius);

            g2d.setColor(isRedAlert ? new Color(255, 0, 0, 80) : new Color(0, 255, 0, 80));
            g2d.fill(new Arc2D.Double(cx - radius, cy - radius, radius * 2, radius * 2, -sweepAngle, 30, Arc2D.PIE));

            g2d.setFont(new Font("Monospaced", Font.BOLD, 12));
            for (Asset asset : liveAssets.values()) {
                int ax = cx + (int)((asset.lon - 74.0) * 50);
                int ay = cy - (int)((asset.lat - 14.0) * 50);

                if (asset.type.equals("SHIP")) g2d.setColor(Color.CYAN);
                else if (asset.type.equals("JET")) g2d.setColor(Color.YELLOW);
                else g2d.setColor(Color.WHITE);

                g2d.fillOval(ax - 4, ay - 4, 8, 8);
                g2d.drawString(asset.id, ax + 10, ay + 5);
            }
        }
    }

    class TickerPanel extends JPanel {
        private String currentMessage = "SYSTEM ONLINE. AWAITING SECURE LINK...";
        private int xPos = 1200;

        public TickerPanel() {
            setPreferredSize(new Dimension(getWidth(), 40));
            setBackground(new Color(10, 10, 10));
            setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, Color.DARK_GRAY));

            Timer timer = new Timer(20, e -> {
                xPos -= 2;
                if (xPos < -800) xPos = getWidth();
                repaint();
            });
            timer.start();
        }

        public void addMessage(String msg) {
            this.currentMessage = "|| VERIFIED SIGNATURE || " + msg;
            this.xPos = getWidth();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setColor(Color.GREEN);
            g2d.setFont(new Font("Monospaced", Font.BOLD, 16));
            g2d.drawString(currentMessage, xPos, 25);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new AetherisDashboard().setVisible(true);
        });
    }
}