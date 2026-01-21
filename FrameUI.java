import java.awt.*;
import java.awt.event.*;

public class FrameUI extends Frame {

    // Constructor to setup the Frame and Components
    public FrameUI() {
        // 1. Set Frame properties
        setTitle("AWT Frame UI Example");
        setSize(400, 350);
        setLayout(new FlowLayout(FlowLayout.LEFT, 20, 20)); // Use FlowLayout for simple arrangement

        // 2. Create UI Components
        Label lblName = new Label("Enter Name:");
        TextField txtName = new TextField(20); // Width of 20 columns

        Label lblGender = new Label("Gender:");
        CheckboxGroup cbg = new CheckboxGroup();
        Checkbox cbMale = new Checkbox("Male", cbg, true);
        Checkbox cbFemale = new Checkbox("Female", cbg, false);

        Label lblCity = new Label("Select City:");
        Choice cityChoice = new Choice();
        cityChoice.add("New York");
        cityChoice.add("London");
        cityChoice.add("Tokyo");
        cityChoice.add("Mumbai");

        Label lblBio = new Label("Bio:");
        TextArea txtBio = new TextArea("Type here...", 5, 30);

        Button btnSubmit = new Button("Submit");
        Button btnCancel = new Button("Close");

        // 3. Add Components to the Frame
        add(lblName);
        add(txtName);

        add(lblGender);
        add(cbMale);
        add(cbFemale);

        add(lblCity);
        add(cityChoice);

        add(lblBio);
        add(txtBio);

        add(btnSubmit);
        add(btnCancel);

        // 4. Handle Window Closing (Crucial for AWT Frames)
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                dispose();
                System.exit(0);
            }
        });

        // 5. Handle Button Click (Optional simple interaction)
        btnCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dispose();
                System.exit(0);
            }
        });
    }

    // Main method to run the application
    public static void main(String[] args) {
        // Create the frame instance
        FrameUI app = new FrameUI();
        app.setVisible(true);
    }
}