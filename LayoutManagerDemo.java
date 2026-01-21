import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class LayoutManagerDemo extends JFrame {

    public LayoutManagerDemo() {
        setTitle("Layout Manager Demo");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Create tabbed pane to show different layouts
        JTabbedPane tabbedPane = new JTabbedPane();

        // Add different layout panels
        tabbedPane.addTab("FlowLayout", createFlowLayoutPanel());
        tabbedPane.addTab("BorderLayout", createBorderLayoutPanel());
        tabbedPane.addTab("GridLayout", createGridLayoutPanel());
        tabbedPane.addTab("BoxLayout", createBoxLayoutPanel());
        tabbedPane.addTab("CardLayout", createCardLayoutPanel());
        tabbedPane.addTab("GridBagLayout", createGridBagLayoutPanel());

        add(tabbedPane);
        setVisible(true);
    }

    // 1. FlowLayout Demo
    private JPanel createFlowLayoutPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 10));

        panel.add(new JLabel("FlowLayout arranges components in a row"));
        panel.add(new JButton("Button 1"));
        panel.add(new JButton("Button 2"));
        panel.add(new JButton("Button 3"));
        panel.add(new JTextField(15));
        panel.add(new JButton("Button 4"));
        panel.add(new JButton("Long Button 5"));

        return panel;
    }

    // 2. BorderLayout Demo
    private JPanel createBorderLayoutPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        panel.add(new JButton("NORTH"), BorderLayout.NORTH);
        panel.add(new JButton("SOUTH"), BorderLayout.SOUTH);
        panel.add(new JButton("EAST"), BorderLayout.EAST);
        panel.add(new JButton("WEST"), BorderLayout.WEST);

        JTextArea center = new JTextArea("CENTER\n\nBorderLayout divides container into 5 regions");
        center.setEditable(false);
        panel.add(new JScrollPane(center), BorderLayout.CENTER);

        return panel;
    }

    // 3. GridLayout Demo
    private JPanel createGridLayoutPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 3, 5, 5));

        for (int i = 1; i <= 9; i++) {
            panel.add(new JButton("Button " + i));
        }

        return panel;
    }

    // 4. BoxLayout Demo
    private JPanel createBoxLayoutPanel() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        mainPanel.add(new JLabel("BoxLayout - Vertical"));
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(new JButton("Button 1"));
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(new JButton("Button 2"));
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(new JTextField(20));
        mainPanel.add(Box.createVerticalGlue());

        JPanel horizontal = new JPanel();
        horizontal.setLayout(new BoxLayout(horizontal, BoxLayout.X_AXIS));
        horizontal.add(new JLabel("Horizontal: "));
        horizontal.add(Box.createHorizontalStrut(10));
        horizontal.add(new JButton("H-Button 1"));
        horizontal.add(Box.createHorizontalStrut(10));
        horizontal.add(new JButton("H-Button 2"));

        mainPanel.add(horizontal);

        return mainPanel;
    }

    // 5. CardLayout Demo
    private JPanel createCardLayoutPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        JPanel cardPanel = new JPanel();
        CardLayout cardLayout = new CardLayout();
        cardPanel.setLayout(cardLayout);

        // Create cards
        JPanel card1 = new JPanel();
        card1.setBackground(Color.CYAN);
        card1.add(new JLabel("Card 1"));

        JPanel card2 = new JPanel();
        card2.setBackground(Color.YELLOW);
        card2.add(new JLabel("Card 2"));

        JPanel card3 = new JPanel();
        card3.setBackground(Color.PINK);
        card3.add(new JLabel("Card 3"));

        cardPanel.add(card1, "Card1");
        cardPanel.add(card2, "Card2");
        cardPanel.add(card3, "Card3");

        // Control buttons
        JPanel controls = new JPanel();
        JButton prev = new JButton("Previous");
        JButton next = new JButton("Next");

        prev.addActionListener(e -> cardLayout.previous(cardPanel));
        next.addActionListener(e -> cardLayout.next(cardPanel));

        controls.add(prev);
        controls.add(next);

        mainPanel.add(cardPanel, BorderLayout.CENTER);
        mainPanel.add(controls, BorderLayout.SOUTH);

        return mainPanel;
    }

    // 6. GridBagLayout Demo
    private JPanel createGridBagLayoutPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Name label and field
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Name:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        panel.add(new JTextField(20), gbc);

        // Email label and field
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        panel.add(new JLabel("Email:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        panel.add(new JTextField(20), gbc);

        // Comments
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.NORTH;
        panel.add(new JLabel("Comments:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.gridheight = 2;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(new JScrollPane(new JTextArea(5, 20)), gbc);

        // Buttons
        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.gridwidth = 1;
        gbc.gridheight = 1;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JButton("Submit"), gbc);

        gbc.gridx = 2;
        panel.add(new JButton("Cancel"), gbc);

        return panel;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LayoutManagerDemo());
    }
}