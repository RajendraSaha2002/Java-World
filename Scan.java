import java.util.Scanner;

public class Scan {
    public static void main(String[] args) {
        Scanner kb=new Scanner(System.in);
        System.out.print("Enter a number: ");
        int x=kb.nextInt();
        System.out.println("Number: "+x);
    }
}
