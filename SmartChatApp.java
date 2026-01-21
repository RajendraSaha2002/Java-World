import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.*;

public class SmartChatApp extends JFrame {

    // --- CONFIGURATION ---
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/chat_db";
    private static final String USER = "postgres";
    private static final String PASS = "varrie75"; // <--- UPDATE THIS

    // UPDATE PATH (Use double backslashes)
    private static final String PYTHON_SCRIPT_PATH = "C:\\Users\\Rajendra Saha\\OneDrive\\Desktop\\Python Programs\\sentiment_analyzer.py";
    // ---------------------

    private JTextField userField, msgField;
    private JTable chatTable;
    private DefaultTableModel tableModel;

    public SmartChatApp() {
        setTitle("Smart Chat & Sentiment Analyzer");
        setSize(600, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // --- TOP: Message Input ---
        JPanel inputPanel = new JPanel(new BorderLayout());
        JPanel fieldsPanel = new JPanel(new GridLayout(2, 2, 5, 5));

        userField = new JTextField("User1");
        msgField = new JTextField();
        JButton sendBtn = new JButton("Send Message");
        sendBtn.setBackground(new Color(135, 206, 250)); // Sky Blue

        fieldsPanel.add(new JLabel("  Username:")); fieldsPanel.add(userField);
        fieldsPanel.add(new JLabel("  Message:")); fieldsPanel.add(msgField);

        inputPanel.add(fieldsPanel, BorderLayout.CENTER);
        inputPanel.add(sendBtn, BorderLayout.EAST);
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(inputPanel, BorderLayout.NORTH);

        // --- CENTER: Chat History ---
        // Columns: User, Message, Sentiment (Icon)
        tableModel = new DefaultTableModel(new String[]{"User", "Message", "Mood"}, 0);
        chatTable = new JTable(tableModel);
        chatTable.setRowHeight(25);
        chatTable.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14)); // Support for Emojis

        add(new JScrollPane(chatTable), BorderLayout.CENTER);

        // --- BOTTOM: Controls ---
        JPanel controlPanel = new JPanel();
        JButton analyzeBtn = new JButton("Analyze Sentiments (Python)");
        JButton refreshBtn = new JButton("Refresh Chat");

        analyzeBtn.setBackground(new Color(144, 238, 144)); // Light Green

        controlPanel.add(analyzeBtn);
        controlPanel.add(refreshBtn);
        add(controlPanel, BorderLayout.SOUTH);

        // --- LISTENERS ---
        sendBtn.addActionListener(e -> sendMessage());
        analyzeBtn.addActionListener(e -> runPythonAnalyzer());
        refreshBtn.addActionListener(e -> loadChat());

        // Initial Load
        loadChat();
    }

    // --- DATABASE METHODS ---
    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL, USER, PASS);
    }

    private void sendMessage() {
        if (msgField.getText().trim().isEmpty()) return;

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement("INSERT INTO messages (user_name, message_text) VALUES (?, ?)")) {

            pstmt.setString(1, userField.getText());
            pstmt.setString(2, msgField.getText());
            pstmt.executeUpdate();

            msgField.setText("");
            loadChat(); // Reload to see the new message (Mood will be empty initially)

            // Optional: Auto-trigger Python
            // runPythonAnalyzer();

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
        }
    }

    private void loadChat() {
        tableModel.setRowCount(0);
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT user_name, message_text, sentiment_icon FROM messages ORDER BY created_at DESC");
            while (rs.next()) {
                String icon = rs.getString("sentiment_icon");
                if (icon == null) icon = "⏳"; // Pending

                tableModel.addRow(new Object[]{
                        rs.getString("user_name"),
                        rs.getString("message_text"),
                        icon
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- PYTHON INTEGRATION ---
    private void runPythonAnalyzer() {
        new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("python", PYTHON_SCRIPT_PATH);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("Python: " + line);
                }

                int exitCode = process.waitFor();
                SwingUtilities.invokeLater(() -> {
                    if (exitCode == 0) {
                        loadChat(); // Refresh UI to show new emojis
                    } else {
                        JOptionPane.showMessageDialog(this, "Error running Python script.");
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SmartChatApp().setVisible(true));
    }
}