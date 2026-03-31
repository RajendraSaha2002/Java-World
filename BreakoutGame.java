import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

// ═══════════════════════════════════════════════════════════════
//  BREAKOUT BALL GAME  –  Advanced Java Swing
//  Compatible with IntelliJ IDEA & Eclipse IDE
//  Single-file design: paste into BreakoutGame.java and run.
// ═══════════════════════════════════════════════════════════════
public class BreakoutGame extends JFrame {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            BreakoutGame frame = new BreakoutGame();
            frame.setVisible(true);
        });
    }

    public BreakoutGame() {
        setTitle("Breakout Ball Game");
        setSize(800, 620);
        setResizable(false);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        GamePanel panel = new GamePanel();
        add(panel);
        panel.requestFocusInWindow();
    }
}

// ───────────────────────────────────────────────────────────────
//  GAME PANEL  – core rendering + logic
// ───────────────────────────────────────────────────────────────
class GamePanel extends JPanel {

    // ── dimensions ──────────────────────────────────────────────
    static final int W = 800, H = 600;

    // ── game states ─────────────────────────────────────────────
    enum State { START, PLAYING, PAUSED, LEVEL_CLEAR, GAME_OVER, WIN }
    State state = State.START;

    // ── game objects ────────────────────────────────────────────
    Paddle paddle;
    List<Ball>  balls    = new ArrayList<>();
    List<Brick> bricks   = new ArrayList<>();
    List<PowerUp> powerUps = new ArrayList<>();

    // ── stats ───────────────────────────────────────────────────
    int score = 0, lives = 3, level = 1;
    int highScore = 0;
    static final int MAX_LEVELS = 3;

    // ── timer (game loop ~16 ms ≈ 60 fps) ───────────────────────
    Timer timer;
    static final int DELAY = 16;

    // ── input flags ─────────────────────────────────────────────
    boolean leftPressed, rightPressed;

    // ── colors / theme ──────────────────────────────────────────
    static final Color BG      = new Color(10, 10, 30);
    static final Color HUD     = new Color(220, 220, 255);
    static final Color OVERLAY = new Color(0, 0, 0, 160);

    Random rng = new Random();

    // ════════════════════════════════════════════════════════════
    GamePanel() {
        setPreferredSize(new Dimension(W, H));
        setBackground(BG);
        setFocusable(true);
        setupKeyBindings();
        initGame();

        timer = new Timer(DELAY, e -> gameLoop());
        timer.start();
    }

    // ── initialise / reset ──────────────────────────────────────
    void initGame() {
        score = 0; lives = 3; level = 1;
        balls.clear(); powerUps.clear();
        paddle = new Paddle();
        resetBall();
        buildBricks();
        state = State.START;
    }

    void resetBall() {
        balls.clear();
        balls.add(new Ball(W / 2, H - 120, 4 + level, -4 - level));
    }

