import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * EmployeePayrollSystem.java
 *
 * Simple console Employee Payroll System with CSV persistence.
 *
 * Features:
 *  - Add / Edit / Delete / List employees
 *  - Calculate monthly payroll (gross, PF, tax, other deductions, net)
 *  - Generate payslip text file
 *  - Save/load employees from employees.csv
 *
 * How to run:
 *   javac EmployeePayrollSystem.java
 *   java EmployeePayrollSystem
 *
 * Data files created:
 *   - employees.csv
 *   - payslip_*.txt (generated per payroll)
 *
 * Note: This is a learning project. For production, use secure storage,
 * proper tax rules, and robust input validation.
 */
public class EmployeePayrollSystem {
    private static final Scanner scanner = new Scanner(System.in);
    private static final String EMP_FILE = "employees.csv";
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // In-memory employee store: key -> employeeId
    private static final Map<String, Employee> employees = new LinkedHashMap<>();

    public static void main(String[] args) {
        loadEmployees();
        seedIfEmpty();

        while (true) {
            System.out.println("\n=== Employee Payroll System ===");
            System.out.println("1. Add employee");
            System.out.println("2. Edit employee");
            System.out.println("3. Delete employee");
            System.out.println("4. List employees");
            System.out.println("5. Calculate payroll & generate payslip");
            System.out.println("6. Exit");
            System.out.print("Choose option: ");
            String opt = scanner.nextLine().trim();

            switch (opt) {
                case "1": addEmployee(); break;
                case "2": editEmployee(); break;
                case "3": deleteEmployee(); break;
                case "4": listEmployees(); break;
                case "5": calculatePayrollFlow(); break;
                case "6": saveEmployees(); System.out.println("Goodbye!"); return;
                default: System.out.println("Invalid option.");
            }
        }
    }

    // ---------------- Employee operations ----------------

    private static void addEmployee() {
        System.out.println("\n--- Add Employee ---");
        System.out.print("Employee ID (unique): ");
        String id = scanner.nextLine().trim();
        if (id.isEmpty() || employees.containsKey(id)) {
            System.out.println("Invalid or duplicate ID.");
            return;
        }
        System.out.print("Full name: ");
        String name = scanner.nextLine().trim();
        System.out.print("Designation: ");
        String desig = scanner.nextLine().trim();
        BigDecimal basic = readMoney("Basic salary (monthly): ");
        BigDecimal hra = readMoney("HRA (monthly): ");
        BigDecimal otherAllowance = readMoney("Other allowances (monthly): ");
        BigDecimal otherDeductions = readMoney("Other deductions (monthly): ");

        Employee e = new Employee(id, name, desig, basic, hra, otherAllowance, otherDeductions);
        employees.put(id, e);
        saveEmployees();
        System.out.println("Added employee " + id + " - " + name);
    }

    private static void editEmployee() {
        System.out.print("Enter Employee ID to edit: ");
        String id = scanner.nextLine().trim();
        Employee e = employees.get(id);
        if (e == null) { System.out.println("Not found."); return; }
        System.out.println("Editing " + e.fullName + " (" + e.employeeId + ")");
        System.out.println("Press Enter to keep current value.");

        System.out.print("Full name [" + e.fullName + "]: ");
        String nm = scanner.nextLine().trim();
        if (!nm.isEmpty()) e.fullName = nm;

        System.out.print("Designation [" + e.designation + "]: ");
        String d = scanner.nextLine().trim();
        if (!d.isEmpty()) e.designation = d;

        String s;
        s = readLineOptional("Basic salary [" + e.basicSalary + "]: ");
        if (!s.isEmpty()) e.basicSalary = new BigDecimal(s);

        s = readLineOptional("HRA [" + e.hra + "]: ");
        if (!s.isEmpty()) e.hra = new BigDecimal(s);

        s = readLineOptional("Other allowances [" + e.otherAllowances + "]: ");
        if (!s.isEmpty()) e.otherAllowances = new BigDecimal(s);

        s = readLineOptional("Other deductions [" + e.otherDeductions + "]: ");
        if (!s.isEmpty()) e.otherDeductions = new BigDecimal(s);

        saveEmployees();
        System.out.println("Updated.");
    }

    private static void deleteEmployee() {
        System.out.print("Enter Employee ID to delete: ");
        String id = scanner.nextLine().trim();
        if (employees.remove(id) != null) {
            saveEmployees();
            System.out.println("Deleted.");
        } else {
            System.out.println("Not found.");
        }
    }

