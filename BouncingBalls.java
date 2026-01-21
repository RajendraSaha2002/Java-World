import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Random;

public class BouncingBalls extends JPanel implements ActionListener {
    private ArrayList<Ball> balls;
    private Timer timer;
    private Random rand;
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final int BALL_COUNT = 15;
    private static final double GRAVITY = 0.5;
    private static final double ENERGY_LOSS = 0.85; // Coefficient of restitution

    public BouncingBalls() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.BLACK);

        rand = new Random();
        balls = new ArrayList<>();

        // Create balls with random properties
        for (int i = 0; i < BALL_COUNT; i++) {
            int diameter = rand.nextInt(30) + 20; // 20-50 pixels
            int x = rand.nextInt(WIDTH - diameter);
            int y = rand.nextInt(HEIGHT / 2); // Start in upper half
            double vx = rand.nextDouble() * 10 - 5; // -5 to 5
            double vy = rand.nextDouble() * 5 - 2.5; // -2.5 to 2.5
            Color color = new Color(rand.nextInt(256), rand.nextInt(256), rand.nextInt(256));

            balls.add(new Ball(x, y, diameter, vx, vy, color));
        }

        timer = new Timer(16, this); // ~60 FPS
        timer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw all balls
        for (Ball ball : balls) {
            ball.draw(g2d);
        }

        // Draw instructions
        g2d.setColor(Color.WHITE);
        g2d.drawString("Bouncing Balls with Gravity - Click to add more balls!", 10, 20);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // Update all balls
        for (Ball ball : balls) {
            ball.update();
        }
        repaint();
    }

    // Inner class for Ball
    class Ball {
        private double x, y;
        private double vx, vy;
        private int diameter;
        private Color color;

        public Ball(double x, double y, int diameter, double vx, double vy, Color color) {
            this.x = x;
            this.y = y;
            this.diameter = diameter;
            this.vx = vx;
            this.vy = vy;
            this.color = color;
        }

        public void update() {
            // Apply gravity
            vy += GRAVITY;

            // Update position
            x += vx;
            y += vy;

            // Collision detection with walls
            // Left wall
            if (x < 0) {
                x = 0;
                vx = -vx * ENERGY_LOSS;
            }
            // Right wall
            if (x + diameter > WIDTH) {
                x = WIDTH - diameter;
                vx = -vx * ENERGY_LOSS;
            }
            // Top wall
            if (y < 0) {
                y = 0;
                vy = -vy * ENERGY_LOSS;
            }
            // Bottom wall (floor) - with energy loss
            if (y + diameter > HEIGHT) {
                y = HEIGHT - diameter;
                vy = -vy * ENERGY_LOSS;

                // Stop tiny bounces
                if (Math.abs(vy) < 0.5) {
                    vy = 0;
                }
            }
        }

        public void draw(Graphics2D g2d) {
            g2d.setColor(color);
            g2d.fillOval((int)x, (int)y, diameter, diameter);

            // Add a highlight for 3D effect
            g2d.setColor(new Color(255, 255, 255, 100));
            g2d.fillOval((int)x + diameter/4, (int)y + diameter/4, diameter/3, diameter/3);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Bouncing Balls Simulation");
            BouncingBalls panel = new BouncingBalls();

            // Add mouse listener to create new balls on click
            panel.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int diameter = panel.rand.nextInt(30) + 20;
                    double vx = panel.rand.nextDouble() * 10 - 5;
                    double vy = panel.rand.nextDouble() * 5 - 10;
                    Color color = new Color(panel.rand.nextInt(256),
                            panel.rand.nextInt(256),
                            panel.rand.nextInt(256));

                    panel.balls.add(panel.new Ball(e.getX(), e.getY(), diameter, vx, vy, color));
                }
            });

            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}