    // ── brick layout (changes per level) ────────────────────────
    void buildBricks() {
        bricks.clear();
        int cols = 10, rows = 4 + level;           // more rows per level
        int bW = 68, bH = 22, xOff = 26, yOff = 60;
        Color[][] palette = {
                { new Color(255,80,80),  new Color(255,140,0),
                        new Color(255,220,50), new Color(80,200,120),
                        new Color(80,160,255), new Color(180,80,255) },
                { new Color(255,60,120), new Color(255,100,0),
                        new Color(200,255,50), new Color(50,230,180),
                        new Color(50,120,255), new Color(220,50,220) },
                { new Color(255,50,50),  new Color(255,120,0),
                        new Color(255,255,50), new Color(50,255,100),
                        new Color(50,200,255), new Color(200,50,255) }
        };
        Color[] pal = palette[Math.min(level - 1, 2)];

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int hp = (r < 2) ? 1 : (r < 4) ? 2 : 3;   // harder bricks lower rows
                Color col = pal[r % pal.length];
                bricks.add(new Brick(xOff + c * (bW + 4),
                        yOff + r * (bH + 4),
                        bW, bH, hp, col));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  GAME LOOP
    // ═══════════════════════════════════════════════════════════
    void gameLoop() {
        if (state == State.PLAYING) {
            updatePaddle();
            updateBalls();
            updatePowerUps();
            checkWin();
        }
        repaint();
    }

    void updatePaddle() {
        if (leftPressed)  paddle.moveLeft();
        if (rightPressed) paddle.moveRight();
    }

    void updateBalls() {
        Iterator<Ball> bi = balls.iterator();
        while (bi.hasNext()) {
            Ball b = bi.next();
            b.move();

            // wall collisions
            if (b.x <= 0)       { b.x = 0;     b.dx = Math.abs(b.dx); }
            if (b.x + b.d >= W) { b.x = W-b.d; b.dx = -Math.abs(b.dx); }
            if (b.y <= 0)       { b.y = 0;      b.dy = Math.abs(b.dy); }

            // paddle collision
            if (b.rect().intersects(paddle.rect()) && b.dy > 0) {
                b.dy = -Math.abs(b.dy);
                // angle based on hit position
                double hit = (b.x + b.d / 2.0) - (paddle.x + paddle.w / 2.0);
                b.dx = (int) Math.signum(hit) * (int)(Math.abs(hit) / 15 + 2);
            }

            // brick collisions
            Iterator<Brick> bri = bricks.iterator();
            while (bri.hasNext()) {
                Brick br = bri.next();
                if (!br.destroyed && b.rect().intersects(br.rect())) {
                    br.hit();
                    bounceOffBrick(b, br);
                    if (br.destroyed) {
                        score += br.maxHp * 10;
                        if (score > highScore) highScore = score;
                        maybeDropPowerUp(br);
                        bri.remove();
                    }
                    break;
                }
            }

            // ball lost
            if (b.y > H) bi.remove();
        }

        if (balls.isEmpty()) {
            lives--;
            if (lives <= 0) {
                state = State.GAME_OVER;
            } else {
                resetBall();
                state = State.PAUSED;   // tiny pause before continuing
                Timer t = new Timer(800, e -> { state = State.PLAYING; });
                t.setRepeats(false); t.start();
            }
        }
    }

    void bounceOffBrick(Ball b, Brick br) {
        Rectangle2D bi = b.rect().createIntersection(br.rect());
        if (bi.getWidth() < bi.getHeight()) b.dx = -b.dx;
        else                                 b.dy = -b.dy;
    }

    void updatePowerUps() {
        Iterator<PowerUp> pi = powerUps.iterator();
        while (pi.hasNext()) {
            PowerUp p = pi.next();
            p.y += 3;
            if (p.rect().intersects(paddle.rect())) {
                applyPowerUp(p.type);
                pi.remove();
            } else if (p.y > H) {
                pi.remove();
            }
        }
    }

    void maybeDropPowerUp(Brick br) {
        if (rng.nextInt(5) == 0) {   // 20 % chance
            powerUps.add(new PowerUp(br.x + br.w / 2 - 12, br.y,
                    PowerUp.Type.values()[rng.nextInt(PowerUp.Type.values().length)]));
        }
    }

    void applyPowerUp(PowerUp.Type t) {
        switch (t) {
            case WIDE_PADDLE ->  { paddle.w = Math.min(paddle.w + 30, 200); scheduleRestore(); }
            case EXTRA_LIFE  ->  lives++;
            case MULTI_BALL  ->  {
                List<Ball> extras = new ArrayList<>();
                for (Ball b : balls) {
                    extras.add(new Ball(b.x, b.y, -b.dx, b.dy));
                    extras.add(new Ball(b.x, b.y,  b.dx, -b.dy + 1));
                }
                balls.addAll(extras);
            }
            case FAST_BALL   ->  balls.forEach(b -> { b.dx = (int)(b.dx * 1.3); b.dy = (int)(b.dy * 1.3); });
            case SLOW_BALL   ->  balls.forEach(b -> { b.dx = (int)(b.dx * 0.7); b.dy = (int)(b.dy * 0.7); });
        }
    }

    void scheduleRestore() {
        Timer t = new Timer(8000, e -> paddle.w = Paddle.DEFAULT_W);
        t.setRepeats(false); t.start();
    }

    void checkWin() {
        if (bricks.isEmpty()) {
            if (level >= MAX_LEVELS) { state = State.WIN; }
            else                     { level++; state = State.LEVEL_CLEAR; }
        }
    }

    void nextLevel() {
        powerUps.clear();
        paddle = new Paddle();
        resetBall();
        buildBricks();
        state = State.PLAYING;
    }

    // ═══════════════════════════════════════════════════════════
    //  RENDERING
    // ═══════════════════════════════════════════════════════════
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawBackground(g2);
        bricks.forEach(br -> br.draw(g2));
        powerUps.forEach(p -> p.draw(g2));
        paddle.draw(g2);
        balls.forEach(b -> b.draw(g2));
        drawHUD(g2);

        switch (state) {
            case START      -> drawOverlay(g2, "BREAKOUT",
                    "Press SPACE to Start",
                    "← → to move  |  P to Pause");
            case PAUSED     -> drawOverlay(g2, "PAUSED",
                    "Press P to Resume", "");
            case LEVEL_CLEAR-> drawOverlay(g2, "LEVEL " + (level-1) + " CLEAR!",
                    "Press SPACE for Level " + level, "");
            case GAME_OVER  -> drawOverlay(g2, "GAME OVER",
                    "Score: " + score + "   High: " + highScore,
                    "Press SPACE to Restart");
            case WIN        -> drawOverlay(g2, "YOU WIN!",
                    "Score: " + score + "   High: " + highScore,
                    "Press SPACE to Restart");
            default -> {}
        }
    }

