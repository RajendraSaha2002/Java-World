import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class ATM_GUI extends JFrame {
    // Bank data
    private Map<String, Account> accounts = new HashMap<>();
    private Account currentAccount = null;

    // UI Components
    private CardLayout cardLayout;
    private JPanel mainPanel;
    private JTextField pinField;
    private JLabel balanceLabel;
    private JTextArea transactionArea;
    private JTextField amountField;

    // Colors
    private final Color PRIMARY_COLOR = new Color(0, 122, 204);
    private final Color SECONDARY_COLOR = new Color(240, 240, 245);
    private final Color SUCCESS_COLOR = new Color(46, 125, 50);
    private final Color ERROR_COLOR = new Color(211, 47, 47);
    private final Color TEXT_COLOR = new Color(33, 33, 33);

    public ATM_GUI() {
        setTitle("ATM Banking System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 700);
        setLocationRelativeTo(null);
        setResizable(false);

        // Initialize sample accounts
        initializeAccounts();

        // Create main panel with CardLayout
        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        // Create all screens
        mainPanel.add(createLoginScreen(), "login");
        mainPanel.add(createMainMenuScreen(), "mainMenu");
        mainPanel.add(createBalanceScreen(), "balance");
        mainPanel.add(createWithdrawScreen(), "withdraw");
        mainPanel.add(createDepositScreen(), "deposit");
        mainPanel.add(createTransferScreen(), "transfer");
        mainPanel.add(createTransactionHistoryScreen(), "history");
        mainPanel.add(createChangePinScreen(), "changePin");

        add(mainPanel);
        cardLayout.show(mainPanel, "login");
    }

    private void initializeAccounts() {
        accounts.put("1234", new Account("1234", "John Doe", 1000.0));
        accounts.put("5678", new Account("5678", "Jane Smith", 2500.0));
        accounts.put("9999", new Account("9999", "Test User", 5000.0));
    }

    // ==================== LOGIN SCREEN ====================
    private JPanel createLoginScreen() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(SECONDARY_COLOR);

        // Header
        JPanel headerPanel = new JPanel();
        headerPanel.setBackground(PRIMARY_COLOR);
        headerPanel.setPreferredSize(new Dimension(600, 120));
        JLabel titleLabel = new JLabel("ATM Banking System");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 28));
        titleLabel.setForeground(Color.WHITE);
        headerPanel.add(titleLabel);

        // Login form
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBackground(SECONDARY_COLOR);
        formPanel.setBorder(new EmptyBorder(50, 100, 50, 100));

        JLabel welcomeLabel = new JLabel("Welcome! Please enter your PIN");
        welcomeLabel.setFont(new Font("Arial", Font.PLAIN, 16));
        welcomeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel pinPanel = new JPanel(new FlowLayout());
        pinPanel.setBackground(SECONDARY_COLOR);
        JLabel pinLabel = new JLabel("PIN: ");
        pinLabel.setFont(new Font("Arial", Font.BOLD, 14));
        pinField = new JPasswordField(15);
        pinField.setFont(new Font("Arial", Font.PLAIN, 18));
        pinPanel.add(pinLabel);
        pinPanel.add(pinField);

        JButton loginButton = createStyledButton("LOGIN", PRIMARY_COLOR);
        loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginButton.addActionListener(e -> handleLogin());

        // Add Enter key support
        pinField.addActionListener(e -> handleLogin());

        JLabel infoLabel = new JLabel("<html><center>Test PINs:<br>1234, 5678, 9999</center></html>");
        infoLabel.setFont(new Font("Arial", Font.ITALIC, 12));
        infoLabel.setForeground(Color.GRAY);
        infoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        formPanel.add(welcomeLabel);
        formPanel.add(Box.createRigidArea(new Dimension(0, 30)));
        formPanel.add(pinPanel);
        formPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        formPanel.add(loginButton);
        formPanel.add(Box.createRigidArea(new Dimension(0, 30)));
        formPanel.add(infoLabel);

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(formPanel, BorderLayout.CENTER);

        return panel;
    }

    private void handleLogin() {
        String pin = pinField.getText();
        if (accounts.containsKey(pin)) {
            currentAccount = accounts.get(pin);
            pinField.setText("");
            updateBalanceDisplay();
            cardLayout.show(mainPanel, "mainMenu");
        } else {
            JOptionPane.showMessageDialog(this,
                    "Invalid PIN. Please try again.",
                    "Login Error",
                    JOptionPane.ERROR_MESSAGE);
            pinField.setText("");
        }
    }

    // ==================== MAIN MENU SCREEN ====================
    private JPanel createMainMenuScreen() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(SECONDARY_COLOR);

        // Header
        JPanel headerPanel = createHeaderPanel("Main Menu");

        // Menu buttons
        JPanel menuPanel = new JPanel(new GridLayout(4, 2, 15, 15));
        menuPanel.setBackground(SECONDARY_COLOR);
        menuPanel.setBorder(new EmptyBorder(30, 50, 30, 50));

        JButton balanceBtn = createMenuButton("💰 Check Balance");
        balanceBtn.addActionListener(e -> {
            updateBalanceDisplay();
            cardLayout.show(mainPanel, "balance");
        });

        JButton withdrawBtn = createMenuButton("💵 Withdraw");
        withdrawBtn.addActionListener(e -> cardLayout.show(mainPanel, "withdraw"));

        JButton depositBtn = createMenuButton("💳 Deposit");
        depositBtn.addActionListener(e -> cardLayout.show(mainPanel, "deposit"));

        JButton transferBtn = createMenuButton("🔄 Transfer");
        transferBtn.addActionListener(e -> cardLayout.show(mainPanel, "transfer"));

        JButton historyBtn = createMenuButton("📊 Transaction History");
        historyBtn.addActionListener(e -> {
            updateTransactionHistory();
            cardLayout.show(mainPanel, "history");
        });

        JButton changePinBtn = createMenuButton("🔐 Change PIN");
        changePinBtn.addActionListener(e -> cardLayout.show(mainPanel, "changePin"));

        JButton miniStatementBtn = createMenuButton("📄 Mini Statement");
        miniStatementBtn.addActionListener(e -> showMiniStatement());

        JButton logoutBtn = createMenuButton("🚪 Logout");
        logoutBtn.setBackground(ERROR_COLOR);
        logoutBtn.addActionListener(e -> handleLogout());

        menuPanel.add(balanceBtn);
        menuPanel.add(withdrawBtn);
        menuPanel.add(depositBtn);
        menuPanel.add(transferBtn);
        menuPanel.add(historyBtn);
        menuPanel.add(changePinBtn);
        menuPanel.add(miniStatementBtn);
        menuPanel.add(logoutBtn);

        // Account info
        JPanel infoPanel = new JPanel();
        infoPanel.setBackground(SECONDARY_COLOR);
        balanceLabel = new JLabel();
        balanceLabel.setFont(new Font("Arial", Font.BOLD, 16));
        infoPanel.add(balanceLabel);

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(menuPanel, BorderLayout.CENTER);
        panel.add(infoPanel, BorderLayout.SOUTH);

        return panel;
    }

    // ==================== BALANCE SCREEN ====================
    private JPanel createBalanceScreen() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(SECONDARY_COLOR);

        JPanel headerPanel = createHeaderPanel("Account Balance");

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(SECONDARY_COLOR);
        contentPanel.setBorder(new EmptyBorder(50, 50, 50, 50));

        JLabel accountLabel = new JLabel();
        accountLabel.setFont(new Font("Arial", Font.BOLD, 18));
        accountLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel balanceDisplayLabel = new JLabel();
        balanceDisplayLabel.setFont(new Font("Arial", Font.BOLD, 36));
        balanceDisplayLabel.setForeground(SUCCESS_COLOR);
        balanceDisplayLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton backButton = createStyledButton("Back to Menu", PRIMARY_COLOR);
        backButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        backButton.addActionListener(e -> cardLayout.show(mainPanel, "mainMenu"));

        contentPanel.add(accountLabel);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 30)));
        contentPanel.add(balanceDisplayLabel);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 50)));
        contentPanel.add(backButton);

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(contentPanel, BorderLayout.CENTER);

        // Store references for updating
        panel.putClientProperty("accountLabel", accountLabel);
        panel.putClientProperty("balanceLabel", balanceDisplayLabel);

        return panel;
    }

    // ==================== WITHDRAW SCREEN ====================
    private JPanel createWithdrawScreen() {
        return createTransactionScreen("Withdraw Money", "withdraw");
    }

    // ==================== DEPOSIT SCREEN ====================
    private JPanel createDepositScreen() {
        return createTransactionScreen("Deposit Money", "deposit");
    }

    // ==================== TRANSFER SCREEN ====================
    private JPanel createTransferScreen() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(SECONDARY_COLOR);

        JPanel headerPanel = createHeaderPanel("Transfer Money");

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(SECONDARY_COLOR);
        contentPanel.setBorder(new EmptyBorder(30, 50, 30, 50));

        JLabel label = new JLabel("Enter recipient PIN:");
        label.setFont(new Font("Arial", Font.PLAIN, 14));

        JTextField recipientField = new JTextField(20);
        recipientField.setFont(new Font("Arial", Font.PLAIN, 16));
        recipientField.setMaximumSize(new Dimension(300, 35));

        JLabel amountLabel = new JLabel("Enter amount:");
        amountLabel.setFont(new Font("Arial", Font.PLAIN, 14));

        JTextField transferAmountField = new JTextField(20);
        transferAmountField.setFont(new Font("Arial", Font.PLAIN, 16));
        transferAmountField.setMaximumSize(new Dimension(300, 35));

        JButton transferButton = createStyledButton("Transfer", SUCCESS_COLOR);
        transferButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        transferButton.addActionListener(e -> {
            try {
                String recipientPin = recipientField.getText();
                double amount = Double.parseDouble(transferAmountField.getText());

                if (amount <= 0) {
                    showError("Amount must be positive!");
                    return;
                }

                if (!accounts.containsKey(recipientPin)) {
                    showError("Recipient account not found!");
                    return;
                }

                if (recipientPin.equals(currentAccount.getPin())) {
                    showError("Cannot transfer to same account!");
                    return;
                }

                if (currentAccount.getBalance() < amount) {
                    showError("Insufficient funds!");
                    return;
                }

                Account recipient = accounts.get(recipientPin);
                currentAccount.withdraw(amount);
                recipient.deposit(amount);
                currentAccount.addTransaction("Transfer to " + recipientPin, -amount);
                recipient.addTransaction("Transfer from " + currentAccount.getPin(), amount);

                showSuccess("Successfully transferred $" + String.format("%.2f", amount) +
                        " to " + recipient.getName());
                recipientField.setText("");
                transferAmountField.setText("");
                updateBalanceDisplay();
                cardLayout.show(mainPanel, "mainMenu");
            } catch (NumberFormatException ex) {
                showError("Invalid amount!");
            }
        });

        JButton backButton = createStyledButton("Back", PRIMARY_COLOR);
        backButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        backButton.addActionListener(e -> {
            recipientField.setText("");
            transferAmountField.setText("");
            cardLayout.show(mainPanel, "mainMenu");
        });

        contentPanel.add(label);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        contentPanel.add(recipientField);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        contentPanel.add(amountLabel);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        contentPanel.add(transferAmountField);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 30)));
        contentPanel.add(transferButton);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        contentPanel.add(backButton);

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(contentPanel, BorderLayout.CENTER);

        return panel;
    }

    // ==================== TRANSACTION HISTORY SCREEN ====================
    private JPanel createTransactionHistoryScreen() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(SECONDARY_COLOR);

        JPanel headerPanel = createHeaderPanel("Transaction History");

        transactionArea = new JTextArea();
        transactionArea.setEditable(false);
        transactionArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        transactionArea.setBackground(Color.WHITE);
        transactionArea.setBorder(new EmptyBorder(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(transactionArea);
        scrollPane.setBorder(new EmptyBorder(20, 20, 20, 20));

        JButton backButton = createStyledButton("Back to Menu", PRIMARY_COLOR);
        backButton.addActionListener(e -> cardLayout.show(mainPanel, "mainMenu"));

        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(SECONDARY_COLOR);
        buttonPanel.add(backButton);

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    // ==================== CHANGE PIN SCREEN ====================
    private JPanel createChangePinScreen() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(SECONDARY_COLOR);

        JPanel headerPanel = createHeaderPanel("Change PIN");

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(SECONDARY_COLOR);
        contentPanel.setBorder(new EmptyBorder(30, 50, 30, 50));

        JLabel oldPinLabel = new JLabel("Enter current PIN:");
        JPasswordField oldPinField = new JPasswordField(20);
        oldPinField.setMaximumSize(new Dimension(300, 35));

        JLabel newPinLabel = new JLabel("Enter new PIN:");
        JPasswordField newPinField = new JPasswordField(20);
        newPinField.setMaximumSize(new Dimension(300, 35));

        JLabel confirmPinLabel = new JLabel("Confirm new PIN:");
        JPasswordField confirmPinField = new JPasswordField(20);
        confirmPinField.setMaximumSize(new Dimension(300, 35));

        JButton changeButton = createStyledButton("Change PIN", SUCCESS_COLOR);
        changeButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        changeButton.addActionListener(e -> {
            String oldPin = new String(oldPinField.getPassword());
            String newPin = new String(newPinField.getPassword());
            String confirmPin = new String(confirmPinField.getPassword());

            if (!oldPin.equals(currentAccount.getPin())) {
                showError("Current PIN is incorrect!");
                return;
            }

            if (newPin.length() != 4 || !newPin.matches("\\d+")) {
                showError("New PIN must be 4 digits!");
                return;
            }

            if (!newPin.equals(confirmPin)) {
                showError("PINs do not match!");
                return;
            }

            if (accounts.containsKey(newPin)) {
                showError("PIN already in use!");
                return;
            }

            accounts.remove(currentAccount.getPin());
            currentAccount.setPin(newPin);
            accounts.put(newPin, currentAccount);

            showSuccess("PIN changed successfully!");
            oldPinField.setText("");
            newPinField.setText("");
            confirmPinField.setText("");
            cardLayout.show(mainPanel, "mainMenu");
        });

        JButton backButton = createStyledButton("Back", PRIMARY_COLOR);
        backButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        backButton.addActionListener(e -> {
            oldPinField.setText("");
            newPinField.setText("");
            confirmPinField.setText("");
            cardLayout.show(mainPanel, "mainMenu");
        });

        contentPanel.add(oldPinLabel);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        contentPanel.add(oldPinField);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 15)));
        contentPanel.add(newPinLabel);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        contentPanel.add(newPinField);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 15)));
        contentPanel.add(confirmPinLabel);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        contentPanel.add(confirmPinField);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 30)));
        contentPanel.add(changeButton);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        contentPanel.add(backButton);

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(contentPanel, BorderLayout.CENTER);

        return panel;
    }

    // ==================== HELPER METHODS ====================
    private JPanel createTransactionScreen(String title, String type) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(SECONDARY_COLOR);

        JPanel headerPanel = createHeaderPanel(title);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(SECONDARY_COLOR);
        contentPanel.setBorder(new EmptyBorder(30, 50, 30, 50));

        JLabel label = new JLabel("Enter amount:");
        label.setFont(new Font("Arial", Font.PLAIN, 14));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);

        amountField = new JTextField(20);
        amountField.setFont(new Font("Arial", Font.PLAIN, 18));
        amountField.setMaximumSize(new Dimension(300, 40));
        amountField.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Quick amount buttons
        JPanel quickAmountPanel = new JPanel(new GridLayout(2, 3, 10, 10));
        quickAmountPanel.setBackground(SECONDARY_COLOR);
        quickAmountPanel.setMaximumSize(new Dimension(400, 120));

        int[] amounts = {20, 50, 100, 200, 500, 1000};
        for (int amount : amounts) {
            JButton btn = createStyledButton("$" + amount, new Color(100, 100, 100));
            btn.addActionListener(e -> amountField.setText(String.valueOf(amount)));
            quickAmountPanel.add(btn);
        }

        JButton submitButton = createStyledButton(
                type.equals("withdraw") ? "Withdraw" : "Deposit",
                SUCCESS_COLOR
        );
        submitButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        submitButton.addActionListener(e -> handleTransaction(type));

        JButton backButton = createStyledButton("Back", PRIMARY_COLOR);
        backButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        backButton.addActionListener(e -> {
            amountField.setText("");
            cardLayout.show(mainPanel, "mainMenu");
        });

        contentPanel.add(label);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 15)));
        contentPanel.add(amountField);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        contentPanel.add(new JLabel("Quick amounts:"));
        contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        contentPanel.add(quickAmountPanel);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 30)));
        contentPanel.add(submitButton);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        contentPanel.add(backButton);

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(contentPanel, BorderLayout.CENTER);

        return panel;
    }

    private void handleTransaction(String type) {
        try {
            double amount = Double.parseDouble(amountField.getText());

            if (amount <= 0) {
                showError("Amount must be positive!");
                return;
            }

            if (type.equals("withdraw")) {
                if (currentAccount.getBalance() < amount) {
                    showError("Insufficient funds!");
                    return;
                }
                currentAccount.withdraw(amount);
                currentAccount.addTransaction("ATM Withdrawal", -amount);
                showSuccess("Successfully withdrew $" + String.format("%.2f", amount));
            } else {
                currentAccount.deposit(amount);
                currentAccount.addTransaction("ATM Deposit", amount);
                showSuccess("Successfully deposited $" + String.format("%.2f", amount));
            }

            amountField.setText("");
            updateBalanceDisplay();
            cardLayout.show(mainPanel, "mainMenu");

        } catch (NumberFormatException e) {
            showError("Invalid amount!");
        }
    }

    private JPanel createHeaderPanel(String title) {
        JPanel headerPanel = new JPanel();
        headerPanel.setBackground(PRIMARY_COLOR);
        headerPanel.setPreferredSize(new Dimension(600, 80));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);
        headerPanel.add(titleLabel);
        return headerPanel;
    }

    private JButton createStyledButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setBackground(bgColor);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setPreferredSize(new Dimension(200, 45));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

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

    private JButton createMenuButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Arial", Font.BOLD, 16));
        button.setBackground(PRIMARY_COLOR);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setPreferredSize(new Dimension(200, 80));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        button.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                button.setBackground(PRIMARY_COLOR.brighter());
            }
            public void mouseExited(MouseEvent e) {
                button.setBackground(PRIMARY_COLOR);
            }
        });

        return button;
    }

    private void updateBalanceDisplay() {
        if (currentAccount != null) {
            balanceLabel.setText("Account: " + currentAccount.getName() +
                    " | Balance: $" + String.format("%.2f", currentAccount.getBalance()));

            // Update balance screen
            Component balanceScreen = mainPanel.getComponent(2);
            if (balanceScreen instanceof JPanel) {
                JLabel accountLabel = (JLabel) ((JPanel) balanceScreen).getClientProperty("accountLabel");
                JLabel balanceDisplayLabel = (JLabel) ((JPanel) balanceScreen).getClientProperty("balanceLabel");
                if (accountLabel != null) {
                    accountLabel.setText("Account Holder: " + currentAccount.getName());
                }
                if (balanceDisplayLabel != null) {
                    balanceDisplayLabel.setText("$" + String.format("%.2f", currentAccount.getBalance()));
                }
            }
        }
    }

    private void updateTransactionHistory() {
        if (currentAccount != null) {
            transactionArea.setText("");
            transactionArea.append("TRANSACTION HISTORY\n");
            transactionArea.append("Account: " + currentAccount.getName() + "\n");
            transactionArea.append("PIN: " + currentAccount.getPin() + "\n");
            transactionArea.append("Current Balance: $" + String.format("%.2f", currentAccount.getBalance()) + "\n");
            transactionArea.append("=" .repeat(50) + "\n\n");

            List<Transaction> transactions = currentAccount.getTransactions();
            if (transactions.isEmpty()) {
                transactionArea.append("No transactions yet.\n");
            } else {
                for (Transaction t : transactions) {
                    transactionArea.append(t.toString() + "\n");
                }
            }
        }
    }

    private void showMiniStatement() {
        if (currentAccount != null) {
            StringBuilder statement = new StringBuilder();
            statement.append("MINI STATEMENT\n\n");
            statement.append("Account: ").append(currentAccount.getName()).append("\n");
            statement.append("PIN: ").append(currentAccount.getPin()).append("\n");
            statement.append("Balance: $").append(String.format("%.2f", currentAccount.getBalance())).append("\n\n");
            statement.append("Last 5 Transactions:\n");
            statement.append("-".repeat(40)).append("\n");

            List<Transaction> transactions = currentAccount.getTransactions();
            int count = Math.min(5, transactions.size());
            for (int i = transactions.size() - count; i < transactions.size(); i++) {
                statement.append(transactions.get(i).toString()).append("\n");
            }

            JOptionPane.showMessageDialog(this, statement.toString(),
                    "Mini Statement", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void handleLogout() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to logout?",
                "Logout Confirmation",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            currentAccount = null;
            pinField.setText("");
            cardLayout.show(mainPanel, "login");
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void showSuccess(String message) {
        JOptionPane.showMessageDialog(this, message, "Success", JOptionPane.INFORMATION_MESSAGE);
    }

    // ==================== ACCOUNT CLASS ====================
    class Account {
        private String pin;
        private String name;
        private double balance;
        private List<Transaction> transactions;

        public Account(String pin, String name, double balance) {
            this.pin = pin;
            this.name = name;
            this.balance = balance;
            this.transactions = new ArrayList<>();
        }

        public String getPin() { return pin; }
        public void setPin(String pin) { this.pin = pin; }
        public String getName() { return name; }
        public double getBalance() { return balance; }
        public List<Transaction> getTransactions() { return transactions; }

        public void withdraw(double amount) {
            balance -= amount;
        }

        public void deposit(double amount) {
            balance += amount;
        }

        public void addTransaction(String description, double amount) {
            transactions.add(new Transaction(description, amount));
        }
    }

    // ==================== TRANSACTION CLASS ====================
    class Transaction {
        private String description;
        private double amount;
        private Date timestamp;
        private SimpleDateFormat dateFormat;

        public Transaction(String description, double amount) {
            this.description = description;
            this.amount = amount;
            this.timestamp = new Date();
            this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        }

        @Override
        public String toString() {
            String amountStr = String.format("%+.2f", amount);
            return String.format("%-20s | %10s | %s",
                    dateFormat.format(timestamp),
                    "$" + amountStr,
                    description);
        }
    }

    // ==================== MAIN METHOD ====================
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            ATM_GUI atm = new ATM_GUI();
            atm.setVisible(true);
        });
    }
}