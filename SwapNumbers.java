import java.util.Scanner;
public class SwapNumbers {
    public static void main(String[] args) {
        int x,y;
        System.out.print("Enter x & y:");
        Scanner input = new Scanner(System.in);
        x = input.nextInt();
        y = input.nextInt();
        System.out.println("Before Swapping \nx = " + x + "\ny = " + y);
        int temp = x;
        x=y;
        y=x;
        System.out.println("After Swapping \nx = " + x + "\ny = " + y);
    }
}
