import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class DialogBoxDemo extends JFrame {
    private JTextArea outputArea;

    public DialogBoxDemo() {
        setTitle("Dialog Box Demo");
        setSize(700, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // Create main panel with buttons
        JPanel buttonPanel = new JPanel(new GridLayout(0, 2, 10, 10));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Add buttons for different dialog types
        addButton(buttonPanel, "Message Dialog", e -> showMessageDialog());
        addButton(buttonPanel, "Confirm Dialog", e -> showConfirmDialog());
        addButton(buttonPanel, "Input Dialog", e -> showInputDialog());
        addButton(buttonPanel, "Option Dialog", e -> showOptionDialog());
        addButton(buttonPanel, "Custom Dialog", e -> showCustomDialog());
        addButton(buttonPanel, "File Chooser Dialog", e -> showFileChooserDialog());
        addButton(buttonPanel, "Color Chooser Dialog", e -> showColorChooserDialog());
        addButton(buttonPanel, "Password Dialog", e -> showPasswordDialog());
        addButton(buttonPanel, "Progress Dialog", e -> showProgressDialog());
        addButton(buttonPanel, "List Selection Dialog", e -> showListSelectionDialog());

        // Output area to display results
        outputArea = new JTextArea(10, 50);
        outputArea.setEditable(false);
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(outputArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Output"));

        // Add components to frame
        add(buttonPanel, BorderLayout.CENTER);
        add(scrollPane, BorderLayout.SOUTH);

        setVisible(true);
    }

    private void addButton(JPanel panel, String text, ActionListener listener) {
        JButton button = new JButton(text);
        button.addActionListener(listener);
        panel.add(button);
    }

    private void appendOutput(String message) {
        outputArea.append(message + "\n");
        outputArea.setCaretPosition(outputArea.getDocument().getLength());
    }

    // 1. Message Dialog
    private void showMessageDialog() {
        // Plain message
        JOptionPane.showMessageDialog(this,
                "This is a simple message dialog",
                "Information",
                JOptionPane.INFORMATION_MESSAGE);

        // Warning message
        JOptionPane.showMessageDialog(this,
                "This is a warning message!",
                "Warning",
                JOptionPane.WARNING_MESSAGE);

        // Error message
        JOptionPane.showMessageDialog(this,
                "This is an error message!",
                "Error",
                JOptionPane.ERROR_MESSAGE);

        appendOutput("Message dialogs displayed");
    }

    // 2. Confirm Dialog
    private void showConfirmDialog() {
        int result = JOptionPane.showConfirmDialog(this,
                "Do you want to continue?",
                "Confirm",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        String response = "";
        if (result == JOptionPane.YES_OPTION) {
            response = "User clicked YES";
        } else if (result == JOptionPane.NO_OPTION) {
            response = "User clicked NO";
        } else {
            response = "User clicked CANCEL";
        }

        appendOutput("Confirm Dialog: " + response);
    }

    // 3. Input Dialog
    private void showInputDialog() {
        String name = JOptionPane.showInputDialog(this,
                "Enter your name:",
                "Input Dialog",
                JOptionPane.QUESTION_MESSAGE);

        if (name != null && !name.trim().isEmpty()) {
            appendOutput("Input Dialog: User entered '" + name + "'");
        } else {
            appendOutput("Input Dialog: User cancelled or entered empty text");
        }
    }

    // 4. Option Dialog
    private void showOptionDialog() {
        Object[] options = {"Option 1", "Option 2", "Option 3", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this,
                "Choose an option:",
                "Option Dialog",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);

        if (choice >= 0 && choice < options.length) {
            appendOutput("Option Dialog: User selected '" + options[choice] + "'");
        } else {
            appendOutput("Option Dialog: User closed the dialog");
        }
    }

    // 5. Custom Dialog
    private void showCustomDialog() {
        JDialog dialog = new JDialog(this, "Custom Dialog", true);
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));

        // Create form
        JPanel formPanel = new JPanel(new GridLayout(3, 2, 5, 5));
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        formPanel.add(new JLabel("Name:"));
        JTextField nameField = new JTextField();
        formPanel.add(nameField);

        formPanel.add(new JLabel("Email:"));
        JTextField emailField = new JTextField();
        formPanel.add(emailField);

        formPanel.add(new JLabel("Age:"));
        JTextField ageField = new JTextField();
        formPanel.add(ageField);

        // Buttons
        JPanel buttonPanel = new JPanel();
        JButton okButton = new JButton("OK");
        JButton cancelButton = new JButton("Cancel");

        okButton.addActionListener(e -> {
            String info = String.format("Name: %s, Email: %s, Age: %s",
                    nameField.getText(), emailField.getText(), ageField.getText());
            appendOutput("Custom Dialog: " + info);
            dialog.dispose();
        });

        cancelButton.addActionListener(e -> {
            appendOutput("Custom Dialog: Cancelled");
            dialog.dispose();
        });

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        dialog.add(formPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    // 6. File Chooser Dialog
    private void showFileChooserDialog() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select a File");

        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            appendOutput("File Chooser: Selected file - " +
                    fileChooser.getSelectedFile().getAbsolutePath());
        } else {
            appendOutput("File Chooser: No file selected");
        }
    }

    // 7. Color Chooser Dialog
    private void showColorChooserDialog() {
        Color selectedColor = JColorChooser.showDialog(this,
                "Choose a Color",
                Color.BLUE);

        if (selectedColor != null) {
            appendOutput(String.format("Color Chooser: RGB(%d, %d, %d)",
                    selectedColor.getRed(),
                    selectedColor.getGreen(),
                    selectedColor.getBlue()));
        } else {
            appendOutput("Color Chooser: No color selected");
        }
    }

    // 8. Password Dialog
    private void showPasswordDialog() {
        JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
        JLabel userLabel = new JLabel("Username:");
        JTextField userField = new JTextField(15);
        JLabel passLabel = new JLabel("Password:");
        JPasswordField passField = new JPasswordField(15);

        panel.add(userLabel);
        panel.add(userField);
        panel.add(passLabel);
        panel.add(passField);

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Login", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String username = userField.getText();
            String password = new String(passField.getPassword());
            appendOutput("Password Dialog: Username: " + username +
                    ", Password: " + "*".repeat(password.length()));
        } else {
            appendOutput("Password Dialog: Login cancelled");
        }
    }

    // 9. Progress Dialog
    private void showProgressDialog() {
        JDialog progressDialog = new JDialog(this, "Progress", true);
        progressDialog.setSize(300, 100);
        progressDialog.setLocationRelativeTo(this);
        progressDialog.setLayout(new BorderLayout(10, 10));

        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.add(new JLabel("Processing..."), BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.CENTER);

        progressDialog.add(panel);

        // Background worker to update progress
        SwingWorker<Void, Integer> worker = new SwingWorker<Void, Integer>() {
            @Override
            protected Void doInBackground() throws Exception {
                for (int i = 0; i <= 100; i++) {
                    Thread.sleep(30);
                    publish(i);
                }
                return null;
            }

            @Override
            protected void process(java.util.List<Integer> chunks) {
                int value = chunks.get(chunks.size() - 1);
                progressBar.setValue(value);
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                appendOutput("Progress Dialog: Task completed");
            }
        };

        worker.execute();
        progressDialog.setVisible(true);
    }

    // 10. List Selection Dialog
    private void showListSelectionDialog() {
        String[] items = {"Java", "Python", "C++", "JavaScript", "Ruby", "Go"};

        String selected = (String) JOptionPane.showInputDialog(this,
                "Select your favorite programming language:",
                "List Selection",
                JOptionPane.QUESTION_MESSAGE,
                null,
                items,
                items[0]);

        if (selected != null) {
            appendOutput("List Selection Dialog: Selected '" + selected + "'");
        } else {
            appendOutput("List Selection Dialog: No selection made");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new DialogBoxDemo());
    }
}