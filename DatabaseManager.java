import java.sql.*;

public class DatabaseManager {
    private static final String URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String USER = "postgres"; // Change to your DB username
    private static final String PASS = "sharma30@"; // Change to your DB password
    private Connection conn;

    public DatabaseManager() {
        try {
            conn = DriverManager.getConnection(URL, USER, PASS);
            System.out.println("[DB] Connected to PostgreSQL Correlation Engine.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void logThreat(String ip, int layer, String payload) {
        String sql = "INSERT INTO unified_threat_ledger (source_ip, attack_layer, payload) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ip);
            pstmt.setInt(2, layer);
            pstmt.setString(3, payload);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean checkForAptAttack(String ip) {
        String sql = "SELECT * FROM apt_view WHERE source_ip = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ip);
            ResultSet rs = pstmt.executeQuery();
            return rs.next(); // Returns true if the IP is currently flagged in the APT view
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
}