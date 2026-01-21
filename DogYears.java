import java.awt.*;
import javax.swing.*;
import java.awt.event.*;

class DogYears extends JFrame {

    private JTextField humanYearsTF = new JTextField(3);
    private JTextField dogYearsTF = new JTextField(3);

    public DogYears() {

        JButton convertBtn = new JButton("Convert");
        JPanel content = new JPanel();
        content.setLayout(new FlowLayout());

        content.add(new JLabel("Dog Years:"));
        content.add(dogYearsTF);
        content.add(convertBtn);

        content.add(new JLabel("Human Years:"));
        content.add(humanYearsTF);

        // Button Action
        convertBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    int dogYears = Integer.parseInt(dogYearsTF.getText());
                    int humanYears = dogYears * 7;   // simple formula
                    humanYearsTF.setText(String.valueOf(humanYears));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(null, "Enter a valid number!");
                }
            }
        });

        setContentPane(content);
        pack();
        setTitle("Dog Year Converter");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
    }

    public static void main(String[] args) {
        DogYears window = new DogYears();
        window.setVisible(true);
    }
}
