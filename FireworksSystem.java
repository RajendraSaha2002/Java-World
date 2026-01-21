import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

public class FireworksSystem extends JPanel implements ActionListener {
    private static final int WIDTH = 1000;
    private static final int HEIGHT = 700;
    private static final double GRAVITY = 0.15;
    private static final double WIND = 0.02;

    private ArrayList<Firework> fireworks;
    private ArrayList<Particle> particles;
    private Timer timer;
    private Random rand;
    private int autoLaunchCounter;
    private boolean autoLaunch = true;

    public FireworksSystem() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(new Color(5, 5, 15));

        rand = new Random();
        fireworks = new ArrayList<>();
        particles = new ArrayList<>();
        autoLaunchCounter = 0;

        // Mouse listener for manual fireworks
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                launchFirework(e.getX(), e.getY());
            }
        });

        timer = new Timer(16, this); // ~60 FPS
        timer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        // Enable anti-aliasing and quality rendering
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Draw all particles
        for (Particle p : particles) {
            p.draw(g2d);
        }

        // Draw firework rockets
        for (Firework f : fireworks) {
            f.draw(g2d);
        }

        // Draw info
        drawInfo(g2d);
    }

    private void drawInfo(Graphics2D g2d) {
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 18));
        g2d.drawString("Fireworks Particle System", 10, 25);

        g2d.setFont(new Font("Arial", Font.PLAIN, 12));
        g2d.drawString("Active Particles: " + particles.size(), 10, 45);
        g2d.drawString("Auto Launch: " + (autoLaunch ? "ON" : "OFF"), 10, 60);
        g2d.drawString("Click anywhere to launch firework!", 10, HEIGHT - 15);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // Auto-launch fireworks
        if (autoLaunch) {
            autoLaunchCounter++;
            if (autoLaunchCounter > 40 + rand.nextInt(30)) {
                int x = rand.nextInt(WIDTH - 200) + 100;
                int targetY = rand.nextInt(200) + 100;
                launchFirework(x, targetY);
                autoLaunchCounter = 0;
            }
        }

        // Update all fireworks
        Iterator<Firework> fIt = fireworks.iterator();
        while (fIt.hasNext()) {
            Firework f = fIt.next();
            f.update();
            if (f.exploded) {
                fIt.remove();
            }
        }

        // Update all particles
        Iterator<Particle> pIt = particles.iterator();
        while (pIt.hasNext()) {
            Particle p = pIt.next();
            p.update();
            // Remove dead particles (memory management)
            if (p.isDead()) {
                pIt.remove();
            }
        }

        repaint();
    }

    private void launchFirework(int targetX, int targetY) {
        int startX = targetX + rand.nextInt(40) - 20;
        int startY = HEIGHT;
        fireworks.add(new Firework(startX, startY, targetX, targetY));
    }

    // Firework rocket class
    class Firework {
        private double x, y;
        private double vx, vy;
        private int targetX, targetY;
        private boolean exploded;
        private Color color;
        private ArrayList<Point2D.Double> trail;

        public Firework(int startX, int startY, int targetX, int targetY) {
            this.x = startX;
            this.y = startY;
            this.targetX = targetX;
            this.targetY = targetY;
            this.exploded = false;
            this.trail = new ArrayList<>();

            // Calculate velocity to reach target
            double dx = targetX - startX;
            double dy = targetY - startY;
            double distance = Math.sqrt(dx * dx + dy * dy);
            double speed = 12;

            vx = (dx / distance) * speed;
            vy = (dy / distance) * speed;

            // Random color for explosion
            color = new Color(
                    rand.nextInt(100) + 155,
                    rand.nextInt(100) + 155,
                    rand.nextInt(100) + 155
            );
        }

        public void update() {
            if (!exploded) {
                // Add to trail
                trail.add(new Point2D.Double(x, y));
                if (trail.size() > 15) {
                    trail.remove(0);
                }

                // Apply gravity to rocket
                vy += GRAVITY * 0.3;

                x += vx;
                y += vy;

                // Check if reached target area or passed it
                if (y <= targetY || vy > 0) {
                    explode();
                }
            }
        }

        private void explode() {
            exploded = true;

            // Create explosion particles
            int numParticles = rand.nextInt(100) + 150; // 150-250 particles

            for (int i = 0; i < numParticles; i++) {
                double angle = rand.nextDouble() * Math.PI * 2;
                double speed = rand.nextDouble() * 8 + 2;

                double pvx = Math.cos(angle) * speed;
                double pvy = Math.sin(angle) * speed;

                // Vary colors slightly
                Color particleColor = new Color(
                        Math.min(255, color.getRed() + rand.nextInt(50) - 25),
                        Math.min(255, color.getGreen() + rand.nextInt(50) - 25),
                        Math.min(255, color.getBlue() + rand.nextInt(50) - 25)
                );

                particles.add(new Particle(x, y, pvx, pvy, particleColor));
            }

            // Add some sparkling particles
            for (int i = 0; i < 30; i++) {
                double angle = rand.nextDouble() * Math.PI * 2;
                double speed = rand.nextDouble() * 12 + 5;

                double pvx = Math.cos(angle) * speed;
                double pvy = Math.sin(angle) * speed;

                particles.add(new Particle(x, y, pvx, pvy, Color.WHITE, true));
            }
        }

        public void draw(Graphics2D g2d) {
            if (!exploded) {
                // Draw trail
                for (int i = 0; i < trail.size(); i++) {
                    Point2D.Double p = trail.get(i);
                    float alpha = (float) i / trail.size() * 0.8f;
                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                    g2d.setColor(new Color(255, 200, 100));
                    g2d.fillOval((int) p.x - 2, (int) p.y - 2, 4, 4);
                }

                // Draw rocket
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
                g2d.setColor(Color.WHITE);
                g2d.fillOval((int) x - 3, (int) y - 3, 6, 6);
            }
        }
    }

    // Particle class
    class Particle {
        private double x, y;
        private double vx, vy;
        private Color color;
        private double life;
        private double lifeDecay;
        private double size;
        private boolean sparkle;

        public Particle(double x, double y, double vx, double vy, Color color) {
            this(x, y, vx, vy, color, false);
        }

        public Particle(double x, double y, double vx, double vy, Color color, boolean sparkle) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.color = color;
            this.life = 1.0;
            this.lifeDecay = rand.nextDouble() * 0.01 + 0.01; // 0.01-0.02
            this.size = sparkle ? 3 : rand.nextDouble() * 2 + 2; // 2-4 pixels
            this.sparkle = sparkle;
        }

        public void update() {
            // Apply physics
            vy += GRAVITY; // Gravity
            vx += WIND * (rand.nextDouble() - 0.5); // Random wind

            // Apply friction
            vx *= 0.98;
            vy *= 0.98;

            // Update position
            x += vx;
            y += vy;

            // Decay life
            life -= lifeDecay;

            // Sparkles decay faster
            if (sparkle) {
                life -= 0.02;
            }
        }

        public boolean isDead() {
            return life <= 0 || y > HEIGHT;
        }

        public void draw(Graphics2D g2d) {
            // Set alpha based on life (transparency/fading effect)
            float alpha = (float) Math.max(0, Math.min(1, life));
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

            if (sparkle) {
                // Draw sparkle with glow
                g2d.setColor(new Color(255, 255, 255));
                g2d.fillOval((int) x - 1, (int) y - 1, 3, 3);

                // Add glow effect
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.3f));
                g2d.setColor(new Color(255, 255, 200));
                g2d.fillOval((int) x - 3, (int) y - 3, 7, 7);
            } else {
                // Draw regular particle
                g2d.setColor(color);
                g2d.fillOval((int) x, (int) y, (int) size, (int) size);

                // Add subtle glow for larger particles
                if (size > 2) {
                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.2f));
                    Color glowColor = new Color(
                            Math.min(255, color.getRed() + 50),
                            Math.min(255, color.getGreen() + 50),
                            Math.min(255, color.getBlue() + 50)
                    );
                    g2d.setColor(glowColor);
                    g2d.fillOval((int) x - 1, (int) y - 1, (int) size + 2, (int) size + 2);
                }
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Fireworks Particle System");
            FireworksSystem fireworks = new FireworksSystem();

            // Create control panel
            JPanel controlPanel = new JPanel();
            controlPanel.setBackground(new Color(30, 30, 40));

            JButton toggleAutoBtn = new JButton("Toggle Auto Launch");
            JButton clearBtn = new JButton("Clear All");
            JButton burstBtn = new JButton("Big Burst!");

            toggleAutoBtn.addActionListener(e -> {
                fireworks.autoLaunch = !fireworks.autoLaunch;
            });

            clearBtn.addActionListener(e -> {
                fireworks.particles.clear();
                fireworks.fireworks.clear();
            });

            burstBtn.addActionListener(e -> {
                // Launch multiple fireworks at once
                for (int i = 0; i < 8; i++) {
                    int x = fireworks.rand.nextInt(WIDTH - 200) + 100;
                    int y = fireworks.rand.nextInt(150) + 100;
                    fireworks.launchFirework(x, y);
                }
            });

            controlPanel.add(toggleAutoBtn);
            controlPanel.add(clearBtn);
            controlPanel.add(burstBtn);

            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new BorderLayout());
            frame.add(fireworks, BorderLayout.CENTER);
            frame.add(controlPanel, BorderLayout.SOUTH);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setResizable(false);
            frame.setVisible(true);
        });
    }
}