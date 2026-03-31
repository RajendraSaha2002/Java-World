import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.sound.sampled.*;


public class BreakoutGame extends JFrame {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            BreakoutGame f = new BreakoutGame();
            f.setVisible(true);
        });
    }

    public BreakoutGame() {
        setTitle("Breakout – Advanced Edition");
        setSize(820, 650);
        setResizable(false);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        GamePanel gp = new GamePanel();
        add(gp);
        gp.requestFocusInWindow();
    }
}


//  SOUND ENGINE  (generates tones via PCM – no external files)

class SoundEngine {
    static void play(int hz, int ms, float vol) {
        new Thread(() -> {
            try {
                int sr = 44100;
                byte[] buf = new byte[sr * ms / 1000];
                for (int i = 0; i < buf.length; i++) {
                    double angle = 2 * Math.PI * i * hz / sr;
                    buf[i] = (byte)(Math.sin(angle) * 80);
                }
                AudioFormat fmt = new AudioFormat(sr, 8, 1, true, false);
                DataLine.Info info = new DataLine.Info(Clip.class, fmt);
                Clip clip = (Clip) AudioSystem.getLine(info);
                clip.open(fmt, buf, 0, buf.length);
                FloatControl fc = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                fc.setValue(Math.max(fc.getMinimum(), Math.min(fc.getMaximum(), vol)));
                clip.start();
            } catch (Exception ignored) {}
        }).start();
    }
    static void wall()    { play(300, 40,  -10f); }
    static void paddle()  { play(440, 50,  -8f);  }
    static void brick()   { play(600, 60,  -6f);  }
    static void powerUp() { play(880, 120, -5f);  }
    static void die()     { play(150, 300, -5f);  }
    static void levelUp() { play(1000,200, -5f);  }
    static void win()     { play(1200,400, -3f);  }
}


//  HIGH SCORE  (persisted to highscore.dat)

class HighScoreManager {
    static final String FILE = "highscore.dat";
    static int load() {
        try (DataInputStream in = new DataInputStream(new FileInputStream(FILE))) {
            return in.readInt();
        } catch (Exception e) { return 0; }
    }
    static void save(int score) {
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(FILE))) {
            out.writeInt(score);
        } catch (Exception ignored) {}
    }
}


//  PARTICLE  (brick explosion + sparkle effects)

class Particle {
    float x, y, vx, vy;
    float life, maxLife;
    Color color;
    int size;
    boolean spark; // sparkle vs. shard

    Particle(float x, float y, Color c, boolean spark) {
        this.x = x; this.y = y; this.spark = spark;
        Random r = new Random();
        float sp = spark ? 5 + r.nextFloat() * 4 : 2 + r.nextFloat() * 3;
        double a = r.nextDouble() * Math.PI * 2;
        vx = (float)(sp * Math.cos(a));
        vy = (float)(sp * Math.sin(a)) - (spark ? 2 : 0);
        maxLife = life = spark ? 30 + r.nextInt(20) : 20 + r.nextInt(20);
        size = spark ? 3 + r.nextInt(3) : 4 + r.nextInt(5);
        color = c;
    }

    boolean dead() { return life <= 0; }

    void update() {
        x += vx; y += vy;
        vy += 0.15f; // gravity
        vx *= 0.95f;
        life--;
    }

    void draw(Graphics2D g2) {
        float alpha = life / maxLife;
        Color c = new Color(color.getRed(), color.getGreen(), color.getBlue(),
                (int)(alpha * 220));
        g2.setColor(c);
        if (spark) {
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine((int)x, (int)y, (int)(x - vx * 2), (int)(y - vy * 2));
        } else {
            g2.fillRect((int)x, (int)y, (int)(size * alpha) + 1, (int)(size * alpha) + 1);
        }
    }
}


//  BALL TRAIL

