import java.util.Scanner;

public class FactorialDemo {
    public static void main(String[] args) {
        // Create scanner object to read input
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter a number to find its factorial: ");
        int num = scanner.nextInt();

        // Use long because factorial grows very quickly
        // long supports factorials up to 20!
        long factorial = 1;

        if (num < 0) {
            System.out.println("Factorial is not defined for negative numbers.");
        } else {
            // Logic to calculate factorial: n! = n * (n-1) * ... * 1
            for (int i = 1; i <= num; i++) {
                factorial = factorial * i;
            }

            System.out.println("The factorial of " + num + " is: " + factorial);
        }

        scanner.close();
    }
}