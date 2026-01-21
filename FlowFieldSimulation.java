import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Random;

/**
 * Flow Field Simulation
 * * Logic:
 * 1. Vector Field: A grid of vectors is generated using 2D Perlin Noise.
 * The noise value determines the angle of the vector at that point.
 * 2. Particles: Thousands of particles spawn and follow these vectors.
 * 3. Rendering: We do not clear the screen every frame. Instead, we draw
 * a semi-transparent rectangle over the previous frame. This creates
 * the "fading trail" effect that looks like hair or liquid.
 */
public class FlowFieldSimulation extends JPanel implements ActionListener {

    // --- Configuration ---
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final int SCALE = 20; // Grid cell size
    private static final int NUM_PARTICLES = 2000;
    private static final double NOISE_SCALE = 0.1;
    private static final double MAGNITUDE = 1.0; // Force strength

    // --- State ---
    private ArrayList<Particle> particles;
    private Vector2D[] flowfield; // The grid of angles
    private int cols, rows;

    // --- Rendering ---
    private BufferedImage canvas; // For the trail effect
    private Graphics2D canvasG;
    private Timer timer;
    private double zOff = 0; // Time dimension for noise (optional animation)

    public static void main(String[] args) {
        JFrame frame = new JFrame("Flow Field (Perlin Noise)");
        FlowFieldSimulation sim = new FlowFieldSimulation();

        frame.add(sim);
        frame.setSize(WIDTH, HEIGHT);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.setVisible(true);
    }

    public FlowFieldSimulation() {
        setBackground(Color.BLACK);

        // Grid setup
        cols = WIDTH / SCALE;
        rows = HEIGHT / SCALE;
        flowfield = new Vector2D[cols * rows];

        // Particle setup
        particles = new ArrayList<>();
        for (int i = 0; i < NUM_PARTICLES; i++) {
            particles.add(new Particle());
        }

        // Canvas setup for trails
        canvas = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        canvasG = canvas.createGraphics();
        canvasG.setColor(Color.BLACK);
        canvasG.fillRect(0, 0, WIDTH, HEIGHT);

        // Loop
        timer = new Timer(16, this); // ~60 FPS
        timer.start();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // 1. Calculate Flow Field
        double yOff = 0;
        for (int y = 0; y < rows; y++) {
            double xOff = 0;
            for (int x = 0; x < cols; x++) {
                // Get Perlin noise value (0.0 to 1.0)
                // Map it to an angle (0 to 2PI * 2) for more rotation
                double theta = PerlinNoise.noise(xOff, yOff, zOff) * Math.PI * 4;

                // Create vector from angle
                Vector2D v = Vector2D.fromAngle(theta);
                v.setMag(MAGNITUDE); // Set Strength

                int index = x + y * cols;
                flowfield[index] = v;

                xOff += NOISE_SCALE;
            }
            yOff += NOISE_SCALE;
        }
        zOff += 0.003; // Slowly evolve the field over time

        // 2. Fade the trails (Draw semi-transparent black rect)
        canvasG.setColor(new Color(0, 0, 0, 15)); // Alpha 15/255
        canvasG.fillRect(0, 0, WIDTH, HEIGHT);

        // 3. Update and Draw Particles onto Canvas
        canvasG.setColor(new Color(100, 200, 255, 100)); // Cyan color
        canvasG.setStroke(new BasicStroke(1));

        for (Particle p : particles) {
            p.follow(flowfield);
            p.update();
            p.edges();
            p.show(canvasG);
        }

        repaint(); // Trigger paintComponent to show the canvas
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // Draw the persistent canvas to the screen
        g.drawImage(canvas, 0, 0, null);
    }

    // --- Inner Classes ---

    class Particle {
        Vector2D pos;
        Vector2D vel;
        Vector2D acc;
        Vector2D prevPos;
        double maxSpeed = 4;

        Particle() {
            pos = new Vector2D(Math.random() * WIDTH, Math.random() * HEIGHT);
            vel = new Vector2D(0, 0);
            acc = new Vector2D(0, 0);
            prevPos = new Vector2D(pos.x, pos.y);
        }

        void update() {
            prevPos.x = pos.x;
            prevPos.y = pos.y;

            vel.add(acc);
            vel.limit(maxSpeed);
            pos.add(vel);
            acc.mult(0); // Reset accel
        }

        void follow(Vector2D[] vectors) {
            int x = (int) (pos.x / SCALE);
            int y = (int) (pos.y / SCALE);
            int index = x + y * cols;

            // Safety check
            if (index >= 0 && index < vectors.length) {
                Vector2D force = vectors[index];
                applyForce(force);
            }
        }