class Trail {
    float x, y, life;
    Trail(float x, float y) { this.x = x; this.y = y; life = 12; }
    boolean dead() { return life <= 0; }
    void update() { life--; }
    void draw(Graphics2D g2, int d) {
        int alpha = (int)(life / 12f * 120);
        g2.setColor(new Color(255, 220, 50, alpha));
        int s = (int)(d * life / 12f);
        g2.fillOval((int)(x + (d - s) / 2f), (int)(y + (d - s) / 2f), s, s);
    }
}


//  GAME PANEL

class GamePanel extends JPanel {

    static final int W = 820, H = 640;
    enum State { START, PLAYING, PAUSED, LEVEL_CLEAR, GAME_OVER, WIN }
    State state = State.START;

    // ── objects
    Paddle paddle;
    List<Ball>    balls    = new ArrayList<>();
    List<Brick>   bricks   = new ArrayList<>();
    List<PowerUp> powerUps = new ArrayList<>();
    List<Particle>particles= new ArrayList<>();
    List<Trail>   trails   = new ArrayList<>();

    // ── stats
    int score = 0, lives = 3, level = 1;
    int highScore = HighScoreManager.load();
    static final int MAX_LEVELS = 4;

    // ── screen effects
    int shakeFrames = 0;
    int flashFrames = 0;
    Color flashColor = Color.WHITE;
    float shakeAmt = 0;

    // ── transition
    float overlayAlpha = 1f;
    boolean fadingIn = true;

    // ── animated BG stars
    float[][] stars = new float[80][3]; // x, y, brightness
    Random rng = new Random();

    // ── power-up active timers
    int widePaddleTimer = 0;
    int stickyTimer     = 0;
    boolean ballStuck   = false;
    int stuckOffsetX    = 0;

    // ── combo
    int combo = 0;
    int comboDisplayTimer = 0;

    // ── timer
    Timer timer;

    // ── input
    boolean leftPressed, rightPressed;

    // ── colors
    static final Color BG = new Color(8, 8, 22);

    //
    GamePanel() {
        setPreferredSize(new Dimension(W, H));
        setBackground(BG);
        setFocusable(true);
        setDoubleBuffered(true);

        // init stars
        for (float[] s : stars) { s[0] = rng.nextFloat()*W; s[1] = rng.nextFloat()*H; s[2] = rng.nextFloat(); }

        setupKeyBindings();
        initGame();

        timer = new Timer(16, e -> gameLoop());
        timer.start();
    }

    // ── init
    void initGame() {
        score = 0; lives = 3; level = 1; combo = 0;
        balls.clear(); powerUps.clear(); particles.clear(); trails.clear();
        paddle = new Paddle();
        widePaddleTimer = 0; stickyTimer = 0; ballStuck = false;
        resetBall();
        buildBricks();
        state = State.START;
        fadingIn = true; overlayAlpha = 1f;
    }

    void resetBall() {
        balls.clear();
        ballStuck = true;
        stuckOffsetX = 0;
        balls.add(new Ball(paddle.x + paddle.w/2 - 7, paddle.y - 16, 5 + level, -(5 + level)));
        combo = 0;
    }

