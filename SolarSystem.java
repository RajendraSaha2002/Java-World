import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;

public class SolarSystem extends JPanel implements ActionListener {
    private Timer timer;
    private static final int WIDTH = 1000;
    private static final int HEIGHT = 800;
    private static final int CENTER_X = WIDTH / 2;
    private static final int CENTER_Y = HEIGHT / 2;

    private Sun sun;
    private ArrayList<Planet> planets;
    private double timeScale = 1.0;

    public SolarSystem() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(new Color(5, 5, 20)); // Deep space background

        // Create the sun
        sun = new Sun(CENTER_X, CENTER_Y, 40, new Color(255, 220, 0));

        // Create planets with moons
        planets = new ArrayList<>();

        // Mercury
        planets.add(new Planet("Mercury", 80, 8, 4.15, new Color(169, 169, 169), null));

        // Venus
        planets.add(new Planet("Venus", 120, 14, 1.62, new Color(255, 198, 73), null));

        // Earth with Moon
        Moon moon = new Moon("Moon", 25, 4, 13.0, new Color(220, 220, 220));
        planets.add(new Planet("Earth", 160, 16, 1.0, new Color(100, 149, 237), moon));

        // Mars with Phobos
        Moon phobos = new Moon("Phobos", 20, 3, 15.0, new Color(180, 180, 180));
        planets.add(new Planet("Mars", 200, 12, 0.53, new Color(188, 39, 50), phobos));

        // Jupiter with Europa
        Moon europa = new Moon("Europa", 35, 5, 8.0, new Color(200, 180, 150));
        planets.add(new Planet("Jupiter", 280, 32, 0.084, new Color(216, 202, 157), europa));

        // Saturn with Titan
        Moon titan = new Moon("Titan", 40, 6, 6.0, new Color(255, 200, 124));
        Planet saturn = new Planet("Saturn", 360, 28, 0.034, new Color(238, 216, 174), titan);
        saturn.hasRings = true;
        planets.add(saturn);

        // Uranus
        planets.add(new Planet("Uranus", 430, 20, 0.012, new Color(79, 208, 231), null));

        // Neptune
        planets.add(new Planet("Neptune", 480, 19, 0.006, new Color(62, 84, 232), null));

        // Update at ~60 FPS
        timer = new Timer(16, this);
        timer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        // Enable anti-aliasing
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw stars in background
        drawStars(g2d);

        // Draw orbit paths
        g2d.setColor(new Color(50, 50, 80, 80));
        g2d.setStroke(new BasicStroke(1));
        for (Planet planet : planets) {
            g2d.drawOval(CENTER_X - planet.orbitRadius, CENTER_Y - planet.orbitRadius,
                    planet.orbitRadius * 2, planet.orbitRadius * 2);
        }

        // Draw sun
        sun.draw(g2d);

        // Draw planets and their moons
        for (Planet planet : planets) {
            planet.draw(g2d);
        }

        // Draw legend
        drawLegend(g2d);

