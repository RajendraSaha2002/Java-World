import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.List;

/**
 * 3D Software Renderer (Rasterizer)
 * * Concept:
 * - A "GPU" written in software.
 * - Manually transforms 3D vertices to 2D screen space.
 * - Rasterizes triangles pixel-by-pixel using Barycentric coordinates.
 * - Implements Z-Buffering to solve depth visibility.
 * * Key Features:
 * - Matrix Math: Rotation, Translation, Projection.
 * - Rasterization: Triangle filling algorithm.
 * - Z-Buffer: Depth testing to ensure solid objects look solid.
 * - Lighting: Simple directional lighting (Dot Product).
 */
public class SoftwareRenderer extends JPanel implements Runnable {

    // --- Configuration ---
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;

    // --- Render State ---
    private BufferedImage displayImage;
    private int[] pixels;
    private float[] zBuffer; // Depth buffer
    private boolean running = true;

    // --- 3D Scene ---
    private Mesh mesh;
    private float angle = 0;

    public static void main(String[] args) {
        JFrame frame = new JFrame("3D Software Renderer (Java)");
        SoftwareRenderer renderer = new SoftwareRenderer();

        frame.add(renderer);
        frame.setSize(WIDTH, HEIGHT);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.setVisible(true);

        new Thread(renderer).start();
    }

    public SoftwareRenderer() {
        // Setup Bitmap for fast pixel writing
        displayImage = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        pixels = ((DataBufferInt) displayImage.getRaster().getDataBuffer()).getData();
        zBuffer = new float[WIDTH * HEIGHT];

        // Create a "Spaceship" mesh procedurally
        mesh = new Mesh();
        // Body
        mesh.tris.addAll(createCube(0, 0, 0, 1, 1, 3).tris);
        // Wings
        mesh.tris.addAll(createCube(-1.5f, 0, 0.5f, 2, 0.1f, 1).tris);
        mesh.tris.addAll(createCube(1.5f, 0, 0.5f, 2, 0.1f, 1).tris);
        // Cockpit (Pyramid-ish)
        mesh.tris.add(new Triangle(new Vec3(-0.5f, 0.5f, 1), new Vec3(0.5f, 0.5f, 1), new Vec3(0, 1.0f, 0)));
        mesh.tris.add(new Triangle(new Vec3(0.5f, 0.5f, 1), new Vec3(0.5f, 0.5f, -1), new Vec3(0, 1.0f, 0)));
        mesh.tris.add(new Triangle(new Vec3(0.5f, 0.5f, -1), new Vec3(-0.5f, 0.5f, -1), new Vec3(0, 1.0f, 0)));
        mesh.tris.add(new Triangle(new Vec3(-0.5f, 0.5f, -1), new Vec3(-0.5f, 0.5f, 1), new Vec3(0, 1.0f, 0)));
    }

    @Override
    public void run() {
        long lastTime = System.nanoTime();

        while (running) {
            long now = System.nanoTime();
            // Limit to ~60 FPS
            if (now - lastTime < 16000000) {
                try { Thread.sleep(1); } catch (Exception e) {}
                continue;
            }
            lastTime = now;

            update();
            render();
            repaint();
        }
    }

    private void update() {
        angle += 0.02f;
    }

    // --- THE RENDER PIPELINE ---
    private void render() {
        // 1. Clear Buffers
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = 0xFF101015; // Dark Background
            zBuffer[i] = Float.MAX_VALUE; // Init Depth to infinity
        }

        // 2. Matrices
        // Model Rotation
        Matrix4 rotX = Matrix4.rotationX(angle * 0.5f);
        Matrix4 rotY = Matrix4.rotationY(angle);
        Matrix4 rotZ = Matrix4.rotationZ(angle * 0.3f);
        Matrix4 world = rotY.multiply(rotX).multiply(rotZ);

        // Translation (Move camera back)
        Matrix4 translation = Matrix4.translation(0, 0, 5.0f);

        // Projection (3D -> 2D)
        Matrix4 proj = Matrix4.perspective(90.0f, (float)WIDTH/HEIGHT, 0.1f, 100.0f);

        Vec3 lightDir = new Vec3(0, 0, -1).normalize(); // Light coming from camera

