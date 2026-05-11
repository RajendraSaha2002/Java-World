import java.sql.*;

public class TitanDB {
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = "sharma30@";

    public boolean isJobAuthorized(String nodeId) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM job_scheduler WHERE node_id = ? AND is_active = TRUE")) {
            ps.setString(1, nodeId);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) { return false; }
    }

    public void logIntrusion(String nodeId, String threat, double load, double temp) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement("INSERT INTO security_logs (node_id, threat_type, cpu_load, temperature) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, nodeId); ps.setString(2, threat); ps.setDouble(3, load); ps.setDouble(4, temp);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }
}