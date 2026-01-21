import java.io.IOException;
public class Guess3 {
    public static void main(String[] args) throws IOException {
        char ch;
        char answer ='S';
        System.out.println("I'm thinking of a letter between A and Z.");
        System.out.println("Can you guess it:");
        ch=(char) System.in.read();
        if(ch==answer) System.out.println("**Right**");
        else
        {
            System.out.println("...Sorry, you're");
            if(ch<answer) System.out.println("too low");
            else System.out.println("too high");
        }
    }
}