    private static void listEmployees() {
        System.out.println("\n--- Employees ---");
        if (employees.isEmpty()) { System.out.println("No employees."); return; }
        System.out.printf("%-10s %-20s %-15s %-12s%n", "ID", "Name", "Designation", "Basic");
        for (Employee e : employees.values()) {
            System.out.printf("%-10s %-20s %-15s %-12s%n",
                    e.employeeId, truncate(e.fullName,20), e.designation, e.basicSalary);
        }
    }

    // ---------------- Payroll calculation flow ----------------

    private static void calculatePayrollFlow() {
        System.out.print("Enter Employee ID for payroll: ");
        String id = scanner.nextLine().trim();
        Employee e = employees.get(id);
        if (e == null) { System.out.println("Not found."); return; }
        System.out.print("Enter payroll month (YYYYMM) [default current]: ");
        String month = scanner.nextLine().trim();
        if (month.isEmpty()) month = LocalDate.now().format(YM);

        // Options for PF %
        BigDecimal pfRate = readPercentageOrDefault("Provident Fund (PF) % of basic (employee contribution) [default 12]: ", new BigDecimal("12"));
        BigDecimal professionalTax = readMoneyOrDefault("Professional tax (fixed monthly) [default 200]: ", new BigDecimal("200"));

        PayrollResult r = calculatePayroll(e, pfRate, professionalTax);

        System.out.println("\nPayslip preview for " + e.fullName + " (" + month + "):");
        printPayrollResult(r);

        System.out.print("Generate payslip file? (y/n): ");
        String ch = scanner.nextLine().trim();
        if (ch.equalsIgnoreCase("y")) {
            String filename = generatePayslipFile(e, month, r);
            System.out.println("Payslip saved: " + filename);
        }
    }

    // Core payroll calculation
    private static PayrollResult calculatePayroll(Employee e, BigDecimal pfPercent, BigDecimal profTax) {
        // Basic salary, HRA, allowances, other deductions are monthly
        BigDecimal basic = e.basicSalary;
        BigDecimal hra = e.hra;
        BigDecimal otherAllow = e.otherAllowances;
        BigDecimal gross = basic.add(hra).add(otherAllow).setScale(2, RoundingMode.HALF_EVEN);

        // Deductions
        BigDecimal pf = basic.multiply(pfPercent).divide(new BigDecimal("100"), 2, RoundingMode.HALF_EVEN);
        BigDecimal otherDed = e.otherDeductions.setScale(2, RoundingMode.HALF_EVEN);

        BigDecimal taxableIncome = gross.subtract(pf).subtract(otherDed).multiply(new BigDecimal("12")); // annual
        if (taxableIncome.compareTo(BigDecimal.ZERO) < 0) taxableIncome = BigDecimal.ZERO;

        BigDecimal annualTax = computeAnnualTax(taxableIncome);
        BigDecimal monthlyTax = annualTax.divide(new BigDecimal("12"), 2, RoundingMode.HALF_EVEN);

        BigDecimal totalDeductions = pf.add(otherDed).add(monthlyTax).add(profTax).setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal netPay = gross.subtract(totalDeductions).setScale(2, RoundingMode.HALF_EVEN);

        return new PayrollResult(gross, pf, otherDed, profTax, monthlyTax, totalDeductions, netPay, taxableIncome, annualTax);
    }

    // Example progressive tax slabs (adjustable)
    // This is a simplified illustration. Replace with real rules as needed.
    private static BigDecimal computeAnnualTax(BigDecimal annualTaxable) {
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal remaining = annualTaxable;

        // Slabs:
        // up to 250,000: 0%
        // 250,001 - 500,000: 5%
        // 500,001 - 1,000,000: 20%
        // above 1,000,000: 30%

        BigDecimal slab1 = new BigDecimal("250000");
        BigDecimal slab2 = new BigDecimal("500000");
        BigDecimal slab3 = new BigDecimal("1000000");

        if (remaining.compareTo(slab1) <= 0) return BigDecimal.ZERO;

        // slab2 portion
        if (remaining.compareTo(slab1) > 0) {
            BigDecimal upTo = remaining.min(slab2).subtract(slab1);
            if (upTo.compareTo(BigDecimal.ZERO) > 0) {
                tax = tax.add(upTo.multiply(new BigDecimal("0.05")));
            }
        }

        // slab3 portion
        if (remaining.compareTo(slab2) > 0) {
            BigDecimal upTo = remaining.min(slab3).subtract(slab2);
            if (upTo.compareTo(BigDecimal.ZERO) > 0) {
                tax = tax.add(upTo.multiply(new BigDecimal("0.20")));
            }
        }

        // above slab3
        if (remaining.compareTo(slab3) > 0) {
            BigDecimal upTo = remaining.subtract(slab3);
            tax = tax.add(upTo.multiply(new BigDecimal("0.30")));
        }

        // Round
        return tax.setScale(2, RoundingMode.HALF_EVEN);
    }

