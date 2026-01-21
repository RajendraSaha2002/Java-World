import javax.swing.*;
public class SwingInput {
    public static void main(String[] args)
    {
        String temp;
        temp =JOptionPane.showInputDialog(null,"Enter First Number:");
       int a = Integer.parseInt(temp);
       temp =JOptionPane.showInputDialog(null,"Enter Second Name:");
       int b = Integer.parseInt(temp);
       temp =JOptionPane.showInputDialog(null,"Enter Third Number:");
       int c = Integer.parseInt(temp);
       JOptionPane.showMessageDialog(null,"Average is " + (a+b+c)/3);
    }
}
