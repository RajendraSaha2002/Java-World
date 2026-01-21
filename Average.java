import java.util.*;

class Average {
    public static void main(String args[]) {
        Scanner sc = new Scanner(System.in);
        int n = args.length;

        // Safety check: Ensure arguments are provided to avoid crashes
        if (n == 0) {
            System.out.println("Please provide numbers as command line arguments.");
            System.out.println("Usage: java Average 10 20 30 40");
            return; // Exit program
        }

        // Parse arguments into an integer array once to avoid repeated parsing
        int[] numbers = new int[n];
        int sum = 0;

        try {
            for (int i = 0; i < n; i++) {
                numbers[i] = Integer.parseInt(args[i]);
                sum += numbers[i];
            }
        } catch (NumberFormatException e) {
            System.out.println("Error: Arguments must be valid integers.");
            return;
        }

        System.out.println("--- Array Operations Menu ---");
        System.out.println("1 - Sum");
        System.out.println("2 - Average");
        System.out.println("3 - Minimum");
        System.out.println("4 - Maximum");
        System.out.print("Enter Your Choice: ");

        if (sc.hasNextInt()) {
            int choice = sc.nextInt();

            switch (choice) {
                case 1:
                    System.out.println("The Sum is: " + sum);
                    break;
                case 2:
                    // Cast to double for accurate decimal results
                    double average = (double) sum / n;
                    System.out.println("The Average is: " + average);
                    break;
                case 3:
                    int min = numbers[0]; // Initialize with first element
                    for (int i = 1; i < n; i++) {
                        if (numbers[i] < min) {
                            min = numbers[i];
                        }
                    }
                    System.out.println("The Minimum is: " + min);
                    break;
                case 4:
                    int max = numbers[0]; // Initialize with first element
                    for (int i = 1; i < n; i++) {
                        if (numbers[i] > max) {
                            max = numbers[i];
                        }
                    }
                    System.out.println("The Maximum is: " + max);
                    break;
                default:
                    System.out.println("Invalid choice. Please select 1-4.");
            }
        } else {
            System.out.println("Invalid input. Please enter a number.");
        }

        sc.close();
    }
}