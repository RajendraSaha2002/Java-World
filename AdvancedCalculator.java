import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.text.*;

public class AdvancedCalculator extends JFrame {
    private JTextField displayField;
    private JTextArea historyArea;
    private StringBuilder currentInput = new StringBuilder();
    private double result = 0;
    private String operator = "";
    private boolean startNewNumber = true;
    private ArrayList<String> history = new ArrayList<>();
    private JPanel scientificPanel;
    private boolean scientificMode = false;

    // Colors
    private final Color BACKGROUND = new Color(30, 30, 30);
    private final Color DISPLAY_BG = new Color(45, 45, 45);
    private final Color NUMBER_BTN = new Color(60, 60, 60);
    private final Color OPERATOR_BTN = new Color(255, 149, 0);
    private final Color FUNCTION_BTN = new Color(50, 50, 50);
    private final Color TEXT_COLOR = Color.WHITE;

    public AdvancedCalculator() {
        setTitle("Advanced Calculator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        getContentPane().setBackground(BACKGROUND);

        createMenuBar();
        createDisplay();
        createButtons();
        createHistoryPanel();

        pack();
        setLocationRelativeTo(null);
        setResizable(false);
    }

    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBackground(BACKGROUND);

        JMenu viewMenu = new JMenu("View");
        viewMenu.setForeground(TEXT_COLOR);

        JMenuItem scientificItem = new JMenuItem("Toggle Scientific Mode");
        scientificItem.addActionListener(e -> toggleScientificMode());
        viewMenu.add(scientificItem);

        JMenuItem historyItem = new JMenuItem("Clear History");
        historyItem.addActionListener(e -> clearHistory());
        viewMenu.add(historyItem);

        menuBar.add(viewMenu);
        setJMenuBar(menuBar);
    }

