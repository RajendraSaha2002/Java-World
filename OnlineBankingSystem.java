import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

// ==================== Main Application Class ====================
public class OnlineBankingSystem {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new LoginFrame();
        });
    }
}

// ==================== Transaction Class ====================
class Transaction implements Serializable {
    private static final long serialVersionUID = 1L;

    private String type;
    private double amount;
    private double balanceAfter;
    private String description;
    private Date timestamp;

    public Transaction(String type, double amount, double balanceAfter, String description) {
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.description = description;
        this.timestamp = new Date();
    }

    public String getType() { return type; }
    public double getAmount() { return amount; }
    public double getBalanceAfter() { return balanceAfter; }
    public String getDescription() { return description; }
    public Date getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return String.format("%-20s | %-15s | $%-10.2f | $%-10.2f | %s",
                sdf.format(timestamp), type, amount, balanceAfter, description);
    }
}

// ==================== User Class ====================
class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private String accountNumber;
    private String username;
    private String password;
    private String name;
    private double balance;
    private List<Transaction> transactions;

    public User(String accountNumber, String username, String password, String name, double balance) {
        this.accountNumber = accountNumber;
        this.username = username;
        this.password = password;
        this.name = name;
        this.balance = balance;
        this.transactions = new ArrayList<>();
    }

    // Getters and Setters
    public String getAccountNumber() { return accountNumber; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getName() { return name; }
    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }
    public List<Transaction> getTransactions() { return transactions; }

    public void addTransaction(Transaction transaction) {
        transactions.add(transaction);
    }

    public void deposit(double amount) {
        balance += amount;
        addTransaction(new Transaction("DEPOSIT", amount, balance, "Deposit to account"));
    }

    public boolean withdraw(double amount) {
        if (balance >= amount) {
            balance -= amount;
            addTransaction(new Transaction("WITHDRAWAL", amount, balance, "Withdrawal from account"));
            return true;
        }
        return false;
    }

    public boolean transfer(User recipient, double amount) {
        if (balance >= amount) {
            balance -= amount;
            recipient.setBalance(recipient.getBalance() + amount);
            addTransaction(new Transaction("TRANSFER_OUT", amount, balance,
                    "Transfer to " + recipient.getAccountNumber()));
            recipient.addTransaction(new Transaction("TRANSFER_IN", amount,
                    recipient.getBalance(), "Transfer from " + this.accountNumber));
            return true;
        }
        return false;
    }
}

// ==================== Bank Data Manager Class ====================
class BankDataManager {
    private static final String DATA_FILE = "bank_data.dat";
    private Map<String, User> users;

    public BankDataManager() {
        loadData();
    }

    @SuppressWarnings("unchecked")
    private void loadData() {
        File file = new File(DATA_FILE);
        if (file.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                users = (Map<String, User>) ois.readObject();
            } catch (Exception e) {
                e.printStackTrace();
                initializeDefaultUsers();
            }
        } else {
            initializeDefaultUsers();
        }
    }

    private void initializeDefaultUsers() {
        users = new HashMap<>();
        // Create default users
        users.put("user1", new User("ACC001", "user1", "pass1", "John Doe", 5000.00));
        users.put("user2", new User("ACC002", "user2", "pass2", "Jane Smith", 7500.00));
        users.put("admin", new User("ACC003", "admin", "admin123", "Admin User", 10000.00));
        saveData();
    }

    public void saveData() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {
            oos.writeObject(users);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public User authenticate(String username, String password) {
        User user = users.get(username);
        if (user != null && user.getPassword().equals(password)) {
            return user;
        }
        return null;
    }

    public User getUserByAccountNumber(String accountNumber) {
        for (User user : users.values()) {
            if (user.getAccountNumber().equals(accountNumber)) {
                return user;
            }
        }
        return null;
    }

    public boolean createUser(String accountNumber, String username, String password, String name, double initialBalance) {
        if (users.containsKey(username)) {
            return false;
        }
        users.put(username, new User(accountNumber, username, password, name, initialBalance));
        saveData();
        return true;
    }

    public Map<String, User> getAllUsers() {
        return users;
    }
}

