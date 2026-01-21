import javax.swing.*;
public class MyWindow2  extends JFrame {
    public static void main(String[] args) {
        MyWindow2 window = new MyWindow2();
        window.setVisible(true);
    }
    public MyWindow2() {
        setTitle("My Window Title using JFrame Subclass");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }
}