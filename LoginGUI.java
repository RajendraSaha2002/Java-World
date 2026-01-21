import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * LoginGUI.java
 *
 * Swing-based Login & Registration GUI using simple file persistence.
 *
 * Features:
 *  - Login (username + password). Passwords are hashed using SHA-256.
 *  - Register new user (username must be unique).
 *  - Show/hide password toggle.
 *  - Admin shortcut: username "admin" and password "admin" opens Admin Panel listing users.
 *  - Users stored in users.txt as lines: username:sha256hash
 *
 * How to run in IntelliJ:
 *  - Create a Java project with a JDK (11+).
 *  - Put this file under src (no package required).
 *  - Run the main method of LoginGUI.
 */
public class LoginGUI extends JFrame {
    private static final String USERS_FILE = "users.txt";
    private static final String DEFAULT_ADMIN_USER = "admin";
    private static final String DEFAULT_ADMIN_PASS = "admin";
    private static final DateTimeFormatter LOG_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private JTextField loginUserField;
    private JPasswordField loginPassField;
    private JCheckBox showPassCheck;

    private JTextField regUserField;
    private JPasswordField regPassField;
    private JPasswordField regPassConfirmField;

    public LoginGUI() {
        setTitle("Login System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(520, 320);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10,10));
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(10,10,10,10));

        JLabel title = new JLabel("Java Login System", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(1,2,12,12));
        center.add(buildLoginPanel());
        center.add(buildRegisterPanel());
        add(center, BorderLayout.CENTER);

        JLabel footer = new JLabel("Tip: Admin shortcut — username: admin password: admin", SwingConstants.CENTER);
        footer.setFont(new Font("SansSerif", Font.ITALIC, 11));
        add(footer, BorderLayout.SOUTH);
    }

    private JPanel buildLoginPanel() {
        JPanel p = new JPanel(new BorderLayout(8,8));
        p.setBorder(BorderFactory.createTitledBorder("Login"));

        JPanel fields = new JPanel(new GridLayout(5,1,6,6));
        loginUserField = new JTextField();
        fields.add(labeled("Username:", loginUserField));

        loginPassField = new JPasswordField();
        fields.add(labeled("Password:", loginPassField));

        showPassCheck = new JCheckBox("Show password");
        showPassCheck.addActionListener(e -> {
            if (showPassCheck.isSelected()) loginPassField.setEchoChar((char)0);
            else loginPassField.setEchoChar('\u2022');
        });
        fields.add(showPassCheck);

        JButton loginBtn = new JButton("Login");
        loginBtn.addActionListener(e -> doLogin());
        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> {
            loginUserField.setText("");
            loginPassField.setText("");
        });

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        btns.add(loginBtn);
        btns.add(clearBtn);
        fields.add(btns);

