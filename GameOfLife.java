import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Random;

public class GameOfLife extends JPanel implements ActionListener {
    private static final int CELL_SIZE = 8;
    private static final int GRID_WIDTH = 120;
    private static final int GRID_HEIGHT = 90;
    private static final int WIDTH = GRID_WIDTH * CELL_SIZE;
    private static final int HEIGHT = GRID_HEIGHT * CELL_SIZE;

    private boolean[][] grid;
    private boolean[][] nextGrid;
    private Timer timer;
    private boolean isRunning = false;
    private int generation = 0;
    private int speed = 100; // milliseconds per generation
    private Random rand;

    public GameOfLife() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT + 100));
        setBackground(new Color(20, 20, 30));

        rand = new Random();
        grid = new boolean[GRID_HEIGHT][GRID_WIDTH];
        nextGrid = new boolean[GRID_HEIGHT][GRID_WIDTH];

        timer = new Timer(speed, this);

        // Add mouse listener for drawing cells
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!isRunning) {
                    toggleCell(e.getX(), e.getY());
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (!isRunning) {
                    drawCell(e.getX(), e.getY());
                }
            }
        });
    }

    private void toggleCell(int x, int y) {
        int col = x / CELL_SIZE;
        int row = y / CELL_SIZE;

        if (row >= 0 && row < GRID_HEIGHT && col >= 0 && col < GRID_WIDTH) {
            grid[row][col] = !grid[row][col];
            repaint();
        }
    }

    private void drawCell(int x, int y) {
        int col = x / CELL_SIZE;
        int row = y / CELL_SIZE;

        if (row >= 0 && row < GRID_HEIGHT && col >= 0 && col < GRID_WIDTH) {
            grid[row][col] = true;
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        // Draw grid
        for (int row = 0; row < GRID_HEIGHT; row++) {
            for (int col = 0; col < GRID_WIDTH; col++) {
                if (grid[row][col]) {
                    // Living cell - vibrant cyan/blue
                    g2d.setColor(new Color(0, 200, 255));
                    g2d.fillRect(col * CELL_SIZE, row * CELL_SIZE, CELL_SIZE - 1, CELL_SIZE - 1);
                } else {
                    // Dead cell - dark grid
                    g2d.setColor(new Color(30, 30, 40));
                    g2d.fillRect(col * CELL_SIZE, row * CELL_SIZE, CELL_SIZE - 1, CELL_SIZE - 1);
                }
            }
        }

        // Draw info panel
        drawInfoPanel(g2d);
    }

    private void drawInfoPanel(Graphics2D g2d) {
        int panelY = GRID_HEIGHT * CELL_SIZE + 10;

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 18));
        g2d.drawString("Conway's Game of Life", 10, panelY);

        g2d.setFont(new Font("Arial", Font.PLAIN, 14));
        g2d.drawString("Generation: " + generation, 10, panelY + 25);
        g2d.drawString("Speed: " + speed + "ms", 150, panelY + 25);
        g2d.drawString("Status: " + (isRunning ? "Running" : "Paused"), 280, panelY + 25);

        // Count living cells
        int alive = countLivingCells();
        g2d.drawString("Living Cells: " + alive, 400, panelY + 25);

        // Draw instructions
        g2d.setFont(new Font("Arial", Font.PLAIN, 11));
        g2d.setColor(new Color(200, 200, 200));
        g2d.drawString("Click/Drag to draw cells | Use buttons below to control", 10, panelY + 50);
    }

    private int countLivingCells() {
        int count = 0;
        for (int row = 0; row < GRID_HEIGHT; row++) {
            for (int col = 0; col < GRID_WIDTH; col++) {
                if (grid[row][col]) count++;
            }
        }
        return count;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (isRunning) {
            nextGeneration();
            repaint();
        }
    }

    private void nextGeneration() {
        // Calculate next generation based on Conway's rules
        for (int row = 0; row < GRID_HEIGHT; row++) {
            for (int col = 0; col < GRID_WIDTH; col++) {
                int neighbors = countNeighbors(row, col);

                // Conway's Game of Life Rules:
                // 1. Any live cell with 2 or 3 neighbors survives
                // 2. Any dead cell with exactly 3 neighbors becomes alive
                // 3. All other cells die or stay dead

                if (grid[row][col]) {
                    // Cell is alive
                    nextGrid[row][col] = (neighbors == 2 || neighbors == 3);
                } else {
                    // Cell is dead
                    nextGrid[row][col] = (neighbors == 3);
                }
            }
        }

        // Copy next generation to current grid
        for (int row = 0; row < GRID_HEIGHT; row++) {
            for (int col = 0; col < GRID_WIDTH; col++) {
                grid[row][col] = nextGrid[row][col];
            }
        }

        generation++;
    }

    private int countNeighbors(int row, int col) {
        int count = 0;

        // Check all 8 neighbors
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if (i == 0 && j == 0) continue; // Skip the cell itself

                int newRow = row + i;
                int newCol = col + j;

                // Wrap around edges (toroidal topology)
                if (newRow < 0) newRow = GRID_HEIGHT - 1;
                if (newRow >= GRID_HEIGHT) newRow = 0;
                if (newCol < 0) newCol = GRID_WIDTH - 1;
                if (newCol >= GRID_WIDTH) newCol = 0;

                if (grid[newRow][newCol]) {
                    count++;
                }
            }
        }

        return count;
    }

    private void clear() {
        for (int row = 0; row < GRID_HEIGHT; row++) {
            for (int col = 0; col < GRID_WIDTH; col++) {
                grid[row][col] = false;
            }
        }
        generation = 0;
        repaint();
    }

    private void randomize() {
        for (int row = 0; row < GRID_HEIGHT; row++) {
            for (int col = 0; col < GRID_WIDTH; col++) {
                grid[row][col] = rand.nextDouble() < 0.3; // 30% chance of being alive
            }
        }
        generation = 0;
        repaint();
    }

    private void loadPattern(String pattern) {
        clear();
        int centerRow = GRID_HEIGHT / 2;
        int centerCol = GRID_WIDTH / 2;

        switch (pattern) {
            case "Glider":
                // Classic glider pattern
                grid[centerRow][centerCol + 1] = true;
                grid[centerRow + 1][centerCol + 2] = true;
                grid[centerRow + 2][centerCol] = true;
                grid[centerRow + 2][centerCol + 1] = true;
                grid[centerRow + 2][centerCol + 2] = true;
                break;

            case "Blinker":
                // Oscillator with period 2
                grid[centerRow][centerCol - 1] = true;
                grid[centerRow][centerCol] = true;
                grid[centerRow][centerCol + 1] = true;
                break;

            case "Toad":
                // Oscillator with period 2
                grid[centerRow][centerCol] = true;
                grid[centerRow][centerCol + 1] = true;
                grid[centerRow][centerCol + 2] = true;
                grid[centerRow + 1][centerCol - 1] = true;
                grid[centerRow + 1][centerCol] = true;
                grid[centerRow + 1][centerCol + 1] = true;
                break;

            case "Beacon":
                // Oscillator with period 2
                grid[centerRow][centerCol] = true;
                grid[centerRow][centerCol + 1] = true;
                grid[centerRow + 1][centerCol] = true;
                grid[centerRow + 2][centerCol + 3] = true;
                grid[centerRow + 3][centerCol + 2] = true;
                grid[centerRow + 3][centerCol + 3] = true;
                break;

            case "Pulsar":
                // Period 3 oscillator
                int[][] pulsar = {
                        {2,0},{2,1},{2,2},{2,6},{2,7},{2,8},
                        {0,2},{1,2},{2,2},{6,2},{7,2},{8,2},
                        {0,8},{1,8},{2,8},{6,8},{7,8},{8,8},
                        {8,0},{8,1},{8,2},{8,6},{8,7},{8,8},
                        {4,0},{4,1},{4,2},{4,6},{4,7},{4,8},
                        {0,4},{1,4},{2,4},{6,4},{7,4},{8,4}
                };
                for (int[] pos : pulsar) {
                    grid[centerRow - 4 + pos[0]][centerCol - 4 + pos[1]] = true;
                }
                break;

            case "Glider Gun":
                // Gosper's Glider Gun - creates gliders periodically
                int[][] gun = {
                        {5,1},{5,2},{6,1},{6,2}, // Left square
                        {5,11},{6,11},{7,11},{4,12},{3,13},{3,14},{8,12},{9,13},{9,14},
                        {6,15},{4,16},{5,17},{6,17},{7,17},{6,18},{8,16},
                        {3,21},{4,21},{5,21},{3,22},{4,22},{5,22},{2,23},{6,23},
                        {1,25},{2,25},{6,25},{7,25}, // Right part
                        {3,35},{4,35},{3,36},{4,36} // Right square
                };
                for (int[] pos : gun) {
                    if (centerRow - 20 + pos[0] >= 0 && centerRow - 20 + pos[0] < GRID_HEIGHT &&
                            centerCol - 20 + pos[1] >= 0 && centerCol - 20 + pos[1] < GRID_WIDTH) {
                        grid[centerRow - 20 + pos[0]][centerCol - 20 + pos[1]] = true;
                    }
                }
                break;
        }

        repaint();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Conway's Game of Life");
            GameOfLife game = new GameOfLife();

            // Create control panel
            JPanel controlPanel = new JPanel();
            controlPanel.setBackground(new Color(40, 40, 50));
            controlPanel.setLayout(new FlowLayout());

            JButton startBtn = new JButton("Start");
            JButton stopBtn = new JButton("Stop");
            JButton stepBtn = new JButton("Step");
            JButton clearBtn = new JButton("Clear");
            JButton randomBtn = new JButton("Random");

            JButton fasterBtn = new JButton("Faster");
            JButton slowerBtn = new JButton("Slower");

            // Pattern buttons
            JComboBox<String> patternBox = new JComboBox<>(new String[]{
                    "Select Pattern", "Glider", "Blinker", "Toad", "Beacon", "Pulsar", "Glider Gun"
            });

            startBtn.addActionListener(e -> {
                game.isRunning = true;
                game.timer.start();
            });

            stopBtn.addActionListener(e -> {
                game.isRunning = false;
                game.timer.stop();
            });

            stepBtn.addActionListener(e -> {
                game.isRunning = false;
                game.timer.stop();
                game.nextGeneration();
                game.repaint();
            });

            clearBtn.addActionListener(e -> {
                game.isRunning = false;
                game.timer.stop();
                game.clear();
            });

            randomBtn.addActionListener(e -> {
                game.isRunning = false;
                game.timer.stop();
                game.randomize();
            });

            fasterBtn.addActionListener(e -> {
                game.speed = Math.max(10, game.speed - 20);
                game.timer.setDelay(game.speed);
                game.repaint();
            });

            slowerBtn.addActionListener(e -> {
                game.speed = Math.min(500, game.speed + 20);
                game.timer.setDelay(game.speed);
                game.repaint();
            });

            patternBox.addActionListener(e -> {
                String selected = (String) patternBox.getSelectedItem();
                if (selected != null && !selected.equals("Select Pattern")) {
                    game.isRunning = false;
                    game.timer.stop();
                    game.loadPattern(selected);
                    patternBox.setSelectedIndex(0);
                }
            });

            controlPanel.add(startBtn);
            controlPanel.add(stopBtn);
            controlPanel.add(stepBtn);
            controlPanel.add(clearBtn);
            controlPanel.add(randomBtn);
            controlPanel.add(new JLabel(" | Speed: "));
            controlPanel.add(fasterBtn);
            controlPanel.add(slowerBtn);
            controlPanel.add(new JLabel(" | "));
            controlPanel.add(patternBox);

            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new BorderLayout());
            frame.add(game, BorderLayout.CENTER);
            frame.add(controlPanel, BorderLayout.SOUTH);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setResizable(false);
            frame.setVisible(true);
        });
    }
}