        // 3. Process Triangles
        for (Triangle tri : mesh.tris) {

            // A. Vertex Shader Stage (Transform Vertices)
            Vec3 v1 = world.multiply(tri.p1);
            Vec3 v2 = world.multiply(tri.p2);
            Vec3 v3 = world.multiply(tri.p3);

            // B. Calculate Normal & Lighting (Flat Shading)
            Vec3 line1 = v2.sub(v1);
            Vec3 line2 = v3.sub(v1);
            Vec3 normal = line1.cross(line2).normalize();

            // Backface Culling (Skip triangles facing away)
            Vec3 cameraRay = v1.sub(new Vec3(0,0,0)); // Camera is at 0,0,0 relative to object before translation
            if (normal.dot(cameraRay) > 0) continue;

            // Simple directional lighting
            float light = Math.max(0.1f, normal.dot(lightDir.scale(-1)));
            int color = getShadedColor(0xFFFFFF, light);

            // C. Project to Screen Space
            // 1. Apply translation (Camera View)
            v1.z += 5.0f; v2.z += 5.0f; v3.z += 5.0f;

            // 2. Apply Projection Matrix
            v1 = proj.multiplyProject(v1);
            v2 = proj.multiplyProject(v2);
            v3 = proj.multiplyProject(v3);

            // 3. Scale to Viewport (Screen Coordinates)
            v1.x = (v1.x + 1) * 0.5f * WIDTH; v1.y = (1 - (v1.y + 1) * 0.5f) * HEIGHT;
            v2.x = (v2.x + 1) * 0.5f * WIDTH; v2.y = (1 - (v2.y + 1) * 0.5f) * HEIGHT;
            v3.x = (v3.x + 1) * 0.5f * WIDTH; v3.y = (1 - (v3.y + 1) * 0.5f) * HEIGHT;

            // D. Rasterization Stage (Fill Triangle)
            drawTriangle(v1, v2, v3, color);
        }
    }

    /**
     * Rasterizes a triangle using Barycentric Coordinates and Z-Buffering.
     */
    private void drawTriangle(Vec3 v1, Vec3 v2, Vec3 v3, int color) {
        // Bounding Box
        int minX = (int) Math.max(0, Math.min(v1.x, Math.min(v2.x, v3.x)));
        int maxX = (int) Math.min(WIDTH - 1, Math.max(v1.x, Math.max(v2.x, v3.x)));
        int minY = (int) Math.max(0, Math.min(v1.y, Math.min(v2.y, v3.y)));
        int maxY = (int) Math.min(HEIGHT - 1, Math.max(v1.y, Math.max(v2.y, v3.y)));

        // Precompute triangle area term for barycentric math
        float area = (v2.y - v3.y) * (v1.x - v3.x) + (v3.x - v2.x) * (v1.y - v3.y);

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {

                // Calculate Barycentric Coordinates (w1, w2, w3)
                // These weights tell us how close pixel (x,y) is to v1, v2, v3
                float w1 = ((v2.y - v3.y) * (x - v3.x) + (v3.x - v2.x) * (y - v3.y)) / area;
                float w2 = ((v3.y - v1.y) * (x - v3.x) + (v1.x - v3.x) * (y - v3.y)) / area;
                float w3 = 1.0f - w1 - w2;

                // Check if point is inside triangle
                if (w1 >= 0 && w2 >= 0 && w3 >= 0) {
                    // Interpolate Depth (Z)
                    float depth = w1 * v1.z + w2 * v2.z + w3 * v3.z;
                    int idx = x + y * WIDTH;

                    // Z-Buffer Test: Only draw if this pixel is closer than what's already there
                    if (depth < zBuffer[idx]) {
                        zBuffer[idx] = depth;
                        pixels[idx] = color;
                    }
                }
            }
        }
    }

    private int getShadedColor(int hexColor, float intensity) {
        int r = (int)(((hexColor >> 16) & 0xFF) * intensity);
        int g = (int)(((hexColor >> 8) & 0xFF) * intensity);
        int b = (int)((hexColor & 0xFF) * intensity);
        return (r << 16) | (g << 8) | b;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(displayImage, 0, 0, null);

        g.setColor(Color.WHITE);
        g.drawString("Software Renderer: Pure Java Math", 10, 20);
        g.drawString("Vertices: " + mesh.tris.size() * 3, 10, 35);
    }

    // --- MATH HELPERS ---

    static class Vec3 {
        float x, y, z;
        float w = 1; // 4th component for matrix math

        Vec3(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }

        Vec3 sub(Vec3 v) { return new Vec3(x - v.x, y - v.y, z - v.z); }
        Vec3 add(Vec3 v) { return new Vec3(x + v.x, y + v.y, z + v.z); }
        Vec3 scale(float s) { return new Vec3(x * s, y * s, z * s); }

        float dot(Vec3 v) { return x * v.x + y * v.y + z * v.z; }

        Vec3 cross(Vec3 v) {
            return new Vec3(y * v.z - z * v.y, z * v.x - x * v.z, x * v.y - y * v.x);
        }

        Vec3 normalize() {
            float len = (float) Math.sqrt(x*x + y*y + z*z);
            if(len != 0) return new Vec3(x/len, y/len, z/len);
            return this;
        }
    }

    static class Triangle {
        Vec3 p1, p2, p3;
        Triangle(Vec3 p1, Vec3 p2, Vec3 p3) { this.p1 = p1; this.p2 = p2; this.p3 = p3; }
    }

    static class Mesh {
        List<Triangle> tris = new ArrayList<>();
    }

    static class Matrix4 {
        float[][] m = new float[4][4];

        // Transforms a vector (Rotation/Translation)
        Vec3 multiply(Vec3 i) {
            Vec3 o = new Vec3(0,0,0);
            o.x = i.x * m[0][0] + i.y * m[1][0] + i.z * m[2][0] + m[3][0];
            o.y = i.x * m[0][1] + i.y * m[1][1] + i.z * m[2][1] + m[3][1];
            o.z = i.x * m[0][2] + i.y * m[1][2] + i.z * m[2][2] + m[3][2];
            float w = i.x * m[0][3] + i.y * m[1][3] + i.z * m[2][3] + m[3][3];
            if (w != 0) o.w = w;
            return o;
        }

        // Projects a vector (Perspective Division)
        Vec3 multiplyProject(Vec3 i) {
            Vec3 o = multiply(i);
            if (o.w != 0) {
                o.x /= o.w; o.y /= o.w; o.z /= o.w;
            }
            return o;
        }

        Matrix4 multiply(Matrix4 other) {
            Matrix4 res = new Matrix4();
            for (int c = 0; c < 4; c++) {
                for (int r = 0; r < 4; r++) {
                    res.m[r][c] = m[r][0] * other.m[0][c] + m[r][1] * other.m[1][c] +
                            m[r][2] * other.m[2][c] + m[r][3] * other.m[3][c];
                }
            }
            return res;
        }

        static Matrix4 rotationX(float angle) {
            Matrix4 mat = new Matrix4();
            mat.m[0][0] = 1;
            mat.m[1][1] = (float)Math.cos(angle); mat.m[1][2] = (float)Math.sin(angle);
            mat.m[2][1] = -(float)Math.sin(angle); mat.m[2][2] = (float)Math.cos(angle);
            mat.m[3][3] = 1;
            return mat;
        }

        static Matrix4 rotationY(float angle) {
            Matrix4 mat = new Matrix4();
            mat.m[0][0] = (float)Math.cos(angle); mat.m[0][2] = (float)Math.sin(angle); // Fixed Y Rotation signs
            mat.m[1][1] = 1;
            mat.m[2][0] = -(float)Math.sin(angle); mat.m[2][2] = (float)Math.cos(angle);
            mat.m[3][3] = 1;
            return mat;
        }

        static Matrix4 rotationZ(float angle) {
            Matrix4 mat = new Matrix4();
            mat.m[0][0] = (float)Math.cos(angle); mat.m[0][1] = (float)Math.sin(angle);
            mat.m[1][0] = -(float)Math.sin(angle); mat.m[1][1] = (float)Math.cos(angle);
            mat.m[2][2] = 1;
            mat.m[3][3] = 1;
            return mat;
        }

        static Matrix4 translation(float x, float y, float z) {
            Matrix4 mat = new Matrix4();
            mat.m[0][0] = 1; mat.m[1][1] = 1; mat.m[2][2] = 1; mat.m[3][3] = 1;
            mat.m[3][0] = x; mat.m[3][1] = y; mat.m[3][2] = z;
            return mat;
        }

        static Matrix4 perspective(float fov, float aspectRatio, float near, float far) {
            Matrix4 mat = new Matrix4();
            float fovRad = 1.0f / (float)Math.tan(Math.toRadians(fov * 0.5f));
            mat.m[0][0] = aspectRatio * fovRad;
            mat.m[1][1] = fovRad;
            mat.m[2][2] = far / (far - near);
            mat.m[3][2] = (-far * near) / (far - near);
            mat.m[2][3] = 1.0f;
            mat.m[3][3] = 0.0f;
            return mat;
        }
    }

    private Mesh createCube(float x, float y, float z, float w, float h, float d) {
        Mesh m = new Mesh();
        float hw = w/2, hh = h/2, hd = d/2;
        // Vertices
        Vec3[] v = {
                new Vec3(x-hw, y-hh, z-hd), new Vec3(x+hw, y-hh, z-hd), new Vec3(x+hw, y+hh, z-hd), new Vec3(x-hw, y+hh, z-hd), // Front
                new Vec3(x-hw, y-hh, z+hd), new Vec3(x+hw, y-hh, z+hd), new Vec3(x+hw, y+hh, z+hd), new Vec3(x-hw, y+hh, z+hd)  // Back
        };
        // Indices (Triangles)
        int[][] idx = {
                {0,1,2}, {0,2,3}, // Front
                {5,4,7}, {5,7,6}, // Back
                {4,0,3}, {4,3,7}, // Left
                {1,5,6}, {1,6,2}, // Right
                {3,2,6}, {3,6,7}, // Top
                {4,5,1}, {4,1,0}  // Bottom
        };
        for (int[] i : idx) m.tris.add(new Triangle(v[i[0]], v[i[1]], v[i[2]]));
        return m;
    }
}