        p.add(fields, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildRegisterPanel() {
        JPanel p = new JPanel(new BorderLayout(8,8));
        p.setBorder(BorderFactory.createTitledBorder("Register"));

        JPanel fields = new JPanel(new GridLayout(6,1,6,6));

        regUserField = new JTextField();
        fields.add(labeled("New Username:", regUserField));

        regPassField = new JPasswordField();
        fields.add(labeled("Password:", regPassField));

        regPassConfirmField = new JPasswordField();
        fields.add(labeled("Confirm Password:", regPassConfirmField));

        JButton regBtn = new JButton("Register");
        regBtn.addActionListener(e -> doRegister());

        JButton importBtn = new JButton("Reload users file");
        importBtn.addActionListener(e -> {
            loadUsersFile();
            JOptionPane.showMessageDialog(this, "Loaded/created users file: " + USERS_FILE, "Info", JOptionPane.INFORMATION_MESSAGE);
        });

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        btns.add(regBtn);
        btns.add(importBtn);
        fields.add(btns);

        p.add(fields, BorderLayout.CENTER);
        return p;
    }

    private JPanel labeled(String label, JComponent comp) {
        JPanel p = new JPanel(new BorderLayout(6,6));
        JLabel l = new JLabel(label);
        l.setPreferredSize(new Dimension(120, 22));
        p.add(l, BorderLayout.WEST);
        p.add(comp, BorderLayout.CENTER);
        return p;
    }

    // ---------------- Actions ----------------

    private void doLogin() {
        String username = loginUserField.getText().trim();
        String password = new String(loginPassField.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter username and password.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (username.equals(DEFAULT_ADMIN_USER) && password.equals(DEFAULT_ADMIN_PASS)) {
            openAdminPanel();
            log("Admin logged in (shortcut).");
            return;
        }

        if (verifyPassword(username, password)) {
            JOptionPane.showMessageDialog(this, "Login successful. Welcome, " + username + "!", "Success", JOptionPane.INFORMATION_MESSAGE);
            openUserPanel(username);
            log("User logged in: " + username);
        } else {
            JOptionPane.showMessageDialog(this, "Login failed. Invalid credentials.", "Error", JOptionPane.ERROR_MESSAGE);
            log("Failed login attempt for: " + username);
        }
    }

    private void doRegister() {
        String username = regUserField.getText().trim();
        String pass = new String(regPassField.getPassword());
        String pass2 = new String(regPassConfirmField.getPassword());

        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Username cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (pass.length() < 4) {
            JOptionPane.showMessageDialog(this, "Password must be at least 4 characters.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!pass.equals(pass2)) {
            JOptionPane.showMessageDialog(this, "Passwords do not match.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (userExists(username)) {
            JOptionPane.showMessageDialog(this, "Username already exists. Choose another.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        boolean saved = saveUser(username, hashPassword(pass));
        if (saved) {
            JOptionPane.showMessageDialog(this, "Registered successfully. You may now login.", "Success", JOptionPane.INFORMATION_MESSAGE);
            regUserField.setText("");
            regPassField.setText("");
            regPassConfirmField.setText("");
            log("Registered new user: " + username);
        } else {
            JOptionPane.showMessageDialog(this, "Failed to register user (I/O error).", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openAdminPanel() {
        SwingUtilities.invokeLater(() -> {
            JFrame admin = new JFrame("Admin Panel — Users");
            admin.setSize(520, 360);
            admin.setLocationRelativeTo(this);

            String[] cols = {"Username"};
            DefaultTableModel model = new DefaultTableModel(cols, 0);
            JTable table = new JTable(model);

            loadUsersFile();
            List<String> users = readAllUserLines();
            for (String line : users) {
                String[] parts = line.split(":", 2);
                if (parts.length >= 1 && !parts[0].isEmpty()) model.addRow(new Object[]{parts[0]});
            }

            JScrollPane scroll = new JScrollPane(table);
            admin.add(scroll, BorderLayout.CENTER);

            JButton refresh = new JButton("Refresh");
            refresh.addActionListener(e -> {
                model.setRowCount(0);
                List<String> u = readAllUserLines();
                for (String line : u) {
                    String[] parts = line.split(":", 2);
                    if (parts.length >= 1 && !parts[0].isEmpty()) model.addRow(new Object[]{parts[0]});
                }
            });

            JButton removeBtn = new JButton("Remove selected user");
            removeBtn.addActionListener(e -> {
                int r = table.getSelectedRow();
                if (r == -1) { JOptionPane.showMessageDialog(admin, "Select a row first."); return; }
                String user = (String) model.getValueAt(r, 0);
                if (user.equals(DEFAULT_ADMIN_USER)) { JOptionPane.showMessageDialog(admin, "Cannot remove default admin shortcut."); return; }
                int conf = JOptionPane.showConfirmDialog(admin, "Delete user '" + user + "' ?", "Confirm", JOptionPane.YES_NO_OPTION);
                if (conf == JOptionPane.YES_OPTION) {
                    boolean ok = deleteUser(user);
                    if (ok) {
                        model.removeRow(r);
                        JOptionPane.showMessageDialog(admin, "User removed.");
                        log("Admin removed user: " + user);
                    } else {
                        JOptionPane.showMessageDialog(admin, "Failed to remove user.");
                    }
                }
            });

            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 8,8));
            bottom.add(refresh);
            bottom.add(removeBtn);
            admin.add(bottom, BorderLayout.SOUTH);

            admin.setVisible(true);
        });
    }

    private void openUserPanel(String username) {
        SwingUtilities.invokeLater(() -> {
            JFrame userF = new JFrame("Welcome — " + username);
            userF.setSize(420, 260);
            userF.setLocationRelativeTo(this);

            JPanel root = new JPanel(new BorderLayout(10,10));
            root.setBorder(new EmptyBorder(10,10,10,10));

            JLabel h = new JLabel("Hello, " + username, SwingConstants.CENTER);
            h.setFont(new Font("SansSerif", Font.BOLD, 16));
            root.add(h, BorderLayout.NORTH);

            JPanel center = new JPanel(new GridLayout(3,1,8,8));
            JButton genPayslip = new JButton("Generate Payslip (text)");
            genPayslip.addActionListener(e -> {
                String filename = String.format("payslip_%s_%s.txt", username, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM")));
                try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(filename))) {
                    bw.write("Payslip for: " + username);
                    bw.newLine();
                    bw.write("Generated on: " + LocalDateTime.now().format(LOG_TS));
                    bw.newLine();
                    bw.write("Sample payslip content...");
                    bw.newLine();
                    JOptionPane.showMessageDialog(userF, "Payslip created: " + filename);
                    log("Payslip generated for: " + username);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(userF, "Failed to write payslip: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            });

            JButton changePass = new JButton("Change Password");
            changePass.addActionListener(e -> {
                JPasswordField pfOld = new JPasswordField();
                JPasswordField pfNew = new JPasswordField();
                JPasswordField pfNew2 = new JPasswordField();
                Object[] obj = {
                        "Current password:", pfOld,
                        "New password:", pfNew,
                        "Confirm new password:", pfNew2
                };
                int res = JOptionPane.showConfirmDialog(userF, obj, "Change Password", JOptionPane.OK_CANCEL_OPTION);
                if (res == JOptionPane.OK_OPTION) {
                    String old = new String(pfOld.getPassword());
                    String n1 = new String(pfNew.getPassword());
                    String n2 = new String(pfNew2.getPassword());
                    if (!verifyPassword(username, old)) {
                        JOptionPane.showMessageDialog(userF, "Current password incorrect.", "Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    if (n1.length() < 4) {
                        JOptionPane.showMessageDialog(userF, "New password too short.", "Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    if (!n1.equals(n2)) {
                        JOptionPane.showMessageDialog(userF, "New passwords do not match.", "Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    boolean ok = updateUserPassword(username, hashPassword(n1));
                    if (ok) {
                        JOptionPane.showMessageDialog(userF, "Password changed.");
                        log("User changed password: " + username);
                    } else {
                        JOptionPane.showMessageDialog(userF, "Failed to change password.", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });

            JButton logout = new JButton("Logout");
            logout.addActionListener(e -> userF.dispose());

            center.add(genPayslip);
            center.add(changePass);
            center.add(logout);

            root.add(center, BorderLayout.CENTER);
            userF.setContentPane(root);
            userF.setVisible(true);
        });
    }

    // ---------------- File operations ----------------

    private static synchronized void loadUsersFile() {
        try {
            Path p = Paths.get(USERS_FILE);
            if (!Files.exists(p)) Files.createFile(p);
        } catch (IOException ignored) {}
    }

    private static List<String> readAllUserLines() {
        loadUsersFile();
        List<String> lines = new ArrayList<>();
        try {
            lines = Files.readAllLines(Paths.get(USERS_FILE), StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
        return lines;
    }

    private static boolean userExists(String username) {
        List<String> lines = readAllUserLines();
        for (String line : lines) {
            String[] parts = line.split(":", 2);
            if (parts.length >= 1 && parts[0].equalsIgnoreCase(username)) return true;
        }
        return false;
    }

    private static boolean saveUser(String username, String hashedPassword) {
        loadUsersFile();
        String entry = username + ":" + hashedPassword;
        try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(USERS_FILE), StandardCharsets.UTF_8, StandardOpenOption.APPEND)) {
            bw.write(entry);
            bw.newLine();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean verifyPassword(String username, String plainPassword) {
        if (username.equalsIgnoreCase(DEFAULT_ADMIN_USER) && plainPassword.equals(DEFAULT_ADMIN_PASS)) return true;
        List<String> lines = readAllUserLines();
        String hashedInput = hashPassword(plainPassword);
        for (String line : lines) {
            String[] parts = line.split(":", 2);
            if (parts.length == 2 && parts[0].equalsIgnoreCase(username)) {
                return parts[1].equals(hashedInput);
            }
        }
        return false;
    }

    private static boolean deleteUser(String username) {
        List<String> lines = readAllUserLines();
        boolean removed = false;
        Iterator<String> it = lines.iterator();
        while (it.hasNext()) {
            String ln = it.next();
            String[] parts = ln.split(":", 2);
            if (parts.length >= 1 && parts[0].equalsIgnoreCase(username)) {
                it.remove();
                removed = true;
            }
        }
        if (!removed) return false;
        try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(USERS_FILE), StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (String l : lines) {
                bw.write(l);
                bw.newLine();
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean updateUserPassword(String username, String newHashed) {
        List<String> lines = readAllUserLines();
        boolean updated = false;
        for (int i = 0; i < lines.size(); i++) {
            String[] parts = lines.get(i).split(":", 2);
            if (parts.length >= 1 && parts[0].equalsIgnoreCase(username)) {
                lines.set(i, username + ":" + newHashed);
                updated = true;
            }
        }
        if (!updated) return false;
        try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(USERS_FILE), StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (String l : lines) {
                bw.write(l);
                bw.newLine();
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // ---------------- Utilities ----------------

    private static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] raw = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : raw) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Hash failed", e);
        }
    }

    private static void log(String message) {
        String ts = LocalDateTime.now().format(LOG_TS);
        System.out.println("[" + ts + "] " + message);
    }

    // ---------------- Main ----------------

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            loadUsersFile();
            LoginGUI gui = new LoginGUI();
            gui.setVisible(true);
        });
    }
}
