import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Random;

public class MatrixRain extends JPanel implements ActionListener {
    private Timer timer;
    private ArrayList<Stream> streams;
    private Random rand;
    private static final int WIDTH = 1000;
    private static final int HEIGHT = 700;
    private static final int FONT_SIZE = 16;
    private static final int COLUMN_WIDTH = FONT_SIZE;

    // Matrix characters (including Katakana, Latin, and numbers)
    private static final String MATRIX_CHARS =
            "ﾊﾐﾋｰｳｼﾅﾓﾆｻﾜﾂｵﾘｱﾎﾃﾏｹﾒｴｶｷﾑﾕﾗｾﾈｽﾀﾇﾍ" +
                    "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                    ":・.\"=*+-<>¦|çﾘｸ";

    public MatrixRain() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.BLACK);

        rand = new Random();
        streams = new ArrayList<>();

        // Create streams for each column
        int numColumns = WIDTH / COLUMN_WIDTH;
        for (int i = 0; i < numColumns; i++) {
            // Random starting position and speed
            int x = i * COLUMN_WIDTH;
            int y = rand.nextInt(HEIGHT) - HEIGHT;
            double speed = rand.nextDouble() * 3 + 2; // 2-5 pixels per frame
            int length = rand.nextInt(15) + 10; // 10-25 characters

            streams.add(new Stream(x, y, speed, length));
        }

        // Update at ~60 FPS
        timer = new Timer(50, this);
        timer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        // Enable anti-aliasing
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Add fade effect by drawing semi-transparent black rectangle
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.1f));
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, WIDTH, HEIGHT);

        // Reset composite for drawing streams
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        // Set font
        g2d.setFont(new Font("Monospaced", Font.BOLD, FONT_SIZE));

        // Draw all streams
        for (Stream stream : streams) {
            stream.draw(g2d);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // Update all streams
        for (Stream stream : streams) {
            stream.update();
        }
        repaint();
    }

    // Inner class for a single stream of falling characters
    class Stream {
        private int x;
        private double y;
        private double speed;
        private ArrayList<Character> characters;
        private int length;
        private int changeCounter;

        public Stream(int x, double y, double speed, int length) {
            this.x = x;
            this.y = y;
            this.speed = speed;
            this.length = length;
            this.changeCounter = 0;
            this.characters = new ArrayList<>();

            // Initialize with random characters
            for (int i = 0; i < length; i++) {
                characters.add(getRandomChar());
            }
        }

        public void update() {
            // Move down
            y += speed;

            // Reset if off screen
            if (y - (length * FONT_SIZE) > HEIGHT) {
                y = -length * FONT_SIZE;
                speed = rand.nextDouble() * 3 + 2;
            }

            // Occasionally change characters for flickering effect
            changeCounter++;
            if (changeCounter > 3) {
                changeCounter = 0;
                int index = rand.nextInt(characters.size());
                characters.set(index, getRandomChar());
            }
        }

        public void draw(Graphics2D g2d) {
            for (int i = 0; i < characters.size(); i++) {
                int charY = (int)(y - (i * FONT_SIZE));

                // Only draw if on screen
                if (charY > 0 && charY < HEIGHT) {
                    // Calculate alpha based on position in stream
                    float alpha;
                    if (i == 0) {
                        // Head of stream is brightest (white)
                        g2d.setColor(new Color(200, 255, 200));
                        alpha = 1.0f;
                    } else {
                        // Rest of stream fades from bright green to dark green
                        int greenValue = Math.max(50, 255 - (i * 15));
                        g2d.setColor(new Color(0, greenValue, 0));
                        alpha = Math.max(0.3f, 1.0f - (i * 0.05f));
                    }

                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                    g2d.drawString(String.valueOf(characters.get(i)), x, charY);
                }
            }

            // Reset composite
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        }

        private char getRandomChar() {
            return MATRIX_CHARS.charAt(rand.nextInt(MATRIX_CHARS.length()));
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Matrix Rain Effect");
            MatrixRain matrixRain = new MatrixRain();

            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(matrixRain);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setResizable(false);
            frame.getContentPane().setBackground(Color.BLACK);
            frame.setVisible(true);
        });
    }
}