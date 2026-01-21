import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/**
 * MinesweeperWithLeaderboard.java
 *
 * Minesweeper clone with Leaderboard (best times per difficulty).
 *
 * Leaderboard file: leaderboard.txt (format per line: difficulty;player;seconds)
 * Shows top 5 per difficulty and prompts for name on a top-5 finish.
 *
 * Save / Compile / Run:
 * javac MinesweeperWithLeaderboard.java
 * java MinesweeperWithLeaderboard
 */
public class MinesweeperWithLeaderboard extends JFrame {
    // Presets
    private static final Difficulty BEGINNER = new Difficulty("Beginner", 9, 9, 10);
    private static final Difficulty INTERMEDIATE = new Difficulty("Intermediate", 16, 16, 40);
    private static final Difficulty EXPERT = new Difficulty("Expert", 16, 30, 99);

    // UI components
    private final JPanel boardPanel = new JPanel();
    private final JLabel minesLabel = new JLabel("Mines: 0");
    private final JLabel timeLabel = new JLabel("Time: 0");
    private final JButton restartBtn = new JButton("Restart");
    private final JComboBox<String> difficultyCombo;
    private final JButton customBtn = new JButton("Custom...");
    private final JButton leaderboardBtn = new JButton("Leaderboard");

    // Game state
    private Tile[][] tiles;
    private int rows = BEGINNER.rows, cols = BEGINNER.cols, totalMines = BEGINNER.mines;
    private boolean firstClick = true;
    private boolean running = false;
    private int flagsPlaced = 0;
    private int revealedCount = 0;
    private Timer gameTimer;
    private int elapsedSeconds = 0;
    private String status = "Ready";

    // Leaderboard data
    private static final Path LEADERBOARD_FILE = Paths.get("leaderboard.txt");
    private static final int LEADERBOARD_TOP_K = 5;
    // Map difficulty -> list of entries (sorted ascending by seconds)
    private final Map<String, List<LeaderboardEntry>> leaderboard = new HashMap<>();

