import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * SnakeGameEnhanced.java
 *
 * Upgraded Snake game:
 *  - High-score saved to highscore.txt
 *  - Speed increases as snake eats food
 *  - Random obstacles (configurable)
 *  - Wrap-around toggle (press W)
 *  - Sound effects (Toolkit.beep for eat/gameover)
 *
 * Controls:
 *  - Arrow keys: move
 *  - P: pause/resume
 *  - SPACE: restart when game over
 *  - W: toggle wrap-around walls on/off
 *
 * Compile & run:
 *  javac SnakeGameEnhanced.java
 *  java SnakeGameEnhanced
 */
public class SnakeGameEnhanced extends JFrame {

    public SnakeGameEnhanced() {
        setTitle("Snake Game — Enhanced");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        GamePanel panel = new GamePanel();
        add(panel);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
        panel.startGame();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SnakeGameEnhanced::new);
    }

    static class GamePanel extends JPanel implements ActionListener {

        // Config
        private static final int CELL_SIZE = 24;
        private static final int GRID_COLS = 20;
        private static final int GRID_ROWS = 20;
        private static final int PANEL_WIDTH = CELL_SIZE * GRID_COLS;
        private static final int PANEL_HEIGHT = CELL_SIZE * GRID_ROWS;
        private static final int BASE_SPEED = 140;    // ms per tick (higher = slower)
        private static final int MIN_SPEED = 45;      // fastest
        private static final int SPEED_STEP = 8;      // decrease delay every X food eaten or per growth
        private static final int SPEED_INCREASE_FOOD = 3; // every N foods, speed up
        private static final int INITIAL_OBSTACLES = 4;   // starting obstacles; increase for harder levels

        // High score file
        private static final Path HIGHSCORE_FILE = Paths.get("highscore.txt");

        // snake state
        private final int maxCells = GRID_COLS * GRID_ROWS;
        private final int[] snakeX = new int[maxCells];
        private final int[] snakeY = new int[maxCells];
        private int snakeLength;

        // movement
        private int dx = 1, dy = 0;

        // food
        private int foodX, foodY;

        // obstacles
        private final boolean[][] obstacles = new boolean[GRID_COLS][GRID_ROWS];

        // game state
        private boolean running = false;
        private boolean paused = false;
        private boolean gameOver = false;
        private boolean wrapAround = false; // toggleable by W key
        private int score = 0;
        private int highScore = 0;
        private int foodsEatenSinceStart = 0;

        private Timer timer;
        private int currentDelay = BASE_SPEED;
        private final Random random = new Random();

        public GamePanel() {
            setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT + 30));
            setBackground(Color.BLACK);
            setFocusable(true);
            requestFocusInWindow();

            loadHighScore();
            initControls();
        }

        private void initControls() {
            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    int key = e.getKeyCode();

                    if (!running && key == KeyEvent.VK_SPACE) {
                        startGame();
                        return;
                    }

                    if (gameOver) {
                        if (key == KeyEvent.VK_SPACE) startGame();
                        return;
                    }

                    if (key == KeyEvent.VK_P) {
                        togglePause();
                        return;
                    }

                    if (key == KeyEvent.VK_W) {
                        wrapAround = !wrapAround;
                        String msg = wrapAround ? "Wrap-around ON" : "Wrap-around OFF";
                        Toolkit.getDefaultToolkit().beep();
                        JOptionPane.showMessageDialog(GamePanel.this, msg, "Wrap Mode", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }

                    // movement (prevent reversing)
                    if (key == KeyEvent.VK_LEFT && dx == 0) { dx = -1; dy = 0; }
                    else if (key == KeyEvent.VK_RIGHT && dx == 0) { dx = 1; dy = 0; }
                    else if (key == KeyEvent.VK_UP && dy == 0) { dx = 0; dy = -1; }
                    else if (key == KeyEvent.VK_DOWN && dy == 0) { dx = 0; dy = 1; }
                }
            });
        }

        public void startGame() {
            // reset
            Arrays.stream(obstacles).forEach(row -> Arrays.fill(row, false));
            placeInitialObstacles(INITIAL_OBSTACLES);

            snakeLength = 5;
            int startX = GRID_COLS / 2;
            int startY = GRID_ROWS / 2;
            for (int i = 0; i < snakeLength; i++) {
                snakeX[i] = startX - i;
                snakeY[i] = startY;
            }
            dx = 1; dy = 0;
            placeFood();
            score = 0;
            foodsEatenSinceStart = 0;
            currentDelay = BASE_SPEED;
            timer = new Timer(currentDelay, this);
            running = true;
            paused = false;
            gameOver = false;
            timer.start();
            repaint();
        }

        private void togglePause() {
            if (!running) return;
            paused = !paused;
            if (paused) timer.stop();
            else timer.start();
            repaint();
        }

        private void placeInitialObstacles(int count) {
            int placed = 0;
            while (placed < count) {
                int ox = random.nextInt(GRID_COLS);
                int oy = random.nextInt(GRID_ROWS);
                // don't place obstacle at center start area
                if (ox >= GRID_COLS/2 - 2 && ox <= GRID_COLS/2 + 2 && oy >= GRID_ROWS/2 -2 && oy <= GRID_ROWS/2 +2) continue;
                if (!obstacles[ox][oy]) {
                    obstacles[ox][oy] = true;
                    placed++;
                }
            }
        }

        private void placeFood() {
            boolean onSnakeOrObstacle;
            do {
                onSnakeOrObstacle = false;
                foodX = random.nextInt(GRID_COLS);
                foodY = random.nextInt(GRID_ROWS);
                if (obstacles[foodX][foodY]) onSnakeOrObstacle = true;
                for (int i = 0; i < snakeLength; i++) {
                    if (snakeX[i] == foodX && snakeY[i] == foodY) { onSnakeOrObstacle = true; break; }
                }
            } while (onSnakeOrObstacle);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (!running || paused || gameOver) return;
            moveSnake();
            if (checkFoodCollision()) {
                Toolkit.getDefaultToolkit().beep(); // eat sound
                score += 10;
                foodsEatenSinceStart++;
                // speed up every SPEED_INCREASE_FOOD foods eaten
                if (foodsEatenSinceStart % SPEED_INCREASE_FOOD == 0) {
                    increaseSpeed();
                }
                // occasionally add new obstacle as difficulty increases
                if (foodsEatenSinceStart % 4 == 0) addRandomObstacle();
            }
            checkCollision();
            repaint();
        }

        private void increaseSpeed() {
            currentDelay = Math.max(MIN_SPEED, currentDelay - SPEED_STEP);
            if (timer != null) timer.setDelay(currentDelay);
        }

        private void addRandomObstacle() {
            for (int tries=0; tries<200; tries++) {
                int ox = random.nextInt(GRID_COLS);
                int oy = random.nextInt(GRID_ROWS);
                if (obstacles[ox][oy]) continue;
                boolean onSnake = false;
                for (int i=0;i<snakeLength;i++) if (snakeX[i]==ox && snakeY[i]==oy) { onSnake=true; break; }
                if (onSnake) continue;
                if (foodX==ox && foodY==oy) continue;
                obstacles[ox][oy] = true;
                break;
            }
        }

        private void moveSnake() {
            for (int i = snakeLength - 1; i > 0; i--) {
                snakeX[i] = snakeX[i - 1];
                snakeY[i] = snakeY[i - 1];
            }
            int nx = snakeX[0] + dx;
            int ny = snakeY[0] + dy;

            // Wrap-around behavior
            if (wrapAround) {
                if (nx < 0) nx = GRID_COLS - 1;
                if (nx >= GRID_COLS) nx = 0;
                if (ny < 0) ny = GRID_ROWS - 1;
                if (ny >= GRID_ROWS) ny = 0;
            }

            snakeX[0] = nx;
            snakeY[0] = ny;
        }

        private boolean checkFoodCollision() {
            if (snakeX[0] == foodX && snakeY[0] == foodY) {
                snakeLength = Math.min(maxCells, snakeLength + 1);
                placeFood();
                return true;
            }
            return false;
        }

        private void checkCollision() {
            int headX = snakeX[0], headY = snakeY[0];

            // wall collision (if not wrap-around)
            if (!wrapAround) {
                if (headX < 0 || headX >= GRID_COLS || headY < 0 || headY >= GRID_ROWS) {
                    doGameOver();
                    return;
                }
            } else {
                // ensure after wrapping head coords are valid
                if (headX < 0 || headX >= GRID_COLS || headY < 0 || headY >= GRID_ROWS) {
                    // shouldn't happen because we wrapped, but guard
                    doGameOver();
                    return;
                }
            }

            // obstacle collision
            if (headX >=0 && headY>=0 && headX < GRID_COLS && headY < GRID_ROWS) {
                if (obstacles[headX][headY]) { doGameOver(); return; }
            }

            // self collision
            for (int i = 1; i < snakeLength; i++) {
                if (snakeX[i] == headX && snakeY[i] == headY) {
                    doGameOver();
                    return;
                }
            }
        }

        private void doGameOver() {
            gameOver = true;
            running = false;
            if (timer != null) timer.stop();
            Toolkit.getDefaultToolkit().beep(); // game over sound
            // update highscore
            if (score > highScore) {
                highScore = score;
                saveHighScore();
                JOptionPane.showMessageDialog(this, "Game Over\nNew High Score: " + highScore + "\nPress SPACE to restart", "Game Over", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Game Over\nScore: " + score + "\nHigh Score: " + highScore + "\nPress SPACE to restart", "Game Over", JOptionPane.INFORMATION_MESSAGE);
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // subtle grid
            g2.setColor(new Color(0x131313));
            for (int x = 0; x <= PANEL_WIDTH; x += CELL_SIZE) g2.drawLine(x, 0, x, PANEL_HEIGHT);
            for (int y = 0; y <= PANEL_HEIGHT; y += CELL_SIZE) g2.drawLine(0, y, PANEL_WIDTH, y);

            // draw obstacles
            g2.setColor(new Color(0x8B0000));
            for (int x = 0; x < GRID_COLS; x++) {
                for (int y = 0; y < GRID_ROWS; y++) {
                    if (obstacles[x][y]) {
                        int ox = x * CELL_SIZE;
                        int oy = y * CELL_SIZE;
                        g2.fillRect(ox + 2, oy + 2, CELL_SIZE - 4, CELL_SIZE - 4);
                    }
                }
            }

            // draw food
            int fx = foodX * CELL_SIZE;
            int fy = foodY * CELL_SIZE;
            g2.setColor(Color.RED);
            g2.fillOval(fx + 4, fy + 4, CELL_SIZE - 8, CELL_SIZE - 8);

            // draw snake
            for (int i = 0; i < snakeLength; i++) {
                int sx = snakeX[i] * CELL_SIZE;
                int sy = snakeY[i] * CELL_SIZE;
                if (i == 0) {
                    g2.setColor(new Color(0x00cc66));
                    g2.fillRoundRect(sx + 2, sy + 2, CELL_SIZE - 4, CELL_SIZE - 4, 8, 8);
                } else {
                    float shade = 1.0f - (i / (float) Math.max(snakeLength, 1)) * 0.6f;
                    g2.setColor(new Color(0f, 0.6f * shade, 0.35f * shade));
                    g2.fillRoundRect(sx + 2, sy + 2, CELL_SIZE - 4, CELL_SIZE - 4, 6, 6);
                }
            }

            // score and info
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 16));
            g2.drawString("Score: " + score + "   High: " + highScore + "   Speed: " + (1000/currentDelay) + " ticks/sec", 8, PANEL_HEIGHT + 20);
            g2.drawString("Wrap: " + (wrapAround ? "ON (press W to toggle)" : "OFF (press W to toggle)"), PANEL_WIDTH - 300, PANEL_HEIGHT + 20);

            if (paused) drawCenteredText(g2, "PAUSED — Press P to resume");
            if (gameOver) drawCenteredText(g2, "GAME OVER — Press SPACE to restart");

            g2.dispose();
        }

        private void drawCenteredText(Graphics2D g2, String text) {
            g2.setColor(new Color(0, 0, 0, 160));
            g2.fillRect(0, PANEL_HEIGHT/2 - 32, PANEL_WIDTH, 64);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 20));
            FontMetrics fm = g2.getFontMetrics();
            int tx = (PANEL_WIDTH - fm.stringWidth(text)) / 2;
            int ty = PANEL_HEIGHT / 2 + fm.getAscent() / 2 - 4;
            g2.drawString(text, tx, ty);
        }

        // ---------------- High-score persistence ----------------

        private void loadHighScore() {
            try {
                if (Files.exists(HIGHSCORE_FILE)) {
                    String s = new String(Files.readAllBytes(HIGHSCORE_FILE), StandardCharsets.UTF_8).trim();
                    if (!s.isEmpty()) highScore = Integer.parseInt(s);
                } else {
                    highScore = 0;
                }
            } catch (Exception e) {
                highScore = 0;
            }
        }

        private void saveHighScore() {
            try {
                Files.write(HIGHSCORE_FILE, String.valueOf(highScore).getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {}
        }
    }
}
