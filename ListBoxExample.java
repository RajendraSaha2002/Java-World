import java.awt.*;
import java.awt.event.*;

public class ListBoxExample extends Frame implements ActionListener {

    // Declare components
    List subjectList;
    Label msgLabel;
    Button showButton;

    public ListBoxExample() {
        // 1. Setup the Frame
        setTitle("ListBox (List) Example");
        setSize(300, 250);
        setLayout(new FlowLayout());

        // 2. Create the ListBox (List)
        // new List(rows, multipleMode) -> 4 rows visible, multiple selection allowed
        subjectList = new List(4, true);

        // Add items to the ListBox
        subjectList.add("C++");
        subjectList.add("Java");
        subjectList.add("Python");
        subjectList.add("HTML/CSS");
        subjectList.add("JavaScript");
        subjectList.add("SQL");

        // 3. Create other UI components
        showButton = new Button("Show Selection");
        msgLabel = new Label("Select items and click button...");

        // Register listener
        showButton.addActionListener(this);

        // 4. Add components to Frame
        add(new Label("Select Programming Languages:"));
        add(subjectList);
        add(showButton);
        add(msgLabel);

        // 5. Handle Window Closing
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                dispose();
                System.exit(0);
            }
        });
    }

    // Handle button click to display selected items
    public void actionPerformed(ActionEvent e) {
        // Get selected items
        String[] selectedItems = subjectList.getSelectedItems();

        if (selectedItems.length == 0) {
            msgLabel.setText("No items selected.");
        } else {
            // Join selected items into a string
            String result = "Selected: ";
            for (String item : selectedItems) {
                result += item + " ";
            }
            msgLabel.setText(result);
        }
    }

    public static void main(String[] args) {
        ListBoxExample app = new ListBoxExample();
        app.setVisible(true);
    }
}