    // ── brick layout
    void buildBricks() {
        bricks.clear();
        int cols = 10, rows = 4 + level;
        int bW = 68, bH = 22, xOff = 26, yOff = 65;
        Color[][] pal = {
                {new Color(255,80,80),  new Color(255,150,0), new Color(255,230,50),
                        new Color(80,210,120), new Color(80,160,255),new Color(200,80,255)},
                {new Color(255,50,120), new Color(255,100,0), new Color(200,255,50),
                        new Color(50,240,190), new Color(50,130,255),new Color(240,50,220)},
                {new Color(255,50,50),  new Color(255,130,0), new Color(255,255,50),
                        new Color(50,255,100), new Color(50,210,255),new Color(210,50,255)},
                {new Color(255,30,30),  new Color(255,100,0), new Color(240,255,30),
                        new Color(30,255,80),  new Color(30,200,255),new Color(255,30,255)}
        };
        Color[] p = pal[Math.min(level-1, 3)];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int hp = (r == 0) ? 3 : (r < 3) ? 2 : 1;
                // randomly place indestructible steel bricks on higher levels
                boolean steel = (level >= 3) && rng.nextInt(12) == 0;
                bricks.add(new Brick(xOff + c*(bW+4), yOff + r*(bH+4), bW, bH,
                        steel ? 99 : hp, p[r % p.length], steel));
            }
        }
    }


    //  GAME LOOP

    void gameLoop() {
        updateStars();
        if (state == State.PLAYING) {
            updatePaddle();
            updateBalls();
            updatePowerUps();
            updateParticles();
            updateTrails();
            updateTimers();
            checkWin();
        } else {
            updateParticles();
        }
        if (fadingIn && overlayAlpha > 0) overlayAlpha = Math.max(0, overlayAlpha - 0.04f);
        if (shakeFrames > 0) shakeFrames--;
        if (flashFrames > 0) flashFrames--;
        repaint();
    }

    void updateStars() {
        for (float[] s : stars) {
            s[2] += 0.02f;
            if (s[2] > 1f) s[2] = 0f;
        }
    }

    void updateTimers() {
        if (widePaddleTimer > 0) { widePaddleTimer--; if (widePaddleTimer == 0) paddle.w = Paddle.DEFAULT_W; }
        if (stickyTimer    > 0) { stickyTimer--;    if (stickyTimer    == 0) ballStuck = false; }
        if (comboDisplayTimer > 0) comboDisplayTimer--;
    }

    void updatePaddle() {
        if (leftPressed)  paddle.moveLeft();
        if (rightPressed) paddle.moveRight();
        // move stuck ball with paddle
        if (ballStuck && !balls.isEmpty()) {
            Ball b = balls.get(0);
            b.x = paddle.x + paddle.w/2 + stuckOffsetX - b.d/2;
            b.y = paddle.y - b.d - 2;
        }
    }

    void updateBalls() {
        if (ballStuck) return;
        Iterator<Ball> bi = balls.iterator();
        while (bi.hasNext()) {
            Ball b = bi.next();
            // trail
            trails.add(new Trail(b.x, b.y));
            b.move();

            // wall collisions
            if (b.x <= 0)       { b.x = 0;       b.dx = Math.abs(b.dx);  SoundEngine.wall(); }
            if (b.x+b.d >= W)   { b.x = W-b.d;   b.dx = -Math.abs(b.dx); SoundEngine.wall(); }
            if (b.y <= 40)      { b.y = 40;       b.dy = Math.abs(b.dy);  SoundEngine.wall(); }

            // paddle collision
            if (b.rect().intersects(paddle.rect()) && b.dy > 0) {
                b.dy = -Math.abs(b.dy);
                double hit = (b.x + b.d/2.0) - (paddle.x + paddle.w/2.0);
                b.dx = (int)(hit / 10.0);
                if (b.dx == 0) b.dx = 1;
                SoundEngine.paddle();
                // sticky paddle?
                if (stickyTimer > 0) {
                    ballStuck = true;
                    stuckOffsetX = b.x - paddle.x - paddle.w/2 + b.d/2;
                }
            }

            // brick collisions
            boolean hitBrick = false;
            for (Iterator<Brick> bri = bricks.iterator(); bri.hasNext() && !hitBrick; ) {
                Brick br = bri.next();
                if (b.rect().intersects(br.rect())) {
                    if (!br.steel) {
                        br.hit();
                        bounceOffBrick(b, br);
                        SoundEngine.brick();
                        if (br.destroyed) {
                            combo++;
                            comboDisplayTimer = 90;
                            int pts = br.maxHp * 10 * combo;
                            score += pts;
                            if (score > highScore) { highScore = score; HighScoreManager.save(highScore); }
                            spawnParticles(br);
                            maybeDropPowerUp(br);
                            bri.remove();
                            shakeFrames = 5; shakeAmt = 3;
                        } else {
                            shakeFrames = 3; shakeAmt = 2;
                            flashFrames = 4; flashColor = new Color(255,255,255,60);
                        }
                        hitBrick = true;
                    } else {
                        // steel: just bounce
                        bounceOffBrick(b, br);
                        SoundEngine.wall();
                        flashFrames = 3; flashColor = new Color(150,150,255,50);
                        hitBrick = true;
                    }
                }
            }

            if (b.y > H) bi.remove();
        }

        if (balls.isEmpty()) {
            combo = 0;
            SoundEngine.die();
            lives--;
            shakeFrames = 15; shakeAmt = 8;
            flashFrames = 12; flashColor = new Color(255,60,60,80);
            if (lives <= 0) {
                if (score > highScore) HighScoreManager.save(score);
                state = State.GAME_OVER;
            } else {
                resetBall();
            }
        }
    }

    void bounceOffBrick(Ball b, Brick br) {
        Rectangle2D i = b.rect().createIntersection(br.rect());
        if (i.getWidth() < i.getHeight()) b.dx = -b.dx;
        else                              b.dy = -b.dy;
    }

    void spawnParticles(Brick br) {
        for (int i = 0; i < 14; i++)
            particles.add(new Particle(br.x + br.w/2f, br.y + br.h/2f, br.color, false));
        for (int i = 0; i < 8; i++)
            particles.add(new Particle(br.x + br.w/2f, br.y + br.h/2f, Color.WHITE, true));
    }

    void updateParticles() { particles.removeIf(p -> { p.update(); return p.dead(); }); }
    void updateTrails()    { trails.removeIf(t   -> { t.update(); return t.dead(); }); }

    void updatePowerUps() {
        Iterator<PowerUp> pi = powerUps.iterator();
        while (pi.hasNext()) {
            PowerUp p = pi.next();
            p.y += 2;
            if (p.rect().intersects(paddle.rect())) {
                applyPowerUp(p.type); SoundEngine.powerUp();
                // sparkle at paddle
                for (int i = 0; i < 12; i++)
                    particles.add(new Particle(p.x+12, p.y+12, PowerUp.COLORS[p.type.ordinal()], true));
                pi.remove();
            } else if (p.y > H) { pi.remove(); }
        }
    }

    void maybeDropPowerUp(Brick br) {
        if (rng.nextInt(4) == 0) {
            powerUps.add(new PowerUp(br.x + br.w/2 - 12, br.y,
                    PowerUp.Type.values()[rng.nextInt(PowerUp.Type.values().length)]));
        }
    }

    void applyPowerUp(PowerUp.Type t) {
        switch (t) {
            case WIDE_PADDLE:
                paddle.w = Math.min(paddle.w + 40, 220);
                widePaddleTimer = 600; break;
            case NARROW_PADDLE:
                paddle.w = Math.max(paddle.w - 30, 50);
                widePaddleTimer = 300; break;
            case EXTRA_LIFE:
                lives = Math.min(lives + 1, 6); break;
            case MULTI_BALL:
                List<Ball> extras = new ArrayList<>();
                for (Ball b : balls) {
                    extras.add(new Ball(b.x, b.y, -b.dx + 1, b.dy));
                    extras.add(new Ball(b.x, b.y,  b.dx + 1, b.dy - 1));
                }
                balls.addAll(extras); break;
            case FAST_BALL:
                for (Ball b : balls) { b.dx = (int)(b.dx*1.4); b.dy = (int)(b.dy*1.4); } break;
            case SLOW_BALL:
                for (Ball b : balls) { b.dx = (int)(b.dx*0.7); b.dy = (int)(b.dy*0.7);
                    if (Math.abs(b.dy) < 2) b.dy = b.dy < 0 ? -2 : 2; } break;
            case STICKY:
                stickyTimer = 300; break;
            case FIREBALL:
                for (Ball b : balls) b.fire = true;
                new Timer(8000, e -> { for (Ball b : balls) b.fire = false; }).start(); break;
        }
    }

    void checkWin() {
        boolean anyNormal = false;
        for (Brick br : bricks) if (!br.steel) { anyNormal = true; break; }
        if (!anyNormal) {
            SoundEngine.levelUp();
            if (level >= MAX_LEVELS) { SoundEngine.win(); state = State.WIN; }
            else { level++; state = State.LEVEL_CLEAR; fadingIn = true; overlayAlpha = 1f; }
        }
    }

    void nextLevel() {
        powerUps.clear(); particles.clear(); trails.clear();
        widePaddleTimer = 0; stickyTimer = 0;
        paddle = new Paddle();
        resetBall();
        buildBricks();
        state = State.PLAYING;
    }


    //  RENDERING

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // offscreen buffer for screenshake
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // background
        g2.setColor(BG);
        g2.fillRect(0, 0, W, H);
        drawStars(g2);

        // game objects
        trails.forEach(t -> { if (!balls.isEmpty()) t.draw(g2, balls.get(0).d); });
        bricks.forEach(b -> b.draw(g2));
        powerUps.forEach(p -> p.draw(g2));
        particles.forEach(p -> p.draw(g2));
        paddle.draw(g2, widePaddleTimer > 0, stickyTimer > 0);
        balls.forEach(b -> b.draw(g2));

        // divider line
        g2.setColor(new Color(255,255,255,30));
        g2.setStroke(new BasicStroke(1));
        g2.drawLine(0, 40, W, 40);

        drawHUD(g2);

        // flash overlay
        if (flashFrames > 0) {
            g2.setColor(flashColor);
            g2.fillRect(0, 0, W, H);
        }

        // state overlays
        switch (state) {
            case START:
                drawStartScreen(g2); break;
            case PAUSED:
                drawOverlay(g2, "⏸  PAUSED", "Press P to Resume", ""); break;
            case LEVEL_CLEAR:
                drawOverlay(g2, "✔  LEVEL " + (level-1) + " CLEAR!",
                        "Press SPACE for Level " + level, "Keep going!"); break;
            case GAME_OVER:
                drawOverlay(g2, "GAME OVER",
                        "Score: " + score + "   High: " + highScore,
                        "Press SPACE to Restart"); break;
            case WIN:
                drawOverlay(g2, "★  YOU WIN!",
                        "Score: " + score + "   High: " + highScore,
                        "Press SPACE to Play Again"); break;
            default: break;
        }

        // fade-in overlay
        if (overlayAlpha > 0) {
            g2.setColor(new Color(0,0,0,(int)(overlayAlpha*255)));
            g2.fillRect(0,0,W,H);
        }

        g2.dispose();

        // apply screenshake
        Graphics2D g3 = (Graphics2D) g;
        int sx = 0, sy = 0;
        if (shakeFrames > 0) {
            sx = (int)((rng.nextFloat()-0.5f) * shakeAmt * 2);
            sy = (int)((rng.nextFloat()-0.5f) * shakeAmt * 2);
        }
        g3.drawImage(img, sx, sy, null);
    }

    void drawStars(Graphics2D g2) {
        for (float[] s : stars) {
            float br = (float)(0.5 + 0.5 * Math.sin(s[2] * Math.PI * 2));
            int alpha = (int)(br * 180 + 40);
            g2.setColor(new Color(200, 200, 255, Math.min(255, alpha)));
            int sz = br > 0.8f ? 2 : 1;
            g2.fillOval((int)s[0], (int)s[1], sz, sz);
        }
    }

    void drawHUD(Graphics2D g2) {
        g2.setFont(new Font("Monospaced", Font.BOLD, 14));
        // score
        g2.setColor(new Color(200,200,255)); g2.drawString("SCORE " + score,  10, 28);
        // high score
        g2.setColor(new Color(255,215,0));   g2.drawString("BEST  " + highScore, 160, 28);
        // level
        g2.setColor(new Color(100,255,200)); g2.drawString("LVL " + level, 360, 28);
        // lives
        g2.setColor(new Color(255,100,120));
        g2.drawString("LIVES", 520, 28);
        for (int i = 0; i < lives; i++) {
            g2.setColor(new Color(255, 80 + i*20, 80));
            g2.fillOval(590 + i * 20, 14, 13, 13);
        }
        // active power indicator
        if (widePaddleTimer > 0) drawPill(g2, "WIDE",  680, 14, new Color(0,200,255));
        if (stickyTimer     > 0) drawPill(g2, "STICKY",740, 14, new Color(255,180,0));

        // combo
        if (comboDisplayTimer > 0 && combo > 1) {
            float a = Math.min(1f, comboDisplayTimer / 30f);
            g2.setColor(new Color(255, 200, 0, (int)(a*255)));
            g2.setFont(new Font("Arial", Font.BOLD, 22));
            g2.drawString("COMBO x" + combo, W/2 - 55, H - 20);
        }
    }

    void drawPill(Graphics2D g2, String txt, int x, int y, Color c) {
        g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 160));
        g2.fillRoundRect(x, y, 70, 16, 8, 8);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 10));
        g2.drawString(txt, x + 5, y + 12);
    }

    void drawStartScreen(Graphics2D g2) {
        // dim
        g2.setColor(new Color(0,0,0,160));
        g2.fillRect(0, 0, W, H);
        // title glow
        for (int i = 8; i > 0; i--) {
            g2.setColor(new Color(0,150,255, 10 + i*4));
            g2.setFont(new Font("Arial Black", Font.BOLD, 52 + i));
            g2.drawString("BREAKOUT", W/2 - 175 - i/2, 240 + i/2);
        }
        g2.setColor(new Color(100, 200, 255));
        g2.setFont(new Font("Arial Black", Font.BOLD, 52));
        g2.drawString("BREAKOUT", W/2 - 175, 240);

        g2.setColor(new Color(255,215,0));
        g2.setFont(new Font("Arial", Font.BOLD, 18));
        g2.drawString("ADVANCED EDITION", W/2 - 100, 275);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.PLAIN, 16));
        g2.drawString("Press  SPACE  to Start", W/2 - 95, 330);

        g2.setColor(new Color(180,180,255));
        g2.setFont(new Font("Monospaced", Font.PLAIN, 13));
        g2.drawString("← →  Move Paddle        SPACE  Launch Ball", W/2 - 165, 380);
        g2.drawString("P  Pause / Resume        ESC  Quit",          W/2 - 165, 400);

        // power-up legend
        g2.setColor(new Color(255,215,0));
        g2.setFont(new Font("Arial", Font.BOLD, 13));
        g2.drawString("POWER-UPS:", W/2 - 200, 440);
        String[] names = {"WIDE","NARROW","+LIFE","MULTI","FAST","SLOW","STICKY","FIRE"};
        for (int i = 0; i < names.length; i++) {
            g2.setColor(PowerUp.COLORS[i]);
            g2.fillRoundRect(W/2 - 200 + i*100, 450, 28, 22, 6, 6);
            g2.setColor(Color.BLACK);
            g2.setFont(new Font("Arial", Font.BOLD, 9));
            g2.drawString(names[i], W/2 - 198 + i*100, 465);
        }

        // high score
        g2.setColor(new Color(255,215,0));
        g2.setFont(new Font("Monospaced", Font.BOLD, 14));
        g2.drawString("HIGH SCORE: " + highScore, W/2 - 80, 510);
    }

    void drawOverlay(Graphics2D g2, String title, String sub1, String sub2) {
        g2.setColor(new Color(0,0,0,170));
        g2.fillRoundRect(160, 210, 500, 220, 30, 30);
        g2.setColor(new Color(80,180,255,80));
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(160, 210, 500, 220, 30, 30);

        g2.setColor(new Color(100,210,255));
        g2.setFont(new Font("Arial Black", Font.BOLD, 38));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(title, (W - fm.stringWidth(title))/2, 275);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.PLAIN, 17));
        fm = g2.getFontMetrics();
        g2.drawString(sub1, (W - fm.stringWidth(sub1))/2, 318);
        g2.setColor(new Color(180,220,255));
        g2.drawString(sub2, (W - fm.stringWidth(sub2))/2, 348);
    }


    //  KEY BINDINGS

    void setupKeyBindings() {
        InputMap  im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT,  0, false), "lOn");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT,  0, true),  "lOff");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, false), "rOn");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, true),  "rOff");
        am.put("lOn",  new AbstractAction() { public void actionPerformed(ActionEvent e) { leftPressed  = true;  }});
        am.put("lOff", new AbstractAction() { public void actionPerformed(ActionEvent e) { leftPressed  = false; }});
        am.put("rOn",  new AbstractAction() { public void actionPerformed(ActionEvent e) { rightPressed = true;  }});
        am.put("rOff", new AbstractAction() { public void actionPerformed(ActionEvent e) { rightPressed = false; }});

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false), "space");
        am.put("space", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                switch (state) {
                    case START:       state = State.PLAYING; fadingIn = true; break;
                    case PLAYING:     if (ballStuck) { ballStuck = false; } break;
                    case LEVEL_CLEAR: nextLevel(); break;
                    case GAME_OVER:
                    case WIN:         initGame(); state = State.PLAYING; fadingIn = true; break;
                    default: break;
                }
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_P, 0, false), "pause");
        am.put("pause", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if      (state == State.PLAYING) state = State.PAUSED;
                else if (state == State.PAUSED)  state = State.PLAYING;
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "quit");
        am.put("quit", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { System.exit(0); }
        });
    }
}


