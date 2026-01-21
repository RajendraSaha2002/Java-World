import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ATMSystem {
        private static final Scanner scanner = new Scanner(System.in);
        private static final Bank bank = new Bank();

        public static void main(String[] args) {
            seedDemoAccounts();

            System.out.println("=== Welcome to Java ATM Simulator ===");

            while (true) {
                System.out.print("\nEnter account number (or 'exit' to quit): ");
                String input = scanner.nextLine().trim();
                if (input.equalsIgnoreCase("exit")) {
                    System.out.println("Goodbye!");
                    break;
                }

                Account user = bank.findAccount(input);
                if (user == null) {
                    System.out.println("Account not found. Try again.");
                    continue;
                }

                boolean authenticated = false;
                for (int tries = 0; tries < 3; tries++) {
                    System.out.print("Enter PIN: ");
                    String pin = scanner.nextLine().trim();
                    if (user.checkPin(pin)) {
                        authenticated = true;
                        break;
                    } else {
                        System.out.println("Incorrect PIN.");
                    }
                }

                if (!authenticated) {
                    System.out.println("Authentication failed. Returning to account prompt.");
                    continue;
                }

                System.out.printf("Welcome, %s!\n", user.getName());
                showMenu(user);
            }
        }

        private static void showMenu(Account user) {
            while (true) {
                System.out.println("\n--- Menu ---");
                System.out.println("1. Check balance");
                System.out.println("2. Deposit");
                System.out.println("3. Withdraw");
                System.out.println("4. Transfer");
                System.out.println("5. Mini-statement");
                System.out.println("6. Change PIN");
                System.out.println("7. Logout");

                System.out.print("Choose option: ");
                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1":
                        System.out.println("Current balance: ₹" + user.getBalance());
                        break;
                    case "2":
                        doDeposit(user);
                        break;
                    case "3":
                        doWithdraw(user);
                        break;
                    case "4":
                        doTransfer(user);
                        break;
                    case "5":
                        List<String> stm = user.getMiniStatement();
                        System.out.println("--- Mini-statement (latest first) ---");
                        if (stm.isEmpty()) {
                            System.out.println("No transactions yet.");
                        } else {
                            stm.forEach(System.out::println);
                        }
                        break;
                    case "6":
                        changePin(user);
                        break;
                    case "7":
                        System.out.println("Logged out.");
                        return;
                    default:
                        System.out.println("Invalid option, try again.");
                }
            }
        }

        private static void doDeposit(Account user) {
            System.out.print("Enter amount to deposit: ");
            String amtStr = scanner.nextLine().trim();
            try {
                BigDecimal amt = new BigDecimal(amtStr);
                if (amt.compareTo(BigDecimal.ZERO) <= 0) {
                    System.out.println("Amount must be positive.");
                    return;
                }
                user.deposit(amt);
                System.out.println("Deposit successful. New balance: ₹" + user.getBalance());
            } catch (NumberFormatException e) {
                System.out.println("Invalid amount.");
            }
        }

        private static void doWithdraw(Account user) {
            System.out.print("Enter amount to withdraw: ");
            String amtStr = scanner.nextLine().trim();
            try {
                BigDecimal amt = new BigDecimal(amtStr);
                if (amt.compareTo(BigDecimal.ZERO) <= 0) {
                    System.out.println("Amount must be positive.");
                    return;
                }
                boolean ok = user.withdraw(amt);
                if (ok) {
                    System.out.println("Withdrawal successful. New balance: ₹" + user.getBalance());
                } else {
                    System.out.println("Insufficient funds.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid amount.");
            }
        }

        private static void doTransfer(Account user) {
            System.out.print("Enter recipient account number: ");
            String to = scanner.nextLine().trim();
            Account recipient = bank.findAccount(to);
            if (recipient == null) {
                System.out.println("Recipient account not found.");
                return;
            }
            System.out.print("Enter amount to transfer: ");
            String amtStr = scanner.nextLine().trim();
            try {
                BigDecimal amt = new BigDecimal(amtStr);
                if (amt.compareTo(BigDecimal.ZERO) <= 0) {
                    System.out.println("Amount must be positive.");
                    return;
                }
                boolean ok = user.transferTo(recipient, amt);
                if (ok) {
                    System.out.println("Transfer successful. New balance: ₹" + user.getBalance());
                } else {
                    System.out.println("Insufficient funds for transfer.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid amount.");
            }
        }

        private static void changePin(Account user) {
            System.out.print("Enter current PIN: ");
            String current = scanner.nextLine().trim();
            if (!user.checkPin(current)) {
                System.out.println("Incorrect current PIN.");
                return;
            }
            System.out.print("Enter new 4-digit PIN: ");
            String newPin = scanner.nextLine().trim();
            if (newPin.length() < 4) {
                System.out.println("PIN too short. Use at least 4 digits/characters.");
                return;
            }
            user.setPin(newPin);
            System.out.println("PIN changed successfully.");
        }

        private static void seedDemoAccounts() {
            // Create some demo accounts
            Account a1 = new Account("1001", "Rajendra Saha", "1234", new BigDecimal("5000.00"));
            Account a2 = new Account("1002", "Anita Kumar", "2345", new BigDecimal("12000.50"));
            Account a3 = new Account("1003", "Vikram Singh", "3456", new BigDecimal("750.00"));

            bank.addAccount(a1);
            bank.addAccount(a2);
            bank.addAccount(a3);

            // Add some sample transactions
            a1.addTransaction("Initial balance: ₹5000.00");
            a2.addTransaction("Initial balance: ₹12000.50");
            a3.addTransaction("Initial balance: ₹750.00");
        }

        // ---------------------------
        // Bank and Account classes
        // ---------------------------

        static class Bank {
            private final Map<String, Account> accounts = new HashMap<>();

            public void addAccount(Account acc) {
                accounts.put(acc.getAccountNumber(), acc);
            }

            public Account findAccount(String accNumber) {
                return accounts.get(accNumber);
            }
        }

        static class Account {
            private final String accountNumber;
            private final String name;
            private String pin;
            private BigDecimal balance;
            private final LinkedList<String> transactions = new LinkedList<>();
            private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            public Account(String accountNumber, String name, String pin, BigDecimal initialBalance) {
                this.accountNumber = accountNumber;
                this.name = name;
                this.pin = pin;
                this.balance = initialBalance.setScale(2, BigDecimal.ROUND_HALF_EVEN);
            }

            public String getAccountNumber() {
                return accountNumber;
            }

            public String getName() {
                return name;
            }

            public boolean checkPin(String inputPin) {
                return this.pin.equals(inputPin);
            }

            public BigDecimal getBalance() {
                return balance.setScale(2, BigDecimal.ROUND_HALF_EVEN);
            }

            public synchronized void deposit(BigDecimal amount) {
                balance = balance.add(amount).setScale(2, BigDecimal.ROUND_HALF_EVEN);
                addTransaction(String.format("%s - Deposited: ₹%s | Balance: ₹%s", now(), amount.setScale(2), getBalance()));
            }

            public synchronized boolean withdraw(BigDecimal amount) {
                if (balance.compareTo(amount) < 0) return false;
                balance = balance.subtract(amount).setScale(2, BigDecimal.ROUND_HALF_EVEN);
                addTransaction(String.format("%s - Withdrawn: ₹%s | Balance: ₹%s", now(), amount.setScale(2), getBalance()));
                return true;
            }

            public synchronized boolean transferTo(Account to, BigDecimal amount) {
                if (balance.compareTo(amount) < 0) return false;
                // withdraw from this
                balance = balance.subtract(amount).setScale(2, BigDecimal.ROUND_HALF_EVEN);
                addTransaction(String.format("%s - Transferred: ₹%s to %s | Balance: ₹%s", now(), amount.setScale(2), to.getAccountNumber(), getBalance()));
                // deposit to recipient
                to.receiveTransfer(this, amount);
                return true;
            }

            private synchronized void receiveTransfer(Account from, BigDecimal amount) {
                balance = balance.add(amount).setScale(2, BigDecimal.ROUND_HALF_EVEN);
                addTransaction(String.format("%s - Received: ₹%s from %s | Balance: ₹%s", now(), amount.setScale(2), from.getAccountNumber(), getBalance()));
            }

            public synchronized void setPin(String newPin) {
                this.pin = newPin;
                addTransaction(now() + " - PIN changed");
            }

            public synchronized void addTransaction(String text) {
                transactions.addFirst(text);
                // keep only latest 20 transactions for mini-statement
                if (transactions.size() > 20) {
                    transactions.removeLast();
                }
            }

            public synchronized List<String> getMiniStatement() {
                return new ArrayList<>(transactions);
            }

            private String now() {
                return LocalDateTime.now().format(DF);
            }
        }
    }

