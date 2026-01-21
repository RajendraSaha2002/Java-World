import java.io.IOException;
import java.util.*;
public class hcf {
    public static void main(String args[]) throws IOException {
        int a, b;
        Scanner sc = new Scanner(System.in);
        System.out.println("Enter two number:");
        a=sc.nextInt();
        b=sc.nextInt();
        int big;
        int small;
        if(a>b)
        {
            small=a;
            big=b;
        }
        else
        {
            small=b;
            big=a;
        }
        for(int i=1;i<=big;i++)
        {
            if(((big*i)%small)==0)
            {
                int lcm=big*i;
                System.out.println("The least common multiple is" +(lcm));
                break;
            }
        }
        int temp=1;
        while(temp!=0)
        {
            temp=big%small;
            if(temp==0)
            {
                System.out.println("GCD is" + small);
            }
            else {
                big=small;
                small=temp;
            }
        }
    }
}
