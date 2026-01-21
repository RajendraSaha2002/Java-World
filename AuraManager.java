import java.sql.*;
import java.util.Scanner;

public class AuraManager {

    // --- CONFIGURATION ---
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/aura_chronicles";
    private static final String USER = "postgres";
    private static final String PASS = "varrie75"; // <--- UPDATE THIS

    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            System.out.println("=== AURA CHRONICLES MANAGER (THE BRAIN) ===");
            System.out.println("Connected to The Soul (Database).");

            Scanner scanner = new Scanner(System.in);

            while (true) {
                System.out.println("\n[1] Add new poetic line");
                System.out.println("[2] View all lines");
                System.out.println("[3] Exit");
                System.out.print("Select option: ");

                String choice = scanner.nextLine();

                if (choice.equals("1")) {
                    addNewLine(conn, scanner);
                } else if (choice.equals("2")) {
                    viewLines(conn);
                } else if (choice.equals("3")) {
                    System.out.println("Exiting...");
                    break;
                }
            }
        } catch (SQLException e) {
            System.err.println("Connection Failed: " + e.getMessage());
            System.err.println("Ensure PostgreSQL is running and credentials are correct.");
        }
    }

    private static void addNewLine(Connection conn, Scanner scanner) throws SQLException {
        System.out.print("Enter new poetic line: ");
        String text = scanner.nextLine();

        System.out.print("Enter display order (e.g., 13): ");
        int order = Integer.parseInt(scanner.nextLine());

        String sql = "INSERT INTO poetic_lines (line_text, display_order) VALUES (?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, text);
            pstmt.setInt(2, order);
            int rows = pstmt.executeUpdate();
            if (rows > 0) {
                System.out.println(">>> Success. The thought has been immortalized.");
                System.out.println(">>> The Python Face will pick this up in the next cycle.");
            }
        }
    }

    private static void viewLines(Connection conn) throws SQLException {
        String sql = "SELECT display_order, line_text FROM poetic_lines ORDER BY display_order";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            System.out.println("\n--- CURRENT CHRONICLE ---");
            while (rs.next()) {
                System.out.printf("[%d] %s%n", rs.getInt("display_order"), rs.getString("line_text"));
            }
            System.out.println("-------------------------");
        }
    }
}