    private void createDisplay() {
        JPanel displayPanel = new JPanel(new BorderLayout(5, 5));
        displayPanel.setBackground(BACKGROUND);
        displayPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        displayField = new JTextField("0");
        displayField.setFont(new Font("Arial", Font.BOLD, 36));
        displayField.setHorizontalAlignment(JTextField.RIGHT);
        displayField.setEditable(false);
        displayField.setBackground(DISPLAY_BG);
        displayField.setForeground(TEXT_COLOR);
        displayField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 70), 2),
                new EmptyBorder(10, 10, 10, 10)
        ));

        displayPanel.add(displayField, BorderLayout.CENTER);
        add(displayPanel, BorderLayout.NORTH);
    }

    private void createButtons() {
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBackground(BACKGROUND);
        mainPanel.setBorder(new EmptyBorder(0, 10, 10, 10));

        // Basic calculator panel
        JPanel basicPanel = new JPanel(new GridLayout(5, 4, 5, 5));
        basicPanel.setBackground(BACKGROUND);

        String[][] buttons = {
                {"C", "⌫", "%", "÷"},
                {"7", "8", "9", "×"},
                {"4", "5", "6", "-"},
                {"1", "2", "3", "+"},
                {"±", "0", ".", "="}
        };

        for (String[] row : buttons) {
            for (String text : row) {
                JButton btn = createButton(text);
                basicPanel.add(btn);
            }
        }

        mainPanel.add(basicPanel, BorderLayout.CENTER);

        // Scientific panel (initially hidden)
        scientificPanel = new JPanel(new GridLayout(5, 3, 5, 5));
        scientificPanel.setBackground(BACKGROUND);
        scientificPanel.setVisible(false);

        String[] scientificButtons = {
                "sin", "cos", "tan",
                "√", "x²", "xʸ",
                "ln", "log", "eˣ",
                "π", "e", "!",
                "(", ")", "1/x"
        };

        for (String text : scientificButtons) {
            JButton btn = createButton(text);
            scientificPanel.add(btn);
        }

        mainPanel.add(scientificPanel, BorderLayout.WEST);
        add(mainPanel, BorderLayout.CENTER);
    }

    private JButton createButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Arial", Font.BOLD, 18));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70), 1));
        button.setPreferredSize(new Dimension(70, 60));

        // Set colors based on button type
        if (text.matches("[0-9.]")) {
            button.setBackground(NUMBER_BTN);
        } else if (text.matches("[+\\-×÷=]")) {
            button.setBackground(OPERATOR_BTN);
        } else {
            button.setBackground(FUNCTION_BTN);
        }

        button.setForeground(TEXT_COLOR);
        button.addActionListener(new ButtonClickListener());

        // Hover effect
        button.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                button.setBackground(button.getBackground().brighter());
            }
            public void mouseExited(MouseEvent e) {
                if (text.matches("[0-9.]")) {
                    button.setBackground(NUMBER_BTN);
                } else if (text.matches("[+\\-×÷=]")) {
                    button.setBackground(OPERATOR_BTN);
                } else {
                    button.setBackground(FUNCTION_BTN);
                }
            }
        });

        return button;
    }

    private void createHistoryPanel() {
        JPanel historyPanel = new JPanel(new BorderLayout());
        historyPanel.setBackground(BACKGROUND);
        historyPanel.setBorder(new EmptyBorder(0, 10, 10, 10));
        historyPanel.setPreferredSize(new Dimension(250, 0));

        JLabel historyLabel = new JLabel("History");
        historyLabel.setFont(new Font("Arial", Font.BOLD, 14));
        historyLabel.setForeground(TEXT_COLOR);
        historyLabel.setBorder(new EmptyBorder(5, 5, 5, 5));

        historyArea = new JTextArea();
        historyArea.setEditable(false);
        historyArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        historyArea.setBackground(DISPLAY_BG);
        historyArea.setForeground(TEXT_COLOR);
        historyArea.setBorder(new EmptyBorder(5, 5, 5, 5));

        JScrollPane scrollPane = new JScrollPane(historyArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70), 1));

        historyPanel.add(historyLabel, BorderLayout.NORTH);
        historyPanel.add(scrollPane, BorderLayout.CENTER);

        add(historyPanel, BorderLayout.EAST);
    }

    private void toggleScientificMode() {
        scientificMode = !scientificMode;
        scientificPanel.setVisible(scientificMode);
        pack();
    }

    private void clearHistory() {
        history.clear();
        historyArea.setText("");
    }

    private void addToHistory(String expression) {
        history.add(expression);
        historyArea.append(expression + "\n");
        historyArea.setCaretPosition(historyArea.getDocument().getLength());
    }

    class ButtonClickListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            String command = e.getActionCommand();

            try {
                if (command.matches("[0-9]")) {
                    handleNumber(command);
                } else if (command.equals(".")) {
                    handleDecimal();
                } else if (command.matches("[+\\-×÷]")) {
                    handleOperator(command);
                } else if (command.equals("=")) {
                    handleEquals();
                } else if (command.equals("C")) {
                    handleClear();
                } else if (command.equals("⌫")) {
                    handleBackspace();
                } else if (command.equals("±")) {
                    handlePlusMinus();
                } else if (command.equals("%")) {
                    handlePercent();
                } else {
                    handleScientific(command);
                }
            } catch (Exception ex) {
                displayField.setText("Error");
                startNewNumber = true;
            }
        }
    }

    private void handleNumber(String num) {
        if (startNewNumber) {
            currentInput = new StringBuilder(num);
            startNewNumber = false;
        } else {
            currentInput.append(num);
        }
        displayField.setText(currentInput.toString());
    }

    private void handleDecimal() {
        if (startNewNumber) {
            currentInput = new StringBuilder("0.");
            startNewNumber = false;
        } else if (!currentInput.toString().contains(".")) {
            currentInput.append(".");
        }
        displayField.setText(currentInput.toString());
    }

    private void handleOperator(String op) {
        if (!operator.isEmpty()) {
            handleEquals();
        } else {
            result = Double.parseDouble(currentInput.toString());
        }
        operator = op;
        startNewNumber = true;
    }

    private void handleEquals() {
        if (operator.isEmpty()) return;

        double currentValue = Double.parseDouble(currentInput.toString());
        String expression = result + " " + operator + " " + currentValue + " = ";

        switch (operator) {
            case "+":
                result += currentValue;
                break;
            case "-":
                result -= currentValue;
                break;
            case "×":
                result *= currentValue;
                break;
            case "÷":
                if (currentValue != 0) {
                    result /= currentValue;
                } else {
                    displayField.setText("Cannot divide by 0");
                    return;
                }
                break;
        }

        expression += result;
        addToHistory(expression);

        displayField.setText(String.valueOf(result));
        currentInput = new StringBuilder(String.valueOf(result));
        operator = "";
        startNewNumber = true;
    }

    private void handleClear() {
        currentInput = new StringBuilder("0");
        result = 0;
        operator = "";
        startNewNumber = true;
        displayField.setText("0");
    }

    private void handleBackspace() {
        if (currentInput.length() > 0 && !startNewNumber) {
            currentInput.deleteCharAt(currentInput.length() - 1);
            if (currentInput.length() == 0) {
                currentInput.append("0");
            }
            displayField.setText(currentInput.toString());
        }
    }

    private void handlePlusMinus() {
        double value = Double.parseDouble(currentInput.toString());
        value *= -1;
        currentInput = new StringBuilder(String.valueOf(value));
        displayField.setText(currentInput.toString());
    }

    private void handlePercent() {
        double value = Double.parseDouble(currentInput.toString());
        value /= 100;
        currentInput = new StringBuilder(String.valueOf(value));
        displayField.setText(currentInput.toString());
        startNewNumber = true;
    }

    private void handleScientific(String function) {
        double value = Double.parseDouble(currentInput.toString());
        double resultValue = 0;
        String expression = "";

        switch (function) {
            case "sin":
                resultValue = Math.sin(Math.toRadians(value));
                expression = "sin(" + value + "°) = " + resultValue;
                break;
            case "cos":
                resultValue = Math.cos(Math.toRadians(value));
                expression = "cos(" + value + "°) = " + resultValue;
                break;
            case "tan":
                resultValue = Math.tan(Math.toRadians(value));
                expression = "tan(" + value + "°) = " + resultValue;
                break;
            case "√":
                resultValue = Math.sqrt(value);
                expression = "√" + value + " = " + resultValue;
                break;
            case "x²":
                resultValue = Math.pow(value, 2);
                expression = value + "² = " + resultValue;
                break;
            case "ln":
                resultValue = Math.log(value);
                expression = "ln(" + value + ") = " + resultValue;
                break;
            case "log":
                resultValue = Math.log10(value);
                expression = "log(" + value + ") = " + resultValue;
                break;
            case "eˣ":
                resultValue = Math.exp(value);
                expression = "e^" + value + " = " + resultValue;
                break;
            case "π":
                resultValue = Math.PI;
                expression = "π = " + resultValue;
                break;
            case "e":
                resultValue = Math.E;
                expression = "e = " + resultValue;
                break;
            case "!":
                resultValue = factorial((int) value);
                expression = (int)value + "! = " + resultValue;
                break;
            case "1/x":
                if (value != 0) {
                    resultValue = 1.0 / value;
                    expression = "1/" + value + " = " + resultValue;
                } else {
                    displayField.setText("Cannot divide by 0");
                    return;
                }
                break;
            case "(":
                currentInput.append("(");
                displayField.setText(currentInput.toString());
                return;
            case ")":
                currentInput.append(")");
                displayField.setText(currentInput.toString());
                return;
            case "xʸ":
                operator = "^";
                result = value;
                startNewNumber = true;
                return;
        }

        if (operator.equals("^")) {
            resultValue = Math.pow(result, value);
            expression = result + "^" + value + " = " + resultValue;
            operator = "";
        }

        addToHistory(expression);
        currentInput = new StringBuilder(String.valueOf(resultValue));
        displayField.setText(currentInput.toString());
        startNewNumber = true;
    }

    private double factorial(int n) {
        if (n < 0) return Double.NaN;
        if (n == 0 || n == 1) return 1;
        double result = 1;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }
        return result;
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            AdvancedCalculator calc = new AdvancedCalculator();
            calc.setVisible(true);
        });
    }
}