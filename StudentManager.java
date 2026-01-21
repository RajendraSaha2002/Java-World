import java.sql.*;
import java.util.Scanner;

/*
 * PREREQUISITES:
 * 1. Ensure you have the MySQL JDBC Driver (mysql-connector-j-8.x.jar) in your classpath.
 * 2. Update DB_USER and DB_PASS below with your MySQL credentials.
 */

public class StudentManager {
    // Database Credentials
    private static final String URL = "jdbc:mysql://localhost:3306/student_db";
    private static final String DB_USER = "root"; // Change this
    private static final String DB_PASS = "varrie75"; // Change this

    private static Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        try {
            // 1. CHECK FOR DRIVER FIRST
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                System.err.println("\n❌ ERROR: MySQL JDBC Driver not found!");
                System.err.println("------------------------------------------------");
                System.err.println("IntelliJ does not know where 'mysql-connector-j.jar' is.");
                System.err.println("Please follow the 'FIX_INTELLIJ.md' guide to add it.");
                System.exit(1); // Stop the program
            }

            while (true) {
                System.out.println("\n=== STUDENT RESULT MANAGEMENT (JAVA) ===");
                System.out.println("1. Add Student");
                System.out.println("2. View All Students");
                System.out.println("3. Update Marks");
                System.out.println("4. Delete Student");
                System.out.println("5. Exit");
                System.out.print("Enter choice: ");

                if (scanner.hasNextInt()) {
                    int choice = scanner.nextInt();
                    switch (choice) {
                        case 1: addStudent(); break;
                        case 2: viewStudents(); break;
                        case 3: updateMarks(); break;
                        case 4: deleteStudent(); break;
                        case 5:
                            System.out.println("Exiting Java Backend...");
                            return;
                        default: System.out.println("Invalid choice!");
                    }
                } else {
                    scanner.next(); // Clear invalid input
                    System.out.println("Please enter a number.");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 1. CREATE
    private static void addStudent() {
        try (Connection conn = DriverManager.getConnection(URL, DB_USER, DB_PASS)) {
            System.out.print("Enter Name: ");
            scanner.nextLine(); // Consume newline
            String name = scanner.nextLine();

            System.out.print("Enter Roll No: ");
            int roll = scanner.nextInt();

            System.out.print("Math Score: ");
            int math = scanner.nextInt();

            System.out.print("Science Score: ");
            int sci = scanner.nextInt();

            System.out.print("English Score: ");
            int eng = scanner.nextInt();

            String sql = "INSERT INTO student_results (name, roll_number, math, science, english) VALUES (?, ?, ?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, name);
            pstmt.setInt(2, roll);
            pstmt.setInt(3, math);
            pstmt.setInt(4, sci);
            pstmt.setInt(5, eng);

            int rows = pstmt.executeUpdate();
            if (rows > 0) System.out.println("Student Added Successfully!");

        } catch (SQLException e) {
            System.out.println("Error adding student: " + e.getMessage());
        }
    }

    // 2. READ
    private static void viewStudents() {
        try (Connection conn = DriverManager.getConnection(URL, DB_USER, DB_PASS)) {
            String sql = "SELECT * FROM student_results";
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);

            System.out.println("\nID | Name           | Roll | Math | Sci | Eng | Total | Avg");
            System.out.println("---------------------------------------------------------------");
            while (rs.next()) {
                int m = rs.getInt("math");
                int s = rs.getInt("science");
                int e = rs.getInt("english");
                int total = m + s + e;
                double avg = total / 3.0;

                System.out.printf("%-3d| %-15s| %-5d| %-5d| %-4d| %-4d| %-6d| %.2f%n",
                        rs.getInt("id"), rs.getString("name"), rs.getInt("roll_number"),
                        m, s, e, total, avg);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 3. UPDATE
    private static void updateMarks() {
        try (Connection conn = DriverManager.getConnection(URL, DB_USER, DB_PASS)) {
            System.out.print("Enter Roll Number to Update: ");
            int roll = scanner.nextInt();

            System.out.print("New Math Score: ");
            int math = scanner.nextInt();
            System.out.print("New Science Score: ");
            int sci = scanner.nextInt();
            System.out.print("New English Score: ");
            int eng = scanner.nextInt();

            String sql = "UPDATE student_results SET math=?, science=?, english=? WHERE roll_number=?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, math);
            pstmt.setInt(2, sci);
            pstmt.setInt(3, eng);
            pstmt.setInt(4, roll);

            int rows = pstmt.executeUpdate();
            if (rows > 0) System.out.println("Marks Updated!");
            else System.out.println("Student not found.");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 4. DELETE
    private static void deleteStudent() {
        try (Connection conn = DriverManager.getConnection(URL, DB_USER, DB_PASS)) {
            System.out.print("Enter Roll Number to Delete: ");
            int roll = scanner.nextInt();

            String sql = "DELETE FROM student_results WHERE roll_number=?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, roll);

            int rows = pstmt.executeUpdate();
            if (rows > 0) System.out.println("Student Deleted.");
            else System.out.println("Student not found.");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}