        // Draw controls
        drawControls(g2d);
    }

    private void drawStars(Graphics2D g2d) {
        g2d.setColor(Color.WHITE);
        for (int i = 0; i < 100; i++) {
            int x = (int)(Math.random() * WIDTH);
            int y = (int)(Math.random() * HEIGHT);
            int size = (int)(Math.random() * 2) + 1;
            g2d.fillOval(x, y, size, size);
        }
    }

    private void drawLegend(Graphics2D g2d) {
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 16));
        g2d.drawString("Solar System Simulation", 10, 25);

        g2d.setFont(new Font("Arial", Font.PLAIN, 12));
        g2d.drawString("Speed: " + String.format("%.1fx", timeScale), 10, 45);
    }

    private void drawControls(Graphics2D g2d) {
        g2d.setColor(new Color(255, 255, 255, 150));
        g2d.setFont(new Font("Arial", Font.PLAIN, 11));
        g2d.drawString("Controls: [+] Speed Up  [-] Slow Down  [SPACE] Pause", 10, HEIGHT - 10);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // Update all planets
        for (Planet planet : planets) {
            planet.update(timeScale);
        }
        repaint();
    }

    // Sun class
    class Sun {
        private int x, y, diameter;
        private Color color;

        public Sun(int x, int y, int diameter, Color color) {
            this.x = x;
            this.y = y;
            this.diameter = diameter;
            this.color = color;
        }

        public void draw(Graphics2D g2d) {
            // Draw glow effect
            for (int i = 3; i > 0; i--) {
                int glowSize = diameter + (i * 8);
                g2d.setColor(new Color(255, 220, 0, 30 / i));
                g2d.fillOval(x - glowSize/2, y - glowSize/2, glowSize, glowSize);
            }

            // Draw sun
            g2d.setColor(color);
            g2d.fillOval(x - diameter/2, y - diameter/2, diameter, diameter);
        }
    }

    // Planet class
    class Planet {
        private String name;
        private int orbitRadius;
        private int diameter;
        private double speed; // Revolutions per minute
        private Color color;
        private Moon moon;
        private double angle;
        private boolean hasRings;

        public Planet(String name, int orbitRadius, int diameter, double speed, Color color, Moon moon) {
            this.name = name;
            this.orbitRadius = orbitRadius;
            this.diameter = diameter;
            this.speed = speed;
            this.color = color;
            this.moon = moon;
            this.angle = Math.random() * Math.PI * 2; // Random starting position
            this.hasRings = false;
        }

        public void update(double timeScale) {
            angle += (speed * 0.001 * timeScale);
            if (moon != null) {
                moon.update(timeScale);
            }
        }

        public void draw(Graphics2D g2d) {
            // Calculate planet position
            int x = CENTER_X + (int)(orbitRadius * Math.cos(angle));
            int y = CENTER_Y + (int)(orbitRadius * Math.sin(angle));

            // Save original transform
            AffineTransform originalTransform = g2d.getTransform();

            // Translate to planet position (hierarchical transformation)
            g2d.translate(x, y);

            // Draw rings if this is Saturn
            if (hasRings) {
                g2d.setColor(new Color(200, 180, 140, 100));
                g2d.setStroke(new BasicStroke(2));
                int ringRadius = diameter + 8;
                g2d.drawOval(-ringRadius/2, -ringRadius/2, ringRadius, ringRadius);
                ringRadius = diameter + 12;
                g2d.drawOval(-ringRadius/2, -ringRadius/2, ringRadius, ringRadius);
            }

            // Draw planet
            g2d.setColor(color);
            g2d.fillOval(-diameter/2, -diameter/2, diameter, diameter);

            // Add highlight for 3D effect
            g2d.setColor(new Color(255, 255, 255, 100));
            g2d.fillOval(-diameter/2 + 2, -diameter/2 + 2, diameter/3, diameter/3);

            // Draw planet name
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.PLAIN, 10));
            g2d.drawString(name, -diameter/2, -diameter/2 - 5);

            // Draw moon if exists (hierarchical transformation)
            if (moon != null) {
                moon.draw(g2d);
            }

            // Restore original transform
            g2d.setTransform(originalTransform);
        }
    }

    // Moon class
    class Moon {
        private String name;
        private int orbitRadius;
        private int diameter;
        private double speed;
        private Color color;
        private double angle;

        public Moon(String name, int orbitRadius, int diameter, double speed, Color color) {
            this.name = name;
            this.orbitRadius = orbitRadius;
            this.diameter = diameter;
            this.speed = speed;
            this.color = color;
            this.angle = Math.random() * Math.PI * 2;
        }

        public void update(double timeScale) {
            angle += (speed * 0.001 * timeScale);
        }

        public void draw(Graphics2D g2d) {
            // Calculate moon position relative to planet (hierarchical)
            int moonX = (int)(orbitRadius * Math.cos(angle));
            int moonY = (int)(orbitRadius * Math.sin(angle));

            // Draw moon orbit
            g2d.setColor(new Color(100, 100, 120, 60));
            g2d.setStroke(new BasicStroke(1));
            g2d.drawOval(-orbitRadius, -orbitRadius, orbitRadius * 2, orbitRadius * 2);

            // Draw moon
            g2d.setColor(color);
            g2d.fillOval(moonX - diameter/2, moonY - diameter/2, diameter, diameter);

            // Add highlight
            g2d.setColor(new Color(255, 255, 255, 150));
            g2d.fillOval(moonX - diameter/2 + 1, moonY - diameter/2 + 1, diameter/3, diameter/3);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Solar System Simulation");
            SolarSystem solarSystem = new SolarSystem();

            // Add keyboard controls
            solarSystem.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_PLUS:
                        case KeyEvent.VK_EQUALS:
                            solarSystem.timeScale += 0.5;
                            break;
                        case KeyEvent.VK_MINUS:
                            solarSystem.timeScale = Math.max(0.1, solarSystem.timeScale - 0.5);
                            break;
                        case KeyEvent.VK_SPACE:
                            solarSystem.timeScale = (solarSystem.timeScale > 0) ? 0 : 1.0;
                            break;
                    }
                }
            });
            solarSystem.setFocusable(true);

            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(solarSystem);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setResizable(false);
            frame.setVisible(true);
        });
    }
}