import java.util.Scanner;

public class Example11 {
    public static void main(String[] args) {
        int a, b, c;
        a=2;
        b=3;
        c=a-b;
        if(c>=0) System.out.println("c is a positive number");
        if(c<0) System.out.println("c is a negative number");
        System.out.println();
        c=b-a;
        if(c>=0) System.out.println("c is a positive number");
        if(c<0) System.out.println("c is a negative number");
    }
}
