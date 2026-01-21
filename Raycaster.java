import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;

/**
 * Raycaster Engine (Wolfenstein 3D Style)
 * * Logic:
 * 1. World: A 2D integer array where 0 is empty space and >0 are walls.
 * 2. Raycasting: For every vertical column of pixels on screen, we cast a ray
 * from the player.
 * 3. DDA Algorithm: The ray steps through the 2D grid square by square until it hits a wall.
 * 4. Projection: The distance to the wall determines the height of the vertical line drawn.
 * * Key Concepts:
 * - FOV (Field of View): Determined by the 'plane' vector.
 * - Fisheye Correction: Using perpendicular distance instead of Euclidean distance.
 */
public class Raycaster extends JFrame implements Runnable, KeyListener {

    // --- Constants ---
    private static final int WIDTH = 640;
    private static final int HEIGHT = 480;
    private static final int TEXTURE_WIDTH = 64;
    private static final int TEXTURE_HEIGHT = 64;

    // --- Game State ---
    private Thread thread;
    private boolean running;
    private BufferedImage image;
    private int[] pixels;

    // --- Player Vectors ---
    // Position
    private double posX = 22.0, posY = 12.0;
    // Direction vector (initially pointing West)
    private double dirX = -1.0, dirY = 0.0;
    // Camera Plane (determines FOV, perpendicular to direction)
    private double planeX = 0.0, planeY = 0.66;

    // --- Movement Speed ---
    private double moveSpeed = 0.05;
    private double rotSpeed = 0.03;

    // --- Input State ---
    private boolean keyLeft, keyRight, keyUp, keyDown;

