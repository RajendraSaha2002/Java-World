import javax.swing.*;
import javax.sound.sampled.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

/**
 * Audio Visualizer (FFT - Fast Fourier Transform)
 * * Logic:
 * 1. Audio Input: Captures raw bytes from the microphone (TargetDataLine).
 * 2. Signal Processing: Converts bytes (Time Domain) to Complex numbers.
 * 3. FFT (Math): Uses recursive Cooley-Tukey algorithm to transform Time Domain -> Frequency Domain.
 * 4. Visualization: Draws the magnitude of each frequency bucket as a bar.
 * * Key Features:
 * - No external libraries (JTransforms, etc. are NOT used).
 * - Custom Complex Number class.
 * - Real-time analysis loop.
 */
public class AudioVisualizer extends JPanel {

    // --- Configuration ---
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final int SAMPLE_RATE = 44100;
    private static final int FFT_SIZE = 1024; // Must be power of 2 (512, 1024, 2048)

    // --- State ---
    private TargetDataLine microphone;
    private Complex[] spectrum; // The frequency data to draw
    private boolean running = true;

    public static void main(String[] args) {
        JFrame frame = new JFrame("Java Audio Visualizer (Recursive FFT)");
        AudioVisualizer vis = new AudioVisualizer();

        frame.add(vis);
        frame.setSize(WIDTH, HEIGHT);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.setVisible(true);

        // Start processing in a separate thread
        new Thread(vis::startAudioCapture).start();
    }

    public AudioVisualizer() {
        setBackground(Color.BLACK);
        spectrum = new Complex[FFT_SIZE];
        // Fill with zeros initially
        for(int i=0; i<FFT_SIZE; i++) spectrum[i] = new Complex(0, 0);
    }

    private void startAudioCapture() {
        try {
            // 1. Setup Audio Format
            // 44.1kHz, 16-bit, Mono, Signed, Little Endian
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

            if (!AudioSystem.isLineSupported(info)) {
                System.err.println("Microphone line not supported.");
                return;
            }

            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(format);
            microphone.start();

            System.out.println("Listening to: " + microphone.getLineInfo());

            // Buffer to hold raw bytes
            byte[] audioBuffer = new byte[FFT_SIZE * 2]; // 2 bytes per sample (16-bit)
            Complex[] timeDomain = new Complex[FFT_SIZE];

            while (running) {
                // 2. Read Raw Data
                int bytesRead = microphone.read(audioBuffer, 0, audioBuffer.length);

                if (bytesRead > 0) {
                    // 3. Convert Bytes to Complex Numbers (Time Domain)
                    for (int i = 0; i < FFT_SIZE; i++) {
                        // Combine 2 bytes into one 16-bit sample (Little Endian)
                        // Low byte | High byte
                        int sample = (audioBuffer[2*i] & 0xFF) | (audioBuffer[2*i+1] << 8);

                        // Normalize to range -1.0 to 1.0
                        double normalized = sample / 32768.0;

                        // Apply Hanning Window (Optional, smooths edges to reduce spectral leakage)
                        double window = 0.5 * (1 - Math.cos(2 * Math.PI * i / (FFT_SIZE - 1)));

                        timeDomain[i] = new Complex(normalized * window, 0);
                    }

                    // 4. Perform FFT
                    spectrum = fft(timeDomain);

                    // 5. Render
                    repaint();
                }
            }
            microphone.close();

        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    // --- The Recursive Cooley-Tukey FFT Algorithm ---
    // x: Array of samples in time domain
    // returns: Array of frequencies
    public Complex[] fft(Complex[] x) {
        int n = x.length;

        // Base Case
        if (n == 1) return new Complex[] { x[0] };

        // Radix-2: Split into even and odd indices
        // Note: Creating arrays inside loop is heavy on GC,
        // usually optimized to bit-reversal in C++, but this is "textbook" recursive style.
        Complex[] even = new Complex[n / 2];
        Complex[] odd  = new Complex[n / 2];

        for (int k = 0; k < n / 2; k++) {
            even[k] = x[2 * k];
            odd[k]  = x[2 * k + 1];
        }

        // Recursion
        Complex[] q = fft(even);
        Complex[] r = fft(odd);

        // Combine
        Complex[] y = new Complex[n];
        for (int k = 0; k < n / 2; k++) {
            // Twiddle Factor: e^(-2*pi*i*k/N)
            double kth = -2 * k * Math.PI / n;
            Complex wk = new Complex(Math.cos(kth), Math.sin(kth));

            Complex times = wk.mult(r[k]);

            y[k]       = q[k].add(times);
            y[k + n/2] = q[k].sub(times);
        }
        return y;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw Spectrum
        // We only display the first half of the spectrum (Nyquist frequency limit)
        int displayBins = FFT_SIZE / 2;
        double binWidth = (double) WIDTH / displayBins;

        for (int i = 0; i < displayBins; i++) {
            if (spectrum[i] == null) continue;

            // Calculate Magnitude: sqrt(re^2 + im^2)
            double mag = spectrum[i].magnitude();

            // Logarithmic scale often looks better for audio, but linear is simpler to implement initially.
            // We multiply by a scale factor to make it visible.
            double height = mag * 150;

            // Clamp height
            if (height > HEIGHT) height = HEIGHT;

            // Color calculation (Bass = Red, Treble = Blue)
            float hue = (float)i / displayBins;
            g2.setColor(Color.getHSBColor(hue, 1.0f, 1.0f));

            int x = (int) (i * binWidth);
            int y = (int) (HEIGHT - height);

            g2.fillRect(x, y, (int)Math.ceil(binWidth), (int)height);
        }

        g2.setColor(Color.WHITE);
        g2.drawString("Frequency Spectrum (Microphone Input)", 10, 20);
    }

    // --- Complex Number Math Class ---
    static class Complex {
        double re, im;

        public Complex(double re, double im) {
            this.re = re;
            this.im = im;
        }

        public Complex add(Complex b) {
            return new Complex(this.re + b.re, this.im + b.im);
        }

        public Complex sub(Complex b) {
            return new Complex(this.re - b.re, this.im - b.im);
        }

        // (a + bi)(c + di) = (ac - bd) + (ad + bc)i
        public Complex mult(Complex b) {
            return new Complex(this.re * b.re - this.im * b.im, this.re * b.im + this.im * b.re);
        }

        public double magnitude() {
            return Math.sqrt(re*re + im*im);
        }
    }
}