// ==================== Login Frame Class ====================
class LoginFrame extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private BankDataManager dataManager;

    public LoginFrame() {
        dataManager = new BankDataManager();

        setTitle("Online Banking System - Login");
        setSize(400, 280);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initComponents();
        setVisible(true);
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Title
        JLabel titleLabel = new JLabel("Online Banking System", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setForeground(new Color(0, 102, 204));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        // Form Panel
        JPanel formPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));

        JLabel userLabel = new JLabel("Username:");
        userLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        formPanel.add(userLabel);

        usernameField = new JTextField();
        usernameField.setFont(new Font("Arial", Font.PLAIN, 14));
        formPanel.add(usernameField);

        JLabel passLabel = new JLabel("Password:");
        passLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        formPanel.add(passLabel);

        passwordField = new JPasswordField();
        passwordField.setFont(new Font("Arial", Font.PLAIN, 14));
        formPanel.add(passwordField);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        // Button Panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton loginButton = new JButton("Login");
        JButton exitButton = new JButton("Exit");

        loginButton.setPreferredSize(new Dimension(100, 35));
        loginButton.setBackground(new Color(0, 153, 76));
        loginButton.setForeground(Color.WHITE);
        loginButton.setFont(new Font("Arial", Font.BOLD, 14));
        loginButton.setFocusPainted(false);

        exitButton.setPreferredSize(new Dimension(100, 35));
        exitButton.setBackground(new Color(204, 0, 0));
        exitButton.setForeground(Color.WHITE);
        exitButton.setFont(new Font("Arial", Font.BOLD, 14));
        exitButton.setFocusPainted(false);

        loginButton.addActionListener(e -> login());
        exitButton.addActionListener(e -> System.exit(0));

        // Enter key support
        passwordField.addActionListener(e -> login());

        buttonPanel.add(loginButton);
        buttonPanel.add(exitButton);

        // Bottom Panel with info
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(buttonPanel, BorderLayout.NORTH);

        JLabel infoLabel = new JLabel("<html><center><i>Default Accounts:</i><br>user1/pass1 | user2/pass2 | admin/admin123</center></html>");
        infoLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        infoLabel.setForeground(Color.GRAY);
        bottomPanel.add(infoLabel, BorderLayout.SOUTH);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private void login() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please enter both username and password!",
                    "Login Error",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        User user = dataManager.authenticate(username, password);
        if (user != null) {
            dispose();
            new DashboardFrame(user, dataManager);
        } else {
            JOptionPane.showMessageDialog(this,
                    "Invalid username or password!",
                    "Login Failed",
                    JOptionPane.ERROR_MESSAGE);
            passwordField.setText("");
        }
    }
}

// ==================== Dashboard Frame Class ====================
class DashboardFrame extends JFrame {
    private User currentUser;
    private BankDataManager dataManager;
    private JLabel balanceLabel;

