import java.sql.*;
import java.util.concurrent.TimeUnit;

public class JammerAI {

    // --- CONFIGURATION ---
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/glass_battlefield_db";
    private static final String USER = "postgres";
    private static final String PASS = "varrie75"; // <--- UPDATE THIS

    public static void main(String[] args) {
        System.out.println("=== RED FORCE EW AI ONLINE ===");
        System.out.println("Scanning RF Spectrum for Blue Force comms...");

        while (true) {
            try {
                runEWCycle();
                // AI "Thinking" time
                TimeUnit.SECONDS.sleep(2);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void runEWCycle() throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {

            // 1. SPECTRUM ANALYZER
            // Find the frequency used by the most Blue units
            String scanSql = "SELECT frequency_mhz, COUNT(*) as count " +
                    "FROM units GROUP BY frequency_mhz " +
                    "ORDER BY count DESC LIMIT 1";

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(scanSql);

            if (rs.next()) {
                double targetFreq = rs.getDouble("frequency_mhz");
                int unitCount = rs.getInt("count");

                System.out.printf("[SCANNER] Detected %d units broadcasting on %.1f MHz.%n", unitCount, targetFreq);

                // 2. ELECTRONIC ATTACK
                // Re-tune all jammers to this frequency
                String jamSql = "UPDATE jammers SET target_freq_mhz = ?";
                PreparedStatement pstmt = conn.prepareStatement(jamSql);
                pstmt.setDouble(1, targetFreq);
                int updated = pstmt.executeUpdate();

                if (updated > 0) {
                    System.out.printf("   >>> JAMMING INITIATED on %.1f MHz (Power: MAX)%n", targetFreq);
                }
            } else {
                System.out.println("[SCANNER] No signals detected. Standing by.");
            }
        }
    }
}