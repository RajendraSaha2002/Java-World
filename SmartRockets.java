import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Random;

/**
 * Smart Rockets - Genetic Algorithm Simulation
 * * Logic:
 * 1. Population: A group of N rockets.
 * 2. DNA: Each rocket has an array of vectors (forces) applied every frame.
 * 3. Evolution Cycle:
 * - Run simulation until lifespan ends.
 * - Evaluate Fitness: Closer to target = higher score. Crashed = low score.
 * - Selection: Create a mating pool where fit rockets appear more often.
 * - Reproduction: Crossover parents' DNA and Mutate slightly.
 * * Features:
 * - Obstacle Editor: Draw walls with the mouse.
 * - Live Evolution: Watch the pathfinding improve over generations.
 */
public class SmartRockets extends JPanel implements ActionListener {

    // --- Configuration ---
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final int LIFESPAN = 400; // Frames per generation
    private static final int POP_SIZE = 100;
    private static final double MUTATION_RATE = 0.01;

    // --- State ---
    private Population population;
    private Vector2D target;
    private int lifeCounter = 0;
    private int generation = 1;
    private Timer timer;

    // --- Obstacles ---
    private ArrayList<Rectangle> obstacles;
    private Rectangle currentDrawingObs = null; // For drag visual
    private Point dragStart = null;

    public static void main(String[] args) {
        JFrame frame = new JFrame("Smart Rockets: Genetic Algorithm");
        SmartRockets sim = new SmartRockets();

        frame.add(sim);
        frame.setSize(WIDTH, HEIGHT);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public SmartRockets() {
        this.setBackground(new Color(30, 30, 35));
        this.setDoubleBuffered(true);

        target = new Vector2D(WIDTH / 2, 50);
        obstacles = new ArrayList<>();

        // Add a default obstacle in the middle
        obstacles.add(new Rectangle(WIDTH / 2 - 100, HEIGHT / 2, 200, 20));

        population = new Population(POP_SIZE);

        // Interaction
        MouseAdapter mouseHandler = new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    obstacles.clear();
                } else {
                    dragStart = e.getPoint();
                    currentDrawingObs = new Rectangle(dragStart.x, dragStart.y, 0, 0);
                }
            }

            public void mouseDragged(MouseEvent e) {
                if (dragStart != null) {
                    int x = Math.min(dragStart.x, e.getX());
                    int y = Math.min(dragStart.y, e.getY());
                    int w = Math.abs(e.getX() - dragStart.x);
                    int h = Math.abs(e.getY() - dragStart.y);
                    currentDrawingObs.setBounds(x, y, w, h);
                }
            }