//  PADDLE

class Paddle {
    static final int DEFAULT_W = 110, H = 14, SPEED = 8;
    int x, y, w;

    Paddle() { w = DEFAULT_W; x = (GamePanel.W - w)/2; y = GamePanel.H - 45; }

    void moveLeft()  { x = Math.max(0, x - SPEED); }
    void moveRight() { x = Math.min(GamePanel.W - w, x + SPEED); }

    Rectangle rect() { return new Rectangle(x, y, w, H); }

    void draw(Graphics2D g2, boolean wide, boolean sticky) {
        Color top = wide   ? new Color(0,230,255)   :
                sticky ? new Color(255,200,50)   : new Color(120,190,255);
        Color bot = wide   ? new Color(0,100,200)   :
                sticky ? new Color(200,120,0)    : new Color(40,110,210);
        GradientPaint gp = new GradientPaint(x, y, top, x, y+H, bot);
        g2.setPaint(gp);
        g2.fillRoundRect(x, y, w, H, 10, 10);
        g2.setColor(new Color(255,255,255,80));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(x, y, w, H, 10, 10);
        // shine
        g2.setColor(new Color(255,255,255,50));
        g2.fillRoundRect(x+4, y+2, w-8, 4, 4, 4);
    }
}


//  BALL

class Ball {
    int x, y, d = 14;
    int dx, dy;
    boolean fire = false;

