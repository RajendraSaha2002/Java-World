import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DigitalClock.java
 *
 * Simple Digital Clock GUI (Swing)
 *
 * Features:
 * - Large digital time display
 * - Toggle 12/24-hour format
 * - Option to show/hide seconds
 * - Show/hide date
 * - Start / Stop button
 * - Timezone selector (few common zones + system default)
 *
 * How to compile & run:
 *   javac DigitalClock.java
 *   java DigitalClock
 */
public class DigitalClock extends JFrame {
    private final JLabel timeLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel dateLabel = new JLabel("", SwingConstants.CENTER);
    private final JCheckBox cb24Hour = new JCheckBox("24-hour format", true);
    private final JCheckBox cbShowSeconds = new JCheckBox("Show seconds", true);
    private final JCheckBox cbShowDate = new JCheckBox("Show date", true);
    private final JButton btnStartStop = new JButton("Stop");
    private final JComboBox<String> tzCombo;
    private final Timer timer;

    // Keep mapping of friendly names -> ZoneId strings
    private final Map<String, String> zoneMap = new LinkedHashMap<>();

    public DigitalClock() {
        super("Digital Clock");

        // prepare timezones
        zoneMap.put("System Default", ZoneId.systemDefault().getId());
        zoneMap.put("Asia/Kolkata (IST)", "Asia/Kolkata");
        zoneMap.put("UTC", "UTC");
        zoneMap.put("Europe/London (GMT/BST)", "Europe/London");
        zoneMap.put("America/New_York (ET)", "America/New_York");
        zoneMap.put("Asia/Tokyo (JST)", "Asia/Tokyo");

        tzCombo = new JComboBox<>(zoneMap.keySet().toArray(new String[0]));
        tzCombo.setSelectedIndex(0);

        // UI setup
        setLayout(new BorderLayout(8, 8));
        timeLabel.setFont(new Font("Monospaced", Font.BOLD, 48));
        timeLabel.setOpaque(true);
        timeLabel.setBackground(Color.BLACK);
        timeLabel.setForeground(new Color(0x00ff88));
        timeLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        dateLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));
        dateLabel.setForeground(Color.DARK_GRAY);

        JPanel center = new JPanel(new BorderLayout(4, 4));
        center.add(timeLabel, BorderLayout.CENTER);
        center.add(dateLabel, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        // Controls panel
        JPanel controls = new JPanel();
        controls.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 8));
        controls.add(cb24Hour);
        controls.add(cbShowSeconds);
        controls.add(cbShowDate);
        controls.add(new JLabel("Timezone:"));
        controls.add(tzCombo);
        controls.add(btnStartStop);
        add(controls, BorderLayout.SOUTH);

        // Default states
        cb24Hour.setSelected(true);
        cbShowSeconds.setSelected(true);
        cbShowDate.setSelected(true);

        // Timer fires every 250 ms to keep seconds accurate and UI responsive
        timer = new Timer(250, e -> updateClock());
        timer.start();

        // Event listeners
        btnStartStop.addActionListener(e -> toggleTimer());
        cb24Hour.addActionListener(e -> updateClock());
        cbShowSeconds.addActionListener(e -> updateClock());
        cbShowDate.addActionListener(e -> dateLabel.setVisible(cbShowDate.isSelected()));
        tzCombo.addActionListener(e -> updateClock());

        // initial visibility
        dateLabel.setVisible(cbShowDate.isSelected());

        // Window settings
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(760, 220);
        setMinimumSize(new Dimension(500, 180));
        setLocationRelativeTo(null);
    }

    private void toggleTimer() {
        if (timer.isRunning()) {
            timer.stop();
            btnStartStop.setText("Start");
        } else {
            timer.start();
            btnStartStop.setText("Stop");
        }
    }

    private void updateClock() {
        // get selected ZoneId
        String key = (String) tzCombo.getSelectedItem();
        String zoneIdStr = zoneMap.getOrDefault(key, ZoneId.systemDefault().getId());
        ZoneId zoneId = ZoneId.of(zoneIdStr);

        ZonedDateTime now = ZonedDateTime.now(zoneId);

        boolean use24 = cb24Hour.isSelected();
        boolean showSeconds = cbShowSeconds.isSelected();

        // build time format pattern
        String pattern;
        if (use24) {
            pattern = showSeconds ? "HH:mm:ss" : "HH:mm";
        } else {
            pattern = showSeconds ? "hh:mm:ss a" : "hh:mm a";
        }

        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern(pattern);
        timeLabel.setText(now.format(timeFmt));

        if (cbShowDate.isSelected()) {
            DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy");
            dateLabel.setText(now.format(dateFmt) + "   [" + zoneId.getId() + "]");
        }
    }

    public static void main(String[] args) {
        // Ensure UI updates happen on EDT
        SwingUtilities.invokeLater(() -> {
            DigitalClock clock = new DigitalClock();
            clock.setVisible(true);
            // Run once to initialize immediately
            clock.updateClock();
        });
    }
}
