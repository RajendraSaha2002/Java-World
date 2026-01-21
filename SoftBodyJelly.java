import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Soft Body Physics Engine (Jelly Physics)
 * * Logic:
 * 1. Particles: The object is made of mass points.
 * 2. Springs: Connect points to form the skin (Hooke's Law: F = -k*x).
 * 3. Pressure: Calculates the volume (area) of the shape. If it shrinks below
 * target volume, an outward pressure force is applied to every edge normal.
 * 4. Integration: Semi-Implicit Euler (Velocity += Force; Position += Velocity).
 * * Features:
 * - Mouse Interaction: Grab and throw the jelly.
 * - Volume Preservation: Acts like a balloon/jelly.
 */
public class SoftBodyJelly extends JPanel implements ActionListener {

    // --- Simulation Constants ---
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final double GRAVITY = 0.4;
    private static final double FRICTION = 0.99;
    private static final double WALL_DAMPING = 0.7; // Energy lost hitting walls

    // Spring Properties
    private static final double STIFFNESS = 1.2; // Spring tension (k)
    private static final double DAMPING = 0.2;   // Spring oscillation damper

    // Pressure Properties
    private static final double PRESSURE_STRENGTH = 6000.0;

    // --- State ---
    private List<Particle> particles;
    private List<Spring> springs;
    private Timer timer;
    private double targetArea; // The volume we want to maintain

    // Interaction
    private Particle draggedParticle = null;

    public static void main(String[] args) {
        JFrame frame = new JFrame("Soft Body Jelly Physics");
        SoftBodyJelly sim = new SoftBodyJelly();

        frame.add(sim);
        frame.setSize(WIDTH, HEIGHT);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setVisible(true);
        frame.setLocationRelativeTo(null);
    }

    public SoftBodyJelly() {
        this.setBackground(new Color(30, 30, 35));
        this.setDoubleBuffered(true);

        initJelly();

        // Mouse Listeners
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // Find closest particle
                Particle closest = null;
                double minDist = Double.MAX_VALUE;
                for (Particle p : particles) {
                    double d = dist(p.x, p.y, e.getX(), e.getY());
                    if (d < 50 && d < minDist) {
                        minDist = d;
                        closest = p;
                    }
                }
                draggedParticle = closest;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (draggedParticle != null) {
                    // Set position directly for tight control
                    draggedParticle.x = e.getX();
                    draggedParticle.y = e.getY();
                    draggedParticle.vx = 0;
                    draggedParticle.vy = 0;
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                draggedParticle = null;
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) initJelly();
            }
        };
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);

        // Game Loop (60 FPS)
        timer = new Timer(16, this);
        timer.start();
    }

    private void initJelly() {
        particles = new ArrayList<>();
        springs = new ArrayList<>();

        double centerX = WIDTH / 2.0;
        double centerY = HEIGHT / 2.0 - 100;
        double radius = 100;
        int segments = 30; // Number of outer points

        // Create Ring of Particles
        for (int i = 0; i < segments; i++) {
            double theta = (Math.PI * 2 * i) / segments;
            double px = centerX + Math.cos(theta) * radius;
            double py = centerY + Math.sin(theta) * radius;
            particles.add(new Particle(px, py));
        }

        // Connect Neighbors with Springs (Skin)
        for (int i = 0; i < segments; i++) {
            Particle a = particles.get(i);
            Particle b = particles.get((i + 1) % segments); // Wrap around
            double len = dist(a.x, a.y, b.x, b.y);
            springs.add(new Spring(a, b, len));
        }

        // Connect to 2nd Neighbor for Structure (Cross-bracing) - Optional but good for stability
        for (int i = 0; i < segments; i++) {
            Particle a = particles.get(i);
            Particle b = particles.get((i + 2) % segments);
            double len = dist(a.x, a.y, b.x, b.y);
            springs.add(new Spring(a, b, len));
        }

        // Calculate initial "Rest Area" for pressure
        targetArea = calculateArea();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // 1. Reset Forces
        for (Particle p : particles) {
            p.fx = 0;
            p.fy = 0;
            // Gravity
            p.fy += GRAVITY;
        }

        // 2. Accumulate Spring Forces (Hooke's Law)
        for (Spring s : springs) {
            s.update();
        }

        // 3. Accumulate Pressure Force (Ideal Gas Law)
        // PV = nRT -> Force is proportional to (TargetVolume - CurrentVolume)
        double currentArea = calculateArea();
        double pressure = (targetArea - currentArea) * PRESSURE_STRENGTH;
        // Apply pressure to each edge normal
        // (Assuming simple sequential list of outer particles)
        // We only want the outer skin for pressure calculation.
        // Since we added cross-braces, the first N springs are the skin.
        // However, iterating points is easier:
        int skinSize = particles.size();
        for (int i = 0; i < skinSize; i++) {
            Particle a = particles.get(i);
            Particle b = particles.get((i + 1) % skinSize);

            // Vector of the edge
            double dx = b.x - a.x;
            double dy = b.y - a.y;
            double dist = Math.sqrt(dx*dx + dy*dy);

            // Normal Vector (Perpendicular) pointing Outward
            double nx = -dy / dist;
            double ny = dx / dist;

            // Force amount (Pressure * Edge Length)
            // Note: Inverse relationship to area diff usually works well for stability
            double force = pressure * (1.0 / dist);

            // Apply half to A, half to B
            a.fx += nx * force;
            a.fy += ny * force;
            b.fx += nx * force;
            b.fy += ny * force;
        }

        // 4. Integration (Euler)
        for (Particle p : particles) {
            if (p == draggedParticle) continue; // Don't move if held by mouse

            p.vx += p.fx; // Mass is 1.0, so a = f
            p.vy += p.fy;

            p.x += p.vx;
            p.y += p.vy;

            // Friction
            p.vx *= FRICTION;
            p.vy *= FRICTION;

            // 5. Floor/Wall Collisions
            if (p.y > HEIGHT - 20) {
                p.y = HEIGHT - 20;
                p.vy *= -WALL_DAMPING;
                p.vx *= 0.9; // Floor friction
            }
            if (p.y < 0) {
                p.y = 0;
                p.vy *= -WALL_DAMPING;
            }
            if (p.x > WIDTH) {
                p.x = WIDTH;
                p.vx *= -WALL_DAMPING;
            }
            if (p.x < 0) {
                p.x = 0;
                p.vx *= -WALL_DAMPING;
            }
        }

        repaint();
    }

    // Shoelace formula to calculate polygon area
    private double calculateArea() {
        double area = 0.0;
        int j = particles.size() - 1;
        for (int i = 0; i < particles.size(); i++) {
            Particle p1 = particles.get(j);
            Particle p2 = particles.get(i);
            area += (p1.x + p2.x) * (p1.y - p2.y);
            j = i;
        }
        return Math.abs(area / 2.0);
    }

    private double dist(double x1, double y1, double x2, double y2) {
        return Math.sqrt(Math.pow(x2-x1, 2) + Math.pow(y2-y1, 2));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw Jelly Body (Filled Polygon)
        Path2D path = new Path2D.Double();
        path.moveTo(particles.get(0).x, particles.get(0).y);
        for (int i = 1; i < particles.size(); i++) {
            path.lineTo(particles.get(i).x, particles.get(i).y);
        }
        path.closePath();

        // Gradient Fill
        GradientPaint gp = new GradientPaint(
                (int)particles.get(0).x, (int)particles.get(0).y, new Color(0, 255, 150, 200),
                (int)particles.get(particles.size()/2).x, (int)particles.get(particles.size()/2).y, new Color(0, 150, 255, 150)
        );
        g2.setPaint(gp);
        g2.fill(path);

        // Draw Outline
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(2));
        g2.draw(path);

        // Draw Internal Structure (Optional visual debug)
        g2.setColor(new Color(255, 255, 255, 50));
        g2.setStroke(new BasicStroke(1));
        for (Spring s : springs) {
            g2.drawLine((int)s.a.x, (int)s.a.y, (int)s.b.x, (int)s.b.y);
        }

        // Instructions
        g2.setColor(Color.WHITE);
        g2.drawString("Left Click & Drag to Squash/Stretch", 10, 20);
        g2.drawString("Right Click to Reset", 10, 40);
    }

    // --- Physics Classes ---

    class Particle {
        double x, y;
        double vx, vy;
        double fx, fy;

        Particle(double x, double y) {
            this.x = x; this.y = y;
        }
    }

    class Spring {
        Particle a, b;
        double restLength;

        Spring(Particle a, Particle b, double len) {
            this.a = a;
            this.b = b;
            this.restLength = len;
        }

        void update() {
            // Vector from a to b
            double dx = b.x - a.x;
            double dy = b.y - a.y;
            double dist = Math.sqrt(dx*dx + dy*dy);

            if (dist == 0) return; // Prevent division by zero

            // Hooke's Law: F = -k * displacement
            double displacement = dist - restLength;
            double forceMag = displacement * STIFFNESS;

            // Damping (resists velocity difference)
            double dvx = b.vx - a.vx;
            double dvy = b.vy - a.vy;
            // Project velocity difference onto the spring axis
            double dampingForce = (dvx * dx + dvy * dy) / dist * DAMPING;

            double totalForce = forceMag + dampingForce;

            // Normalized direction components
            double nx = dx / dist;
            double ny = dy / dist;

            // Apply to A
            a.fx += nx * totalForce;
            a.fy += ny * totalForce;

            // Apply opposite to B
            b.fx -= nx * totalForce;
            b.fy -= ny * totalForce;
        }
    }
}