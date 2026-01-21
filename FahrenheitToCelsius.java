import java.util.*;
public class FahrenheitToCelsius {
    public static void main(String[] args) {
        float temp;
        Scanner input = new Scanner(System.in);
        System.out.print("Enter Fahrenheit: ");
        temp = input.nextFloat();
        temp = (temp - 32) * 5 / 9;
        System.out.println("temp in Celsius: " + temp);
    }
}
