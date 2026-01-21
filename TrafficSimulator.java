import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

/**
 * Traffic Intersection Simulator
 * * Logic:
 * 1. AI & Pathfinding: Cars move along 4 cardinal directions. They detect
 * obstacles (cars ahead or red lights) and brake accordingly.
 * 2. State Machine: Traffic lights cycle Green -> Yellow -> Red.
 * 3. Queue Logic: Cars maintain a 'safe distance' from the entity in front,
 * creating realistic queues at red lights.
 * * Features:
 * - Dynamic Control Panel: Sliders for Spawn Rate and Traffic Light Speed.
 * - Real-time Statistics: Monitors simulation FPS and Car count.
 */
public class TrafficSimulator extends JPanel implements ActionListener {

    // --- Simulation Constants ---
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final int TILE_SIZE = 100; // Road width

    // --- State ---
    private Timer timer;
    private ArrayList<Car> cars;
    private TrafficLight[] lights; // 0:North, 1:East, 2:South, 3:West
    private long tickCount = 0;

    // --- Stats ---
    private int carsPassed = 0;

    // --- Controls (Sliders) ---
    private JSlider spawnRateSlider;
    private JSlider lightDurationSlider;
    private JLabel statsLabel;

    public static void main(String[] args) {
        JFrame frame = new JFrame("Traffic Intersection Simulator");
        frame.setLayout(new BorderLayout());

        TrafficSimulator sim = new TrafficSimulator();
        frame.add(sim, BorderLayout.CENTER);

        // Control Panel
        JPanel controls = new JPanel();
        controls.setLayout(new GridLayout(2, 1));

        JPanel sliders = new JPanel();
        sliders.setLayout(new GridLayout(4, 1));

        // Spawn Rate Slider
        sliders.add(new JLabel("Traffic Density (Spawn Rate):"));
        sim.spawnRateSlider = new JSlider(1, 100, 50); // Higher is fewer cars
        sliders.add(sim.spawnRateSlider);

        // Light Duration Slider
        sliders.add(new JLabel("Green Light Duration:"));
        sim.lightDurationSlider = new JSlider(50, 500, 200);
        sliders.add(sim.lightDurationSlider);

        controls.add(sliders);

        // Stats
        sim.statsLabel = new JLabel("Stats: Waiting...");
        sim.statsLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        controls.add(sim.statsLabel);

        frame.add(controls, BorderLayout.EAST);

        frame.setSize(WIDTH + 250, HEIGHT); // Extra width for controls
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setVisible(true);
        frame.setLocationRelativeTo(null);
    }

    public TrafficSimulator() {
        setBackground(new Color(50, 168, 82)); // Grass color
        cars = new ArrayList<>();
        lights = new TrafficLight[4];

        // Initialize Traffic Lights
        // N/S pair and E/W pair
        lights[0] = new TrafficLight(WIDTH/2 - 20, HEIGHT/2 - TILE_SIZE/2 - 20, true); // North Facing (controls Southbound)
        lights[1] = new TrafficLight(WIDTH/2 + TILE_SIZE/2 + 20, HEIGHT/2 - 20, false); // East Facing (controls Westbound)
        lights[2] = new TrafficLight(WIDTH/2 + 20, HEIGHT/2 + TILE_SIZE/2 + 20, true); // South Facing (controls Northbound)
        lights[3] = new TrafficLight(WIDTH/2 - TILE_SIZE/2 - 20, HEIGHT/2 + 20, false); // West Facing (controls Eastbound)

        timer = new Timer(16, this); // ~60 FPS
        timer.start();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        tickCount++;

        updateTrafficLights();
        spawnCars();
        updateCars();
        updateStats();

        repaint();
    }

    private void updateStats() {
        if (tickCount % 10 == 0) {
            statsLabel.setText("<html><h3>Live Stats</h3>" +
                    "Cars Active: " + cars.size() + "<br>" +
                    "Cars Passed: " + carsPassed + "</html>");
        }
    }

    private void updateTrafficLights() {
        int duration = lightDurationSlider.getValue();

        // Simple Cycle: N/S Green -> Yellow -> Red -> E/W Green...
        // Use tickCount to determine state
        long cycleTime = duration * 2 + 100; // Total cycle length
        long currentTick = tickCount % cycleTime;

        // State Logic
        boolean nsGo = currentTick < duration;
        boolean nsYellow = currentTick >= duration && currentTick < duration + 50;

        boolean ewGo = currentTick >= duration + 50 && currentTick < duration * 2 + 50;
        boolean ewYellow = currentTick >= duration * 2 + 50;

        // Apply states
        lights[0].state = nsGo ? 0 : (nsYellow ? 1 : 2); // Controls Northbound/Southbound lane
        lights[2].state = lights[0].state;

        lights[1].state = ewGo ? 0 : (ewYellow ? 1 : 2); // Controls Eastbound/Westbound lane
        lights[3].state = lights[1].state;
    }