    Ball(int x, int y, int dx, int dy) { this.x=x; this.y=y; this.dx=dx; this.dy=dy; }

    void move() { x += dx; y += dy; }

    Rectangle rect() { return new Rectangle(x, y, d, d); }

    void draw(Graphics2D g2) {
        // glow
        Color gc = fire ? new Color(255,100,0,40) : new Color(255,255,100,40);
        g2.setColor(gc);
        g2.fillOval(x-6, y-6, d+12, d+12);
        // body
        Color c1 = fire ? new Color(255,180,0)  : new Color(255,255,180);
        Color c2 = fire ? new Color(255,50,0)   : new Color(255,180,30);
        RadialGradientPaint rg = new RadialGradientPaint(
                new Point2D.Float(x + d/3f, y + d/3f), d,
                new float[]{0f, 1f}, new Color[]{c1, c2});
        g2.setPaint(rg);
        g2.fillOval(x, y, d, d);
        // shine
        g2.setColor(new Color(255,255,255,120));
        g2.fillOval(x+2, y+2, 5, 5);
    }
}


//  BRICK

class Brick {
    int x, y, w, h, hp, maxHp;
    Color color;
    boolean destroyed = false;
    boolean steel;

    Brick(int x, int y, int w, int h, int hp, Color color, boolean steel) {
        this.x=x; this.y=y; this.w=w; this.h=h;
        this.hp=hp; this.maxHp=hp; this.color=color; this.steel=steel;
    }

