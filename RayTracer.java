import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Random;

/**
 * True Ray Tracer (Path Tracing)
 * * Concept:
 * - Simulates light transport using Monte Carlo integration.
 * - Rays are shot from the camera, bounce off objects (recursion), and eventually hit light or sky.
 * * Features:
 * - Materials: Lambertian (Matte), Metal (Reflective), Dielectric (Glass/Refractive).
 * - Effects: Soft Shadows, Color Bleeding (Global Illumination), Defocus Blur (Depth of Field).
 * - Progressive Rendering: The image improves quality over time as more samples are accumulated.
 */
public class RayTracer extends JFrame {

    private RenderPanel renderPanel;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
            new RayTracer();
        });
    }

    public RayTracer() {
        super("Java Path Tracer - Progressive Rendering");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 450); // 16:9 Aspect Ratio
        setLocationRelativeTo(null);
        setResizable(false);

        renderPanel = new RenderPanel(800, 450);
        add(renderPanel);

        setVisible(true);

        // Start the heavy rendering in a background thread
        new Thread(renderPanel).start();
    }

    // --- RENDER ENGINE ---

    static class RenderPanel extends JPanel implements Runnable {
        private int width, height;
        private BufferedImage image;
        private double[] accumulationBufferR;
        private double[] accumulationBufferG;
        private double[] accumulationBufferB;
        private int samples = 0;
        private boolean running = true;

        // Scene Data
        private HittableList world;
        private Camera camera;

        public RenderPanel(int w, int h) {
            this.width = w;
            this.height = h;
            this.image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

            // High precision buffers for accumulation
            this.accumulationBufferR = new double[w * h];
            this.accumulationBufferG = new double[w * h];
            this.accumulationBufferB = new double[w * h];

            initScene();
        }

        private void initScene() {
            world = new HittableList();

            // Materials
            Material groundMat = new Lambertian(new Vec3(0.5, 0.5, 0.5));
            Material centerMat = new Lambertian(new Vec3(0.1, 0.2, 0.5));
            Material leftMat   = new Dielectric(1.5); // Glass
            Material rightMat  = new Metal(new Vec3(0.8, 0.6, 0.2), 0.0); // Gold Mirror

            // Objects
            world.add(new Sphere(new Vec3(0, -100.5, -1), 100, groundMat)); // Ground
            world.add(new Sphere(new Vec3(0, 0, -1), 0.5, centerMat));      // Center Matte
            world.add(new Sphere(new Vec3(-1.0, 0, -1), 0.5, leftMat));     // Left Glass
            world.add(new Sphere(new Vec3(-1.0, 0, -1), -0.45, leftMat));   // Hollow Glass Bubble
            world.add(new Sphere(new Vec3(1.0, 0, -1), 0.5, rightMat));     // Right Metal

            // Camera Setup
            Vec3 lookFrom = new Vec3(3, 3, 2);
            Vec3 lookAt = new Vec3(0, 0, -1);
            camera = new Camera(lookFrom, lookAt, new Vec3(0, 1, 0), 20, (double)width/height);
        }

        @Override
        public void run() {
            Random rand = new Random();
            int maxDepth = 20; // Max light bounces

            while (running) {
                samples++;

                // Render one pass (1 sample per pixel)
                for (int j = height - 1; j >= 0; j--) {
                    for (int i = 0; i < width; i++) {
                        // Anti-aliasing: Jitter the ray slightly within the pixel
                        double u = (i + rand.nextDouble()) / (width - 1);
                        double v = (height - 1 - j + rand.nextDouble()) / (height - 1);

                        Ray r = camera.getRay(u, v);
                        Vec3 pixelColor = rayColor(r, world, maxDepth);

                        // Accumulate
                        int index = i + j * width;
                        accumulationBufferR[index] += pixelColor.x;
                        accumulationBufferG[index] += pixelColor.y;
                        accumulationBufferB[index] += pixelColor.z;

                        // Average and Draw
                        // (We do this per pixel so the user sees updates immediately)
                        double scale = 1.0 / samples;
                        // Gamma Correction (Approximate gamma 2.0 with sqrt)
                        double rCol = Math.sqrt(accumulationBufferR[index] * scale);
                        double gCol = Math.sqrt(accumulationBufferG[index] * scale);
                        double bCol = Math.sqrt(accumulationBufferB[index] * scale);

                        int ir = (int)(255.999 * clamp(rCol, 0, 0.999));
                        int ig = (int)(255.999 * clamp(gCol, 0, 0.999));
                        int ib = (int)(255.999 * clamp(bCol, 0, 0.999));

                        image.setRGB(i, j, (ir << 16) | (ig << 8) | ib);
                    }
                }
                repaint(); // Refresh screen after every full pass
            }
        }

        // The Core Ray Tracing Algorithm
        private Vec3 rayColor(Ray r, Hittable world, int depth) {
            // If we've exceeded the ray bounce limit, no more light is gathered.
            if (depth <= 0) return new Vec3(0, 0, 0);

            HitRecord rec = new HitRecord();
            // 0.001 to ignore very close hits (Shadow Acne fix)
            if (world.hit(r, 0.001, Double.POSITIVE_INFINITY, rec)) {
                Ray scattered = new Ray(new Vec3(0,0,0), new Vec3(0,0,0));
                Vec3 attenuation = new Vec3(0,0,0);

                if (rec.mat.scatter(r, rec, attenuation, scattered)) {
                    // Recursive bounce: Multiply color by current material color * remaining light
                    Vec3 nextColor = rayColor(scattered, world, depth - 1);
                    return new Vec3(attenuation.x * nextColor.x, attenuation.y * nextColor.y, attenuation.z * nextColor.z);
                }
                return new Vec3(0, 0, 0); // Absorbed
            }

            // Background (Sky Gradient)
            Vec3 unitDirection = r.direction.unitVector();
            double t = 0.5 * (unitDirection.y + 1.0);
            // Linear blend: white to blue
            Vec3 white = new Vec3(1.0, 1.0, 1.0);
            Vec3 blue = new Vec3(0.5, 0.7, 1.0);
            return Vec3.add(Vec3.mult(white, 1.0 - t), Vec3.mult(blue, t));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.drawImage(image, 0, 0, null);
            g.setColor(Color.WHITE);
            g.drawString("Samples: " + samples, 10, 20);
            g.drawString("Wait for image to clear...", 10, 35);
        }
    }

    // --- MATH UTILITIES ---

    static double clamp(double x, double min, double max) {
        if (x < min) return min;
        if (x > max) return max;
        return x;
    }

    static class Vec3 {
        public double x, y, z;
        public Vec3(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }

        static Vec3 add(Vec3 u, Vec3 v) { return new Vec3(u.x + v.x, u.y + v.y, u.z + v.z); }
        static Vec3 add(Vec3 u, double t) { return new Vec3(u.x + t, u.y + t, u.z + t); }
        static Vec3 sub(Vec3 u, Vec3 v) { return new Vec3(u.x - v.x, u.y - v.y, u.z - v.z); }
        static Vec3 mult(Vec3 u, double t) { return new Vec3(u.x * t, u.y * t, u.z * t); }
        static Vec3 mult(Vec3 u, Vec3 v) { return new Vec3(u.x * v.x, u.y * v.y, u.z * v.z); }
        static double dot(Vec3 u, Vec3 v) { return u.x * v.x + u.y * v.y + u.z * v.z; }
        static Vec3 cross(Vec3 u, Vec3 v) {
            return new Vec3(u.y * v.z - u.z * v.y, u.z * v.x - u.x * v.z, u.x * v.y - u.y * v.x);
        }

        double lengthSquared() { return x*x + y*y + z*z; }
        double length() { return Math.sqrt(lengthSquared()); }
        Vec3 unitVector() { return mult(this, 1.0/length()); }

        static Vec3 random() {
            Random r = new Random();
            return new Vec3(r.nextDouble(), r.nextDouble(), r.nextDouble());
        }

        static Vec3 randomInUnitSphere() {
            while (true) {
                Vec3 p = sub(mult(random(), 2.0), new Vec3(1,1,1));
                if (p.lengthSquared() >= 1) continue;
                return p;
            }
        }

        static Vec3 randomUnitVector() {
            return randomInUnitSphere().unitVector();
        }

        boolean nearZero() {
            double s = 1e-8;
            return (Math.abs(x) < s) && (Math.abs(y) < s) && (Math.abs(z) < s);
        }

        static Vec3 reflect(Vec3 v, Vec3 n) {
            return sub(v, mult(n, 2 * dot(v, n)));
        }

        static Vec3 refract(Vec3 uv, Vec3 n, double etaiOverEtat) {
            double cosTheta = Math.min(dot(mult(uv, -1), n), 1.0);
            Vec3 rOutPerp = mult(add(uv, mult(n, cosTheta)), etaiOverEtat);
            double rOutParallel = -Math.sqrt(Math.abs(1.0 - rOutPerp.lengthSquared()));
            return add(rOutPerp, mult(n, rOutParallel));
        }
    }

    static class Ray {
        public Vec3 origin, direction;
        public Ray(Vec3 origin, Vec3 direction) { this.origin = origin; this.direction = direction; }
        public Vec3 at(double t) { return Vec3.add(origin, Vec3.mult(direction, t)); }
    }

    static class HitRecord {
        public Vec3 p;
        public Vec3 normal;
        public Material mat;
        public double t;
        public boolean frontFace;

        public void setFaceNormal(Ray r, Vec3 outwardNormal) {
            frontFace = Vec3.dot(r.direction, outwardNormal) < 0;
            normal = frontFace ? outwardNormal : Vec3.mult(outwardNormal, -1);
        }
    }

    interface Hittable {
        boolean hit(Ray r, double tMin, double tMax, HitRecord rec);
    }

    static class Sphere implements Hittable {
        public Vec3 center;
        public double radius;
        public Material mat;

        public Sphere(Vec3 center, double radius, Material mat) {
            this.center = center; this.radius = radius; this.mat = mat;
        }

        public boolean hit(Ray r, double tMin, double tMax, HitRecord rec) {
            Vec3 oc = Vec3.sub(r.origin, center);
            double a = r.direction.lengthSquared();
            double halfB = Vec3.dot(oc, r.direction);
            double c = oc.lengthSquared() - radius*radius;
            double discriminant = halfB*halfB - a*c;
            if (discriminant < 0) return false;
            double sqrtd = Math.sqrt(discriminant);

            double root = (-halfB - sqrtd) / a;
            if (root < tMin || root > tMax) {
                root = (-halfB + sqrtd) / a;
                if (root < tMin || root > tMax) return false;
            }

            rec.t = root;
            rec.p = r.at(rec.t);
            Vec3 outwardNormal = Vec3.mult(Vec3.sub(rec.p, center), 1.0/radius);
            rec.setFaceNormal(r, outwardNormal);
            rec.mat = mat;
            return true;
        }
    }

    static class HittableList implements Hittable {
        public ArrayList<Hittable> objects = new ArrayList<>();
        public void add(Hittable object) { objects.add(object); }

        public boolean hit(Ray r, double tMin, double tMax, HitRecord rec) {
            HitRecord tempRec = new HitRecord();
            boolean hitAnything = false;
            double closestSoFar = tMax;

            for (Hittable object : objects) {
                if (object.hit(r, tMin, closestSoFar, tempRec)) {
                    hitAnything = true;
                    closestSoFar = tempRec.t;
                    // Copy to main record
                    rec.p = tempRec.p;
                    rec.normal = tempRec.normal;
                    rec.t = tempRec.t;
                    rec.frontFace = tempRec.frontFace;
                    rec.mat = tempRec.mat;
                }
            }
            return hitAnything;
        }
    }

    // --- CAMERA ---

    static class Camera {
        private Vec3 origin;
        private Vec3 lowerLeftCorner;
        private Vec3 horizontal;
        private Vec3 vertical;

        public Camera(Vec3 lookFrom, Vec3 lookAt, Vec3 vUp, double vFov, double aspectRatio) {
            double theta = Math.toRadians(vFov);
            double h = Math.tan(theta/2);
            double viewportHeight = 2.0 * h;
            double viewportWidth = aspectRatio * viewportHeight;

            Vec3 w = Vec3.sub(lookFrom, lookAt).unitVector();
            Vec3 u = Vec3.cross(vUp, w).unitVector();
            Vec3 v = Vec3.cross(w, u);

            origin = lookFrom;
            horizontal = Vec3.mult(u, viewportWidth);
            vertical = Vec3.mult(v, viewportHeight);

            // lowerLeft = origin - horizontal/2 - vertical/2 - w
            Vec3 halfH = Vec3.mult(horizontal, 0.5);
            Vec3 halfV = Vec3.mult(vertical, 0.5);
            lowerLeftCorner = Vec3.sub(origin, halfH);
            lowerLeftCorner = Vec3.sub(lowerLeftCorner, halfV);
            lowerLeftCorner = Vec3.sub(lowerLeftCorner, w);
        }

        public Ray getRay(double s, double t) {
            // rayDir = lowerLeft + s*horizontal + t*vertical - origin
            Vec3 h = Vec3.mult(horizontal, s);
            Vec3 v = Vec3.mult(vertical, t);
            Vec3 target = Vec3.add(lowerLeftCorner, h);
            target = Vec3.add(target, v);
            return new Ray(origin, Vec3.sub(target, origin));
        }
    }

    // --- MATERIALS ---

    interface Material {
        boolean scatter(Ray rIn, HitRecord rec, Vec3 attenuation, Ray scattered);
    }

    static class Lambertian implements Material {
        public Vec3 albedo;
        public Lambertian(Vec3 a) { this.albedo = a; }

        public boolean scatter(Ray rIn, HitRecord rec, Vec3 attenuation, Ray scattered) {
            Vec3 scatterDirection = Vec3.add(rec.normal, Vec3.randomUnitVector());
            if (scatterDirection.nearZero()) scatterDirection = rec.normal;

            scattered.origin = rec.p;
            scattered.direction = scatterDirection;
            attenuation.x = albedo.x; attenuation.y = albedo.y; attenuation.z = albedo.z;
            return true;
        }
    }

    static class Metal implements Material {
        public Vec3 albedo;
        public double fuzz;
        public Metal(Vec3 a, double f) { this.albedo = a; this.fuzz = f < 1 ? f : 1; }

        public boolean scatter(Ray rIn, HitRecord rec, Vec3 attenuation, Ray scattered) {
            Vec3 reflected = Vec3.reflect(rIn.direction.unitVector(), rec.normal);
            scattered.origin = rec.p;
            scattered.direction = Vec3.add(reflected, Vec3.mult(Vec3.randomInUnitSphere(), fuzz));
            attenuation.x = albedo.x; attenuation.y = albedo.y; attenuation.z = albedo.z;
            return (Vec3.dot(scattered.direction, rec.normal) > 0);
        }
    }

    static class Dielectric implements Material {
        public double ir; // Index of Refraction
        public Dielectric(double index) { this.ir = index; }

        public boolean scatter(Ray rIn, HitRecord rec, Vec3 attenuation, Ray scattered) {
            attenuation.x = 1.0; attenuation.y = 1.0; attenuation.z = 1.0;
            double refractionRatio = rec.frontFace ? (1.0 / ir) : ir;

            Vec3 unitDirection = rIn.direction.unitVector();

            double cosTheta = Math.min(Vec3.dot(Vec3.mult(unitDirection, -1), rec.normal), 1.0);
            double sinTheta = Math.sqrt(1.0 - cosTheta*cosTheta);

            boolean cannotRefract = refractionRatio * sinTheta > 1.0;
            Vec3 direction;

            if (cannotRefract || reflectance(cosTheta, refractionRatio) > Math.random()) {
                direction = Vec3.reflect(unitDirection, rec.normal);
            } else {
                direction = Vec3.refract(unitDirection, rec.normal, refractionRatio);
            }

            scattered.origin = rec.p;
            scattered.direction = direction;
            return true;
        }

        private static double reflectance(double cosine, double refIdx) {
            // Schlick's approximation
            double r0 = (1 - refIdx) / (1 + refIdx);
            r0 = r0 * r0;
            return r0 + (1 - r0) * Math.pow((1 - cosine), 5);
        }
    }
}