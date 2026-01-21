import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.Random;

/**
 * Neural Network Visualizer (From Scratch)
 * * Logic:
 * 1. Math: Implements a 3-layer Perceptron (Input -> Hidden -> Output).
 * 2. Training: Uses Stochastic Gradient Descent (Backpropagation) to adjust weights.
 * 3. Visualization:
 * - Decision Boundary: Renders the network's prediction for every pixel (low-res)
 * to show what the network "thinks" the space looks like.
 * - Network Graph: Draws neurons and weights. Line thickness = weight strength.
 * * Features:
 * - Interactive Data: Click to add training examples.
 * - Live Hyperparameters: Adjust learning rate and neuron count on the fly.
 */
public class NeuralNetViz extends JPanel implements ActionListener {

    // --- Configuration ---
    private static final int WIDTH = 1000;
    private static final int HEIGHT = 600;
    private static final int GRAPH_WIDTH = 600; // Width of the classification area
    private static final int RESOLUTION = 8;    // Pixel size for decision boundary (lower is faster)

    // --- State ---
    private NeuralNetwork nn;
    private ArrayList<DataPoint> data;
    private Timer timer;
    private int trainingStepsPerFrame = 50;

    // --- UI Controls ---
    private double learningRate = 0.05;
    private JLabel lossLabel;
    private JSlider lrSlider;
    private JSpinner hiddenNodesSpinner;

    public static void main(String[] args) {
        JFrame frame = new JFrame("Neural Network Visualizer (Java)");
        NeuralNetViz sim = new NeuralNetViz();

        frame.add(sim);
        frame.setSize(WIDTH, HEIGHT);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setVisible(true);
        frame.setLocationRelativeTo(null);
    }

    public NeuralNetViz() {
        this.setLayout(null);
        this.setBackground(new Color(30, 30, 30));

        // Initialize Data
        data = new ArrayList<>();
        // Start with a basic XOR pattern
        data.add(new DataPoint(0.2, 0.2, 0)); // Red
        data.add(new DataPoint(0.8, 0.8, 0)); // Red
        data.add(new DataPoint(0.8, 0.2, 1)); // Blue
        data.add(new DataPoint(0.2, 0.8, 1)); // Blue

        // Initialize Network (2 Inputs, 6 Hidden, 1 Output)
        nn = new NeuralNetwork(2, 6, 1);

        // --- Interaction ---
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getX() > GRAPH_WIDTH) return; // Only click on graph

                double normX = (double) e.getX() / GRAPH_WIDTH;
                double normY = (double) e.getY() / HEIGHT;
                int label = SwingUtilities.isRightMouseButton(e) ? 1 : 0;