    void drawBackground(Graphics2D g2) {
        // subtle grid lines
        g2.setColor(new Color(255, 255, 255, 8));
        for (int x = 0; x < W; x += 40) g2.drawLine(x, 0, x, H);
        for (int y = 0; y < H; y += 40) g2.drawLine(0, y, W, y);
    }

    void drawHUD(Graphics2D g2) {
        g2.setColor(HUD);
        g2.setFont(new Font("Monospaced", Font.BOLD, 15));
        g2.drawString("SCORE: " + score,   14,  20);
        g2.drawString("HIGH:  " + highScore, 14,  38);
        g2.drawString("LEVEL: " + level,   320, 20);
        // lives as circles
        g2.drawString("LIVES:", 630, 20);
        for (int i = 0; i < lives; i++) {
            g2.setColor(new Color(255, 100, 100));
            g2.fillOval(700 + i * 22, 8, 14, 14);
        }
    }

    void drawOverlay(Graphics2D g2, String title, String sub1, String sub2) {
        g2.setColor(OVERLAY);
        g2.fillRoundRect(150, 200, 500, 200, 30, 30);

        g2.setColor(new Color(100, 200, 255));
        g2.setFont(new Font("Arial", Font.BOLD, 42));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(title, (W - fm.stringWidth(title)) / 2, 270);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.PLAIN, 18));
        fm = g2.getFontMetrics();
        g2.drawString(sub1, (W - fm.stringWidth(sub1)) / 2, 315);
        g2.drawString(sub2, (W - fm.stringWidth(sub2)) / 2, 345);
    }

    // ═══════════════════════════════════════════════════════════
    //  KEY BINDINGS  (more reliable than KeyListener in Swing)
    // ═══════════════════════════════════════════════════════════
    void setupKeyBindings() {
        InputMap  im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        // LEFT press / release
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT,  0, false), "leftOn");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT,  0, true),  "leftOff");
        am.put("leftOn",  new AbstractAction() { public void actionPerformed(ActionEvent e2) { leftPressed  = true;  } });
        am.put("leftOff", new AbstractAction() { public void actionPerformed(ActionEvent e2) { leftPressed  = false; } });

        // RIGHT press / release
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, false), "rightOn");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, true),  "rightOff");
        am.put("rightOn",  new AbstractAction() { public void actionPerformed(ActionEvent e2) { rightPressed = true;  } });
        am.put("rightOff", new AbstractAction() { public void actionPerformed(ActionEvent e2) { rightPressed = false; } });

        // SPACE  – context-sensitive action
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false), "space");
        am.put("space", new AbstractAction() {
            public void actionPerformed(ActionEvent e2) {
                switch (state) {
                    case START:       state = State.PLAYING; break;
                    case LEVEL_CLEAR: nextLevel();           break;
                    case GAME_OVER:
                    case WIN:         initGame();            break;
                    default:                                 break;
                }
            }
        });

        // P – pause / resume
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_P, 0, false), "pause");
        am.put("pause", new AbstractAction() {
            public void actionPerformed(ActionEvent e2) {
                if      (state == State.PLAYING) state = State.PAUSED;
                else if (state == State.PAUSED)  state = State.PLAYING;
            }
        });
    }
}

// ───────────────────────────────────────────────────────────────
//  PADDLE
// ───────────────────────────────────────────────────────────────
class Paddle {
    static final int DEFAULT_W = 110, H = 14, SPEED = 7;
    int x, y, w;

    Paddle() { w = DEFAULT_W; x = (GamePanel.W - w) / 2; y = GamePanel.H - 40; }

