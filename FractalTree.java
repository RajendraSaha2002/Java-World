import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Fractal Tree Simulation (Recursive)
 * * Logic:
 * 1. Recursion: The 'drawBranch' function draws a line, then calls itself twice
 * (once for the left branch, once for the right).
 * 2. Growth: The recursion depth limits how many times it splits.
 * 3. Wind: A time-based sine wave modifies the angle of every branch,
 * simulating swaying.
 * * Interaction:
 * - Move mouse horizontally to change the branching angle.
 */
public class FractalTree extends JPanel implements ActionListener {

    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;

    // Animation state
    private double angleOffset = Math.PI / 4; // Controlled by mouse
    private double time = 0;
    private Timer timer;

    // Tree Configuration
    private double lengthScale = 0.7; // How much shorter each branch is
    private int maxDepth = 10; // Recursion limit

    public static void main(String[] args) {
        JFrame frame = new JFrame("Recursive Fractal Tree");
        FractalTree sim = new FractalTree();

        frame.add(sim);
        frame.setSize(WIDTH, HEIGHT);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.setVisible(true);
    }

    public FractalTree() {
        setBackground(Color.BLACK);

        // Mouse listener to control branch angle
        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                // Map mouse X to an angle between 0 and PI
                angleOffset = (e.getX() / (double)WIDTH) * Math.PI;
            }
        };
        addMouseMotionListener(mouseAdapter);

        // Animation Loop
        timer = new Timer(16, this); // ~60 FPS
        timer.start();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        time += 0.02; // Increment time for wind simulation
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        // Better graphics quality
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(Color.WHITE);
        g2.drawString("Move mouse to change branch angle.", 10, 20);

        // Start recursion from the bottom center
        // Initial inputs: x, y, length, angle, currentDepth
        drawBranch(g2, WIDTH / 2, HEIGHT, 180, -Math.PI / 2, maxDepth);
    }

    /**
     * Recursive function to draw a branch and its children
     */
    private void drawBranch(Graphics2D g, double x, double y, double len, double angle, int depth) {
        if (depth == 0) return;

        // Calculate end point of this branch
        double x2 = x + Math.cos(angle) * len;
        double y2 = y + Math.sin(angle) * len;

        // Draw the branch
        // Make the trunk thicker and tips thinner
        g.setStroke(new BasicStroke(depth));

        // Color gradient from brown (trunk) to green (leaves)
        if (depth > 4) g.setColor(new Color(101, 67, 33)); // Wood
        else g.setColor(new Color(50, 200, 50)); // Leaf

        g.drawLine((int)x, (int)y, (int)x2, (int)y2);

        // Wind Effect:
        // Add a slight sine wave variation based on time and depth.
        // Smaller branches (lower depth) sway more.
        double wind = Math.sin(time + depth * 0.2) * 0.05;

        // Recursive calls for two new branches
        // Right branch
        drawBranch(g, x2, y2, len * lengthScale, angle + angleOffset + wind, depth - 1);
        // Left branch
        drawBranch(g, x2, y2, len * lengthScale, angle - angleOffset + wind, depth - 1);
    }
}