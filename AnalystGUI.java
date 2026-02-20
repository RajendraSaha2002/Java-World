import com.formdev.flatlaf.FlatDarkLaf;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class AnalystGUI extends JFrame {

    // UI Components
    private JPanel networkGrid;
    private JTextArea logArea;
    private Map<String, JPanel> portMap = new HashMap<>();

    public AnalystGUI() {
        super("VIGILANT-S: PMO NETWORK INTEGRITY MONITOR");
        setSize(900, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 1. Header
        JPanel header = new JPanel();
        header.setBackground(new Color(20, 20, 20));
        JLabel title = new JLabel("ACTIVE PORT MONITORING // ZMQ LINK ACTIVE");
        title.setForeground(Color.CYAN);
        title.setFont(new Font("Consolas", Font.BOLD, 16));
        header.add(title);
        add(header, BorderLayout.NORTH);

        // 2. Network Port Grid
        networkGrid = new JPanel(new GridLayout(2, 2, 20, 20));
        networkGrid.setBackground(new Color(40, 40, 40));
        networkGrid.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Create 4 Simulated Ports
        addPort("8080", "HTTP (Web)");
        addPort("443", "HTTPS (Secure)");
        addPort("21", "FTP (File)");
        addPort("25", "SMTP (Mail)");

        add(networkGrid, BorderLayout.CENTER);

        // 3. Activity Log
        logArea = new JTextArea(6, 40);
        logArea.setBackground(Color.BLACK);
        logArea.setForeground(Color.GREEN);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        add(new JScrollPane(logArea), BorderLayout.SOUTH);

        // 4. Start ZeroMQ Listener Thread
        new Thread(this::listenToTrafficGuard).start();

        setVisible(true);
    }

    private void addPort(String portId, String label) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(0, 100, 0)); // Green = Open
        p.setBorder(BorderFactory.createLineBorder(Color.WHITE));

        JLabel lbl = new JLabel("PORT " + portId + ": " + label, SwingConstants.CENTER);
        lbl.setForeground(Color.WHITE);
        lbl.setFont(new Font("Arial", Font.BOLD, 18));

        p.add(lbl, BorderLayout.CENTER);

        portMap.put(portId, p);
        networkGrid.add(p);
    }

    // --- ZEROMQ LISTENER (Ultra-Low Latency) ---
    private void listenToTrafficGuard() {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket subscriber = context.createSocket(SocketType.SUB);
            subscriber.connect("tcp://localhost:5556");
            subscriber.subscribe("".getBytes()); // Listen to everything

            SwingUtilities.invokeLater(() -> logArea.append("System: Listening for Threat Signals on Port 5556...\n"));

            while (!Thread.currentThread().isInterrupted()) {
                // Blocks until message received
                String msg = subscriber.recvStr(0);
                if (msg != null) {
                    processSignal(msg);
                }
            }
        }
    }

    private void processSignal(String msg) {
        // Message Format: "LOCK_PORT [PORT] [REASON]"
        if (msg.startsWith("LOCK_PORT")) {
            String[] parts = msg.split(" ", 3);
            String portId = parts[1];
            String reason = parts[2];

            SwingUtilities.invokeLater(() -> {
                lockdownPort(portId, reason);
            });
        }
    }

    private void lockdownPort(String portId, String reason) {
        logArea.append("!!! CRITICAL ALERT: EXFILTRATION ATTEMPT ON PORT " + portId + " !!!\n");
        logArea.append(">>> REASON: " + reason + "\n");

        if (portMap.containsKey(portId)) {
            JPanel p = portMap.get(portId);
            p.setBackground(Color.RED); // Visual Lockdown

            // Add a "LOCKED" Label
            JLabel lockLbl = new JLabel("🔒 LOCKED", SwingConstants.CENTER);
            lockLbl.setForeground(Color.YELLOW);
            lockLbl.setFont(new Font("Impact", Font.BOLD, 24));
            p.add(lockLbl, BorderLayout.SOUTH);

            p.revalidate();
            p.repaint();

            // Show Modal Dialog
            JOptionPane.showMessageDialog(this,
                    "SECURITY INTERVENTION!\nPort " + portId + " has been frozen.\nPayload matches: " + reason,
                    "VIGILANT-S ALERT",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(new FlatDarkLaf()); } catch (Exception ex) {}
        SwingUtilities.invokeLater(AnalystGUI::new);
    }
}