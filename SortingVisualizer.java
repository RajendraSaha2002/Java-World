import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Random;

public class SortingVisualizer extends JPanel {
    private static final int WIDTH = 1000;
    private static final int HEIGHT = 600;
    private static final int ARRAY_SIZE = 100;
    private static final int BAR_WIDTH = WIDTH / ARRAY_SIZE;
    private static final int DELAY = 10; // milliseconds

    private int[] array;
    private int[] highlights; // 0=normal, 1=comparing, 2=swapping, 3=sorted
    private String currentAlgorithm = "None";
    private boolean isSorting = false;
    private Random rand;

    public SortingVisualizer() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(new Color(20, 20, 30));
        rand = new Random();
        array = new int[ARRAY_SIZE];
        highlights = new int[ARRAY_SIZE];
        randomizeArray();
    }

    private void randomizeArray() {
        for (int i = 0; i < array.length; i++) {
            array[i] = rand.nextInt(HEIGHT - 100) + 10;
            highlights[i] = 0;
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw title
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        g2d.drawString("Sorting Algorithm Visualizer", 20, 40);

        g2d.setFont(new Font("Arial", Font.PLAIN, 16));
        g2d.drawString("Current Algorithm: " + currentAlgorithm, 20, 70);

        // Draw bars
        for (int i = 0; i < array.length; i++) {
            Color barColor;
            switch (highlights[i]) {
                case 1: // Comparing
                    barColor = new Color(255, 255, 0); // Yellow
                    break;
                case 2: // Swapping
                    barColor = new Color(255, 0, 0); // Red
                    break;
                case 3: // Sorted
                    barColor = new Color(0, 255, 0); // Green
                    break;
                default: // Normal
                    barColor = new Color(100, 150, 255); // Blue
                    break;
            }

            g2d.setColor(barColor);
            int x = i * BAR_WIDTH;
            int barHeight = array[i];
            int y = HEIGHT - barHeight - 50;
            g2d.fillRect(x, y, BAR_WIDTH - 1, barHeight);
        }

        // Draw legend
        drawLegend(g2d);
    }

    private void drawLegend(Graphics2D g2d) {
        int legendY = HEIGHT - 30;
        g2d.setFont(new Font("Arial", Font.PLAIN, 12));

        // Blue - Unsorted
        g2d.setColor(new Color(100, 150, 255));
        g2d.fillRect(20, legendY, 15, 15);
        g2d.setColor(Color.WHITE);
        g2d.drawString("Unsorted", 40, legendY + 12);

        // Yellow - Comparing
        g2d.setColor(new Color(255, 255, 0));
        g2d.fillRect(120, legendY, 15, 15);
        g2d.setColor(Color.WHITE);
        g2d.drawString("Comparing", 140, legendY + 12);

        // Red - Swapping
        g2d.setColor(new Color(255, 0, 0));
        g2d.fillRect(240, legendY, 15, 15);
        g2d.setColor(Color.WHITE);
        g2d.drawString("Swapping", 260, legendY + 12);

        // Green - Sorted
        g2d.setColor(new Color(0, 255, 0));
        g2d.fillRect(350, legendY, 15, 15);
        g2d.setColor(Color.WHITE);
        g2d.drawString("Sorted", 370, legendY + 12);
    }

    // Bubble Sort
    private void bubbleSort() {
        currentAlgorithm = "Bubble Sort";
        isSorting = true;

        new Thread(() -> {
            for (int i = 0; i < array.length - 1; i++) {
                for (int j = 0; j < array.length - i - 1; j++) {
                    if (!isSorting) return;

                    // Highlight comparing elements
                    highlights[j] = 1;
                    highlights[j + 1] = 1;
                    repaint();
                    sleep(DELAY);

                    if (array[j] > array[j + 1]) {
                        // Highlight swapping
                        highlights[j] = 2;
                        highlights[j + 1] = 2;
                        repaint();
                        sleep(DELAY);

                        // Swap
                        int temp = array[j];
                        array[j] = array[j + 1];
                        array[j + 1] = temp;
                        repaint();
                        sleep(DELAY);
                    }

                    // Reset highlights
                    highlights[j] = 0;
                    highlights[j + 1] = 0;
                }
                // Mark as sorted
                highlights[array.length - i - 1] = 3;
            }
            highlights[0] = 3;
            repaint();
            isSorting = false;
        }).start();
    }

    // Quick Sort
    private void quickSort() {
        currentAlgorithm = "Quick Sort";
        isSorting = true;

        new Thread(() -> {
            quickSortHelper(0, array.length - 1);
            // Mark all as sorted
            for (int i = 0; i < array.length; i++) {
                highlights[i] = 3;
            }
            repaint();
            isSorting = false;
        }).start();
    }

    private void quickSortHelper(int low, int high) {
        if (!isSorting || low >= high) return;

        int pivotIndex = partition(low, high);
        quickSortHelper(low, pivotIndex - 1);
        quickSortHelper(pivotIndex + 1, high);
    }

    private int partition(int low, int high) {
        int pivot = array[high];
        highlights[high] = 2; // Pivot in red
        repaint();

        int i = low - 1;

        for (int j = low; j < high; j++) {
            if (!isSorting) return i + 1;

            highlights[j] = 1; // Comparing
            repaint();
            sleep(DELAY);

            if (array[j] < pivot) {
                i++;

                // Swap
                highlights[i] = 2;
                highlights[j] = 2;
                repaint();
                sleep(DELAY);

                int temp = array[i];
                array[i] = array[j];
                array[j] = temp;
                repaint();
                sleep(DELAY);
            }

            highlights[j] = 0;
        }

        // Swap pivot
        highlights[i + 1] = 2;
        repaint();
        sleep(DELAY);

        int temp = array[i + 1];
        array[i + 1] = array[high];
        array[high] = temp;

        // Reset highlights
        for (int k = low; k <= high; k++) {
            highlights[k] = 0;
        }
        repaint();

        return i + 1;
    }

    // Merge Sort
    private void mergeSort() {
        currentAlgorithm = "Merge Sort";
        isSorting = true;

        new Thread(() -> {
            mergeSortHelper(0, array.length - 1);
            // Mark all as sorted
            for (int i = 0; i < array.length; i++) {
                highlights[i] = 3;
            }
            repaint();
            isSorting = false;
        }).start();
    }

    private void mergeSortHelper(int left, int right) {
        if (!isSorting || left >= right) return;

        int mid = (left + right) / 2;

        mergeSortHelper(left, mid);
        mergeSortHelper(mid + 1, right);
        merge(left, mid, right);
    }

    private void merge(int left, int mid, int right) {
        if (!isSorting) return;

        int n1 = mid - left + 1;
        int n2 = right - mid;

        int[] leftArray = new int[n1];
        int[] rightArray = new int[n2];

        for (int i = 0; i < n1; i++) {
            leftArray[i] = array[left + i];
        }
        for (int j = 0; j < n2; j++) {
            rightArray[j] = array[mid + 1 + j];
        }

        int i = 0, j = 0, k = left;

        while (i < n1 && j < n2) {
            if (!isSorting) return;

            highlights[k] = 1;
            repaint();
            sleep(DELAY);

            if (leftArray[i] <= rightArray[j]) {
                array[k] = leftArray[i];
                i++;
            } else {
                array[k] = rightArray[j];
                j++;
            }

            highlights[k] = 2;
            repaint();
            sleep(DELAY);
            highlights[k] = 0;
            k++;
        }

        while (i < n1) {
            if (!isSorting) return;
            array[k] = leftArray[i];
            highlights[k] = 2;
            repaint();
            sleep(DELAY);
            highlights[k] = 0;
            i++;
            k++;
        }

        while (j < n2) {
            if (!isSorting) return;
            array[k] = rightArray[j];
            highlights[k] = 2;
            repaint();
            sleep(DELAY);
            highlights[k] = 0;
            j++;
            k++;
        }
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Sorting Algorithm Visualizer");
            SortingVisualizer visualizer = new SortingVisualizer();

            // Create control panel
            JPanel controlPanel = new JPanel();
            controlPanel.setBackground(new Color(40, 40, 50));

            JButton bubbleBtn = new JButton("Bubble Sort");
            JButton quickBtn = new JButton("Quick Sort");
            JButton mergeBtn = new JButton("Merge Sort");
            JButton randomizeBtn = new JButton("Randomize");
            JButton stopBtn = new JButton("Stop");

            // Style buttons
            JButton[] buttons = {bubbleBtn, quickBtn, mergeBtn, randomizeBtn, stopBtn};
            for (JButton btn : buttons) {
                btn.setFont(new Font("Arial", Font.BOLD, 14));
                btn.setFocusPainted(false);
                controlPanel.add(btn);
            }

            bubbleBtn.addActionListener(e -> {
                if (!visualizer.isSorting) {
                    visualizer.randomizeArray();
                    visualizer.bubbleSort();
                }
            });

            quickBtn.addActionListener(e -> {
                if (!visualizer.isSorting) {
                    visualizer.randomizeArray();
                    visualizer.quickSort();
                }
            });

            mergeBtn.addActionListener(e -> {
                if (!visualizer.isSorting) {
                    visualizer.randomizeArray();
                    visualizer.mergeSort();
                }
            });

            randomizeBtn.addActionListener(e -> {
                if (!visualizer.isSorting) {
                    visualizer.randomizeArray();
                    visualizer.currentAlgorithm = "None";
                }
            });

            stopBtn.addActionListener(e -> {
                visualizer.isSorting = false;
                visualizer.currentAlgorithm = "Stopped";
                for (int i = 0; i < visualizer.highlights.length; i++) {
                    visualizer.highlights[i] = 0;
                }
                visualizer.repaint();
            });

            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new BorderLayout());
            frame.add(visualizer, BorderLayout.CENTER);
            frame.add(controlPanel, BorderLayout.SOUTH);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setResizable(false);
            frame.setVisible(true);
        });
    }
}