import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

public class PaintApplication extends JFrame {
    private DrawingCanvas canvas;
    private JButton selectedToolButton = null;
    private JButton selectedColorButton = null;

    // Tool types
    private enum Tool {
        PENCIL, LINE, RECTANGLE, CIRCLE, OVAL, FILLED_RECTANGLE,
        FILLED_CIRCLE, FILLED_OVAL, ERASER, TEXT
    }

    private Tool currentTool = Tool.PENCIL;
    private Color currentColor = Color.BLACK;
    private int strokeSize = 3;

    // UI Colors
    private final Color TOOLBAR_BG = new Color(240, 240, 240);
    private final Color CANVAS_BG = Color.WHITE;
    private final Color SELECTED_BTN = new Color(200, 230, 255);

    public PaintApplication() {
        setTitle("Paint Application");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        createMenuBar();
        createToolbar();
        createCanvas();
        createColorPanel();
        createStrokeSizePanel();

        setSize(1000, 700);
        setLocationRelativeTo(null);
    }

    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        JMenuItem clearItem = new JMenuItem("Clear Canvas");
        clearItem.addActionListener(e -> canvas.clear());
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(clearItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        JMenu editMenu = new JMenu("Edit");
        JMenuItem undoItem = new JMenuItem("Undo");
        undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
        undoItem.addActionListener(e -> canvas.undo());
        editMenu.add(undoItem);

        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        setJMenuBar(menuBar);
    }

    private void createToolbar() {
        JPanel toolbar = new JPanel();
        toolbar.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        toolbar.setBackground(TOOLBAR_BG);
        toolbar.setBorder(new EmptyBorder(5, 5, 5, 5));

        // Tool buttons with icons
        JButton pencilBtn = createToolButton("✏️ Pencil", Tool.PENCIL);
        JButton lineBtn = createToolButton("📏 Line", Tool.LINE);
        JButton rectBtn = createToolButton("▢ Rectangle", Tool.RECTANGLE);
        JButton circleBtn = createToolButton("○ Circle", Tool.CIRCLE);
        JButton ovalBtn = createToolButton("⬭ Oval", Tool.OVAL);
        JButton filledRectBtn = createToolButton("◼ Filled Rect", Tool.FILLED_RECTANGLE);
        JButton filledCircleBtn = createToolButton("● Filled Circle", Tool.FILLED_CIRCLE);
        JButton filledOvalBtn = createToolButton("⬬ Filled Oval", Tool.FILLED_OVAL);
        JButton eraserBtn = createToolButton("🧹 Eraser", Tool.ERASER);
        JButton textBtn = createToolButton("T Text", Tool.TEXT);

        toolbar.add(pencilBtn);
        toolbar.add(lineBtn);
        toolbar.add(rectBtn);
        toolbar.add(circleBtn);
        toolbar.add(ovalBtn);
        toolbar.add(filledRectBtn);
        toolbar.add(filledCircleBtn);
        toolbar.add(filledOvalBtn);
        toolbar.add(eraserBtn);
        toolbar.add(textBtn);

        // Select pencil by default
        pencilBtn.doClick();

        add(toolbar, BorderLayout.NORTH);
    }

