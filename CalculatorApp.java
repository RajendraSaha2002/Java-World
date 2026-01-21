import java.util.Scanner;

/**
 * CalculatorApp is a simple command-line application that performs basic arithmetic operations.
 * This project is excellent for beginners to practice:
 * 1. Taking user input (Scanner).
 * 2. Using conditional statements (switch or if/else).
 * 3. Handling basic arithmetic.
 * 4. Error handling (like division by zero).
 */
public class CalculatorApp {

    public static void main(String[] args) {
        // Create a Scanner object to read user input from the console
        Scanner scanner = new Scanner(System.in);

        System.out.println("--- Simple Java Console Calculator ---");

        try {
            // 1. Get the first number
            System.out.print("Enter first number (num1): ");
            // We use nextDouble to allow for floating-point numbers
            double num1 = scanner.nextDouble();

            // 2. Get the operation
            System.out.print("Enter operation (+, -, *, /): ");
            // We use next() to read the operation string and charAt(0) to get the first character
            char operation = scanner.next().charAt(0);

            // 3. Get the second number
            System.out.print("Enter second number (num2): ");
            double num2 = scanner.nextDouble();

            double result = 0;
            boolean validOperation = true;

            // Use a switch statement to perform the calculation based on the operator
            switch (operation) {
                case '+':
                    result = num1 + num2;
                    break;
                case '-':
                    result = num1 - num2;
                    break;
                case '*':
                    result = num1 * num2;
                    break;
                case '/':
                    // Handle division by zero error specifically
                    if (num2 == 0) {
                        System.out.println("Error: Division by zero is not allowed.");
                        validOperation = false; // Mark as invalid to prevent displaying result
                    } else {
                        result = num1 / num2;
                    }
                    break;
                default:
                    // Handle unknown operator
                    System.out.println("Error: Invalid operation. Please use +, -, *, or /.");
                    validOperation = false;
                    break;
            }

            // Display the result only if the operation was valid
            if (validOperation) {
                // Use printf for formatted output, showing up to two decimal places
                System.out.printf("\nResult: %.2f %c %.2f = %.2f\n", num1, operation, num2, result);
            }

        } catch (java.util.InputMismatchException e) {
            // This catches errors if the user enters text instead of a number
            System.out.println("Error: Invalid input. Please ensure you enter numbers for the operands.");
        } finally {
            // Close the scanner object to prevent resource leaks
            scanner.close();
            System.out.println("\nCalculator session ended.");
        }
    }
}