        void applyForce(Vector2D force) {
            acc.add(force);
        }

        void show(Graphics2D g) {
            // Draw line from previous position to current
            g.drawLine((int)prevPos.x, (int)prevPos.y, (int)pos.x, (int)pos.y);
        }

        void edges() {
            if (pos.x > WIDTH) { pos.x = 0; prevPos.x = 0; }
            if (pos.x < 0) { pos.x = WIDTH; prevPos.x = WIDTH; }
            if (pos.y > HEIGHT) { pos.y = 0; prevPos.y = 0; }
            if (pos.y < 0) { pos.y = HEIGHT; prevPos.y = HEIGHT; }
        }
    }

    static class Vector2D {
        double x, y;

        Vector2D(double x, double y) {
            this.x = x;
            this.y = y;
        }

        void add(Vector2D v) { x += v.x; y += v.y; }
        void mult(double n) { x *= n; y *= n; }

        void setMag(double mag) {
            normalize();
            mult(mag);
        }

        void normalize() {
            double m = Math.sqrt(x*x + y*y);
            if (m != 0) {
                x /= m;
                y /= m;
            }
        }

        void limit(double max) {
            if (Math.sqrt(x*x + y*y) > max) {
                setMag(max);
            }
        }

        static Vector2D fromAngle(double angle) {
            return new Vector2D(Math.cos(angle), Math.sin(angle));
        }
    }

    /**
     * Compact implementation of Perlin Noise
     * Adapted from reference implementations (Ken Perlin / Processing).
     */
    static class PerlinNoise {
        static int[] p = new int[512];
        static int[] permutation = { 151,160,137,91,90,15,
                131,13,201,95,96,53,194,233,7,225,140,36,103,30,69,142,8,99,37,240,21,10,23,
                190, 6,148,247,120,234,75,0,26,197,62,94,252,219,203,117,35,11,32,57,177,33,
                88,237,149,56,87,174,20,125,136,171,168, 68,175,74,165,71,134,139,48,27,166,
                77,146,158,231,83,111,229,122,60,211,133,230,220,105,92,41,55,46,245,40,244,
                102,143,54, 65,25,63,161, 1,216,80,73,209,76,132,187,208, 89,18,169,200,196,
                135,130,116,188,159,86,164,100,109,198,173,186, 3,64,52,217,226,250,124,123,
                5,202,38,147,118,126,255,82,85,212,207,206,59,227,47,16,58,17,182,189,28,42,
                223,183,170,213,119,248,152, 2,44,154,163, 70,221,153,101,155,167, 43,172,9,
                129,22,39,253, 19,98,108,110,79,113,224,232,178,185, 112,104,218,246,97,228,
                251,34,242,193,238,210,144,12,191,179,162,241, 81,51,145,235,249,14,239,107,
                49,192,214, 31,181,199,106,157,184, 84,204,176,115,121,50,45,127, 4,150,254,
                138,236,205,93,222,114,67,29,24,72,243,141,128,195,78,66,215,61,156,180
        };

        static {
            for (int i=0; i < 256 ; i++) p[256+i] = p[i] = permutation[i];
        }

        static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
        static double lerp(double t, double a, double b) { return a + t * (b - a); }
        static double grad(int hash, double x, double y, double z) {
            int h = hash & 15;
            double u = h < 8 ? x : y, v = h < 4 ? y : h==12||h==14 ? x : z;
            return ((h&1) == 0 ? u : -u) + ((h&2) == 0 ? v : -v);
        }

        public static double noise(double x, double y, double z) {
            int X = (int)Math.floor(x) & 255;
            int Y = (int)Math.floor(y) & 255;
            int Z = (int)Math.floor(z) & 255;
            x -= Math.floor(x);
            y -= Math.floor(y);
            z -= Math.floor(z);
            double u = fade(x), v = fade(y), w = fade(z);
            int A = p[X]+Y, AA = p[A]+Z, AB = p[A+1]+Z, B = p[X+1]+Y, BA = p[B]+Z, BB = p[B+1]+Z;

            return lerp(w, lerp(v, lerp(u, grad(p[AA], x, y, z), grad(p[BA], x-1, y, z)),
                            lerp(u, grad(p[AB], x, y-1, z), grad(p[BB], x-1, y-1, z))),
                    lerp(v, lerp(u, grad(p[AA+1], x, y, z-1), grad(p[BA+1], x-1, y, z-1)),
                            lerp(u, grad(p[AB+1], x, y-1, z-1), grad(p[BB+1], x-1, y-1, z-1))));
        }
    }
}