    private JButton createToolButton(String text, Tool tool) {
        JButton button = new JButton(text);
        button.setFont(new Font("Arial", Font.PLAIN, 12));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1),
                new EmptyBorder(5, 10, 5, 10)
        ));

        button.addActionListener(e -> {
            currentTool = tool;
            if (selectedToolButton != null) {
                selectedToolButton.setBackground(null);
            }
            button.setBackground(SELECTED_BTN);
            selectedToolButton = button;
        });

        return button;
    }

    private void createCanvas() {
        canvas = new DrawingCanvas();
        canvas.setBackground(CANVAS_BG);
        canvas.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));

        JScrollPane scrollPane = new JScrollPane(canvas);
        add(scrollPane, BorderLayout.CENTER);
    }

    private void createColorPanel() {
        JPanel colorPanel = new JPanel();
        colorPanel.setLayout(new GridLayout(0, 2, 5, 5));
        colorPanel.setBackground(TOOLBAR_BG);
        colorPanel.setBorder(BorderFactory.createTitledBorder("Colors"));
        colorPanel.setPreferredSize(new Dimension(120, 0));

        Color[] colors = {
                Color.BLACK, Color.WHITE, Color.RED, Color.GREEN,
                Color.BLUE, Color.YELLOW, Color.ORANGE, Color.PINK,
                Color.MAGENTA, Color.CYAN, Color.GRAY, Color.LIGHT_GRAY,
                new Color(139, 69, 19), new Color(128, 0, 128),
                new Color(0, 128, 0), new Color(255, 165, 0)
        };

        for (Color color : colors) {
            JButton colorBtn = createColorButton(color);
            colorPanel.add(colorBtn);
        }

        // Custom color button
        JButton customBtn = new JButton("Custom");
        customBtn.setFont(new Font("Arial", Font.BOLD, 10));
        customBtn.addActionListener(e -> {
            Color color = JColorChooser.showDialog(this, "Choose Color", currentColor);
            if (color != null) {
                currentColor = color;
                updateSelectedColorButton(customBtn);
            }
        });
        colorPanel.add(customBtn);

        add(colorPanel, BorderLayout.WEST);
    }

    private JButton createColorButton(Color color) {
        JButton button = new JButton();
        button.setBackground(color);
        button.setPreferredSize(new Dimension(40, 40));
        button.setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));
        button.setFocusPainted(false);

        button.addActionListener(e -> {
            currentColor = color;
            updateSelectedColorButton(button);
        });

        // Select black by default
        if (color.equals(Color.BLACK)) {
            button.setBorder(BorderFactory.createLineBorder(Color.RED, 3));
            selectedColorButton = button;
        }

        return button;
    }

    private void updateSelectedColorButton(JButton button) {
        if (selectedColorButton != null) {
            if (selectedColorButton.getText().isEmpty()) {
                selectedColorButton.setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));
            }
        }
        if (button.getText().isEmpty()) {
            button.setBorder(BorderFactory.createLineBorder(Color.RED, 3));
        }
        selectedColorButton = button;
    }

    private void createStrokeSizePanel() {
        JPanel strokePanel = new JPanel();
        strokePanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        strokePanel.setBackground(TOOLBAR_BG);
        strokePanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        JLabel label = new JLabel("Stroke Size: ");
        JSlider slider = new JSlider(1, 20, 3);
        slider.setMajorTickSpacing(5);
        slider.setMinorTickSpacing(1);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        slider.setPreferredSize(new Dimension(200, 50));

        JLabel valueLabel = new JLabel("3");
        valueLabel.setFont(new Font("Arial", Font.BOLD, 14));

        slider.addChangeListener(e -> {
            strokeSize = slider.getValue();
            valueLabel.setText(String.valueOf(strokeSize));
        });

        strokePanel.add(label);
        strokePanel.add(slider);
        strokePanel.add(valueLabel);

        add(strokePanel, BorderLayout.SOUTH);
    }

    // Drawing Canvas Class
    class DrawingCanvas extends JPanel {
        private BufferedImage image;
        private Graphics2D g2d;
        private int startX, startY, endX, endY;
        private boolean drawing = false;
        private ArrayList<BufferedImage> history = new ArrayList<>();

        public DrawingCanvas() {
            setPreferredSize(new Dimension(800, 600));

            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    startX = e.getX();
                    startY = e.getY();
                    drawing = true;

                    if (currentTool == Tool.TEXT) {
                        String text = JOptionPane.showInputDialog(PaintApplication.this,
                                "Enter text:", "Text Input", JOptionPane.PLAIN_MESSAGE);
                        if (text != null && !text.isEmpty()) {
                            saveToHistory();
                            g2d.setColor(currentColor);
                            g2d.setFont(new Font("Arial", Font.PLAIN, 20));
                            g2d.drawString(text, startX, startY);
                            repaint();
                        }
                        drawing = false;
                    }
                }

                public void mouseReleased(MouseEvent e) {
                    if (!drawing) return;

                    endX = e.getX();
                    endY = e.getY();

                    saveToHistory();
                    drawShape(g2d);
                    drawing = false;
                    repaint();
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                public void mouseDragged(MouseEvent e) {
                    if (!drawing) return;

                    endX = e.getX();
                    endY = e.getY();

                    if (currentTool == Tool.PENCIL || currentTool == Tool.ERASER) {
                        g2d.setColor(currentTool == Tool.ERASER ? CANVAS_BG : currentColor);
                        g2d.setStroke(new BasicStroke(strokeSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2d.drawLine(startX, startY, endX, endY);
                        startX = endX;
                        startY = endY;
                        repaint();
                    } else {
                        repaint();
                    }
                }
            });
        }

        private void saveToHistory() {
            BufferedImage snapshot = new BufferedImage(
                    image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = snapshot.createGraphics();
            g.drawImage(image, 0, 0, null);
            g.dispose();
            history.add(snapshot);

            // Keep only last 20 states
            if (history.size() > 20) {
                history.remove(0);
            }
        }

        public void undo() {
            if (!history.isEmpty()) {
                image = history.remove(history.size() - 1);
                g2d = image.createGraphics();
                setupGraphics(g2d);
                repaint();
            }
        }

        public void clear() {
            saveToHistory();
            g2d.setColor(CANVAS_BG);
            g2d.fillRect(0, 0, getWidth(), getHeight());
            repaint();
        }

        private void drawShape(Graphics2D g) {
            g.setColor(currentColor);
            g.setStroke(new BasicStroke(strokeSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            int x = Math.min(startX, endX);
            int y = Math.min(startY, endY);
            int width = Math.abs(endX - startX);
            int height = Math.abs(endY - startY);

            switch (currentTool) {
                case LINE:
                    g.drawLine(startX, startY, endX, endY);
                    break;
                case RECTANGLE:
                    g.drawRect(x, y, width, height);
                    break;
                case CIRCLE:
                    int diameter = Math.max(width, height);
                    g.drawOval(x, y, diameter, diameter);
                    break;
                case OVAL:
                    g.drawOval(x, y, width, height);
                    break;
                case FILLED_RECTANGLE:
                    g.fillRect(x, y, width, height);
                    break;
                case FILLED_CIRCLE:
                    int diam = Math.max(width, height);
                    g.fillOval(x, y, diam, diam);
                    break;
                case FILLED_OVAL:
                    g.fillOval(x, y, width, height);
                    break;
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (image == null) {
                image = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
                g2d = image.createGraphics();
                setupGraphics(g2d);
                g2d.setColor(CANVAS_BG);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }

            g.drawImage(image, 0, 0, null);

            // Draw preview for shapes
            if (drawing && currentTool != Tool.PENCIL && currentTool != Tool.ERASER && currentTool != Tool.TEXT) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(currentColor);
                g2.setStroke(new BasicStroke(strokeSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                int x = Math.min(startX, endX);
                int y = Math.min(startY, endY);
                int width = Math.abs(endX - startX);
                int height = Math.abs(endY - startY);

                switch (currentTool) {
                    case LINE:
                        g2.drawLine(startX, startY, endX, endY);
                        break;
                    case RECTANGLE:
                        g2.drawRect(x, y, width, height);
                        break;
                    case CIRCLE:
                        int diameter = Math.max(width, height);
                        g2.drawOval(x, y, diameter, diameter);
                        break;
                    case OVAL:
                        g2.drawOval(x, y, width, height);
                        break;
                    case FILLED_RECTANGLE:
                        g2.fillRect(x, y, width, height);
                        break;
                    case FILLED_CIRCLE:
                        int diam = Math.max(width, height);
                        g2.fillOval(x, y, diam, diam);
                        break;
                    case FILLED_OVAL:
                        g2.fillOval(x, y, width, height);
                        break;
                }
                g2.dispose();
            }
        }

        private void setupGraphics(Graphics2D g) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            PaintApplication app = new PaintApplication();
            app.setVisible(true);
        });
    }
}