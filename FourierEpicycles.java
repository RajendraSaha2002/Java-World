import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Fourier Series Visualization (Epicycles)
 * * Logic:
 * 1. INPUT: User draws a path (sequence of 2D points).
 * 2. MATH (DFT): We treat these points as Complex numbers and apply the
 * Discrete Fourier Transform. This gives us a set of frequencies, amplitudes, and phases.
 * 3. VISUALIZATION: We reconstruct the path by summing rotating vectors (epicycles)
 * based on the DFT data.
 * * Instructions:
 * - Run the app.
 * - Click and drag to draw a shape.
 * - Release to see the Fourier Series reconstruction.
 */
public class FourierEpicycles extends JPanel implements ActionListener {

    // --- State ---
    private enum State {
        USER_DRAWING,
        CALCULATING,
        ANIMATING
    }

    private State currentState = State.USER_DRAWING;

    // --- Data ---
    private ArrayList<Complex> drawing; // User's raw input
    private ArrayList<EpicycleData> fourierData; // Calculated circles
    private ArrayList<Point> path; // The trail drawn by the circles

    // --- Animation ---
    private Timer timer;
    private double time; // 0 to 2PI
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;

    // --- UI ---
    private JButton btnReset;

    public static void main(String[] args) {
        JFrame frame = new JFrame("Fourier Series Epicycles");
        FourierEpicycles app = new FourierEpicycles();

        frame.add(app);
        frame.setSize(WIDTH, HEIGHT);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.setVisible(true);
    }