                data.add(new DataPoint(normX, normY, label));
                repaint();
            }
        };
        addMouseListener(mouseHandler);

        // --- UI Setup ---
        setupControls();

        // Animation Loop
        timer = new Timer(16, this);
        timer.start();
    }

    private void setupControls() {
        int cx = GRAPH_WIDTH + 20;
        int cw = WIDTH - GRAPH_WIDTH - 40;
        int y = 20;

        JLabel title = new JLabel("Controls");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Arial", Font.BOLD, 18));
        title.setBounds(cx, y, cw, 30);
        add(title);
        y += 40;

        // Reset Button
        JButton btnReset = new JButton("Reset Network");
        btnReset.setBounds(cx, y, cw, 30);
        btnReset.addActionListener(e -> {
            int hidden = (Integer) hiddenNodesSpinner.getValue();
            nn = new NeuralNetwork(2, hidden, 1);
        });
        add(btnReset);
        y += 40;

        JButton btnClear = new JButton("Clear Points");
        btnClear.setBounds(cx, y, cw, 30);
        btnClear.addActionListener(e -> data.clear());
        add(btnClear);
        y += 40;

        // Learning Rate Slider
        JLabel lblLR = new JLabel("Learning Rate:");
        lblLR.setForeground(Color.LIGHT_GRAY);
        lblLR.setBounds(cx, y, cw, 20);
        add(lblLR);
        y += 20;

        lrSlider = new JSlider(1, 100, (int)(learningRate * 100));
        lrSlider.setBounds(cx, y, cw, 30);
        lrSlider.setBackground(new Color(30, 30, 30));
        lrSlider.addChangeListener(e -> learningRate = lrSlider.getValue() / 100.0);
        add(lrSlider);
        y += 40;

        // Hidden Nodes Spinner
        JLabel lblHidden = new JLabel("Hidden Neurons:");
        lblHidden.setForeground(Color.LIGHT_GRAY);
        lblHidden.setBounds(cx, y, cw, 20);
        add(lblHidden);
        y += 20;

        hiddenNodesSpinner = new JSpinner(new SpinnerNumberModel(6, 1, 20, 1));
        hiddenNodesSpinner.setBounds(cx, y, cw, 30);
        hiddenNodesSpinner.addChangeListener(e -> {
            int hidden = (Integer) hiddenNodesSpinner.getValue();
            // Preserve rough state if possible, or just reset
            nn = new NeuralNetwork(2, hidden, 1);
        });
        add(hiddenNodesSpinner);
        y += 40;

        // Instructions
        JTextArea instructions = new JTextArea(
                "Left Click: Add Red (0)\nRight Click: Add Blue (1)\n\n" +
                        "The background shows the\nNeural Network's decision\nboundary updating in real-time."
        );
        instructions.setEditable(false);
        instructions.setBackground(new Color(30,30,30));
        instructions.setForeground(Color.GRAY);
        instructions.setBounds(cx, HEIGHT - 150, cw, 100);
        add(instructions);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // Training Step
        if (!data.isEmpty()) {
            for (int i = 0; i < trainingStepsPerFrame; i++) {
                // Stochastic Gradient Descent: Pick random point and train
                DataPoint p = data.get(new Random().nextInt(data.size()));
                nn.train(new double[]{p.x, p.y}, new double[]{p.label}, learningRate);
            }
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        // 1. Draw Decision Boundary (The Heatmap)
        // We scan the graph area in low resolution
        for (int x = 0; x < GRAPH_WIDTH; x += RESOLUTION) {
            for (int y = 0; y < HEIGHT; y += RESOLUTION) {
                double nx = (double) x / GRAPH_WIDTH;
                double ny = (double) y / HEIGHT;

                double[] output = nn.feedForward(new double[]{nx, ny});
                double val = output[0]; // 0.0 to 1.0

                // Color interpolation: Red (0) <-> Blue (1)
                // We use semi-transparent colors to see grid
                Color c;
                if (val < 0.5) {
                    // Bias towards Red
                    int alpha = (int) (255 * (1 - val * 2)); // Stronger red when val is closer to 0
                    c = new Color(255, 100, 100, Math.min(alpha, 150)); // Soft Red
                } else {
                    // Bias towards Blue
                    int alpha = (int) (255 * ((val - 0.5) * 2));
                    c = new Color(100, 100, 255, Math.min(alpha, 150)); // Soft Blue
                }

                g2.setColor(c);
                g2.fillRect(x, y, RESOLUTION, RESOLUTION);
            }
        }

        // 2. Draw Data Points
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (DataPoint p : data) {
            int px = (int) (p.x * GRAPH_WIDTH);
            int py = (int) (p.y * HEIGHT);

            g2.setColor(Color.WHITE);
            g2.fillOval(px - 7, py - 7, 14, 14); // Border

            g2.setColor(p.label == 0 ? Color.RED : Color.BLUE);
            g2.fillOval(px - 5, py - 5, 10, 10);
        }

        // 3. Draw Network Diagram (Right Side)
        drawNetwork(g2, GRAPH_WIDTH + 20, 300, 350, 200);

        // Divider line
        g2.setColor(Color.WHITE);
        g2.drawLine(GRAPH_WIDTH, 0, GRAPH_WIDTH, HEIGHT);
    }

    private void drawNetwork(Graphics2D g2, int x, int y, int w, int h) {
        // Simple visualization for Input(2) -> Hidden(N) -> Output(1)
        int r = 15; // Neuron radius

        int[] layers = {2, nn.hiddenNodes, 1};
        int[][] nodePositionsX = new int[layers.length][];
        int[][] nodePositionsY = new int[layers.length][];

        // Calculate Positions
        int layerGap = w / (layers.length - 1);
        for (int l = 0; l < layers.length; l++) {
            nodePositionsX[l] = new int[layers[l]];
            nodePositionsY[l] = new int[layers[l]];

            int nodeGap = h / (layers[l] + 1);
            for (int n = 0; n < layers[l]; n++) {
                nodePositionsX[l][n] = x + l * layerGap;
                nodePositionsY[l][n] = y - h/2 + (n + 1) * nodeGap;
            }
        }

        // Draw Weights (Lines)
        // Layer 0 to 1
        drawLayerWeights(g2, nn.weightsIH, nodePositionsX, nodePositionsY, 0);
        // Layer 1 to 2
        drawLayerWeights(g2, nn.weightsHO, nodePositionsX, nodePositionsY, 1);

        // Draw Neurons (Circles)
        for (int l = 0; l < layers.length; l++) {
            for (int n = 0; n < layers[l]; n++) {
                g2.setColor(Color.WHITE);
                g2.fillOval(nodePositionsX[l][n] - r/2, nodePositionsY[l][n] - r/2, r, r);
                g2.setColor(Color.BLACK);
                g2.drawOval(nodePositionsX[l][n] - r/2, nodePositionsY[l][n] - r/2, r, r);
            }
        }
    }

    private void drawLayerWeights(Graphics2D g, Matrix weights, int[][] nx, int[][] ny, int layerIdx) {
        for (int i = 0; i < weights.rows; i++) { // Target nodes (next layer)
            for (int j = 0; j < weights.cols; j++) { // Source nodes (current layer)
                double val = weights.data[i][j];

                // Color based on sign
                if (val > 0) g.setColor(new Color(100, 100, 255)); // Blue for positive
                else g.setColor(new Color(255, 100, 100)); // Red for negative

                // Thickness based on magnitude
                float thickness = (float) Math.min(Math.abs(val) * 2, 4.0);
                g.setStroke(new BasicStroke(thickness));

                g.drawLine(nx[layerIdx][j], ny[layerIdx][j], nx[layerIdx+1][i], ny[layerIdx+1][i]);
            }
        }
    }

    // --- Helper Classes ---

    static class DataPoint {
        double x, y;
        int label;
        DataPoint(double x, double y, int label) {
            this.x = x; this.y = y; this.label = label;
        }
    }

    /**
     * A Simple 3-Layer Neural Network Implementation
     */
    static class NeuralNetwork {
        int inputNodes, hiddenNodes, outputNodes;
        Matrix weightsIH, weightsHO;
        Matrix biasH, biasO;

        NeuralNetwork(int input, int hidden, int output) {
            this.inputNodes = input;
            this.hiddenNodes = hidden;
            this.outputNodes = output;

            weightsIH = new Matrix(hidden, input);
            weightsHO = new Matrix(output, hidden);
            biasH = new Matrix(hidden, 1);
            biasO = new Matrix(output, 1);

            weightsIH.randomize();
            weightsHO.randomize();
            biasH.randomize();
            biasO.randomize();
        }

        double[] feedForward(double[] inputArray) {
            Matrix inputs = Matrix.fromArray(inputArray);

            // Hidden Layer
            Matrix hidden = Matrix.multiply(weightsIH, inputs);
            hidden.add(biasH);
            hidden.mapSigmoid();

            // Output Layer
            Matrix output = Matrix.multiply(weightsHO, hidden);
            output.add(biasO);
            output.mapSigmoid();

            return output.toArray();
        }

        void train(double[] inputArray, double[] targetArray, double learningRate) {
            Matrix inputs = Matrix.fromArray(inputArray);
            Matrix targets = Matrix.fromArray(targetArray);

            // --- Forward Pass ---
            Matrix hidden = Matrix.multiply(weightsIH, inputs);
            hidden.add(biasH);
            hidden.mapSigmoid();

            Matrix outputs = Matrix.multiply(weightsHO, hidden);
            outputs.add(biasO);
            outputs.mapSigmoid();

            // --- Backpropagation ---

            // 1. Calculate Output Errors (Target - Output)
            Matrix outputErrors = Matrix.subtract(targets, outputs);

            // 2. Calculate Output Gradients
            // Gradient = lr * error * (output * (1 - output))
            Matrix gradients = outputs.dsigmoid();
            gradients.hadamard(outputErrors);
            gradients.multiply(learningRate);

            // 3. Calculate Deltas for HO Weights
            Matrix hiddenT = Matrix.transpose(hidden);
            Matrix weightHO_deltas = Matrix.multiply(gradients, hiddenT);

            // 4. Adjust HO Weights and Biases
            weightsHO.add(weightHO_deltas);
            biasO.add(gradients);

            // 5. Calculate Hidden Errors
            Matrix whoT = Matrix.transpose(weightsHO);
            Matrix hiddenErrors = Matrix.multiply(whoT, outputErrors);

            // 6. Calculate Hidden Gradients
            Matrix hiddenGradient = hidden.dsigmoid();
            hiddenGradient.hadamard(hiddenErrors);
            hiddenGradient.multiply(learningRate);

            // 7. Calculate Deltas for IH Weights
            Matrix inputsT = Matrix.transpose(inputs);
            Matrix weightIH_deltas = Matrix.multiply(hiddenGradient, inputsT);

            // 8. Adjust IH Weights and Biases
            weightsIH.add(weightIH_deltas);
            biasH.add(hiddenGradient);
        }
    }

    /**
     * Minimal Matrix Math Library
     */
    static class Matrix {
        int rows, cols;
        double[][] data;

        Matrix(int rows, int cols) {
            this.rows = rows;
            this.cols = cols;
            this.data = new double[rows][cols];
        }

        static Matrix fromArray(double[] arr) {
            Matrix m = new Matrix(arr.length, 1);
            for (int i = 0; i < arr.length; i++) m.data[i][0] = arr[i];
            return m;
        }

        double[] toArray() {
            double[] arr = new double[rows * cols];
            int k = 0;
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) arr[k++] = data[i][j];
            }
            return arr;
        }

        void randomize() {
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) data[i][j] = Math.random() * 2 - 1;
            }
        }

        void add(Matrix n) {
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) data[i][j] += n.data[i][j];
            }
        }

        static Matrix subtract(Matrix a, Matrix b) {
            Matrix temp = new Matrix(a.rows, a.cols);
            for (int i = 0; i < a.rows; i++) {
                for (int j = 0; j < a.cols; j++) temp.data[i][j] = a.data[i][j] - b.data[i][j];
            }
            return temp;
        }

        static Matrix transpose(Matrix a) {
            Matrix temp = new Matrix(a.cols, a.rows);
            for (int i = 0; i < a.rows; i++) {
                for (int j = 0; j < a.cols; j++) temp.data[j][i] = a.data[i][j];
            }
            return temp;
        }

        static Matrix multiply(Matrix a, Matrix b) {
            Matrix temp = new Matrix(a.rows, b.cols);
            for (int i = 0; i < temp.rows; i++) {
                for (int j = 0; j < temp.cols; j++) {
                    double sum = 0;
                    for (int k = 0; k < a.cols; k++) sum += a.data[i][k] * b.data[k][j];
                    temp.data[i][j] = sum;
                }
            }
            return temp;
        }

        void multiply(double n) {
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) data[i][j] *= n;
            }
        }

        // Element-wise multiplication (Hadamard product)
        void hadamard(Matrix n) {
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) data[i][j] *= n.data[i][j];
            }
        }

        void mapSigmoid() {
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) data[i][j] = 1 / (1 + Math.exp(-data[i][j]));
            }
        }

        // Calculates derivative of sigmoid assuming data already contains sigmoid values
        // y * (1 - y)
        Matrix dsigmoid() {
            Matrix temp = new Matrix(rows, cols);
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) temp.data[i][j] = data[i][j] * (1 - data[i][j]);
            }
            return temp;
        }
    }
}