    public MinesweeperWithLeaderboard() {
        super("Minesweeper");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8,8));
        setResizable(false);

        // Top control panel
        JPanel top = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 6));
        difficultyCombo = new JComboBox<>(new String[]{BEGINNER.name, INTERMEDIATE.name, EXPERT.name});
        difficultyCombo.setSelectedIndex(0);
        top.add(new JLabel("Difficulty:"));
        top.add(difficultyCombo);
        top.add(customBtn);
        top.add(restartBtn);
        top.add(minesLabel);
        top.add(timeLabel);
        top.add(leaderboardBtn);

        add(top, BorderLayout.NORTH);

        // center board
        boardPanel.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        add(boardPanel, BorderLayout.CENTER);

        // bottom status
        JLabel statusLabel = new JLabel("Status: " + status);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0,8,8,8));
        add(statusLabel, BorderLayout.SOUTH);

        // Timer
        gameTimer = new Timer(1000, e -> {
            elapsedSeconds++;
            timeLabel.setText("Time: " + elapsedSeconds);
        });

        // Listeners
        restartBtn.addActionListener(e -> startNewGame());
        difficultyCombo.addActionListener(e -> {
            String choice = (String) difficultyCombo.getSelectedItem();
            if (BEGINNER.name.equals(choice)) setDifficulty(BEGINNER);
            else if (INTERMEDIATE.name.equals(choice)) setDifficulty(INTERMEDIATE);
            else if (EXPERT.name.equals(choice)) setDifficulty(EXPERT);
            startNewGame();
        });
        customBtn.addActionListener(e -> showCustomDialog());
        leaderboardBtn.addActionListener(e -> showLeaderboardDialog((String) difficultyCombo.getSelectedItem()));

        // load leaderboard from file
        loadLeaderboard();

        // start
        setDifficulty(BEGINNER);
        startNewGame();

        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        // update status label periodically (simple binding)
        Timer statusUpdater = new Timer(200, e -> statusLabel.setText("Status: " + status));
        statusUpdater.start();
    }

    private void setDifficulty(Difficulty d) {
        this.rows = d.rows;
        this.cols = d.cols;
        this.totalMines = d.mines;
        this.setTitle("Minesweeper — " + d.name + " (" + rows + "x" + cols + " / " + totalMines + " mines)");
    }

    private void showCustomDialog() {
        JPanel p = new JPanel(new GridLayout(3,2,6,6));
        JTextField rField = new JTextField(String.valueOf(rows));
        JTextField cField = new JTextField(String.valueOf(cols));
        JTextField mField = new JTextField(String.valueOf(totalMines));
        p.add(new JLabel("Rows:")); p.add(rField);
        p.add(new JLabel("Cols:")); p.add(cField);
        p.add(new JLabel("Mines:")); p.add(mField);
        int res = JOptionPane.showConfirmDialog(this, p, "Custom size", JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) {
            try {
                int nr = Integer.parseInt(rField.getText().trim());
                int nc = Integer.parseInt(cField.getText().trim());
                int nm = Integer.parseInt(mField.getText().trim());
                if (nr <= 0 || nc <= 0 || nm <= 0 || nm >= nr*nc) {
                    JOptionPane.showMessageDialog(this, "Invalid values.", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                rows = nr; cols = nc; totalMines = nm;
                difficultyCombo.setSelectedIndex(-1);
                setDifficulty(new Difficulty("Custom", rows, cols, totalMines));
                startNewGame();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Enter integer values.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void startNewGame() {
        // stop timer
        gameTimer.stop();
        elapsedSeconds = 0;
        timeLabel.setText("Time: 0");
        firstClick = true;
        running = true;
        flagsPlaced = 0;
        revealedCount = 0;
        status = "Playing";

        // board
        boardPanel.removeAll();
        boardPanel.setLayout(new GridLayout(rows, cols, 1, 1));

        tiles = new Tile[rows][cols];
        for (int r=0;r<rows;r++){
            for (int c=0;c<cols;c++){
                Tile t = new Tile(r, c);
                tiles[r][c] = t;
                boardPanel.add(t.button);
            }
        }

        updateMinesLabel();
        boardPanel.revalidate();
        boardPanel.repaint();

        // resize frame to fit board
        int buttonSize = 28; // pixel size per cell tweak
        int w = Math.min(1200, cols * buttonSize + 120);
        int h = Math.min(900, rows * buttonSize + 220);
        setSize(w, h);
        setLocationRelativeTo(null);
    }

    // place mines after first click to ensure safe-first-click
    private void placeMines(int safeRow, int safeCol) {
        Random rnd = new Random();
        int placed = 0;
        while (placed < totalMines) {
            int r = rnd.nextInt(rows);
            int c = rnd.nextInt(cols);
            if (tiles[r][c].hasMine) continue;
            // don't place on the safe cell or neighbors
            if (Math.abs(r - safeRow) <= 1 && Math.abs(c - safeCol) <= 1) continue;
            tiles[r][c].hasMine = true;
            placed++;
        }
        // after placing mines, compute numbers
        for (int r=0;r<rows;r++){
            for (int c=0;c<cols;c++){
                tiles[r][c].neighborMines = countNeighborMines(r,c);
            }
        }
    }

    private int countNeighborMines(int r, int c) {
        int count = 0;
        for (int dr=-1; dr<=1; dr++) for (int dc=-1; dc<=1; dc++){
            int nr = r+dr, nc = c+dc;
            if (nr<0 || nr>=rows || nc<0 || nc>=cols) continue;
            if (tiles[nr][nc].hasMine) count++;
        }
        return count;
    }

    private void updateMinesLabel() {
        minesLabel.setText("Mines: " + (totalMines - flagsPlaced));
    }

    private void revealTile(Tile t) {
        if (!running || t.revealed || t.flagged) return;
        if (firstClick) {
            // place mines avoiding this tile and neighbors, then start timer
            placeMines(t.row, t.col);
            gameTimer.start();
            firstClick = false;
        }

        if (t.hasMine) {
            // reveal all mines and game over
            t.revealed = true;
            t.updateButton();
            revealAllMines();
            running = false;
            gameTimer.stop();
            status = "Lost";
            JOptionPane.showMessageDialog(this, "Boom! You hit a mine. Game over.", "Game Over", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // flood-fill reveal
        int revealedNow = floodReveal(t.row, t.col);
        revealedCount += revealedNow;
        // check win: all non-mine tiles revealed
        if (revealedCount == rows * cols - totalMines) {
            running = false;
            gameTimer.stop();
            status = "Won";
            revealAllMines(); // show mines as well
            handleWin();
        }
    }

    // flood-fill using stack iterative approach; returns number of tiles newly revealed
    private int floodReveal(int startR, int startC) {
        int newly = 0;
        Stack<Point> stack = new Stack<>();
        stack.push(new Point(startR, startC));
        while (!stack.isEmpty()) {
            Point p = stack.pop();
            int r = p.x, c = p.y;
            Tile tt = tiles[r][c];
            if (tt.revealed || tt.flagged) continue;
            tt.revealed = true;
            tt.updateButton();
            newly++;
            if (tt.neighborMines == 0) {
                // push neighbors
                for (int dr=-1; dr<=1; dr++) for (int dc=-1; dc<=1; dc++){
                    int nr = r+dr, nc = c+dc;
                    if (nr<0 || nr>=rows || nc<0 || nc>=cols) continue;
                    Tile nei = tiles[nr][nc];
                    if (!nei.revealed && !nei.hasMine) stack.push(new Point(nr,nc));
                }
            }
        }
        return newly;
    }

    private void revealAllMines() {
        for (int r=0;r<rows;r++) for (int c=0;c<cols;c++){
            Tile t = tiles[r][c];
            if (t.hasMine) {
                t.revealed = true;
                t.updateButton();
            }
        }
    }

    // toggle flag on tile
    private void toggleFlag(Tile t) {
        if (!running || t.revealed) return;
        if (t.flagged) {
            t.flagged = false;
            flagsPlaced--;
        } else {
            t.flagged = true;
            flagsPlaced++;
        }
        t.updateButton();
        updateMinesLabel();
    }

    // Called when player wins: check leaderboard and possibly record score
    private void handleWin() {
        String diff = getSelectedDifficultyName();
        int time = elapsedSeconds;
        List<LeaderboardEntry> list = leaderboard.computeIfAbsent(diff, k -> new ArrayList<>());

        // determine if qualifies for top K
        boolean qualifies = false;
        if (list.size() < LEADERBOARD_TOP_K) qualifies = true;
        else {
            // sorted ascending by seconds
            list.sort(Comparator.comparingInt(e -> e.seconds));
            int worst = list.get(list.size()-1).seconds;
            if (time < worst) qualifies = true;
        }

        // show win dialog first
        JOptionPane.showMessageDialog(this, "Congratulations — You cleared the minefield!\nTime: " + time + " seconds", "You Win", JOptionPane.INFORMATION_MESSAGE);

        if (qualifies) {
            String name = JOptionPane.showInputDialog(this, "You made the leaderboard for " + diff + "!\nEnter your name (max 20 chars):", "New High Score", JOptionPane.PLAIN_MESSAGE);
            if (name == null) name = "Anonymous";
            name = name.trim();
            if (name.isEmpty()) name = "Anonymous";
            if (name.length() > 20) name = name.substring(0, 20);
            // add entry
            list.add(new LeaderboardEntry(name, time));
            // sort & trim
            list.sort(Comparator.comparingInt(e -> e.seconds));
            if (list.size() > LEADERBOARD_TOP_K) {
                list.subList(LEADERBOARD_TOP_K, list.size()).clear();
            }
            // persist
            saveLeaderboard();
            // show updated leaderboard
            showLeaderboardDialog(diff);
        }
    }

    // UI tile wrapper
    private class Tile {
        final int row, col;
        boolean hasMine = false;
        boolean revealed = false;
        boolean flagged = false;
        int neighborMines = 0;
        final JButton button;

        Tile(int r, int c) {
            this.row = r; this.col = c;
            button = new JButton();
            button.setPreferredSize(new Dimension(28,28));
            button.setMargin(new Insets(0,0,0,0));
            button.setFont(new Font("SansSerif", Font.BOLD, 12));
            button.setFocusPainted(false);
            updateButton();

            // Mouse listener for left/right clicks
            button.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    // left click or primary
                    if (SwingUtilities.isLeftMouseButton(e) && !e.isControlDown()) {
                        revealTile(Tile.this);
                    } else if (SwingUtilities.isRightMouseButton(e) || (SwingUtilities.isLeftMouseButton(e) && e.isControlDown())) {
                        toggleFlag(Tile.this);
                    }
                }
            });
        }

        void updateButton() {
            if (revealed) {
                button.setEnabled(false);
                if (hasMine) {
                    button.setText("\u2739"); // star-like mine char
                    button.setBackground(Color.RED);
                } else {
                    if (neighborMines > 0) {
                        button.setText(String.valueOf(neighborMines));
                        button.setBackground(new Color(0xdddddd));
                        button.setForeground(colorForNumber(neighborMines));
                    } else {
                        button.setText("");
                        button.setBackground(new Color(0xeeeeee));
                    }
                }
            } else {
                button.setEnabled(true);
                if (flagged) {
                    button.setText("\u2691"); // flag char
                    button.setForeground(Color.BLUE);
                    button.setBackground(new Color(0xcce6ff));
                } else {
                    button.setText("");
                    button.setBackground(null);
                }
            }
        }

        private Color colorForNumber(int n) {
            switch (n) {
                case 1: return Color.BLUE;
                case 2: return new Color(0x008200);
                case 3: return Color.RED;
                case 4: return new Color(0x000084);
                case 5: return new Color(0x840000);
                case 6: return new Color(0x008284);
                case 7: return Color.BLACK;
                case 8: return Color.DARK_GRAY;
                default: return Color.BLACK;
            }
        }
    }

    private String getSelectedDifficultyName() {
        String choice = (String) difficultyCombo.getSelectedItem();
        if (choice == null || choice.isEmpty()) return "Custom";
        return choice;
    }

    // ---------------- Leaderboard Persistence ----------------

    private static class LeaderboardEntry {
        final String name;
        final int seconds;
        LeaderboardEntry(String name, int seconds) { this.name = name; this.seconds = seconds; }
        @Override public String toString() { return name + " - " + seconds + "s"; }
    }

    private void loadLeaderboard() {
        leaderboard.clear();
        if (!Files.exists(LEADERBOARD_FILE)) return;
        try (BufferedReader br = Files.newBufferedReader(LEADERBOARD_FILE, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                // format: difficulty;player;seconds
                String[] parts = line.split(";", 3);
                if (parts.length != 3) continue;
                String diff = parts[0];
                String player = parts[1];
                int secs;
                try { secs = Integer.parseInt(parts[2]); }
                catch (NumberFormatException ex) { continue; }
                leaderboard.computeIfAbsent(diff, k -> new ArrayList<>()).add(new LeaderboardEntry(player, secs));
            }
            // sort and trim each list
            for (List<LeaderboardEntry> list : leaderboard.values()) {
                list.sort(Comparator.comparingInt(e -> e.seconds));
                if (list.size() > LEADERBOARD_TOP_K) list.subList(LEADERBOARD_TOP_K, list.size()).clear();
            }
        } catch (IOException ex) {
            // ignore - start with empty leaderboard
        }
    }

    private void saveLeaderboard() {
        try (BufferedWriter bw = Files.newBufferedWriter(LEADERBOARD_FILE, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (Map.Entry<String, List<LeaderboardEntry>> e : leaderboard.entrySet()) {
                String diff = e.getKey();
                for (LeaderboardEntry le : e.getValue()) {
                    bw.write(diff + ";" + le.name + ";" + le.seconds);
                    bw.newLine();
                }
            }
        } catch (IOException ex) {
            // ignore write errors
        }
    }

    private void showLeaderboardDialog(String difficulty) {
        List<LeaderboardEntry> list = leaderboard.getOrDefault(difficulty, Collections.emptyList());
        StringBuilder sb = new StringBuilder();
        sb.append("Top ").append(LEADERBOARD_TOP_K).append(" — ").append(difficulty).append("\n\n");
        if (list.isEmpty()) sb.append("No scores yet.\n");
        else {
            int rank = 1;
            for (LeaderboardEntry le : list) {
                sb.append(String.format("%d. %s — %ds\n", rank++, le.name, le.seconds));
            }
        }
        JOptionPane.showMessageDialog(this, sb.toString(), "Leaderboard", JOptionPane.INFORMATION_MESSAGE);
    }

    // Difficulty helper
    private static class Difficulty {
        final String name;
        final int rows, cols, mines;
        Difficulty(String name, int rows, int cols, int mines) {
            this.name = name; this.rows = rows; this.cols = cols; this.mines = mines;
        }
    }

    // entry point
    public static void main(String[] args) {
        SwingUtilities.invokeLater(MinesweeperWithLeaderboard::new);
    }
}