    // Generate payslip file
    private static String generatePayslipFile(Employee e, String monthYm, PayrollResult r) {
        String filename = String.format("payslip_%s_%s.txt", e.employeeId, monthYm);
        try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(filename))) {
            bw.write("Payslip for month: " + monthYm);
            bw.newLine();
            bw.write("Generated on: " + LocalDate.now().format(DF));
            bw.newLine();
            bw.newLine();
            bw.write("Employee ID: " + e.employeeId);
            bw.newLine();
            bw.write("Name: " + e.fullName);
            bw.newLine();
            bw.write("Designation: " + e.designation);
            bw.newLine();
            bw.newLine();
            bw.write("---- Earnings ----");
            bw.newLine();
            bw.write(String.format("Basic: %s%n", e.basicSalary));
            bw.write(String.format("HRA: %s%n", e.hra));
            bw.write(String.format("Other Allowances: %s%n", e.otherAllowances));
            bw.newLine();
            bw.write("Gross Pay: " + r.gross);
            bw.newLine();
            bw.newLine();
            bw.write("---- Deductions ----");
            bw.newLine();
            bw.write("PF (monthly): " + r.pf);
            bw.newLine();
            bw.write("Other Deductions: " + r.otherDeductions);
            bw.newLine();
            bw.write("Professional Tax: " + r.professionalTax);
            bw.newLine();
            bw.write("Income Tax (monthly): " + r.monthlyTax);
            bw.newLine();
            bw.newLine();
            bw.write("Total Deductions: " + r.totalDeductions);
            bw.newLine();
            bw.newLine();
            bw.write("Net Pay: " + r.netPay);
            bw.newLine();
            bw.newLine();
            bw.write("Annual Taxable Income: " + r.annualTaxable);
            bw.newLine();
            bw.write("Annual Tax (computed): " + r.annualTax);
            bw.newLine();
        } catch (IOException ex) {
            System.out.println("Failed to write payslip: " + ex.getMessage());
            return null;
        }
        return filename;
    }

    private static void printPayrollResult(PayrollResult r) {
        System.out.println("Gross Pay: " + r.gross);
        System.out.println(" PF (monthly): " + r.pf);
        System.out.println(" Other Deductions: " + r.otherDeductions);
        System.out.println(" Professional Tax: " + r.professionalTax);
        System.out.println(" Income Tax (monthly): " + r.monthlyTax);
        System.out.println("Total Deductions: " + r.totalDeductions);
        System.out.println("Net Pay: " + r.netPay);
        System.out.println("Annual Taxable Income: " + r.annualTaxable);
        System.out.println("Annual Tax: " + r.annualTax);
    }

    // ---------------- Persistence (CSV) ----------------

    private static void loadEmployees() {
        Path p = Paths.get(EMP_FILE);
        if (!Files.exists(p)) return;
        try (BufferedReader br = Files.newBufferedReader(p)) {
            String line;
            while ((line = br.readLine()) != null) {
                // CSV format: id,fullName,designation,basic,hra,otherAllowances,otherDeductions
                if (line.trim().isEmpty()) continue;
                String[] parts = splitCsv(line);
                if (parts.length < 7) continue;
                Employee e = new Employee(
                        parts[0],
                        parts[1],
                        parts[2],
                        new BigDecimal(parts[3]),
                        new BigDecimal(parts[4]),
                        new BigDecimal(parts[5]),
                        new BigDecimal(parts[6])
                );
                employees.put(e.employeeId, e);
            }
        } catch (IOException ex) {
            System.out.println("Failed to load employees: " + ex.getMessage());
        }
    }

    private static void saveEmployees() {
        try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(EMP_FILE))) {
            for (Employee e : employees.values()) {
                // simple CSV; escaping commas not implemented (names with commas will break)
                String line = String.join(",",
                        e.employeeId,
                        e.fullName,
                        e.designation,
                        e.basicSalary.toPlainString(),
                        e.hra.toPlainString(),
                        e.otherAllowances.toPlainString(),
                        e.otherDeductions.toPlainString()
                );
                bw.write(line);
                bw.newLine();
            }
        } catch (IOException ex) {
            System.out.println("Failed to save employees: " + ex.getMessage());
        }
    }

    // Very simple CSV splitter that supports no quoted commas (for demo purposes)
    private static String[] splitCsv(String line) {
        return line.split(",", -1);
    }

    // ---------------- Utilities & Helpers ----------------

    private static BigDecimal readMoney(String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = scanner.nextLine().trim();
            try {
                BigDecimal v = new BigDecimal(s);
                return v.setScale(2, RoundingMode.HALF_EVEN);
            } catch (Exception e) {
                System.out.println("Invalid number. Try again.");
            }
        }
    }

    private static BigDecimal readMoneyOrDefault(String prompt, BigDecimal def) {
        System.out.print(prompt);
        String s = scanner.nextLine().trim();
        if (s.isEmpty()) return def.setScale(2, RoundingMode.HALF_EVEN);
        try {
            return new BigDecimal(s).setScale(2, RoundingMode.HALF_EVEN);
        } catch (Exception e) {
            System.out.println("Invalid input, using default " + def);
            return def.setScale(2, RoundingMode.HALF_EVEN);
        }
    }

    private static BigDecimal readPercentageOrDefault(String prompt, BigDecimal def) {
        System.out.print(prompt);
        String s = scanner.nextLine().trim();
        if (s.isEmpty()) return def.setScale(2, RoundingMode.HALF_EVEN);
        try {
            return new BigDecimal(s).setScale(2, RoundingMode.HALF_EVEN);
        } catch (Exception e) {
            System.out.println("Invalid input, using default " + def);
            return def.setScale(2, RoundingMode.HALF_EVEN);
        }
    }

    private static String readLineOptional(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    private static String truncate(String s, int len) {
        if (s.length() <= len) return s;
        return s.substring(0, len - 3) + "...";
    }

    // Seed some employees for first run
    private static void seedIfEmpty() {
        if (!employees.isEmpty()) return;
        Employee a = new Employee("E1001", "Rajendra Saha", "Engineer", new BigDecimal("30000.00"),
                new BigDecimal("9000.00"), new BigDecimal("2000.00"), new BigDecimal("500.00"));
        Employee b = new Employee("E1002", "Anita Kumar", "Manager", new BigDecimal("50000.00"),
                new BigDecimal("15000.00"), new BigDecimal("5000.00"), new BigDecimal("1000.00"));
        employees.put(a.employeeId, a);
        employees.put(b.employeeId, b);
        saveEmployees();
    }

    // ---------------- Data classes ----------------

    private static class Employee {
        String employeeId;
        String fullName;
        String designation;
        BigDecimal basicSalary;
        BigDecimal hra;
        BigDecimal otherAllowances;
        BigDecimal otherDeductions;

        Employee(String id, String name, String desig,
                 BigDecimal basic, BigDecimal hra, BigDecimal otherAllow, BigDecimal otherDed) {
            this.employeeId = id;
            this.fullName = name;
            this.designation = desig;
            this.basicSalary = basic.setScale(2, RoundingMode.HALF_EVEN);
            this.hra = hra.setScale(2, RoundingMode.HALF_EVEN);
            this.otherAllowances = otherAllow.setScale(2, RoundingMode.HALF_EVEN);
            this.otherDeductions = otherDed.setScale(2, RoundingMode.HALF_EVEN);
        }
    }

    private static class PayrollResult {
        BigDecimal gross;
        BigDecimal pf;
        BigDecimal otherDeductions;
        BigDecimal professionalTax;
        BigDecimal monthlyTax;
        BigDecimal totalDeductions;
        BigDecimal netPay;
        BigDecimal annualTaxable;
        BigDecimal annualTax;

        PayrollResult(BigDecimal gross, BigDecimal pf, BigDecimal otherDeductions, BigDecimal professionalTax,
                      BigDecimal monthlyTax, BigDecimal totalDeductions, BigDecimal netPay,
                      BigDecimal annualTaxable, BigDecimal annualTax) {
            this.gross = gross;
            this.pf = pf;
            this.otherDeductions = otherDeductions;
            this.professionalTax = professionalTax;
            this.monthlyTax = monthlyTax;
            this.totalDeductions = totalDeductions;
            this.netPay = netPay;
            this.annualTaxable = annualTaxable;
            this.annualTax = annualTax;
        }
    }
}