    public FourierEpicycles() {
        this.setLayout(null);
        this.setBackground(Color.BLACK);
        this.setDoubleBuffered(true);

        drawing = new ArrayList<>();
        path = new ArrayList<>();
        fourierData = new ArrayList<>();

        // Setup Interaction
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (currentState != State.USER_DRAWING) return;
                drawing.clear();
                path.clear();
                drawing.add(new Complex(e.getX(), e.getY()));
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (currentState != State.USER_DRAWING) return;
                drawing.add(new Complex(e.getX(), e.getY()));
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (currentState == State.USER_DRAWING && !drawing.isEmpty()) {
                    computeFourier();
                }
            }
        };

        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);

        // UI Controls
        btnReset = new JButton("Reset / Draw New");
        btnReset.setBounds(10, 10, 140, 30);
        btnReset.setBackground(new Color(50, 50, 50));
        btnReset.setForeground(Color.WHITE);
        btnReset.setFocusPainted(false);
        btnReset.addActionListener(e -> reset());
        add(btnReset);

        // Animation Loop
        timer = new Timer(10, this); // Fast update rate
        timer.start();
    }

    private void reset() {
        currentState = State.USER_DRAWING;
        drawing.clear();
        fourierData.clear();
        path.clear();
        time = 0;
        repaint();
    }

    /**
     * The Mathematical Core: Discrete Fourier Transform (DFT)
     * Transforms spatial data (x, y) into frequency data (amp, freq, phase).
     * Formula: X_k = sum(x_n * e^(-i * 2PI * k * n / N))
     */
    private void computeFourier() {
        currentState = State.CALCULATING;
        fourierData.clear();

        int N = drawing.size();

        // Skip every n-th point to speed up calculation if drawing is huge
        int step = 1;
        if (N > 500) step = 2;

        ArrayList<Complex> input = new ArrayList<>();
        for(int i=0; i<N; i+=step) {
            input.add(drawing.get(i));
        }
        N = input.size();

        for (int k = 0; k < N; k++) {
            double re = 0;
            double im = 0;

            for (int n = 0; n < N; n++) {
                double phi = (2 * Math.PI * k * n) / N;
                Complex c = input.get(n);

                // Euler's Formula: e^(-ix) = cos(x) - i*sin(x)
                double cos = Math.cos(phi);
                double sin = -Math.sin(phi); // negative because exponent is negative

                // Complex multiplication: (a+bi)(c+di) = (ac - bd) + i(ad + bc)
                // c.re corresponds to 'a', c.im to 'b'
                // cos corresponds to 'c', sin to 'd'
                re += (c.re * cos - c.im * sin);
                im += (c.re * sin + c.im * cos);
            }

            re = re / N; // Average
            im = im / N;

            double freq = k;
            double amp = Math.sqrt(re * re + im * im);
            double phase = Math.atan2(im, re);

            fourierData.add(new EpicycleData(re, im, freq, amp, phase));
        }

        // Sort by amplitude (Descending) so largest circles are at the center
        Collections.sort(fourierData, new Comparator<EpicycleData>() {
            @Override
            public int compare(EpicycleData o1, EpicycleData o2) {
                return Double.compare(o2.amp, o1.amp);
            }
        });

        time = 0;
        path.clear();
        currentState = State.ANIMATING;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (currentState == State.ANIMATING) {
            // Step forward in time
            // Period is 2PI. Step size depends on number of epicycles
            double dt = (2 * Math.PI) / fourierData.size();
            time += dt;

            if (time > 2 * Math.PI) {
                time = 0;
                path.clear();
            }
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // --- DRAW MODE ---
        if (currentState == State.USER_DRAWING) {
            g2.setColor(Color.WHITE);
            g2.drawString("Draw a continuous closed shape, then release mouse.", WIDTH/2 - 150, 50);

            if (drawing.size() > 1) {
                g2.setStroke(new BasicStroke(2));
                for (int i = 0; i < drawing.size() - 1; i++) {
                    Complex p1 = drawing.get(i);
                    Complex p2 = drawing.get(i + 1);
                    g2.drawLine((int)p1.re, (int)p1.im, (int)p2.re, (int)p2.im);
                }
            }
            return;
        }

        // --- ANIMATION MODE ---
        if (currentState == State.ANIMATING) {

            // Calculate Epicycles Position
            // Start at center of screen (optional, but our DFT includes the DC offset at freq 0,
            // so we actually start at 0,0 relative to the data).
            double x = 0;
            double y = 0;

            // Draw Epicycles
            for (int i = 0; i < fourierData.size(); i++) {
                EpicycleData epi = fourierData.get(i);

                double prevX = x;
                double prevY = y;

                // Calculate position for this circle at current time
                // formula: c = radius * e^(i * (freq * time + phase))
                double angle = epi.freq * time + epi.phase;
                x += epi.amp * Math.cos(angle);
                y += epi.amp * Math.sin(angle);

                // Don't draw tiny circles, it gets messy
                if (epi.amp > 1) {
                    // Draw Circle
                    g2.setColor(new Color(100, 100, 100, 100)); // Transparent grey
                    g2.setStroke(new BasicStroke(1));
                    int r = (int) epi.amp;
                    g2.drawOval((int)(prevX - r), (int)(prevY - r), r * 2, r * 2);

                    // Draw Radius Line
                    g2.setColor(new Color(255, 255, 255, 100));
                    g2.drawLine((int)prevX, (int)prevY, (int)x, (int)y);
                }
            }

            // Add current tip position to path
            path.add(new Point((int)x, (int)y));

            // Draw the traced path
            g2.setColor(Color.CYAN);
            g2.setStroke(new BasicStroke(2));
            if (path.size() > 1) {
                Path2D trace = new Path2D.Double();
                trace.moveTo(path.get(0).x, path.get(0).y);
                for(int i=1; i<path.size(); i++) {
                    trace.lineTo(path.get(i).x, path.get(i).y);
                }
                g2.draw(trace);
            }

            // Highlight the tip
            g2.setColor(Color.WHITE);
            g2.fillOval((int)x-3, (int)y-3, 6, 6);
        }
    }

    // --- Helper Classes ---

    static class Complex {
        double re;
        double im;
        Complex(double re, double im) {
            this.re = re;
            this.im = im;
        }
    }

    static class EpicycleData {
        double re, im, freq, amp, phase;

        EpicycleData(double re, double im, double freq, double amp, double phase) {
            this.re = re;
            this.im = im;
            this.freq = freq;
            this.amp = amp;
            this.phase = phase;
        }
    }
}