    void hit() { if (!steel && --hp <= 0) destroyed = true; }

    Rectangle rect() { return new Rectangle(x, y, w, h); }

    void draw(Graphics2D g2) {
        float ratio = steel ? 1f : (float)hp / maxHp;
        Color bright = steel ? new Color(180,190,200) : color.brighter();
        Color dark   = steel ? new Color(80,90,100)   :
                new Color((int)(color.getRed()*ratio*0.6f),
                        (int)(color.getGreen()*ratio*0.6f),
                        (int)(color.getBlue()*ratio*0.6f));
        GradientPaint gp = new GradientPaint(x, y, bright, x, y+h, dark);
        g2.setPaint(gp);
        g2.fillRoundRect(x, y, w, h, 6, 6);

        g2.setColor(new Color(255,255,255, steel ? 80 : 60));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(x, y, w, h, 6, 6);

        if (steel) {
            // steel cross pattern
            g2.setColor(new Color(200,210,220,100));
            g2.drawLine(x+w/2, y+2, x+w/2, y+h-2);
            g2.drawLine(x+2, y+h/2, x+w-2, y+h/2);
        } else if (maxHp > 1) {
            g2.setColor(new Color(255,255,255,160));
            for (int i = 0; i < hp; i++)
                g2.fillOval(x+4+i*10, y+h/2-3, 6, 6);
        }
    }
}