    // --- Map (1 = Wall, 0 = Empty) ---
    // 24x24 grid
    private int[][] worldMap = {
            {1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1},
            {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
            {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
            {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
            {1,0,0,0,0,0,2,2,2,2,2,0,0,0,0,3,0,3,0,3,0,0,0,1},
            {1,0,0,0,0,0,2,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,1},
            {1,0,0,0,0,0,2,0,0,0,2,0,0,0,0,3,0,0,0,3,0,0,0,1},
            {1,0,0,0,0,0,2,0,0,0,2,0,0,0,0,0,0,0,0,0,0,0,0,1},
            {1,0,0,0,0,0,2,2,0,2,2,0,0,0,0,3,0,3,0,3,0,0,0,1},
            {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
            {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
            {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
            {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
            {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
            {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
            {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
            {1,4,4,4,4,4,4,4,4,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
            {1,4,0,4,0,0,0,0,4,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
            {1,4,0,0,0,0,5,0,4,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
            {1,4,0,4,0,0,0,0,4,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
            {1,4,0,4,4,4,4,4,4,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
            {1,4,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
            {1,4,4,4,4,4,4,4,4,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
            {1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1}
    };

    public Raycaster() {
        thread = new Thread(this);
        image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

        setSize(WIDTH, HEIGHT);
        setResizable(false);
        setTitle("Raycaster (Java)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBackground(Color.BLACK);
        setLocationRelativeTo(null);
        setVisible(true);

        addKeyListener(this);
        start();
    }

    private synchronized void start() {
        running = true;
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        long lastTime = System.nanoTime();
        final double ns = 1000000000.0 / 60.0; // 60 updates per sec
        double delta = 0;

        while (running) {
            long now = System.nanoTime();
            delta += (now - lastTime) / ns;
            lastTime = now;

            // Update logic loop
            while (delta >= 1) {
                update();
                delta--;
            }
            // Render loop
            render();
        }
    }

    /**
     * Handle Movement and Rotation Math
     */
    private void update() {
        // MOVEMENT
        if (keyUp) {
            if (worldMap[(int)(posX + dirX * moveSpeed)][(int)posY] == 0)
                posX += dirX * moveSpeed;
            if (worldMap[(int)posX][(int)(posY + dirY * moveSpeed)] == 0)
                posY += dirY * moveSpeed;
        }
        if (keyDown) {
            if (worldMap[(int)(posX - dirX * moveSpeed)][(int)posY] == 0)
                posX -= dirX * moveSpeed;
            if (worldMap[(int)posX][(int)(posY - dirY * moveSpeed)] == 0)
                posY -= dirY * moveSpeed;
        }

        // ROTATION (Rotation Matrix)
        if (keyRight) {
            double oldDirX = dirX;
            dirX = dirX * Math.cos(-rotSpeed) - dirY * Math.sin(-rotSpeed);
            dirY = oldDirX * Math.sin(-rotSpeed) + dirY * Math.cos(-rotSpeed);
            double oldPlaneX = planeX;
            planeX = planeX * Math.cos(-rotSpeed) - planeY * Math.sin(-rotSpeed);
            planeY = oldPlaneX * Math.sin(-rotSpeed) + planeY * Math.cos(-rotSpeed);
        }
        if (keyLeft) {
            double oldDirX = dirX;
            dirX = dirX * Math.cos(rotSpeed) - dirY * Math.sin(rotSpeed);
            dirY = oldDirX * Math.sin(rotSpeed) + dirY * Math.cos(rotSpeed);
            double oldPlaneX = planeX;
            planeX = planeX * Math.cos(rotSpeed) - planeY * Math.sin(rotSpeed);
            planeY = oldPlaneX * Math.sin(rotSpeed) + planeY * Math.cos(rotSpeed);
        }
    }

    /**
     * Core Rendering Loop: Raycasting & DDA
     */
    private void render() {
        BufferStrategy bs = getBufferStrategy();
        if (bs == null) {
            createBufferStrategy(3);
            return;
        }

        // Clear screen (Draw Floor and Ceiling)
        for(int i = 0; i < pixels.length / 2; i++) {
            pixels[i] = 0x333333; // Dark Grey Ceiling
        }
        for(int i = pixels.length / 2; i < pixels.length; i++) {
            pixels[i] = 0x555555; // Lighter Grey Floor
        }

        // --- RAYCASTING LOOP ---
        for (int x = 0; x < WIDTH; x++) {
            // 1. Calculate ray position and direction
            double cameraX = 2 * x / (double)WIDTH - 1; // x-coordinate in camera space
            double rayDirX = dirX + planeX * cameraX;
            double rayDirY = dirY + planeY * cameraX;

            // 2. Map position (which box of the map we're in)
            int mapX = (int)posX;
            int mapY = (int)posY;

            // 3. Length of ray from one x or y-side to next x or y-side
            double sideDistX;
            double sideDistY;

            // Delta distance calculation (avoid division by zero)
            double deltaDistX = (rayDirX == 0) ? 1e30 : Math.abs(1 / rayDirX);
            double deltaDistY = (rayDirY == 0) ? 1e30 : Math.abs(1 / rayDirY);
            double perpWallDist;

            // Step direction and initial sideDist
            int stepX;
            int stepY;
            int hit = 0; // Was there a wall hit?
            int side = 0; // Was a NS or a EW wall hit?

            if (rayDirX < 0) {
                stepX = -1;
                sideDistX = (posX - mapX) * deltaDistX;
            } else {
                stepX = 1;
                sideDistX = (mapX + 1.0 - posX) * deltaDistX;
            }
            if (rayDirY < 0) {
                stepY = -1;
                sideDistY = (posY - mapY) * deltaDistY;
            } else {
                stepY = 1;
                sideDistY = (mapY + 1.0 - posY) * deltaDistY;
            }

            // 4. Perform DDA (Digital Differential Analysis)
            // Jump to next map square, OR in x-direction, OR in y-direction
            while (hit == 0) {
                if (sideDistX < sideDistY) {
                    sideDistX += deltaDistX;
                    mapX += stepX;
                    side = 0;
                } else {
                    sideDistY += deltaDistY;
                    mapY += stepY;
                    side = 1;
                }
                // Check if ray has hit a wall
                if (worldMap[mapX][mapY] > 0) hit = 1;
            }

            // 5. Calculate distance projected on camera direction
            // (Euclidean distance will give fisheye effect!)
            if (side == 0)
                perpWallDist = (sideDistX - deltaDistX);
            else
                perpWallDist = (sideDistY - deltaDistY);

            // 6. Calculate height of line to draw on screen
            int lineHeight = (int) (HEIGHT / perpWallDist);

            // Calculate lowest and highest pixel to fill in current stripe
            int drawStart = -lineHeight / 2 + HEIGHT / 2;
            if (drawStart < 0) drawStart = 0;
            int drawEnd = lineHeight / 2 + HEIGHT / 2;
            if (drawEnd >= HEIGHT) drawEnd = HEIGHT - 1;

            // 7. Choose Wall Color
            int color;
            switch(worldMap[mapX][mapY]) {
                case 1:  color = 0xFF0000; break; // Red
                case 2:  color = 0x00FF00; break; // Green
                case 3:  color = 0x0000FF; break; // Blue
                case 4:  color = 0xFFFFFF; break; // White
                default: color = 0xFFFF00; break; // Yellow
            }

            // Give x and y sides different brightness for pseudo-lighting
            if (side == 1) {
                color = (color >> 1) & 8355711; // Dim the color by bit shifting
            }

            // 8. Draw the vertical line to the buffer
            for(int y = drawStart; y < drawEnd; y++) {
                pixels[x + y * WIDTH] = color;
            }
        }

        // Draw Buffer to Screen
        Graphics g = bs.getDrawGraphics();
        g.drawImage(image, 0, 0, getWidth(), getHeight(), null);

        // UI Text
        g.setColor(Color.WHITE);
        g.drawString("Use WASD or Arrows to move.", 20, 30);
        g.drawString("X: " + String.format("%.2f", posX) + " Y: " + String.format("%.2f", posY), 20, 50);

        g.dispose();
        bs.show();
    }

    // --- Input Handling ---
    public void keyTyped(KeyEvent e) {}

    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_LEFT || e.getKeyCode() == KeyEvent.VK_A) keyLeft = true;
        if (e.getKeyCode() == KeyEvent.VK_RIGHT || e.getKeyCode() == KeyEvent.VK_D) keyRight = true;
        if (e.getKeyCode() == KeyEvent.VK_UP || e.getKeyCode() == KeyEvent.VK_W) keyUp = true;
        if (e.getKeyCode() == KeyEvent.VK_DOWN || e.getKeyCode() == KeyEvent.VK_S) keyDown = true;
    }

    public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_LEFT || e.getKeyCode() == KeyEvent.VK_A) keyLeft = false;
        if (e.getKeyCode() == KeyEvent.VK_RIGHT || e.getKeyCode() == KeyEvent.VK_D) keyRight = false;
        if (e.getKeyCode() == KeyEvent.VK_UP || e.getKeyCode() == KeyEvent.VK_W) keyUp = false;
        if (e.getKeyCode() == KeyEvent.VK_DOWN || e.getKeyCode() == KeyEvent.VK_S) keyDown = false;
    }

    public static void main(String[] args) {
        new Raycaster();
    }
}