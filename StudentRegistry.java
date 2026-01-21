import java.sql.*;
import java.util.Scanner;

public class StudentRegistry {

    // --- CONFIGURATION ---
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/school_db";
    private static final String USER = "postgres";
    private static final String PASS = "varrie75"; // <--- UPDATE THIS
    // ---------------------

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\n=== CONSOLE STUDENT REGISTRY ===");
            System.out.println("1. Add Student");
            System.out.println("2. View All Students");
            System.out.println("3. Exit");
            System.out.print("Enter choice: ");

            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    addStudent(scanner);
                    break;
                case "2":
                    viewStudents();
                    break;
                case "3":
                    System.out.println("Goodbye!");
                    return;
                default:
                    System.out.println("Invalid option.");
            }
        }
    }

    // --- DB CONNECTION HELPER ---
    // This acts as your "DBConnection" logic
    private static Connection connect() {
        Connection conn = null;
        try {
            // Load Driver (Optional in newer Java versions, but good practice for learning)
            Class.forName("org.postgresql.Driver");
            conn = DriverManager.getConnection(DB_URL, USER, PASS);
        } catch (ClassNotFoundException e) {
            System.out.println("PostgreSQL JDBC Driver not found! Did you add the .jar?");
        } catch (SQLException e) {
            System.out.println("Connection Failed: " + e.getMessage());
        }
        return conn;
    }

    // --- OPTION 1: ADD STUDENT (INSERT) ---
    private static void addStudent(Scanner scanner) {
        System.out.print("Enter Full Name: ");
        String name = scanner.nextLine();

        System.out.print("Enter Email: ");
        String email = scanner.nextLine();

        System.out.print("Enter Grade Level (number): ");
        int grade = Integer.parseInt(scanner.nextLine());

        // SQL Query with Placeholders (?)
        // This is safer than concatenating strings!
        String sql = "INSERT INTO students (full_name, email, grade_level) VALUES (?, ?, ?)";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            if (conn == null) return;

            // Fill in the blanks (?)
            pstmt.setString(1, name);
            pstmt.setString(2, email);
            pstmt.setInt(3, grade);

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("✅ Student registered successfully!");
            }

        } catch (SQLException e) {
            System.out.println("Error adding student: " + e.getMessage());
        }
    }

    // --- OPTION 2: VIEW STUDENTS (SELECT) ---
    private static void viewStudents() {
        String sql = "SELECT id, full_name, email, grade_level FROM students ORDER BY id";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (conn == null) return;

            System.out.println("\n--- Student List ---");
            System.out.printf("%-5s %-20s %-25s %-5s%n", "ID", "Name", "Email", "Grade");
            System.out.println("----------------------------------------------------------");

            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("full_name");
                String email = rs.getString("email");
                int grade = rs.getInt("grade_level");

                System.out.printf("%-5d %-20s %-25s %-5d%n", id, name, email, grade);
            }
            System.out.println("----------------------------------------------------------");

        } catch (SQLException e) {
            System.out.println("Error fetching students: " + e.getMessage());
        }
    }
}