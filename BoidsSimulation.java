import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Random;
import java.util.List;

/**
 * BoidsSimulation
 * A classic flocking simulation implementing Reynolds' Boids.
 * * Logic:
 * 1. Separation: Steer to avoid crowding local flockmates.
 * 2. Alignment: Steer towards the average heading of local flockmates.
 * 3. Cohesion: Steer to move toward the average position of local flockmates.
 * * Features:
 * - Obstacle avoidance (Green circles).
 * - Mouse interaction: Click and drag to add obstacles dynamically.
 * - Wrapping borders (Toroidal space).
 */
public class BoidsSimulation extends JPanel implements ActionListener {

    // Simulation Parameters
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final int FLOCK_SIZE = 150;
    private static final int OBSTACLE_COUNT = 3;

    private List<Boid> flock;
    private List<Obstacle> obstacles;
    private Timer timer;

    public static void main(String[] args) {
        JFrame frame = new JFrame("Boids Flocking Simulation (Java)");
        BoidsSimulation sim = new BoidsSimulation();

        frame.add(sim);
        frame.setSize(WIDTH, HEIGHT);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setVisible(true);
        frame.setLocationRelativeTo(null);
    }

    public BoidsSimulation() {
        this.setBackground(new Color(30, 33, 40)); // Dark background
        this.setDoubleBuffered(true);

        flock = new ArrayList<>();
        obstacles = new ArrayList<>();

        // Initialize Flock
        for (int i = 0; i < FLOCK_SIZE; i++) {
            flock.add(new Boid(WIDTH / 2.0, HEIGHT / 2.0));
        }

        // Initialize Random Obstacles
        Random rand = new Random();
        for(int i = 0; i < OBSTACLE_COUNT; i++) {
            obstacles.add(new Obstacle(rand.nextInt(WIDTH), rand.nextInt(HEIGHT), 40));
        }

        // Mouse Listener to add obstacles on click
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                obstacles.add(new Obstacle(e.getX(), e.getY(), 30));
            }
        };
        addMouseListener(mouseHandler);

        // Animation Loop (approx 60 FPS)
        timer = new Timer(16, this);
        timer.start();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // Update Logic
        for (Boid boid : flock) {
            boid.flock(flock, obstacles);
            boid.update();
            boid.borders();
        }
        // Repaint Screen
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        // Enable Antialiasing for smoother shapes
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw Obstacles
        for(Obstacle obs : obstacles) {
            obs.render(g2d);
        }

        // Draw Boids
        for (Boid boid : flock) {
            boid.render(g2d);
        }

        // Draw UI Text
        g2d.setColor(Color.WHITE);
        g2d.drawString("Boids: " + flock.size(), 10, 20);
        g2d.drawString("Click to add obstacles", 10, 35);
    }

    // --- INNER CLASSES ---

    /**
     * Represents a single bird/fish in the flock.
     */
    class Boid {
        Vector2D position;
        Vector2D velocity;
        Vector2D acceleration;

        // Configuration
        double maxForce = 0.05;    // Maximum steering force
        double maxSpeed = 3.0;     // Maximum speed
        float r = 5.0f;           // Size (radius)

        // Weighting of rules
        double sepWeight = 1.5;
        double aliWeight = 1.0;
        double cohWeight = 1.0;
        double avoidWeight = 3.0;

        public Boid(double x, double y) {
            position = new Vector2D(x, y);
            Random rand = new Random();
            double angle = rand.nextDouble() * 2 * Math.PI;
            velocity = new Vector2D(Math.cos(angle), Math.sin(angle));
            acceleration = new Vector2D(0, 0);
        }

        public void update() {
            velocity.add(acceleration);
            velocity.limit(maxSpeed);
            position.add(velocity);
            acceleration.mult(0); // Reset acceleration for next frame
        }

        public void flock(List<Boid> boids, List<Obstacle> obstacles) {
            Vector2D sep = separate(boids);
            Vector2D ali = align(boids);
            Vector2D coh = cohesion(boids);
            Vector2D avd = avoid(obstacles);

            // Apply arbitrary weights
            sep.mult(sepWeight);
            ali.mult(aliWeight);
            coh.mult(cohWeight);
            avd.mult(avoidWeight);

            applyForce(sep);
            applyForce(ali);
            applyForce(coh);
            applyForce(avd);
        }

        void applyForce(Vector2D force) {
            acceleration.add(force);
        }

        // Rule 1: Separation
        // Steer to avoid crowding local flockmates
        Vector2D separate(List<Boid> boids) {
            double desiredseparation = 25.0;
            Vector2D steer = new Vector2D(0, 0);
            int count = 0;

            for (Boid other : boids) {
                double d = Vector2D.dist(position, other.position);
                if ((d > 0) && (d < desiredseparation)) {
                    Vector2D diff = Vector2D.sub(position, other.position);
                    diff.normalize();
                    diff.div(d); // Weight by distance
                    steer.add(diff);
                    count++;
                }
            }

            if (count > 0) {
                steer.div((double)count);
            }

            if (steer.mag() > 0) {
                steer.normalize();
                steer.mult(maxSpeed);
                steer.sub(velocity);
                steer.limit(maxForce);
            }
            return steer;
        }

        // Rule 2: Alignment
        // Steer towards the average heading of local flockmates
        Vector2D align(List<Boid> boids) {
            double neighborDist = 50;
            Vector2D sum = new Vector2D(0, 0);
            int count = 0;

            for (Boid other : boids) {
                double d = Vector2D.dist(position, other.position);
                if ((d > 0) && (d < neighborDist)) {
                    sum.add(other.velocity);
                    count++;
                }
            }

            if (count > 0) {
                sum.div((double)count);
                sum.normalize();
                sum.mult(maxSpeed);
                Vector2D steer = Vector2D.sub(sum, velocity);
                steer.limit(maxForce);
                return steer;
            } else {
                return new Vector2D(0, 0);
            }
        }

        // Rule 3: Cohesion
        // Steer to move toward the average position of local flockmates
        Vector2D cohesion(List<Boid> boids) {
            double neighborDist = 50;
            Vector2D sum = new Vector2D(0, 0);
            int count = 0;

            for (Boid other : boids) {
                double d = Vector2D.dist(position, other.position);
                if ((d > 0) && (d < neighborDist)) {
                    sum.add(other.position);
                    count++;
                }
            }

            if (count > 0) {
                sum.div((double)count);
                return seek(sum);
            } else {
                return new Vector2D(0, 0);
            }
        }

        // Obstacle Avoidance
        Vector2D avoid(List<Obstacle> obstacles) {
            Vector2D steer = new Vector2D(0,0);
            for (Obstacle obs : obstacles) {
                double d = Vector2D.dist(position, obs.position);
                // If we are heading towards it and close
                if (d < obs.radius + 50) {
                    Vector2D diff = Vector2D.sub(position, obs.position);
                    diff.normalize();
                    diff.mult(maxSpeed); // Get away fast
                    Vector2D force = Vector2D.sub(diff, velocity);
                    force.limit(maxForce * 2); // Stronger force to avoid
                    steer.add(force);
                }
            }
            return steer;
        }

        // Helper method to steer towards a target
        Vector2D seek(Vector2D target) {
            Vector2D desired = Vector2D.sub(target, position);
            desired.normalize();
            desired.mult(maxSpeed);
            Vector2D steer = Vector2D.sub(desired, velocity);
            steer.limit(maxForce);
            return steer;
        }

        // Wraparound borders
        void borders() {
            if (position.x < -r) position.x = WIDTH + r;
            if (position.y < -r) position.y = HEIGHT + r;
            if (position.x > WIDTH + r) position.x = -r;
            if (position.y > HEIGHT + r) position.y = -r;
        }

        // Draw the boid
        void render(Graphics2D g) {
            double angle = velocity.heading() + Math.PI / 2;

            AffineTransform old = g.getTransform();
            g.translate(position.x, position.y);
            g.rotate(angle);

            g.setColor(new Color(100, 200, 255));
            Path2D p = new Path2D.Double();
            p.moveTo(0, -r * 2);
            p.lineTo(-r, r * 2);
            p.lineTo(r, r * 2);
            p.closePath();
            g.fill(p);

            g.setTransform(old);
        }
    }

    class Obstacle {
        Vector2D position;
        double radius;

        public Obstacle(double x, double y, double r) {
            this.position = new Vector2D(x, y);
            this.radius = r;
        }

        public void render(Graphics2D g) {
            g.setColor(new Color(100, 255, 100, 150));
            int r = (int) radius;
            g.fillOval((int)position.x - r, (int)position.y - r, r * 2, r * 2);
        }
    }

    /**
     * Simple 2D Vector Class Helper
     */
    static class Vector2D {
        double x, y;

        Vector2D(double x, double y) {
            this.x = x;
            this.y = y;
        }

        void add(Vector2D v) { x += v.x; y += v.y; }
        void sub(Vector2D v) { x -= v.x; y -= v.y; }
        void mult(double n) { x *= n; y *= n; }
        void div(double n) { x /= n; y /= n; }

        double mag() { return Math.sqrt(x*x + y*y); }

        void normalize() {
            double m = mag();
            if (m != 0) div(m);
        }

        void limit(double max) {
            if (mag() > max) {
                normalize();
                mult(max);
            }
        }

        double heading() { return Math.atan2(y, x); }

        static Vector2D sub(Vector2D v1, Vector2D v2) {
            return new Vector2D(v1.x - v2.x, v1.y - v2.y);
        }

        static double dist(Vector2D v1, Vector2D v2) {
            return Math.sqrt(Math.pow(v1.x - v2.x, 2) + Math.pow(v1.y - v2.y, 2));
        }
    }
}