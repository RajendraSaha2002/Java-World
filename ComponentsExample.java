import java.awt.*;
import java.awt.event.*;

public class ComponentsExample extends Frame implements ItemListener {

    // Declare components
    Choice fontChoice;
    Checkbox cbBold, cbItalic;     // Normal Checkboxes
    Checkbox rbRed, rbBlue;        // Radio Buttons
    CheckboxGroup colorGroup;      // Group for Radio Buttons
    Label displayLabel;            // Label to show changes

    public ComponentsExample() {
        // 1. Frame Setup
        setTitle("Choice, Checkbox & RadioButton Demo");
        setSize(400, 350);
        setLayout(new FlowLayout(FlowLayout.CENTER, 20, 20));

        // 2. Initialize Choice (Dropdown)
        fontChoice = new Choice();
        fontChoice.add("Arial");
        fontChoice.add("Times New Roman");
        fontChoice.add("Courier");
        fontChoice.add("Verdana");

        // 3. Initialize Checkboxes (Independent options)
        cbBold = new Checkbox("Bold");
        cbItalic = new Checkbox("Italic");

        // 4. Initialize Radio Buttons (Mutually exclusive options)
        // We must create a CheckboxGroup first
        colorGroup = new CheckboxGroup();
        // The 'false'/'true' indicates default selection
        rbRed = new Checkbox("Red", colorGroup, true);
        rbBlue = new Checkbox("Blue", colorGroup, false);

        // 5. Initialize Display Label
        displayLabel = new Label("Interact with components to see changes...");
        displayLabel.setSize(350, 50);
        displayLabel.setAlignment(Label.CENTER);

        // 6. Register Event Listeners (ItemListener)
        // ItemListener is best for checking state changes (checked/unchecked)
        fontChoice.addItemListener(this);
        cbBold.addItemListener(this);
        cbItalic.addItemListener(this);
        rbRed.addItemListener(this);
        rbBlue.addItemListener(this);

        // 7. Add Components to Frame
        add(new Label("Font:"));
        add(fontChoice);

        add(new Label("| Style:"));
        add(cbBold);
        add(cbItalic);

        add(new Label("| Color:"));
        add(rbRed);
        add(rbBlue);

        add(displayLabel);

        // 8. Handle Window Closing
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                dispose();
                System.exit(0);
            }
        });
    }

    // Event Handler: Called when any checkbox or choice state changes
    public void itemStateChanged(ItemEvent e) {
        String msg = "";

        // Get state of Choice
        msg += "Font: " + fontChoice.getSelectedItem();

        // Get state of Checkboxes
        msg += " | Bold: " + cbBold.getState();
        msg += " | Italic: " + cbItalic.getState();

        // Get state of Radio Buttons
        // We check which radio button is currently selected in the group
        Checkbox selectedRadio = colorGroup.getSelectedCheckbox();
        if (selectedRadio != null) {
            msg += " | Color: " + selectedRadio.getLabel();
        }

        // Update the display label
        displayLabel.setText(msg);

        // Optional: Actually change the label's font/color based on selection
        int style = Font.PLAIN;
        if (cbBold.getState()) style += Font.BOLD;
        if (cbItalic.getState()) style += Font.ITALIC;

        displayLabel.setFont(new Font(fontChoice.getSelectedItem(), style, 14));

        if (rbRed.getState()) displayLabel.setForeground(Color.RED);
        if (rbBlue.getState()) displayLabel.setForeground(Color.BLUE);
    }

    public static void main(String[] args) {
        ComponentsExample app = new ComponentsExample();
        app.setVisible(true);
    }
}