    public DashboardFrame(User user, BankDataManager dataManager) {
        this.currentUser = user;
        this.dataManager = dataManager;

        setTitle("Online Banking - Dashboard");
        setSize(900, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initComponents();
        setVisible(true);
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Header Panel
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(0, 102, 204));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel welcomeLabel = new JLabel("Welcome, " + currentUser.getName());
        welcomeLabel.setFont(new Font("Arial", Font.BOLD, 20));
        welcomeLabel.setForeground(Color.WHITE);

        balanceLabel = new JLabel(String.format("Balance: $%.2f", currentUser.getBalance()));
        balanceLabel.setFont(new Font("Arial", Font.BOLD, 18));
        balanceLabel.setForeground(new Color(255, 255, 153));

        JLabel accountLabel = new JLabel("Account: " + currentUser.getAccountNumber());
        accountLabel.setFont(new Font("Arial", Font.PLAIN, 13));
        accountLabel.setForeground(Color.WHITE);

        JPanel leftHeader = new JPanel(new BorderLayout());
        leftHeader.setOpaque(false);
        leftHeader.add(welcomeLabel, BorderLayout.NORTH);
        leftHeader.add(accountLabel, BorderLayout.SOUTH);

        headerPanel.add(leftHeader, BorderLayout.WEST);
        headerPanel.add(balanceLabel, BorderLayout.EAST);

        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Button Panel
        JPanel buttonPanel = new JPanel(new GridLayout(2, 3, 15, 15));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JButton depositBtn = createStyledButton("💰 Deposit", new Color(0, 153, 76));
        JButton withdrawBtn = createStyledButton("💵 Withdraw", new Color(255, 140, 0));
        JButton transferBtn = createStyledButton("🔄 Transfer", new Color(70, 130, 180));
        JButton statementBtn = createStyledButton("📄 Statement", new Color(138, 43, 226));
        JButton changePasswordBtn = createStyledButton("🔒 Change Password", new Color(220, 20, 60));
        JButton logoutBtn = createStyledButton("🚪 Logout", new Color(105, 105, 105));

        depositBtn.addActionListener(e -> depositMoney());
        withdrawBtn.addActionListener(e -> withdrawMoney());
        transferBtn.addActionListener(e -> transferMoney());
        statementBtn.addActionListener(e -> viewStatement());
        changePasswordBtn.addActionListener(e -> changePassword());
        logoutBtn.addActionListener(e -> logout());

        buttonPanel.add(depositBtn);
        buttonPanel.add(withdrawBtn);
        buttonPanel.add(transferBtn);
        buttonPanel.add(statementBtn);
        buttonPanel.add(changePasswordBtn);
        buttonPanel.add(logoutBtn);

        mainPanel.add(buttonPanel, BorderLayout.CENTER);

        // Recent Transactions Panel
        JPanel transactionsPanel = new JPanel(new BorderLayout());
        transactionsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(0, 102, 204), 2),
                "Recent Transactions",
                javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
                javax.swing.border.TitledBorder.DEFAULT_POSITION,
                new Font("Arial", Font.BOLD, 14),
                new Color(0, 102, 204)
        ));

        String[] columns = {"Date", "Type", "Amount", "Balance", "Description"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable transactionTable = new JTable(model);
        transactionTable.setRowHeight(25);
        transactionTable.setFont(new Font("Arial", Font.PLAIN, 12));
        transactionTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));
        transactionTable.getTableHeader().setBackground(new Color(0, 102, 204));
        transactionTable.getTableHeader().setForeground(Color.WHITE);

        updateRecentTransactions(model);

        JScrollPane scrollPane = new JScrollPane(transactionTable);
        scrollPane.setPreferredSize(new Dimension(850, 200));
        transactionsPanel.add(scrollPane, BorderLayout.CENTER);

        mainPanel.add(transactionsPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private JButton createStyledButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setBackground(bgColor);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Arial", Font.BOLD, 15));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setPreferredSize(new Dimension(150, 60));

        // Hover effect
        button.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                button.setBackground(bgColor.brighter());
            }
            public void mouseExited(MouseEvent e) {
                button.setBackground(bgColor);
            }
        });

        return button;
    }

    private void updateBalance() {
        balanceLabel.setText(String.format("Balance: $%.2f", currentUser.getBalance()));
        dataManager.saveData();
    }

    private void updateRecentTransactions(DefaultTableModel model) {
        model.setRowCount(0);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        int count = 0;
        for (int i = currentUser.getTransactions().size() - 1; i >= 0 && count < 5; i--) {
            Transaction t = currentUser.getTransactions().get(i);
            model.addRow(new Object[]{
                    sdf.format(t.getTimestamp()),
                    t.getType(),
                    String.format("$%.2f", t.getAmount()),
                    String.format("$%.2f", t.getBalanceAfter()),
                    t.getDescription()
            });
            count++;
        }
    }

    private void depositMoney() {
        String input = JOptionPane.showInputDialog(this,
                "Enter amount to deposit:",
                "Deposit Money",
                JOptionPane.QUESTION_MESSAGE);

        if (input != null && !input.trim().isEmpty()) {
            try {
                double amount = Double.parseDouble(input);
                if (amount > 0) {
                    currentUser.deposit(amount);
                    updateBalance();
                    JOptionPane.showMessageDialog(this,
                            String.format("Successfully deposited $%.2f\nNew Balance: $%.2f",
                                    amount, currentUser.getBalance()),
                            "Deposit Successful",
                            JOptionPane.INFORMATION_MESSAGE);
                    refreshDashboard();
                } else {
                    JOptionPane.showMessageDialog(this,
                            "Amount must be positive!",
                            "Invalid Amount",
                            JOptionPane.ERROR_MESSAGE);
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this,
                        "Please enter a valid number!",
                        "Invalid Input",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void withdrawMoney() {
        String input = JOptionPane.showInputDialog(this,
                String.format("Current Balance: $%.2f\nEnter amount to withdraw:", currentUser.getBalance()),
                "Withdraw Money",
                JOptionPane.QUESTION_MESSAGE);

        if (input != null && !input.trim().isEmpty()) {
            try {
                double amount = Double.parseDouble(input);
                if (amount > 0) {
                    if (currentUser.withdraw(amount)) {
                        updateBalance();
                        JOptionPane.showMessageDialog(this,
                                String.format("Successfully withdrawn $%.2f\nNew Balance: $%.2f",
                                        amount, currentUser.getBalance()),
                                "Withdrawal Successful",
                                JOptionPane.INFORMATION_MESSAGE);
                        refreshDashboard();
                    } else {
                        JOptionPane.showMessageDialog(this,
                                String.format("Insufficient balance!\nAvailable: $%.2f", currentUser.getBalance()),
                                "Withdrawal Failed",
                                JOptionPane.ERROR_MESSAGE);
                    }
                } else {
                    JOptionPane.showMessageDialog(this,
                            "Amount must be positive!",
                            "Invalid Amount",
                            JOptionPane.ERROR_MESSAGE);
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this,
                        "Please enter a valid number!",
                        "Invalid Input",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void transferMoney() {
        JPanel panel = new JPanel(new GridLayout(3, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JTextField accountField = new JTextField();
        JTextField amountField = new JTextField();
        JLabel balanceInfo = new JLabel(String.format("Your Balance: $%.2f", currentUser.getBalance()));
        balanceInfo.setFont(new Font("Arial", Font.BOLD, 12));
        balanceInfo.setForeground(new Color(0, 102, 0));

        panel.add(balanceInfo);
        panel.add(new JLabel(""));
        panel.add(new JLabel("Recipient Account:"));
        panel.add(accountField);
        panel.add(new JLabel("Amount:"));
        panel.add(amountField);

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Transfer Money", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            try {
                String recipientAccount = accountField.getText().trim();
                double amount = Double.parseDouble(amountField.getText().trim());

                if (recipientAccount.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Please enter recipient account number!");
                    return;
                }

                User recipient = dataManager.getUserByAccountNumber(recipientAccount);
                if (recipient == null) {
                    JOptionPane.showMessageDialog(this,
                            "Recipient account not found!",
                            "Transfer Failed",
                            JOptionPane.ERROR_MESSAGE);
                } else if (recipient.getAccountNumber().equals(currentUser.getAccountNumber())) {
                    JOptionPane.showMessageDialog(this,
                            "Cannot transfer to the same account!",
                            "Transfer Failed",
                            JOptionPane.ERROR_MESSAGE);
                } else if (amount <= 0) {
                    JOptionPane.showMessageDialog(this,
                            "Amount must be positive!",
                            "Invalid Amount",
                            JOptionPane.ERROR_MESSAGE);
                } else if (currentUser.transfer(recipient, amount)) {
                    updateBalance();
                    JOptionPane.showMessageDialog(this,
                            String.format("Successfully transferred $%.2f to %s (%s)\nNew Balance: $%.2f",
                                    amount, recipient.getName(), recipientAccount, currentUser.getBalance()),
                            "Transfer Successful",
                            JOptionPane.INFORMATION_MESSAGE);
                    refreshDashboard();
                } else {
                    JOptionPane.showMessageDialog(this,
                            String.format("Insufficient balance!\nAvailable: $%.2f\nRequired: $%.2f",
                                    currentUser.getBalance(), amount),
                            "Transfer Failed",
                            JOptionPane.ERROR_MESSAGE);
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this,
                        "Please enter a valid amount!",
                        "Invalid Input",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void viewStatement() {
        new StatementFrame(currentUser);
    }

    private void changePassword() {
        JPanel panel = new JPanel(new GridLayout(3, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPasswordField currentPwdField = new JPasswordField();
        JPasswordField newPwdField = new JPasswordField();
        JPasswordField confirmPwdField = new JPasswordField();

        panel.add(new JLabel("Current Password:"));
        panel.add(currentPwdField);
        panel.add(new JLabel("New Password:"));
        panel.add(newPwdField);
        panel.add(new JLabel("Confirm Password:"));
        panel.add(confirmPwdField);

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Change Password", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String currentPwd = new String(currentPwdField.getPassword());
            String newPwd = new String(newPwdField.getPassword());
            String confirmPwd = new String(confirmPwdField.getPassword());

            if (!currentPwd.equals(currentUser.getPassword())) {
                JOptionPane.showMessageDialog(this,
                        "Current password is incorrect!",
                        "Password Change Failed",
                        JOptionPane.ERROR_MESSAGE);
            } else if (newPwd.length() < 4) {
                JOptionPane.showMessageDialog(this,
                        "New password must be at least 4 characters!",
                        "Password Change Failed",
                        JOptionPane.ERROR_MESSAGE);
            } else if (!newPwd.equals(confirmPwd)) {
                JOptionPane.showMessageDialog(this,
                        "New passwords do not match!",
                        "Password Change Failed",
                        JOptionPane.ERROR_MESSAGE);
            } else {
                currentUser.setPassword(newPwd);
                dataManager.saveData();
                JOptionPane.showMessageDialog(this,
                        "Password changed successfully!",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    private void logout() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to logout?",
                "Logout Confirmation",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            dispose();
            new LoginFrame();
        }
    }

    private void refreshDashboard() {
        dispose();
        new DashboardFrame(currentUser, dataManager);
    }
}

// ==================== Statement Frame Class ====================
class StatementFrame extends JFrame {
    private User user;

    public StatementFrame(User user) {
        this.user = user;

        setTitle("Account Statement - " + user.getAccountNumber());
        setSize(1000, 550);
        setLocationRelativeTo(null);

        initComponents();
        setVisible(true);
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        mainPanel.setBackground(Color.WHITE);

        // Header Panel
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBackground(new Color(240, 248, 255));
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 102, 204), 2),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));

        JLabel titleLabel = new JLabel("ACCOUNT STATEMENT");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setForeground(new Color(0, 102, 204));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel nameLabel = new JLabel("Account Holder: " + user.getName());
        nameLabel.setFont(new Font("Arial", Font.PLAIN, 15));
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel accountLabel = new JLabel("Account Number: " + user.getAccountNumber());
        accountLabel.setFont(new Font("Arial", Font.PLAIN, 15));
        accountLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel balanceLabel = new JLabel(String.format("Current Balance: $%.2f", user.getBalance()));
        balanceLabel.setFont(new Font("Arial", Font.BOLD, 18));
        balanceLabel.setForeground(new Color(0, 128, 0));
        balanceLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel dateLabel = new JLabel("Statement Date: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        dateLabel.setFont(new Font("Arial", Font.ITALIC, 12));
        dateLabel.setForeground(Color.GRAY);
        dateLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        headerPanel.add(titleLabel);
        headerPanel.add(Box.createVerticalStrut(10));
        headerPanel.add(nameLabel);
        headerPanel.add(accountLabel);
        headerPanel.add(Box.createVerticalStrut(5));
        headerPanel.add(balanceLabel);
        headerPanel.add(Box.createVerticalStrut(5));
        headerPanel.add(dateLabel);

        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Transactions Table
        String[] columns = {"Date & Time", "Type", "Amount", "Balance After", "Description"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        if (user.getTransactions().isEmpty()) {
            model.addRow(new Object[]{"No transactions yet", "", "", "", ""});
        } else {
            for (Transaction t : user.getTransactions()) {
                model.addRow(new Object[]{
                        sdf.format(t.getTimestamp()),
                        t.getType(),
                        String.format("$%.2f", t.getAmount()),
                        String.format("$%.2f", t.getBalanceAfter()),
                        t.getDescription()
                });
            }
        }

        JTable table = new JTable(model);
        table.setRowHeight(28);
        table.setFont(new Font("Arial", Font.PLAIN, 13));
        table.getTableHeader().setFont(new Font("Arial", Font.BOLD, 13));
        table.getTableHeader().setBackground(new Color(0, 102, 204));
        table.getTableHeader().setForeground(Color.WHITE);
        table.getTableHeader().setReorderingAllowed(false);
        table.setSelectionBackground(new Color(173, 216, 230));

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(0, 102, 204), 1));

        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Footer Panel
        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JLabel recordLabel = new JLabel("Total Transactions: " + user.getTransactions().size());
        recordLabel.setFont(new Font("Arial", Font.BOLD, 13));
        footerPanel.add(recordLabel, BorderLayout.WEST);

        JButton closeButton = new JButton("Close");
        closeButton.setPreferredSize(new Dimension(100, 35));
        closeButton.setBackground(new Color(105, 105, 105));
        closeButton.setForeground(Color.WHITE);
        closeButton.setFont(new Font("Arial", Font.BOLD, 14));
        closeButton.setFocusPainted(false);
        closeButton.addActionListener(e -> dispose());

        footerPanel.add(closeButton, BorderLayout.EAST);
        mainPanel.add(footerPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }
}