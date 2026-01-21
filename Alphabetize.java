import java.util.ArrayList;
import java.util.Collections;
import java.util.Scanner;

public class Alphabetize {
    public static void main(String[] args) {
        Scanner input = new Scanner(System.in);
        ArrayList<String> words = new ArrayList<String>();
        System.out.println("Enter words.End with EOF(CTRL-Z then Enter)");
        while(input.hasNextLine())
        {
            words.add(input.nextLine());
        }
        Collections.sort(words);
        System.out.println("\n\nSorted words\n");
        for(String word:words)
        {
            System.out.println(word);
        }
    }
}
