import java.io.*;
import java.util.Scanner;

public class RiddleFileReader {

    private Scanner fileScan;
    private Scanner kbScan;

    public RiddleFileReader(String fName) {

        kbScan = new Scanner(System.in);

        try {
            File theFile = new File(fName);
            fileScan = new Scanner(theFile);

            // Read line-by-line
            fileScan.useDelimiter("\r\n|\n");
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Riddle readRiddle() {

        String ques = null;
        String ans = null;

        if (fileScan.hasNext())
            ques = fileScan.next().trim();

        if (fileScan.hasNext())
            ans = fileScan.next().trim();

        if (ques == null || ans == null)
            return null;

        return new Riddle(ques, ans);
    }

    public void displayRiddle(Riddle riddle) {

        System.out.println(riddle.getQuestion());
        System.out.println("Press Enter to see the answer...");

        kbScan.nextLine();   // Wait for user input

        System.out.println("Answer: " + riddle.getAnswer());
        System.out.println();
    }
}
