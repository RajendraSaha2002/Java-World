import javax.swing.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DashboardUI extends JFrame {
    private JTextArea consoleArea;
    private JPanel l4Panel;
    private JPanel l7Panel;
    private JLabel aptWarningLabel;

    public DashboardUI() {
        setTitle("Omni-Defender C2 Dashboard");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(30, 30, 30));

        // Top Status Panels (OSI Layers)
        JPanel statusPanel = new JPanel(new GridLayout(1, 2));
        l4Panel = createIndicatorPanel("Layer 4 (Transport)");
        l7Panel = createIndicatorPanel("Layer 7 (Application)");
        statusPanel.add(l4Panel);
        statusPanel.add(l7Panel);
        add(statusPanel, BorderLayout.NORTH);

        // Center Console
        consoleArea = new JTextArea();
        consoleArea.setBackground(new Color(15, 15, 15));
        consoleArea.setForeground(new Color(0, 255, 0));
        consoleArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        consoleArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(consoleArea);
        add(scrollPane, BorderLayout.CENTER);

        // Bottom APT Warning
        aptWarningLabel = new JLabel("SYSTEM SECURE", SwingConstants.CENTER);
        aptWarningLabel.setForeground(Color.GREEN);
        aptWarningLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
        add(aptWarningLabel, BorderLayout.SOUTH);

        setVisible(true);
    }

    private JPanel createIndicatorPanel(String title) {
        JPanel panel = new JPanel();
        panel.setBackground(Color.DARK_GRAY);
        panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.GRAY), title));
        return panel;
    }

    public void logToConsole(String message) {
        SwingUtilities.invokeLater(() -> {
            String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
            consoleArea.append("[" + time + "] " + message + "\n");
            consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
        });
    }

    public void triggerFlash(int layer) {
        SwingUtilities.invokeLater(() -> {
            JPanel target = (layer == 4) ? l4Panel : l7Panel;
            target.setBackground(Color.RED);
            Timer timer = new Timer(500, e -> target.setBackground(Color.DARK_GRAY));
            timer.setRepeats(false);
            timer.start();
        });
    }

    public void setAptAlert(boolean isApt) {
        SwingUtilities.invokeLater(() -> {
            if (isApt) {
                aptWarningLabel.setText("CRITICAL: MULTI-LAYER APT DETECTED!");
                aptWarningLabel.setForeground(Color.RED);
            } else {
                aptWarningLabel.setText("SYSTEM SECURE");
                aptWarningLabel.setForeground(Color.GREEN);
            }
        });
    }
}