/*
 * INSTRUCTIONS TO RUN:
 * 1. Save this file as CalculatorApplet.java
 * 2. Compile it: javac CalculatorApplet.java
 * 3. Run it using appletviewer: appletviewer CalculatorApplet.java
 */

/*
<applet code="CalculatorApplet.class" width="300" height="300">
</applet>
*/

import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;

public class CalculatorApplet extends Applet implements ActionListener {

    // Declare components
    TextField txtNum1, txtNum2, txtResult;
    Label lblNum1, lblNum2, lblResult;
    Button btnAdd, btnSub, btnMul, btnDiv, btnClear;

    // This method is called when the Applet is loaded
    public void init() {
        // Set the layout of the applet
        setLayout(new GridLayout(5, 2, 10, 10)); // 5 rows, 2 cols, gaps

        // Initialize components
        lblNum1 = new Label("First Number:");
        txtNum1 = new TextField(10);

        lblNum2 = new Label("Second Number:");
        txtNum2 = new TextField(10);

        lblResult = new Label("Result:");
        txtResult = new TextField(10);
        txtResult.setEditable(false); // Make result read-only

        // Initialize buttons
        btnAdd = new Button("+");
        btnSub = new Button("-");
        btnMul = new Button("*");
        btnDiv = new Button("/");
        btnClear = new Button("Clear");

        // Register event listeners
        btnAdd.addActionListener(this);
        btnSub.addActionListener(this);
        btnMul.addActionListener(this);
        btnDiv.addActionListener(this);
        btnClear.addActionListener(this);

        // Add components to the Applet window
        add(lblNum1);
        add(txtNum1);

        add(lblNum2);
        add(txtNum2);

        add(lblResult);
        add(txtResult);

        add(btnAdd);
        add(btnSub);

        add(btnMul);
        add(btnDiv);

        // Add clear button separately or manage layout to fit it
        // For simplicity in a 5x2 grid, we replace the last slot or add to end
        add(btnClear);
        add(new Label("")); // Empty placeholder for grid balance
    }

    // This method handles button clicks
    public void actionPerformed(ActionEvent e) {
        try {
            double n1 = 0, n2 = 0, result = 0;

            // Handle Clear button first (no need to parse numbers)
            if (e.getSource() == btnClear) {
                txtNum1.setText("");
                txtNum2.setText("");
                txtResult.setText("");
                return;
            }

            // Parse inputs
            n1 = Double.parseDouble(txtNum1.getText());
            n2 = Double.parseDouble(txtNum2.getText());

            // Check which button was clicked
            if (e.getSource() == btnAdd) {
                result = n1 + n2;
            } else if (e.getSource() == btnSub) {
                result = n1 - n2;
            } else if (e.getSource() == btnMul) {
                result = n1 * n2;
            } else if (e.getSource() == btnDiv) {
                if (n2 == 0) {
                    txtResult.setText("Cannot div by 0");
                    return;
                }
                result = n1 / n2;
            }

            // Display result
            txtResult.setText(String.valueOf(result));

        } catch (NumberFormatException ex) {
            txtResult.setText("Invalid Input");
        }
    }
}