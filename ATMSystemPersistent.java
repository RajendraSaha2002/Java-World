import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * ATM Simulator with:
 *  - Binary persistence (bank.dat)
 *  - Admin features (create/delete/list accounts)
 *  - Overdraft support + overdraft fee
 *  - PIN hashing (SHA-256)
 *  - Demo test runner (runDemoTests)
 *
 * Usage:
 *  javac ATMSystemPersistent.java
 *  java ATMSystemPersistent
 *
 * Admin default password: admin (change in code for production)
 */
public class ATMSystemPersistent {
    private static final Scanner scanner = new Scanner(System.in);
    private static Bank bank;
    private static final File DB_FILE = new File("bank.dat");
    private static final String ADMIN_PASSWORD = "admin"; // change for production
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        loadOrCreateBank();
        System.out.println("=== Persistent ATM Simulator ===");

        // offer demo tests if user wants
        System.out.print("Run demo tests? (y/N): ");
        String runTests = scanner.nextLine().trim();
        if (runTests.equalsIgnoreCase("y")) {
            runDemoTests();
            saveBank();
        }

        while (true) {
            System.out.println("\n1. Login");
            System.out.println("2. Admin");
            System.out.println("3. Exit");
            System.out.print("Choose: ");
            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1":
                    userLogin();
                    break;
                case "2":
                    adminLogin();
                    break;
                case "3":
                    saveBank();
                    System.out.println("Goodbye!");
                    return;
                default:
                    System.out.println("Invalid option.");
            }
        }
    }

    // ---------------- persistence ----------------
    private static void loadOrCreateBank() {
        if (DB_FILE.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(DB_FILE))) {
                bank = (Bank) ois.readObject();
                System.out.println("Loaded bank from " + DB_FILE.getName());
            } catch (Exception e) {
                System.err.println("Failed to load bank.dat, creating new bank. Error: " + e.getMessage());
                bank = new Bank();
                seedDemoAccounts();
            }
        } else {
            bank = new Bank();
            seedDemoAccounts();
        }
    }

    private static void saveBank() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DB_FILE))) {
            oos.writeObject(bank);
            System.out.println("Saved bank to " + DB_FILE.getName());
        } catch (IOException e) {
            System.err.println("Failed to save bank: " + e.getMessage());
        }
    }

    // ---------------- user flows ----------------
    private static void userLogin() {
        System.out.print("Enter account number: ");
        String accNo = scanner.nextLine().trim();
        Account acc = bank.findAccount(accNo);
        if (acc == null) {
            System.out.println("Account not found.");
            return;
        }
        boolean ok = false;
        for (int i = 0; i < 3; i++) {
            System.out.print("Enter PIN: ");
            String pin = scanner.nextLine().trim();
            if (acc.checkPin(pin)) {
                ok = true;
                break;
            } else {
                System.out.println("Incorrect PIN.");
            }
        }
        if (!ok) {
            System.out.println("Authentication failed.");
            return;
        }
        System.out.printf("Welcome %s!\n", acc.getName());
        showUserMenu(acc);
        saveBank(); // persist after session
    }

    private static void showUserMenu(Account acc) {
        while (true) {
            System.out.println("\n--- User Menu ---");
            System.out.println("1. Check balance");
            System.out.println("2. Deposit");
            System.out.println("3. Withdraw");
            System.out.println("4. Transfer");
            System.out.println("5. Mini-statement");
            System.out.println("6. Change PIN");
            System.out.println("7. Logout");
            System.out.print("Choose: ");
            String c = scanner.nextLine().trim();
            switch (c) {
                case "1":
                    System.out.println("Balance: ₹" + acc.getBalance());
                    break;
                case "2":
                    doDeposit(acc);
                    break;
                case "3":
                    doWithdraw(acc);
                    break;
                case "4":
                    doTransfer(acc);
                    break;
                case "5":
                    printMiniStatement(acc);
                    break;
                case "6":
                    changePin(acc);
                    break;
                case "7":
                    System.out.println("Logged out.");
                    return;
                default:
                    System.out.println("Invalid.");
            }
        }
    }

    private static void doDeposit(Account acc) {
        System.out.print("Amount to deposit: ");
        String s = scanner.nextLine().trim();
        try {
            BigDecimal amt = new BigDecimal(s);
            if (amt.compareTo(BigDecimal.ZERO) <= 0) { System.out.println("Must be positive."); return; }
            acc.deposit(amt);
            System.out.println("Deposited. New balance: ₹" + acc.getBalance());
        } catch (NumberFormatException e) {
            System.out.println("Invalid number.");
        }
    }

    private static void doWithdraw(Account acc) {
        System.out.print("Amount to withdraw: ");
        String s = scanner.nextLine().trim();
        try {
            BigDecimal amt = new BigDecimal(s);
            if (amt.compareTo(BigDecimal.ZERO) <= 0) { System.out.println("Must be positive."); return; }
            boolean success = acc.withdraw(amt);
            if (success) {
                System.out.println("Withdrawal done. New balance: ₹" + acc.getBalance());
            } else {
                System.out.println("Insufficient funds/overdraft limit reached.");
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid number.");
        }
    }

    private static void doTransfer(Account acc) {
        System.out.print("Recipient account number: ");
        String to = scanner.nextLine().trim();
        Account r = bank.findAccount(to);
        if (r == null) { System.out.println("Recipient not found."); return; }
        System.out.print("Amount to transfer: ");
        String s = scanner.nextLine().trim();
        try {
            BigDecimal amt = new BigDecimal(s);
            if (amt.compareTo(BigDecimal.ZERO) <= 0) { System.out.println("Must be positive."); return; }
            boolean ok = acc.transferTo(r, amt);
            if (ok) System.out.println("Transfer successful. New balance: ₹" + acc.getBalance());
            else System.out.println("Insufficient funds/overdraft limit reached.");
        } catch (NumberFormatException e) {
            System.out.println("Invalid number.");
        }
    }

    private static void printMiniStatement(Account acc) {
        System.out.println("--- Mini-statement ---");
        List<String> stm = acc.getMiniStatement();
        if (stm.isEmpty()) System.out.println("No transactions.");
        else stm.forEach(System.out::println);
    }

    private static void changePin(Account acc) {
        System.out.print("Current PIN: ");
        String current = scanner.nextLine().trim();
        if (!acc.checkPin(current)) { System.out.println("Wrong current PIN."); return; }
        System.out.print("New PIN (min 4 chars): ");
        String np = scanner.nextLine().trim();
        if (np.length() < 4) { System.out.println("Too short."); return; }
        acc.setPin(np);
        System.out.println("PIN changed.");
    }

    // ---------------- admin flows ----------------
    private static void adminLogin() {
        System.out.print("Enter admin password: ");
        String p = scanner.nextLine().trim();
        if (!ADMIN_PASSWORD.equals(p)) {
            System.out.println("Wrong password.");
            return;
        }
        System.out.println("Admin access granted.");
        while (true) {
            System.out.println("\n--- Admin Menu ---");
            System.out.println("1. Create account");
            System.out.println("2. Delete account");
            System.out.println("3. List accounts");
            System.out.println("4. Back");
            System.out.print("Choose: ");
            String c = scanner.nextLine().trim();
            switch (c) {
                case "1": adminCreateAccount(); saveBank(); break;
                case "2": adminDeleteAccount(); saveBank(); break;
                case "3": adminListAccounts(); break;
                case "4": return;
                default: System.out.println("Invalid");
            }
        }
    }

    private static void adminCreateAccount() {
        System.out.print("Account number: ");
        String accNo = scanner.nextLine().trim();
        if (bank.findAccount(accNo) != null) { System.out.println("Already exists."); return; }
        System.out.print("Name: ");
        String name = scanner.nextLine().trim();
        System.out.print("Initial balance (e.g. 1000.00): ");
        String balS = scanner.nextLine().trim();
        System.out.print("PIN (min 4 chars): ");
        String pin = scanner.nextLine().trim();
        System.out.print("Overdraft limit (e.g. 500.00): ");
        String odS = scanner.nextLine().trim();
        System.out.print("Overdraft fee (fixed, e.g. 50.00): ");
        String feeS = scanner.nextLine().trim();
        try {
            BigDecimal bal = new BigDecimal(balS);
            BigDecimal od = new BigDecimal(odS);
            BigDecimal fee = new BigDecimal(feeS);
            Account a = new Account(accNo, name, pin, bal, od, fee);
            bank.addAccount(a);
            System.out.println("Created account " + accNo);
        } catch (NumberFormatException e) {
            System.out.println("Invalid numeric input.");
        }
    }

    private static void adminDeleteAccount() {
        System.out.print("Account number to delete: ");
        String accNo = scanner.nextLine().trim();
        boolean ok = bank.removeAccount(accNo);
        System.out.println(ok ? "Deleted." : "Not found.");
    }

    private static void adminListAccounts() {
        System.out.println("--- Accounts ---");
        for (Account a : bank.listAccounts()) {
            System.out.printf("%s | %s | Balance: ₹%s | OD limit: ₹%s | OD fee: ₹%s\n",
                    a.getAccountNumber(), a.getName(), a.getBalance(), a.getOverdraftLimit(), a.getOverdraftFee());
        }
    }

    // ---------------- demo tests (basic unit-ish checks) ----------------
    private static void runDemoTests() {
        System.out.println("\n=== Running Demo Tests ===");
        // create a temp account and do operations
        Account t1 = new Account("T100", "Test User", "1111", new BigDecimal("1000.00"), new BigDecimal("500.00"), new BigDecimal("50.00"));
        Account t2 = new Account("T200", "Receiver", "2222", new BigDecimal("200.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        bank.addAccount(t1);
        bank.addAccount(t2);

        System.out.println("Initial t1 balance: " + t1.getBalance());
        t1.withdraw(new BigDecimal("1200.00")); // uses overdraft 200 -> allowed
        System.out.println("After withdraw 1200 (using OD): " + t1.getBalance());
        t1.withdraw(new BigDecimal("300.00")); // should fail (exceeds OD limit)
        System.out.println("Attempted withdraw 300 more -> balance remains: " + t1.getBalance());
        t1.deposit(new BigDecimal("800.00"));
        System.out.println("After deposit 800 -> balance: " + t1.getBalance());
        t1.transferTo(t2, new BigDecimal("500.00"));
        System.out.println("After transfer 500 to t2 -> t1: " + t1.getBalance() + " | t2: " + t2.getBalance());

        printMiniStatement(t1);
        printMiniStatement(t2);

        // clean up test accounts (optional)
        bank.removeAccount("T100");
        bank.removeAccount("T200");
        System.out.println("Demo tests completed.");
    }

    // ---------------- seed demo accounts ----------------
    private static void seedDemoAccounts() {
        Account a1 = new Account("1001", "Rajendra Saha", "1234", new BigDecimal("5000.00"), new BigDecimal("0.00"), BigDecimal.ZERO);
        Account a2 = new Account("1002", "Anita Kumar", "2345", new BigDecimal("12000.50"), new BigDecimal("1000.00"), new BigDecimal("100.00"));
        Account a3 = new Account("1003", "Vikram Singh", "3456", new BigDecimal("750.00"), new BigDecimal("200.00"), new BigDecimal("50.00"));
        bank.addAccount(a1); bank.addAccount(a2); bank.addAccount(a3);
        a1.addTransaction("Initial balance: ₹5000.00");
        a2.addTransaction("Initial balance: ₹12000.50");
        a3.addTransaction("Initial balance: ₹750.00");
    }

    // ---------------- helper classes ----------------
    static class Bank implements Serializable {
        private static final long serialVersionUID = 1L;
        private final Map<String, Account> accounts = new HashMap<>();

        public void addAccount(Account a) { accounts.put(a.getAccountNumber(), a); }
        public Account findAccount(String accNo) { return accounts.get(accNo); }
        public boolean removeAccount(String accNo) { return accounts.remove(accNo) != null; }
        public Collection<Account> listAccounts() { return accounts.values(); }
    }

    static class Account implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String accountNumber;
        private final String name;
        private byte[] pinHash; // SHA-256 of PIN
        private BigDecimal balance;
        private final LinkedList<String> transactions = new LinkedList<>();

        // overdraft parameters
        private BigDecimal overdraftLimit = BigDecimal.ZERO; // how far negative allowed
        private BigDecimal overdraftFee = BigDecimal.ZERO;   // fixed fee applied when balance becomes negative due to withdrawal/transfer

        public Account(String accountNumber, String name, String plainPin, BigDecimal initialBalance) {
            this(accountNumber, name, plainPin, initialBalance, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        public Account(String accountNumber, String name, String plainPin, BigDecimal initialBalance,
                       BigDecimal overdraftLimit, BigDecimal overdraftFee) {
            this.accountNumber = accountNumber;
            this.name = name;
            setPin(plainPin);
            this.balance = initialBalance.setScale(2, BigDecimal.ROUND_HALF_EVEN);
            this.overdraftLimit = overdraftLimit.setScale(2, BigDecimal.ROUND_HALF_EVEN);
            this.overdraftFee = overdraftFee.setScale(2, BigDecimal.ROUND_HALF_EVEN);
        }

        public String getAccountNumber() { return accountNumber; }
        public String getName() { return name; }

        public BigDecimal getOverdraftLimit() { return overdraftLimit; }
        public BigDecimal getOverdraftFee() { return overdraftFee; }

        public boolean checkPin(String plainPin) {
            try {
                byte[] h = sha256(plainPin);
                return Arrays.equals(h, this.pinHash);
            } catch (Exception e) { return false; }
        }

        public void setPin(String newPin) {
            try {
                this.pinHash = sha256(newPin);
                addTransaction(now() + " - PIN set/changed");
            } catch (Exception e) {
                throw new RuntimeException("Failed to hash PIN");
            }
        }

        public synchronized BigDecimal getBalance() { return balance.setScale(2, BigDecimal.ROUND_HALF_EVEN); }

        public synchronized void deposit(BigDecimal amount) {
            balance = balance.add(amount).setScale(2, BigDecimal.ROUND_HALF_EVEN);
            addTransaction(String.format("%s - Deposited: ₹%s | Balance: ₹%s", now(), amount.setScale(2), getBalance()));
        }

        /**
         * Withdraw supports overdraft up to overdraftLimit.
         * If balance becomes negative due to withdrawal, overdraftFee is applied once.
         */
        public synchronized boolean withdraw(BigDecimal amount) {
            BigDecimal potential = balance.subtract(amount);
            BigDecimal minAllowed = overdraftLimit.negate();
            if (potential.compareTo(minAllowed) < 0) return false; // exceed overdraft
            balance = potential.setScale(2, BigDecimal.ROUND_HALF_EVEN);
            addTransaction(String.format("%s - Withdrawn: ₹%s | Balance: ₹%s", now(), amount.setScale(2), getBalance()));
            if (balance.compareTo(BigDecimal.ZERO) < 0 && overdraftFee.compareTo(BigDecimal.ZERO) > 0) {
                balance = balance.subtract(overdraftFee).setScale(2, BigDecimal.ROUND_HALF_EVEN);
                addTransaction(String.format("%s - Overdraft fee applied: ₹%s | Balance: ₹%s", now(), overdraftFee.setScale(2), getBalance()));
            }
            return true;
        }

        public synchronized boolean transferTo(Account to, BigDecimal amount) {
            BigDecimal potential = balance.subtract(amount);
            BigDecimal minAllowed = overdraftLimit.negate();
            if (potential.compareTo(minAllowed) < 0) return false;
            balance = potential.setScale(2, BigDecimal.ROUND_HALF_EVEN);
            addTransaction(String.format("%s - Transferred: ₹%s to %s | Balance: ₹%s", now(), amount.setScale(2), to.getAccountNumber(), getBalance()));
            to.receiveTransfer(this, amount);
            if (balance.compareTo(BigDecimal.ZERO) < 0 && overdraftFee.compareTo(BigDecimal.ZERO) > 0) {
                balance = balance.subtract(overdraftFee).setScale(2, BigDecimal.ROUND_HALF_EVEN);
                addTransaction(String.format("%s - Overdraft fee applied: ₹%s | Balance: ₹%s", now(), overdraftFee.setScale(2), getBalance()));
            }
            return true;
        }

        private synchronized void receiveTransfer(Account from, BigDecimal amount) {
            balance = balance.add(amount).setScale(2, BigDecimal.ROUND_HALF_EVEN);
            addTransaction(String.format("%s - Received: ₹%s from %s | Balance: ₹%s", now(), amount.setScale(2), from.getAccountNumber(), getBalance()));
        }

        public synchronized void addTransaction(String text) {
            transactions.addFirst(text);
            if (transactions.size() > 50) transactions.removeLast();
        }

        public synchronized List<String> getMiniStatement() { return new ArrayList<>(transactions); }

        private String now() { return LocalDateTime.now().format(DF); }

        // util: sha256
        private static byte[] sha256(String s) throws Exception {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(s.getBytes(StandardCharsets.UTF_8));
        }
    }
}