    private void spawnCars() {
        // Lower slider value = higher chance
        int threshold = spawnRateSlider.getValue();
        if (new Random().nextInt(100) > threshold) return;

        // 0: N->S, 1: E->W, 2: S->N, 3: W->E
        int dir = new Random().nextInt(4);
        Car newCar = null;

        // Spawn positions based on direction
        switch(dir) {
            case 0: newCar = new Car(WIDTH/2 - TILE_SIZE/4, -50, dir); break; // Top going Down
            case 1: newCar = new Car(WIDTH + 50, HEIGHT/2 - TILE_SIZE/4, dir); break; // Right going Left
            case 2: newCar = new Car(WIDTH/2 + TILE_SIZE/4, HEIGHT + 50, dir); break; // Bottom going Up
            case 3: newCar = new Car(-50, HEIGHT/2 + TILE_SIZE/4, dir); break; // Left going Right
        }

        // Check if spawn point is clear to prevent overlap
        boolean safe = true;
        for (Car c : cars) {
            if (c.dir == dir && Point.distance(c.x, c.y, newCar.x, newCar.y) < 60) {
                safe = false;
                break;
            }
        }

        if (safe) cars.add(newCar);
    }

    private void updateCars() {
        Iterator<Car> it = cars.iterator();
        while (it.hasNext()) {
            Car c = it.next();
            c.update(cars, lights);

            // Remove cars off-screen
            if (c.x < -100 || c.x > WIDTH + 100 || c.y < -100 || c.y > HEIGHT + 100) {
                it.remove();
                carsPassed++;
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // --- Draw Roads ---
        g2.setColor(Color.DARK_GRAY);
        // Vertical Road
        g2.fillRect(WIDTH/2 - TILE_SIZE/2, 0, TILE_SIZE, HEIGHT);
        // Horizontal Road
        g2.fillRect(0, HEIGHT/2 - TILE_SIZE/2, WIDTH, TILE_SIZE);

        // Road Markings (Dashed Lines)
        g2.setColor(Color.WHITE);
        float[] dash = {10.0f};
        g2.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dash, 0.0f));

        g2.drawLine(WIDTH/2, 0, WIDTH/2, HEIGHT/2 - TILE_SIZE/2); // N
        g2.drawLine(WIDTH/2, HEIGHT/2 + TILE_SIZE/2, WIDTH/2, HEIGHT); // S
        g2.drawLine(0, HEIGHT/2, WIDTH/2 - TILE_SIZE/2, HEIGHT/2); // W
        g2.drawLine(WIDTH/2 + TILE_SIZE/2, HEIGHT/2, WIDTH, HEIGHT/2); // E

        // Stop Lines
        g2.setStroke(new BasicStroke(4));
        g2.drawLine(WIDTH/2 - TILE_SIZE/2, HEIGHT/2 - TILE_SIZE/2, WIDTH/2, HEIGHT/2 - TILE_SIZE/2); // Top Stop
        g2.drawLine(WIDTH/2 + TILE_SIZE/2, HEIGHT/2 - TILE_SIZE/2, WIDTH/2 + TILE_SIZE/2, HEIGHT/2); // Right Stop
        g2.drawLine(WIDTH/2, HEIGHT/2 + TILE_SIZE/2, WIDTH/2 + TILE_SIZE/2, HEIGHT/2 + TILE_SIZE/2); // Bottom Stop
        g2.drawLine(WIDTH/2 - TILE_SIZE/2, HEIGHT/2, WIDTH/2 - TILE_SIZE/2, HEIGHT/2 + TILE_SIZE/2); // Left Stop

        // --- Draw Entities ---
        for (TrafficLight l : lights) l.draw(g2);
        for (Car c : cars) c.draw(g2);
    }

    // --- Inner Classes ---

    class TrafficLight {
        int x, y;
        int state; // 0=Green, 1=Yellow, 2=Red
        boolean vertical; // Helps with rendering logic

        public TrafficLight(int x, int y, boolean vertical) {
            this.x = x;
            this.y = y;
            this.vertical = vertical;
            this.state = 2; // Start Red
        }

        void draw(Graphics2D g) {
            // Housing
            g.setColor(Color.BLACK);
            g.fillRect(x - 5, y - 5, 30, 10);

            // Light
            Color c = Color.GRAY;
            if (state == 0) c = Color.GREEN;
            if (state == 1) c = Color.YELLOW;
            if (state == 2) c = Color.RED;

            g.setColor(c);
            g.fillOval(x, y - 4, 8, 8);
        }

