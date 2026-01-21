import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class SystemClock extends JFrame {
    private JLabel digitalClock;
    private JLabel dateLabelLabel;
    private AnalogClockPanel analogClock;
    private Timer timer;

    public SystemClock() {
        setTitle("System Clock");
        setSize(600, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));
        getContentPane().setBackground(new Color(30, 30, 30));

        createDigitalClock();
        createAnalogClock();
        createControlPanel();

        // Start the clock
        startClock();

        setVisible(true);
    }

    private void createDigitalClock() {
        JPanel digitalPanel = new JPanel();
        digitalPanel.setLayout(new BoxLayout(digitalPanel, BoxLayout.Y_AXIS));
        digitalPanel.setBackground(new Color(30, 30, 30));
        digitalPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Digital time display
        digitalClock = new JLabel();
        digitalClock.setFont(new Font("Digital-7", Font.BOLD, 72));
        digitalClock.setForeground(new Color(0, 255, 0));
        digitalClock.setAlignmentX(Component.CENTER_ALIGNMENT);
        digitalClock.setHorizontalAlignment(SwingConstants.CENTER);

        // Date display
        dateLabelLabel = new JLabel();
        dateLabelLabel.setFont(new Font("Arial", Font.BOLD, 24));
        dateLabelLabel.setForeground(new Color(100, 200, 255));
        dateLabelLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        dateLabelLabel.setHorizontalAlignment(SwingConstants.CENTER);

        digitalPanel.add(digitalClock);
        digitalPanel.add(Box.createVerticalStrut(10));
        digitalPanel.add(dateLabelLabel);

        add(digitalPanel, BorderLayout.NORTH);
    }

    private void createAnalogClock() {
        analogClock = new AnalogClockPanel();
        analogClock.setPreferredSize(new Dimension(400, 400));
        analogClock.setBackground(new Color(30, 30, 30));

        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setBackground(new Color(30, 30, 30));
        centerPanel.add(analogClock);

        add(centerPanel, BorderLayout.CENTER);
    }

    private void createControlPanel() {
        JPanel controlPanel = new JPanel(new FlowLayout());
        controlPanel.setBackground(new Color(50, 50, 50));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JButton btnAlarm = createStyledButton("Set Alarm", new Color(255, 100, 100));
        JButton btnStopwatch = createStyledButton("Stopwatch", new Color(100, 150, 255));
        JButton btnTimer = createStyledButton("Timer", new Color(100, 255, 150));

        btnAlarm.addActionListener(e -> showAlarmDialog());
        btnStopwatch.addActionListener(e -> openStopwatch());
        btnTimer.addActionListener(e -> openTimer());

        controlPanel.add(btnAlarm);
        controlPanel.add(btnStopwatch);
        controlPanel.add(btnTimer);

        add(controlPanel, BorderLayout.SOUTH);
    }

    private JButton createStyledButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }

    private void startClock() {
        timer = new Timer(1000, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateTime();
            }
        });
        timer.start();
        updateTime(); // Initial update
    }

    private void updateTime() {
        Date now = new Date();

        // Update digital clock
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
        digitalClock.setText(timeFormat.format(now));

        // Update date
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMMM dd, yyyy");
        dateLabelLabel.setText(dateFormat.format(now));

        // Update analog clock
        analogClock.repaint();
    }

    private void showAlarmDialog() {
        JPanel panel = new JPanel(new GridLayout(3, 2, 10, 10));

        JSpinner hourSpinner = new JSpinner(new SpinnerNumberModel(12, 0, 23, 1));
        JSpinner minuteSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 59, 1));

        panel.add(new JLabel("Hour (0-23):"));
        panel.add(hourSpinner);
        panel.add(new JLabel("Minute (0-59):"));
        panel.add(minuteSpinner);

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Set Alarm", JOptionPane.OK_CANCEL_OPTION);

        if (result == JOptionPane.OK_OPTION) {
            int hour = (Integer) hourSpinner.getValue();
            int minute = (Integer) minuteSpinner.getValue();
            JOptionPane.showMessageDialog(this,
                    String.format("Alarm set for %02d:%02d", hour, minute),
                    "Alarm Set", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void openStopwatch() {
        new StopwatchWindow();
    }

    private void openTimer() {
        JOptionPane.showMessageDialog(this,
                "Timer feature coming soon!",
                "Timer", JOptionPane.INFORMATION_MESSAGE);
    }

    // Analog Clock Panel
    class AnalogClockPanel extends JPanel {

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();
            int centerX = width / 2;
            int centerY = height / 2;
            int radius = Math.min(width, height) / 2 - 20;

            // Draw clock face
            g2d.setColor(new Color(60, 60, 60));
            g2d.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

            // Draw outer ring
            g2d.setColor(new Color(150, 150, 150));
            g2d.setStroke(new BasicStroke(3));
            g2d.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

            // Draw hour markers
            g2d.setColor(Color.WHITE);
            for (int i = 0; i < 12; i++) {
                double angle = Math.toRadians(i * 30 - 90);
                int x1 = (int) (centerX + Math.cos(angle) * (radius - 10));
                int y1 = (int) (centerY + Math.sin(angle) * (radius - 10));
                int x2 = (int) (centerX + Math.cos(angle) * (radius - 20));
                int y2 = (int) (centerY + Math.sin(angle) * (radius - 20));

                g2d.setStroke(new BasicStroke(3));
                g2d.drawLine(x1, y1, x2, y2);

                // Draw numbers
                g2d.setFont(new Font("Arial", Font.BOLD, 18));
                int num = i == 0 ? 12 : i;
                int numX = (int) (centerX + Math.cos(angle) * (radius - 40)) - 8;
                int numY = (int) (centerY + Math.sin(angle) * (radius - 40)) + 6;
                g2d.drawString(String.valueOf(num), numX, numY);
            }

            // Get current time
            Calendar cal = Calendar.getInstance();
            int hour = cal.get(Calendar.HOUR);
            int minute = cal.get(Calendar.MINUTE);
            int second = cal.get(Calendar.SECOND);

            // Draw second hand (red)
            double secondAngle = Math.toRadians(second * 6 - 90);
            int secondX = (int) (centerX + Math.cos(secondAngle) * (radius - 30));
            int secondY = (int) (centerY + Math.sin(secondAngle) * (radius - 30));
            g2d.setColor(Color.RED);
            g2d.setStroke(new BasicStroke(2));
            g2d.drawLine(centerX, centerY, secondX, secondY);

            // Draw minute hand (white)
            double minuteAngle = Math.toRadians(minute * 6 + second * 0.1 - 90);
            int minuteX = (int) (centerX + Math.cos(minuteAngle) * (radius - 40));
            int minuteY = (int) (centerY + Math.sin(minuteAngle) * (radius - 40));
            g2d.setColor(Color.WHITE);
            g2d.setStroke(new BasicStroke(4));
            g2d.drawLine(centerX, centerY, minuteX, minuteY);

            // Draw hour hand (white)
            double hourAngle = Math.toRadians(hour * 30 + minute * 0.5 - 90);
            int hourX = (int) (centerX + Math.cos(hourAngle) * (radius - 60));
            int hourY = (int) (centerY + Math.sin(hourAngle) * (radius - 60));
            g2d.setStroke(new BasicStroke(6));
            g2d.drawLine(centerX, centerY, hourX, hourY);

            // Draw center circle
            g2d.setColor(new Color(200, 200, 200));
            g2d.fillOval(centerX - 8, centerY - 8, 16, 16);
        }
    }

    // Stopwatch Window
    class StopwatchWindow extends JFrame {
        private JLabel stopwatchLabel;
        private JButton btnStart, btnStop, btnReset;
        private Timer stopwatchTimer;
        private int elapsedTime = 0; // in milliseconds

        public StopwatchWindow() {
            setTitle("Stopwatch");
            setSize(400, 250);
            setLocationRelativeTo(null);
            setLayout(new BorderLayout(10, 10));
            getContentPane().setBackground(new Color(40, 40, 40));

            // Display
            stopwatchLabel = new JLabel("00:00:00.000");
            stopwatchLabel.setFont(new Font("Digital-7", Font.BOLD, 48));
            stopwatchLabel.setForeground(new Color(0, 255, 255));
            stopwatchLabel.setHorizontalAlignment(SwingConstants.CENTER);
            stopwatchLabel.setBorder(BorderFactory.createEmptyBorder(30, 20, 30, 20));

            // Buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 20));
            buttonPanel.setBackground(new Color(40, 40, 40));

            btnStart = createStyledButton("Start", new Color(50, 200, 50));
            btnStop = createStyledButton("Stop", new Color(255, 100, 100));
            btnReset = createStyledButton("Reset", new Color(100, 150, 255));

            btnStop.setEnabled(false);

            btnStart.addActionListener(e -> startStopwatch());
            btnStop.addActionListener(e -> stopStopwatch());
            btnReset.addActionListener(e -> resetStopwatch());

            buttonPanel.add(btnStart);
            buttonPanel.add(btnStop);
            buttonPanel.add(btnReset);

            add(stopwatchLabel, BorderLayout.CENTER);
            add(buttonPanel, BorderLayout.SOUTH);

            setVisible(true);
        }

        private void startStopwatch() {
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);

            stopwatchTimer = new Timer(10, e -> {
                elapsedTime += 10;
                updateStopwatchDisplay();
            });
            stopwatchTimer.start();
        }

        private void stopStopwatch() {
            btnStart.setEnabled(true);
            btnStop.setEnabled(false);
            if (stopwatchTimer != null) {
                stopwatchTimer.stop();
            }
        }

        private void resetStopwatch() {
            elapsedTime = 0;
            updateStopwatchDisplay();
        }

        private void updateStopwatchDisplay() {
            int millis = elapsedTime % 1000;
            int seconds = (elapsedTime / 1000) % 60;
            int minutes = (elapsedTime / 60000) % 60;
            int hours = (elapsedTime / 3600000);

            stopwatchLabel.setText(String.format("%02d:%02d:%02d.%03d",
                    hours, minutes, seconds, millis));
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SystemClock());
    }
}