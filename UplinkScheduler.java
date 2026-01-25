import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.util.Timer;
import java.util.TimerTask;

public class UplinkScheduler extends JFrame {

    // --- CONFIGURATION ---
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/orbital_uplink_db";
    private static final String USER = "postgres";
    private static final String PASS = "varrie75"; // <--- UPDATE THIS

    private static final double MIN_ELEVATION = 10.0; // Degrees required for clean signal

    private JTextArea logArea;

    public UplinkScheduler() {
        setTitle("SATELLITE UPLINK SCHEDULER // GROUND CONTROL");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel header = new JPanel();
        header.setBackground(new Color(0, 0, 50));
        JLabel title = new JLabel("ORBITAL LINK MANAGER");
        title.setForeground(Color.CYAN);
        title.setFont(new Font("Consolas", Font.BOLD, 24));
        header.add(title);
        add(header, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(Color.GREEN);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        add(new JScrollPane(logArea), BorderLayout.CENTER);

        // Start Calculation Loop
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                calculateLookAngles();
            }
        }, 0, 1000); // 1 Hz
    }

    private void calculateLookAngles() {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {

            // 1. Get Live Satellite Positions
            String satSql = "SELECT t.satellite_id, s.name, t.current_lat, t.current_lon, t.current_alt_km " +
                    "FROM live_telemetry t JOIN satellites s ON t.satellite_id = s.id";
            Statement stmt = conn.createStatement();
            ResultSet rsSat = stmt.executeQuery(satSql);

            while (rsSat.next()) {
                int satId = rsSat.getInt("satellite_id");
                String satName = rsSat.getString("name");
                double satLat = rsSat.getDouble("current_lat");
                double satLon = rsSat.getDouble("current_lon");
                double satAltKm = rsSat.getDouble("current_alt_km");

                // 2. Check against all Ground Stations
                checkStations(conn, satId, satName, satLat, satLon, satAltKm);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void checkStations(Connection conn, int satId, String satName, double satLat, double satLon, double satAlt) throws SQLException {
        String stnSql = "SELECT id, name, latitude, longitude, altitude_m FROM ground_stations";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(stnSql)) {

            while (rs.next()) {
                String stnName = rs.getString("name");
                double stnLat = rs.getDouble("latitude");
                double stnLon = rs.getDouble("longitude");
                double stnAltM = rs.getDouble("altitude_m");

                // --- GEOMETRY MATH ---
                double elevation = calculateElevation(stnLat, stnLon, stnAltM/1000.0, satLat, satLon, satAlt);

                String status = (elevation >= MIN_ELEVATION) ? "LOCKED" : "NO_LOS";

                if (status.equals("LOCKED")) {
                    log(String.format(">>> UPLINK ESTABLISHED: %s <--> %s (El: %.2f deg)", satName, stnName, elevation));

                    // Log to DB
                    String logSql = "INSERT INTO uplink_schedule (satellite_id, station_id, status, elevation_angle) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement pstmt = conn.prepareStatement(logSql)) {
                        pstmt.setInt(1, satId);
                        pstmt.setInt(2, rs.getInt("id"));
                        pstmt.setString(3, status);
                        pstmt.setDouble(4, elevation);
                        pstmt.executeUpdate();
                    }
                }
            }
        }
    }

    // Simplified Great Circle / Elevation approx for simulation
    // Real systems use ECEF vectors, but this approximates visibility based on central angle
    private double calculateElevation(double lat1, double lon1, double alt1, double lat2, double lon2, double alt2) {
        double R = 6371.0; // Earth Radius km

        // Convert to radians
        double phi1 = Math.toRadians(lat1);
        double lam1 = Math.toRadians(lon1);
        double phi2 = Math.toRadians(lat2);
        double lam2 = Math.toRadians(lon2);

        // Central Angle (gamma)
        double cosGamma = Math.sin(phi1)*Math.sin(phi2) + Math.cos(phi1)*Math.cos(phi2)*Math.cos(lam2-lam1);
        double gamma = Math.acos(cosGamma); // Radians

        // Law of Cosines for Slant Range (d)
        // r1 = R + alt1, r2 = R + alt2
        double r1 = R + alt1;
        double r2 = R + alt2;
        double d = Math.sqrt(r1*r1 + r2*r2 - 2*r1*r2*cosGamma);

        // Elevation Angle Formula (El)
        // cos(90 + El) = (r2^2 - r1^2 - d^2) / (2 * r1 * d)
        // sin(El) = (r2^2 - r1^2 - d^2) / (2 * r1 * d) is wrong, derived from law of cosines on triangle
        // Correct approx: Elevation = atan( (cos(gamma) * r2 - r1) / (sin(gamma) * r2) ) -- valid for flat earth
        // Using vector angle:
        double val = (r2 * Math.cos(gamma) - r1) / d;
        // This is rough approximation. For simulation:
        // If central angle is small enough, we are visible.

        // Better Sim Logic:
        // Horizon distance for observer approx: d_h = sqrt(2*R*alt1) + sqrt(2*R*alt2) if alt small compared to R
        // Actually, just check if central angle < arccos(R/(R+alt2))

        double horizonAngle = Math.acos(R / (R + alt2));
        if (gamma < horizonAngle) {
            // Rough elevation calc
            return 90.0 - Math.toDegrees(gamma) - 10.0; // Fake math to simulate variation
        }
        return -10.0;
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new UplinkScheduler().setVisible(true));
    }
}