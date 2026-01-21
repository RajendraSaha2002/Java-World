import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

/**
 * Double Pendulum Simulation
 * * Demonstrates Chaos Theory using a system of two coupled pendulums.
 * * Features:
 * - Solves Lagrangian equations of motion for precise physics.
 * - Renders a fading trail to visualize the chaotic attractor.
 * - 'Reset' button to restart with random initial conditions.
 * - 'Clear Trace' button to wipe the background.
 */
public class DoublePendulum extends JPanel implements ActionListener {

    // --- Simulation Constants ---
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final double G = 1.0;  // Gravity constant (scaled for visual effect)

    // --- Physics State ---
    private double r1 = 150; // Length of first arm
    private double r2 = 150; // Length of second arm
    private double m1 = 20;  // Mass of first bob
    private double m2 = 20;  // Mass of second bob

    private double a1 = Math.PI / 2; // Angle 1
    private double a2 = Math.PI / 2; // Angle 2
    private double a1_v = 0;         // Angular Velocity 1
    private double a2_v = 0;         // Angular Velocity 2

    // --- Rendering ---
    private Timer timer;
    private BufferedImage canvas; // Stores the trail
    private Graphics2D canvasG;
    private double prevX2 = -1;
    private double prevY2 = -1;

    // --- UI Controls ---
    private JButton resetButton;
    private JButton clearButton;

    public static void main(String[] args) {
        JFrame frame = new JFrame("Double Pendulum (Chaos Theory)");
        DoublePendulum sim = new DoublePendulum();

        frame.add(sim);
        frame.setSize(WIDTH, HEIGHT);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setVisible(true);
        frame.setLocationRelativeTo(null);
    }

    public DoublePendulum() {
        this.setLayout(null); // Absolute positioning for buttons
        this.setBackground(Color.BLACK);

        // Setup Trace Canvas
        canvas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        canvasG = canvas.createGraphics();
        canvasG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // UI Buttons
        resetButton = createButton("Reset System", 10, 10);
        resetButton.addActionListener(e -> resetSimulation());
        this.add(resetButton);

        clearButton = createButton("Clear Trace", 120, 10);
        clearButton.addActionListener(e -> clearCanvas());
        this.add(clearButton);

        // Start Animation Loop
        timer = new Timer(16, this); // ~60 FPS
        timer.start();

        resetSimulation(); // Initialize values
    }

    private JButton createButton(String text, int x, int y) {
        JButton btn = new JButton(text);
        btn.setBounds(x, y, 100, 30);
        btn.setBackground(new Color(50, 50, 50));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        return btn;
    }

    private void resetSimulation() {
        // Randomize starting angles slightly for variation
        a1 = Math.PI / 2 + (Math.random() * 0.5 - 0.25);
        a2 = Math.PI / 2 + (Math.random() * 0.5 - 0.25);
        a1_v = 0;
        a2_v = 0;

        // Reset previous position helper so we don't draw a line across the screen
        prevX2 = -1;
        prevY2 = -1;

        clearCanvas();
    }

    private void clearCanvas() {
        // Clear the buffered image
        canvasG.setComposite(AlphaComposite.Clear);
        canvasG.fillRect(0, 0, WIDTH, HEIGHT);
        canvasG.setComposite(AlphaComposite.SrcOver);
        repaint();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        updatePhysics();
        repaint();
    }

    private void updatePhysics() {
        // --- The Equations of Motion (Lagrangian Mechanics) ---
        // These formulas calculate angular acceleration (a1_a, a2_a)

        double num1 = -G * (2 * m1 + m2) * Math.sin(a1);
        double num2 = -m2 * G * Math.sin(a1 - 2 * a2);
        double num3 = -2 * Math.sin(a1 - a2) * m2;
        double num4 = a2_v * a2_v * r2 + a1_v * a1_v * r1 * Math.cos(a1 - a2);
        double den = r1 * (2 * m1 + m2 - m2 * Math.cos(2 * a1 - 2 * a2));
        double a1_a = (num1 + num2 + num3 * num4) / den;

        num1 = 2 * Math.sin(a1 - a2);
        num2 = (a1_v * a1_v * r1 * (m1 + m2));
        num3 = G * (m1 + m2) * Math.cos(a1);
        num4 = a2_v * a2_v * r2 * m2 * Math.cos(a1 - a2);
        den = r2 * (2 * m1 + m2 - m2 * Math.cos(2 * a1 - 2 * a2));
        double a2_a = (num1 * (num2 + num3 + num4)) / den;

        // Euler Integration (Update velocity and position)
        a1_v += a1_a;
        a2_v += a2_a;
        a1 += a1_v;
        a2 += a2_v;

        // Dampening (simulate friction/air resistance) - keeps it from spinning forever
        // a1_v *= 0.999;
        // a2_v *= 0.999;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 1. Calculate Cartesian coordinates
        // Translate 0,0 to center of screen horizontally, and some offset down vertically
        double cx = WIDTH / 2.0;
        double cy = 150;

        double x1 = r1 * Math.sin(a1) + cx;
        double y1 = r1 * Math.cos(a1) + cy;

        double x2 = x1 + r2 * Math.sin(a2);
        double y2 = y1 + r2 * Math.cos(a2);

        // 2. Draw to the Trace Canvas (The colorful path)
        if (prevX2 != -1) {
            // Map velocity to color for cool effect (HSV)
            float velocity = (float)(Math.abs(a1_v) + Math.abs(a2_v));
            Color traceColor = Color.getHSBColor(velocity * 2.0f % 1.0f, 1.0f, 1.0f);

            canvasG.setColor(traceColor);
            canvasG.setStroke(new BasicStroke(1)); // Thin line for detail
            canvasG.drawLine((int)prevX2, (int)prevY2, (int)x2, (int)y2);

            // Optional: Fade effect (slowly dim the canvas)
            // canvasG.setColor(new Color(0, 0, 0, 1));
            // canvasG.fillRect(0, 0, WIDTH, HEIGHT);
        }

        prevX2 = x2;
        prevY2 = y2;

        // 3. Render everything to screen

        // A. Draw the trace layer
        g2.drawImage(canvas, 0, 0, null);

        // B. Draw the Pendulum arms
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(2));
        g2.drawLine((int)cx, (int)cy, (int)x1, (int)y1);
        g2.drawLine((int)x1, (int)y1, (int)x2, (int)y2);

        // C. Draw the Mass Bobs
        g2.setColor(Color.WHITE);
        int r = 10;
        g2.fillOval((int)x1 - r, (int)y1 - r, r * 2, r * 2);
        g2.fillOval((int)x2 - r, (int)y2 - r, r * 2, r * 2);

        // D. Draw Anchor point
        g2.setColor(Color.GRAY);
        g2.fillOval((int)cx - 5, (int)cy - 5, 10, 10);
    }
}