            public void mouseReleased(MouseEvent e) {
                if (currentDrawingObs != null && currentDrawingObs.width > 5 && currentDrawingObs.height > 5) {
                    obstacles.add(currentDrawingObs);
                }
                currentDrawingObs = null;
                dragStart = null;
            }
        };
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);

        // 60 FPS Loop
        timer = new Timer(16, this);
        timer.start();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // Run Logic
        population.run(obstacles, target);
        lifeCounter++;

        // End of Generation?
        if (lifeCounter >= LIFESPAN || population.allStopped()) {
            lifeCounter = 0;
            population.evaluate(target);
            population.selection();
            generation++;
        }

        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw Target
        g2.setColor(new Color(50, 255, 100));
        g2.fillOval((int)target.x - 12, (int)target.y - 12, 24, 24);
        g2.setColor(new Color(255, 255, 255, 100));
        g2.drawOval((int)target.x - 16, (int)target.y - 16, 32, 32);

        // Draw Obstacles
        g2.setColor(new Color(200, 100, 100));
        for (Rectangle rect : obstacles) {
            g2.fillRect(rect.x, rect.y, rect.width, rect.height);
        }

        // Draw Mouse Dragging
        if (currentDrawingObs != null) {
            g2.setColor(new Color(255, 255, 255, 100));
            g2.fillRect(currentDrawingObs.x, currentDrawingObs.y, currentDrawingObs.width, currentDrawingObs.height);
            g2.setColor(Color.WHITE);
            g2.drawRect(currentDrawingObs.x, currentDrawingObs.y, currentDrawingObs.width, currentDrawingObs.height);
        }

        // Draw Rockets
        population.draw(g2);

        // Draw UI
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.BOLD, 14));
        g2.drawString("Generation: " + generation, 10, 20);
        g2.drawString("Frame: " + lifeCounter + "/" + LIFESPAN, 10, 40);
        g2.drawString("Best Fit: " + String.format("%.4f", population.maxFit), 10, 60);

        g2.setColor(Color.LIGHT_GRAY);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g2.drawString("Left Drag: Draw Wall | Right Click: Clear Walls", 10, HEIGHT - 15);
    }

    // --- Genetic Algorithm Components ---

    class Population {
        Rocket[] rockets;
        ArrayList<Rocket> matingPool;
        double maxFit = 0;

        Population(int size) {
            rockets = new Rocket[size];
            for (int i = 0; i < size; i++) {
                rockets[i] = new Rocket();
            }
            matingPool = new ArrayList<>();
        }

        void run(ArrayList<Rectangle> obstacles, Vector2D target) {
            for (Rocket r : rockets) {
                r.update(obstacles, target);
            }
        }

        // Calculate fitness for all
        void evaluate(Vector2D target) {
            maxFit = 0;
            for (Rocket r : rockets) {
                r.calcFitness(target);
                if (r.fitness > maxFit) maxFit = r.fitness;
            }
            // Normalize
            for (Rocket r : rockets) {
                r.fitness /= maxFit;
            }

            // Build mating pool (Weighted Selection)
            matingPool.clear();
            for (Rocket r : rockets) {
                int n = (int)(r.fitness * 100);
                for (int j = 0; j < n; j++) {
                    matingPool.add(r);
                }
            }
        }

        // Generate new population
        void selection() {
            Rocket[] newRockets = new Rocket[rockets.length];
            Random rand = new Random();

            for (int i = 0; i < rockets.length; i++) {
                // If pool is empty (complete failure), random fallback
                if (matingPool.isEmpty()) {
                    newRockets[i] = new Rocket();
                    continue;
                }

                // Pick 2 parents
                int idxA = rand.nextInt(matingPool.size());
                int idxB = rand.nextInt(matingPool.size());
                DNA parentA = matingPool.get(idxA).dna;
                DNA parentB = matingPool.get(idxB).dna;

                // Crossover & Mutate
                DNA childDNA = parentA.crossover(parentB);
                childDNA.mutate();

                newRockets[i] = new Rocket(childDNA);
            }
            rockets = newRockets;
        }

        void draw(Graphics2D g) {
            for (Rocket r : rockets) {
                r.draw(g);
            }
        }

        boolean allStopped() {
            for(Rocket r : rockets) if (!r.crashed && !r.completed) return false;
            return true;
        }
    }

    class DNA {
        Vector2D[] genes;

        // Constructor for random DNA
        DNA() {
            genes = new Vector2D[LIFESPAN];
            for (int i = 0; i < genes.length; i++) {
                genes[i] = Vector2D.random2D();
                genes[i].setMag(0.2); // Force strength
            }
        }

        // Constructor for inherited DNA
        DNA(Vector2D[] newGenes) {
            this.genes = newGenes;
        }

        // Combine this DNA with partner DNA
        DNA crossover(DNA partner) {
            Vector2D[] newGenes = new Vector2D[genes.length];
            Random rand = new Random();
            int mid = rand.nextInt(genes.length); // Split point

            for (int i = 0; i < genes.length; i++) {
                if (i > mid) newGenes[i] = this.genes[i]; // Take from self
                else newGenes[i] = partner.genes[i];      // Take from partner
            }
            return new DNA(newGenes);
        }

        // Randomly alter genes
        void mutate() {
            Random rand = new Random();
            for (int i = 0; i < genes.length; i++) {
                if (rand.nextDouble() < MUTATION_RATE) {
                    genes[i] = Vector2D.random2D();
                    genes[i].setMag(0.2);
                }
            }
        }
    }

    class Rocket {
        Vector2D pos;
        Vector2D vel;
        Vector2D acc;
        DNA dna;
        double fitness = 0;
        boolean completed = false;
        boolean crashed = false;

        // Keep track of time to finish (faster = better)
        int finishTime = 0;

        Rocket() {
            this(new DNA());
        }

        Rocket(DNA dna) {
            this.dna = dna;
            pos = new Vector2D(WIDTH / 2, HEIGHT - 20);
            vel = new Vector2D(0, 0);
            acc = new Vector2D(0, 0);
        }

        void applyForce(Vector2D force) {
            acc.add(force);
        }

        void update(ArrayList<Rectangle> obstacles, Vector2D target) {
            // Check status
            double d = Vector2D.dist(pos, target);
            if (d < 16) {
                completed = true;
                pos = new Vector2D(target.x, target.y); // Snap to target
                finishTime = lifeCounter;
            }

            // Check Obstacles
            // Bounds check
            if (pos.x > WIDTH || pos.x < 0 || pos.y > HEIGHT || pos.y < 0) {
                crashed = true;
            }
            // Wall check
            for (Rectangle obs : obstacles) {
                if (obs.contains(pos.x, pos.y)) {
                    crashed = true;
                }
            }

            if (!completed && !crashed) {
                applyForce(dna.genes[lifeCounter % LIFESPAN]);

                vel.add(acc);
                pos.add(vel);
                acc.mult(0); // Reset acc
            }
        }

        void calcFitness(Vector2D target) {
            double d = Vector2D.dist(pos, target);

            // Inverse distance squared (closer = exponentially higher score)
            fitness = 1 / (d * d);

            if (completed) {
                fitness *= 10; // Huge bonus for finishing
                // Bonus for speed (finishTime)
                fitness *= (1.0 + (double)(LIFESPAN - finishTime) / LIFESPAN);
            }
            if (crashed) {
                fitness /= 10; // Penalty for crashing
            }
        }

        void draw(Graphics2D g) {
            // Rotate to velocity
            double theta = vel.heading() + Math.PI / 2;

            AffineTransform old = g.getTransform();
            g.translate(pos.x, pos.y);
            g.rotate(theta);

            // Color based on status
            if (completed) g.setColor(Color.GREEN);
            else if (crashed) g.setColor(new Color(100, 50, 50, 100)); // Faded red
            else g.setColor(new Color(255, 200, 100, 150)); // Transparent Orange

            // Draw Triangle shape
            Path2D p = new Path2D.Double();
            p.moveTo(0, -10);
            p.lineTo(-5, 5);
            p.lineTo(5, 5);
            p.closePath();
            g.fill(p);

            g.setTransform(old);
        }
    }

    // --- Vector Math Helper ---
    static class Vector2D {
        double x, y;

        Vector2D(double x, double y) {
            this.x = x;
            this.y = y;
        }

        void add(Vector2D v) { x += v.x; y += v.y; }
        void mult(double n) { x *= n; y *= n; }
        double heading() { return Math.atan2(y, x); }

        void setMag(double mag) {
            normalize();
            mult(mag);
        }

        void normalize() {
            double m = Math.sqrt(x*x + y*y);
            if (m != 0) { x /= m; y /= m; }
        }

        static double dist(Vector2D v1, Vector2D v2) {
            return Math.sqrt(Math.pow(v1.x - v2.x, 2) + Math.pow(v1.y - v2.y, 2));
        }

        static Vector2D random2D() {
            Random r = new Random();
            double angle = r.nextDouble() * Math.PI * 2;
            return new Vector2D(Math.cos(angle), Math.sin(angle));
        }
    }
}