        boolean isStop() {
            return state == 1 || state == 2;
        }
    }

    class Car {
        double x, y;
        double speed;
        double maxSpeed = 3.0 + Math.random(); // Varied speeds
        int dir; // 0:S, 1:W, 2:N, 3:E
        Color color;

        // Rectangle Collision Box
        int w = 20, h = 35;

        public Car(double x, double y, int dir) {
            this.x = x;
            this.y = y;
            this.dir = dir;
            this.speed = maxSpeed;
            // Random distinct color
            this.color = new Color(new Random().nextInt(255), new Random().nextInt(255), new Random().nextInt(255));
        }

        void update(ArrayList<Car> allCars, TrafficLight[] lights) {
            // 1. Acceleration / Deceleration Logic
            double targetSpeed = maxSpeed;

            // 2. Check Car in Front (Queue Logic)
            double distToNext = 9999;
            for (Car other : allCars) {
                if (other == this) continue;
                if (other.dir != this.dir) continue; // Only care about same lane

                double d = getDistanceInLane(other);
                if (d > 0 && d < distToNext) {
                    distToNext = d;
                }
            }

            if (distToNext < 50) {
                targetSpeed = 0; // Emergency Brake / Stopped car ahead
            } else if (distToNext < 100) {
                targetSpeed = maxSpeed * 0.3; // Slow down
            }

            // 3. Check Traffic Lights
            double distToLight = getDistanceToStopLine();
            int relevantLightIdx = getRelevantLightIndex();

            // If approaching light, light is red/yellow, and we haven't passed the line yet
            if (distToLight > 0 && distToLight < 120 && lights[relevantLightIdx].isStop()) {
                if (distToLight < 40) targetSpeed = 0; // Stop
                else targetSpeed = maxSpeed * 0.2; // Coast to stop
            }

            // Apply Speed
            if (speed < targetSpeed) speed += 0.1;
            if (speed > targetSpeed) speed -= 0.2;
            if (speed < 0) speed = 0;

            // Move
            if (dir == 0) y += speed;
            if (dir == 1) x -= speed;
            if (dir == 2) y -= speed;
            if (dir == 3) x += speed;
        }

        // Get index of traffic light that controls this car's lane
        int getRelevantLightIndex() {
            if (dir == 0) return 0; // Southbound controlled by North Light
            if (dir == 1) return 1;
            if (dir == 2) return 2;
            return 3;
        }

        // Distance to the specific stop line for this direction
        double getDistanceToStopLine() {
            double stopLine = 0;
            switch(dir) {
                case 0: stopLine = HEIGHT/2 - TILE_SIZE/2 - 10; return stopLine - y; // Moving Down, stop at top of intersection
                case 1: stopLine = WIDTH/2 + TILE_SIZE/2 + 10; return x - stopLine; // Moving Left, stop at right of intersection
                case 2: stopLine = HEIGHT/2 + TILE_SIZE/2 + 10; return y - stopLine; // Moving Up, stop at bottom
                case 3: stopLine = WIDTH/2 - TILE_SIZE/2 - 10; return stopLine - x; // Moving Right, stop at left
            }
            return 9999;
        }

        // Calculate distance to another car in the queue
        double getDistanceInLane(Car other) {
            switch(dir) {
                case 0: return other.y - y; // Moving Down, other should be larger Y
                case 1: return x - other.x; // Moving Left, other should be smaller X
                case 2: return y - other.y; // Moving Up, other should be smaller Y
                case 3: return other.x - x; // Moving Right, other should be larger X
            }
            return 9999;
        }

        void draw(Graphics2D g) {
            g.setColor(color);
            // Rotate based on direction
            if (dir == 0 || dir == 2) {
                g.fillRect((int)x, (int)y, w, h);
                // Headlights
                g.setColor(Color.YELLOW);
                if(dir == 0) { g.fillRect((int)x, (int)y+h-5, 5, 5); g.fillRect((int)x+w-5, (int)y+h-5, 5, 5); } // Facing Down
                else { g.fillRect((int)x, (int)y, 5, 5); g.fillRect((int)x+w-5, (int)y, 5, 5); } // Facing Up
            } else {
                g.fillRect((int)x, (int)y, h, w); // Swap w/h for horizontal
                // Headlights
                g.setColor(Color.YELLOW);
                if(dir == 3) { g.fillRect((int)x+h-5, (int)y, 5, 5); g.fillRect((int)x+h-5, (int)y+w-5, 5, 5); } // Facing Right
                else { g.fillRect((int)x, (int)y, 5, 5); g.fillRect((int)x, (int)y+w-5, 5, 5); } // Facing Left
            }
        }
    }
}