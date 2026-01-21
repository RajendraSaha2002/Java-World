import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;

public class ClothSimulation extends JPanel implements ActionListener {
    private static final int WIDTH = 1000;
    private static final int HEIGHT = 700;
    private static final int CLOTH_WIDTH = 40;
    private static final int CLOTH_HEIGHT = 30;
    private static final double SPACING = 15;
    private static final double GRAVITY = 0.5;
    private static final int ITERATIONS = 3; // Constraint solving iterations
    private static final double TEAR_DISTANCE = 35; // Distance before tearing

    private Point[][] points;
    private ArrayList<Constraint> constraints;
    private Timer timer;
    private Point draggedPoint;
    private boolean isRightClick;

    public ClothSimulation() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(new Color(20, 20, 30));

        initializeCloth();

        // Mouse interaction
        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                Point clicked = findNearestPoint(e.getX(), e.getY());
                if (clicked != null) {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        // Right click - cut/tear
                        isRightClick = true;
                        tearNearConstraints(e.getX(), e.getY());
                    } else {
                        // Left click - drag
                        draggedPoint = clicked;
                        draggedPoint.pinned = true;
                        isRightClick = false;
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (draggedPoint != null && !isRightClick) {
                    // Only unpin if it wasn't originally pinned at top
                    if (draggedPoint.y > 50) {
                        draggedPoint.pinned = false;
                    }
                    draggedPoint = null;
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (draggedPoint != null && !isRightClick) {
                    draggedPoint.x = e.getX();
                    draggedPoint.y = e.getY();
                } else if (isRightClick) {
                    // Continue tearing while dragging
                    tearNearConstraints(e.getX(), e.getY());
                }
            }
        };

        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);

        timer = new Timer(16, this); // ~60 FPS
        timer.start();
    }

    private void initializeCloth() {
        points = new Point[CLOTH_HEIGHT][CLOTH_WIDTH];
        constraints = new ArrayList<>();

        // Create points
        int startX = (WIDTH - (int)(CLOTH_WIDTH * SPACING)) / 2;
        int startY = 50;

        for (int y = 0; y < CLOTH_HEIGHT; y++) {
            for (int x = 0; x < CLOTH_WIDTH; x++) {
                double px = startX + x * SPACING;
                double py = startY + y * SPACING;
                points[y][x] = new Point(px, py);

                // Pin top row
                if (y == 0) {
                    points[y][x].pinned = true;
                }
            }
        }

        // Create constraints (horizontal, vertical, and diagonal for stability)
        for (int y = 0; y < CLOTH_HEIGHT; y++) {
            for (int x = 0; x < CLOTH_WIDTH; x++) {
                // Horizontal constraint
                if (x < CLOTH_WIDTH - 1) {
                    constraints.add(new Constraint(points[y][x], points[y][x + 1]));
                }

                // Vertical constraint
                if (y < CLOTH_HEIGHT - 1) {
                    constraints.add(new Constraint(points[y][x], points[y + 1][x]));
                }

                // Diagonal constraints (for shear resistance)
                if (x < CLOTH_WIDTH - 1 && y < CLOTH_HEIGHT - 1) {
                    constraints.add(new Constraint(points[y][x], points[y + 1][x + 1]));
                    constraints.add(new Constraint(points[y][x + 1], points[y + 1][x]));
                }
            }
        }
    }

    private Point findNearestPoint(int mouseX, int mouseY) {
        Point nearest = null;
        double minDist = 20; // Maximum distance to grab

        for (int y = 0; y < CLOTH_HEIGHT; y++) {
            for (int x = 0; x < CLOTH_WIDTH; x++) {
                double dx = points[y][x].x - mouseX;
                double dy = points[y][x].y - mouseY;
                double dist = Math.sqrt(dx * dx + dy * dy);

                if (dist < minDist) {
                    minDist = dist;
                    nearest = points[y][x];
                }
            }
        }

        return nearest;
    }

    private void tearNearConstraints(int mouseX, int mouseY) {
        ArrayList<Constraint> toRemove = new ArrayList<>();

        for (Constraint c : constraints) {
            // Check if constraint is near mouse
            double midX = (c.p1.x + c.p2.x) / 2;
            double midY = (c.p1.y + c.p2.y) / 2;

            double dx = midX - mouseX;
            double dy = midY - mouseY;
            double dist = Math.sqrt(dx * dx + dy * dy);

            if (dist < 15) {
                toRemove.add(c);
            }
        }

        constraints.removeAll(toRemove);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw constraints (cloth lines)
        g2d.setStroke(new BasicStroke(1));
        for (Constraint c : constraints) {
            // Color based on stress
            double currentLength = c.getCurrentLength();
            double stress = Math.abs(currentLength - c.restLength) / c.restLength;

            if (stress > 0.5) {
                g2d.setColor(new Color(255, 100, 100)); // Red - high stress
            } else if (stress > 0.3) {
                g2d.setColor(new Color(255, 200, 100)); // Orange - medium stress
            } else {
                g2d.setColor(new Color(150, 150, 200)); // Blue - normal
            }

            g2d.drawLine((int) c.p1.x, (int) c.p1.y, (int) c.p2.x, (int) c.p2.y);
        }

        // Draw points
        for (int y = 0; y < CLOTH_HEIGHT; y++) {
            for (int x = 0; x < CLOTH_WIDTH; x++) {
                Point p = points[y][x];
                if (p.pinned) {
                    g2d.setColor(new Color(255, 200, 0)); // Gold - pinned
                    g2d.fillOval((int) p.x - 3, (int) p.y - 3, 6, 6);
                } else {
                    g2d.setColor(new Color(200, 200, 255)); // Light blue
                    g2d.fillOval((int) p.x - 2, (int) p.y - 2, 4, 4);
                }
            }
        }

        // Draw info
        drawInfo(g2d);
    }

    private void drawInfo(Graphics2D g2d) {
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 18));
        g2d.drawString("Cloth Simulation (Verlet Integration)", 10, 25);

        g2d.setFont(new Font("Arial", Font.PLAIN, 12));
        g2d.drawString("Constraints: " + constraints.size(), 10, 45);
        g2d.drawString("Left Click & Drag: Move cloth", 10, HEIGHT - 50);
        g2d.drawString("Right Click & Drag: Cut/Tear cloth", 10, HEIGHT - 35);
        g2d.drawString("Press R: Reset cloth", 10, HEIGHT - 20);

        // Draw stress indicator
        g2d.drawString("Stress Colors:", WIDTH - 200, HEIGHT - 50);
        g2d.setColor(new Color(150, 150, 200));
        g2d.fillRect(WIDTH - 200, HEIGHT - 40, 30, 5);
        g2d.setColor(Color.WHITE);
        g2d.drawString("Normal", WIDTH - 165, HEIGHT - 35);

        g2d.setColor(new Color(255, 200, 100));
        g2d.fillRect(WIDTH - 200, HEIGHT - 30, 30, 5);
        g2d.setColor(Color.WHITE);
        g2d.drawString("Stressed", WIDTH - 165, HEIGHT - 25);

        g2d.setColor(new Color(255, 100, 100));
        g2d.fillRect(WIDTH - 200, HEIGHT - 20, 30, 5);
        g2d.setColor(Color.WHITE);
        g2d.drawString("Tearing", WIDTH - 165, HEIGHT - 15);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        updatePhysics();
        repaint();
    }

    private void updatePhysics() {
        // Verlet Integration - update positions
        for (int y = 0; y < CLOTH_HEIGHT; y++) {
            for (int x = 0; x < CLOTH_WIDTH; x++) {
                Point p = points[y][x];

                if (!p.pinned) {
                    // Verlet integration: velocity = current - previous
                    double vx = p.x - p.oldX;
                    double vy = p.y - p.oldY;

                    // Damping
                    vx *= 0.99;
                    vy *= 0.99;

                    // Save current position
                    p.oldX = p.x;
                    p.oldY = p.y;

                    // Update position with velocity and gravity
                    p.x += vx;
                    p.y += vy + GRAVITY;

                    // Keep cloth on screen
                    if (p.y > HEIGHT - 10) {
                        p.y = HEIGHT - 10;
                        p.oldY = p.y;
                    }
                    if (p.x < 10) {
                        p.x = 10;
                        p.oldX = p.x;
                    }
                    if (p.x > WIDTH - 10) {
                        p.x = WIDTH - 10;
                        p.oldX = p.x;
                    }
                }
            }
        }

        // Constraint solving (multiple iterations for stability)
        for (int iter = 0; iter < ITERATIONS; iter++) {
            // Check and remove torn constraints
            ArrayList<Constraint> toRemove = new ArrayList<>();

            for (Constraint c : constraints) {
                double currentLength = c.getCurrentLength();

                // Tear if stretched too much
                if (currentLength > TEAR_DISTANCE) {
                    toRemove.add(c);
                    continue;
                }

                // Satisfy constraint
                c.satisfy();
            }

            constraints.removeAll(toRemove);
        }
    }

    // Point class representing a particle in the cloth
    class Point {
        double x, y;        // Current position
        double oldX, oldY;  // Previous position (for Verlet integration)
        boolean pinned;     // Is this point fixed in place?

        public Point(double x, double y) {
            this.x = x;
            this.y = y;
            this.oldX = x;
            this.oldY = y;
            this.pinned = false;
        }
    }

    // Constraint class representing a connection between two points
    class Constraint {
        Point p1, p2;
        double restLength; // Original distance between points

        public Constraint(Point p1, Point p2) {
            this.p1 = p1;
            this.p2 = p2;

            // Calculate rest length
            double dx = p2.x - p1.x;
            double dy = p2.y - p1.y;
            this.restLength = Math.sqrt(dx * dx + dy * dy);
        }

        public double getCurrentLength() {
            double dx = p2.x - p1.x;
            double dy = p2.y - p1.y;
            return Math.sqrt(dx * dx + dy * dy);
        }

        public void satisfy() {
            // Calculate current distance
            double dx = p2.x - p1.x;
            double dy = p2.y - p1.y;
            double currentLength = Math.sqrt(dx * dx + dy * dy);

            if (currentLength == 0) return;

            // Calculate difference from rest length
            double diff = (currentLength - restLength) / currentLength;

            // Move points to satisfy constraint (Hooke's Law approximation)
            double offsetX = dx * diff * 0.5;
            double offsetY = dy * diff * 0.5;

            if (!p1.pinned) {
                p1.x += offsetX;
                p1.y += offsetY;
            }

            if (!p2.pinned) {
                p2.x -= offsetX;
                p2.y -= offsetY;
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Cloth Simulation - Verlet Integration");
            ClothSimulation cloth = new ClothSimulation();

            // Add keyboard listener for reset
            cloth.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_R) {
                        cloth.initializeCloth();
                    }
                }
            });
            cloth.setFocusable(true);

            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(cloth);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setResizable(false);
            frame.setVisible(true);
        });
    }
}