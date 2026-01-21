import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalTime;

public class AnalogClock extends JPanel implements ActionListener {
    private Timer timer;
    private static final int SIZE = 400;
    private static final int CENTER_X = SIZE / 2;
    private static final int CENTER_Y = SIZE / 2;
    private static final int CLOCK_RADIUS = 180;

    public AnalogClock() {
        setPreferredSize(new Dimension(SIZE, SIZE));
        setBackground(new Color(240, 240, 245));

        // Update every 100ms for smooth second hand movement
        timer = new Timer(100, this);
        timer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        // Enable anti-aliasing for smooth lines
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // Draw clock face
        drawClockFace(g2d);

        // Get current time
        LocalTime time = LocalTime.now();
        int hour = time.getHour() % 12;
        int minute = time.getMinute();
        int second = time.getSecond();
        int millis = time.getNano() / 1_000_000;

        // Draw clock hands
        drawHourHand(g2d, hour, minute);
        drawMinuteHand(g2d, minute, second);
        drawSecondHand(g2d, second, millis);

        // Draw center dot
        g2d.setColor(new Color(50, 50, 50));
        g2d.fillOval(CENTER_X - 8, CENTER_Y - 8, 16, 16);

        // Draw digital time display
        drawDigitalTime(g2d, time);
    }

    private void drawClockFace(Graphics2D g2d) {
        // Draw outer circle
        g2d.setColor(Color.WHITE);
        g2d.fillOval(CENTER_X - CLOCK_RADIUS, CENTER_Y - CLOCK_RADIUS,
                CLOCK_RADIUS * 2, CLOCK_RADIUS * 2);

        // Draw border
        g2d.setColor(new Color(60, 60, 60));
        g2d.setStroke(new BasicStroke(4));
        g2d.drawOval(CENTER_X - CLOCK_RADIUS, CENTER_Y - CLOCK_RADIUS,
                CLOCK_RADIUS * 2, CLOCK_RADIUS * 2);

        // Draw hour markers
        for (int i = 0; i < 12; i++) {
            double angle = Math.toRadians(i * 30 - 90); // Start at 12 o'clock

            // Calculate positions for markers
            int outerX = CENTER_X + (int)(CLOCK_RADIUS * 0.9 * Math.cos(angle));
            int outerY = CENTER_Y + (int)(CLOCK_RADIUS * 0.9 * Math.sin(angle));
            int innerX = CENTER_X + (int)(CLOCK_RADIUS * 0.8 * Math.cos(angle));
            int innerY = CENTER_Y + (int)(CLOCK_RADIUS * 0.8 * Math.sin(angle));

            // Draw thick lines for hour markers
            g2d.setStroke(new BasicStroke(3));
            g2d.setColor(new Color(80, 80, 80));
            g2d.drawLine(innerX, innerY, outerX, outerY);

            // Draw numbers
            int numX = CENTER_X + (int)(CLOCK_RADIUS * 0.7 * Math.cos(angle));
            int numY = CENTER_Y + (int)(CLOCK_RADIUS * 0.7 * Math.sin(angle));

            g2d.setFont(new Font("Arial", Font.BOLD, 18));
            String num = String.valueOf(i == 0 ? 12 : i);
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(num);
            int textHeight = fm.getAscent();
            g2d.drawString(num, numX - textWidth / 2, numY + textHeight / 3);
        }

        // Draw minute markers
        g2d.setStroke(new BasicStroke(1));
        g2d.setColor(new Color(150, 150, 150));
        for (int i = 0; i < 60; i++) {
            if (i % 5 != 0) { // Skip hour positions
                double angle = Math.toRadians(i * 6 - 90);
                int outerX = CENTER_X + (int)(CLOCK_RADIUS * 0.9 * Math.cos(angle));
                int outerY = CENTER_Y + (int)(CLOCK_RADIUS * 0.9 * Math.sin(angle));
                int innerX = CENTER_X + (int)(CLOCK_RADIUS * 0.85 * Math.cos(angle));
                int innerY = CENTER_Y + (int)(CLOCK_RADIUS * 0.85 * Math.sin(angle));
                g2d.drawLine(innerX, innerY, outerX, outerY);
            }
        }
    }

    private void drawHourHand(Graphics2D g2d, int hour, int minute) {
        // Hour hand moves continuously (not just on the hour)
        double hourAngle = Math.toRadians((hour * 30 + minute * 0.5) - 90);
        int handLength = (int)(CLOCK_RADIUS * 0.5);

        int endX = CENTER_X + (int)(handLength * Math.cos(hourAngle));
        int endY = CENTER_Y + (int)(handLength * Math.sin(hourAngle));

        g2d.setColor(new Color(40, 40, 40));
        g2d.setStroke(new BasicStroke(8, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawLine(CENTER_X, CENTER_Y, endX, endY);
    }

    private void drawMinuteHand(Graphics2D g2d, int minute, int second) {
        // Minute hand moves continuously (not just on the minute)
        double minuteAngle = Math.toRadians((minute * 6 + second * 0.1) - 90);
        int handLength = (int)(CLOCK_RADIUS * 0.7);

        int endX = CENTER_X + (int)(handLength * Math.cos(minuteAngle));
        int endY = CENTER_Y + (int)(handLength * Math.sin(minuteAngle));

        g2d.setColor(new Color(60, 60, 60));
        g2d.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawLine(CENTER_X, CENTER_Y, endX, endY);
    }

    private void drawSecondHand(Graphics2D g2d, int second, int millis) {
        // Second hand moves smoothly using milliseconds
        double secondAngle = Math.toRadians((second * 6 + millis * 0.006) - 90);
        int handLength = (int)(CLOCK_RADIUS * 0.85);

        int endX = CENTER_X + (int)(handLength * Math.cos(secondAngle));
        int endY = CENTER_Y + (int)(handLength * Math.sin(secondAngle));

        // Draw tail (opposite direction)
        int tailLength = (int)(CLOCK_RADIUS * 0.15);
        int tailX = CENTER_X - (int)(tailLength * Math.cos(secondAngle));
        int tailY = CENTER_Y - (int)(tailLength * Math.sin(secondAngle));

        g2d.setColor(new Color(220, 50, 50));
        g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawLine(tailX, tailY, endX, endY);
    }

    private void drawDigitalTime(Graphics2D g2d, LocalTime time) {
        String timeStr = String.format("%02d:%02d:%02d",
                time.getHour(),
                time.getMinute(),
                time.getSecond());

        g2d.setFont(new Font("Monospaced", Font.BOLD, 20));
        g2d.setColor(new Color(60, 60, 60));
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(timeStr);
        g2d.drawString(timeStr, CENTER_X - textWidth / 2, SIZE - 30);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        repaint();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Analog Clock");
            AnalogClock clock = new AnalogClock();

            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(clock);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setResizable(false);
            frame.setVisible(true);
        });
    }
}