    void moveLeft()  { x = Math.max(0, x - SPEED); }
    void moveRight() { x = Math.min(GamePanel.W - w, x + SPEED); }

    Rectangle rect() { return new Rectangle(x, y, w, H); }

    void draw(Graphics2D g2) {
        GradientPaint gp = new GradientPaint(x, y, new Color(100, 180, 255),
                x, y + H, new Color(30, 100, 200));
        g2.setPaint(gp);
        g2.fillRoundRect(x, y, w, H, 10, 10);
        g2.setColor(new Color(180, 220, 255, 180));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(x, y, w, H, 10, 10);
    }
}

// ───────────────────────────────────────────────────────────────
//  BALL
// ───────────────────────────────────────────────────────────────
class Ball {
    int x, y, d = 14;
    int dx, dy;

    Ball(int x, int y, int dx, int dy) {
        this.x = x; this.y = y; this.dx = dx; this.dy = dy;
    }

    void move() { x += dx; y += dy; }

    Rectangle rect() { return new Rectangle(x, y, d, d); }

    void draw(Graphics2D g2) {
        // glow effect
        g2.setColor(new Color(255, 255, 150, 50));
        g2.fillOval(x - 4, y - 4, d + 8, d + 8);
        // ball body
        RadialGradientPaint rg = new RadialGradientPaint(
                new Point2D.Float(x + d / 3f, y + d / 3f), d,
                new float[]{0f, 1f},
                new Color[]{new Color(255, 255, 200), new Color(255, 200, 50)});
        g2.setPaint(rg);
        g2.fillOval(x, y, d, d);
    }
}

// ───────────────────────────────────────────────────────────────
//  BRICK
// ───────────────────────────────────────────────────────────────
class Brick {
    int x, y, w, h, hp, maxHp;
    Color color;
    boolean destroyed = false;

    Brick(int x, int y, int w, int h, int hp, Color color) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        this.hp = hp; this.maxHp = hp; this.color = color;
    }

    void hit() { if (--hp <= 0) destroyed = true; }

    Rectangle rect() { return new Rectangle(x, y, w, h); }

    void draw(Graphics2D g2) {
        // fade brick color based on remaining HP
        float ratio = (float) hp / maxHp;
        Color c = color.darker();
        Color bright = color.brighter();
        GradientPaint gp = new GradientPaint(x, y, bright, x, y + h,
                new Color((int)(c.getRed() * ratio), (int)(c.getGreen() * ratio),
                        (int)(c.getBlue() * ratio)));
        g2.setPaint(gp);
        g2.fillRoundRect(x, y, w, h, 6, 6);

        // border
        g2.setColor(new Color(255, 255, 255, 60));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(x, y, w, h, 6, 6);

        // HP indicator dots for multi-hit bricks
        if (maxHp > 1) {
            g2.setColor(new Color(255, 255, 255, 180));
            for (int i = 0; i < hp; i++)
                g2.fillOval(x + 4 + i * 9, y + h / 2 - 3, 6, 6);
        }
    }
}

// ───────────────────────────────────────────────────────────────
//  POWER-UP
// ───────────────────────────────────────────────────────────────
class PowerUp {
    enum Type { WIDE_PADDLE, EXTRA_LIFE, MULTI_BALL, FAST_BALL, SLOW_BALL }

    int x, y; final int W = 24, H = 24;
    Type type;

    static final Color[] COLORS = {
            new Color(0, 200, 255),   // WIDE_PADDLE
            new Color(0, 255, 100),   // EXTRA_LIFE
            new Color(255, 100, 255), // MULTI_BALL
            new Color(255, 80, 80),   // FAST_BALL
            new Color(100, 100, 255)  // SLOW_BALL
    };
    static final String[] LABELS = { "W", "+", "M", ">>", "<<" };

    PowerUp(int x, int y, Type type) {
        this.x = x; this.y = y; this.type = type;
    }

    Rectangle rect() { return new Rectangle(x, y, W, H); }

    void draw(Graphics2D g2) {
        Color c = COLORS[type.ordinal()];
        g2.setColor(c);
        g2.fillRoundRect(x, y, W, H, 8, 8);
        g2.setColor(Color.BLACK);
        g2.setFont(new Font("Arial", Font.BOLD, 11));
        String lbl = LABELS[type.ordinal()];
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(lbl, x + (W - fm.stringWidth(lbl)) / 2, y + H - 7);
    }
}