//  POWER-UP

class PowerUp {
    enum Type { WIDE_PADDLE, NARROW_PADDLE, EXTRA_LIFE, MULTI_BALL,
        FAST_BALL, SLOW_BALL, STICKY, FIREBALL }

    int x, y; final int W = 28, H = 22;
    Type type;

    static final Color[] COLORS = {
            new Color(0,200,255),   // WIDE
            new Color(255,80,80),   // NARROW
            new Color(0,255,120),   // LIFE
            new Color(255,100,255), // MULTI
            new Color(255,60,60),   // FAST
            new Color(80,120,255),  // SLOW
            new Color(255,200,0),   // STICKY
            new Color(255,130,0)    // FIREBALL
    };
    static final String[] LABELS = {"WIDE","NRW","+1","MLT","FST","SLW","STK","FIRE"};

    float bobTimer = (float)(Math.random() * Math.PI * 2);

    PowerUp(int x, int y, Type type) { this.x=x; this.y=y; this.type=type; }

    Rectangle rect() { return new Rectangle(x, y, W, H); }

    void draw(Graphics2D g2) {
        bobTimer += 0.1f;
        int bob = (int)(Math.sin(bobTimer) * 2);
        Color c = COLORS[type.ordinal()];
        GradientPaint gp = new GradientPaint(x, y+bob, c.brighter(), x, y+H+bob, c.darker());
        g2.setPaint(gp);
        g2.fillRoundRect(x, y+bob, W, H, 8, 8);
        g2.setColor(new Color(255,255,255,120));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(x, y+bob, W, H, 8, 8);
        g2.setColor(Color.BLACK);
        g2.setFont(new Font("Arial", Font.BOLD, 9));
        FontMetrics fm = g2.getFontMetrics();
        String lbl = LABELS[type.ordinal()];
        g2.drawString(lbl, x + (W - fm.stringWidth(lbl))/2, y + H - 6 + bob);
    }
}