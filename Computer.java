public class Computer {
    Computer(){
        System.out.println("Constructor of Computer class.");
    }
    void computer_method(){
        System.out.println("Power gone! Shut down your PC soon...");
    }
    public static void main(String[] args) {
        Computer computer = new Computer();
        computer.computer_method();
    }
}
class laptop{
    laptop(){
        System.out.println("Constructor of laptop class.");
    }
    void laptop_method(){
        System.out.println("99% Battery available");
    }
}
