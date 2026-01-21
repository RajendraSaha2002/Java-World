public class NestedTry {
    public static void main(String args[]) {
        // 'a' stores the number of command line arguments
        int a = args.length;

        try {
            // If a is 0, this will throw an ArithmeticException (divide by zero)
            // This is caught by the OUTER catch block
            int d = 42 / a;

            System.out.println("a = " + a);

            try {
                // If a is 1, this block executes
                if (a == 1) {
                    // Divide by zero error: 1 / (1-1)
                    // This throws ArithmeticException
                    // It is NOT caught by the inner catch, so it propagates to the OUTER catch
                    int c = a / (a - a);
                }

                // If a is 2, this block executes
                if (a == 2) {
                    int c[] = { 2, 3, 4 };
                    // Array index out of bounds error (index 5 exists not in size 3 array)
                    // This throws ArrayIndexOutOfBoundsException
                    // This IS caught by the inner catch
                    c[5] = 90;
                }

            } catch (ArrayIndexOutOfBoundsException e) {
                // This block handles exceptions from the INNER try only
                System.out.println("Inner Catch: Array index out of bounds: " + e);
            }

        } catch (ArithmeticException e) {
            // This block handles exceptions from the OUTER try
            // OR exceptions propagated from the inner try (like when a=1)
            System.out.println("Outer Catch: Divide by 0: " + e);
        }
    }
}