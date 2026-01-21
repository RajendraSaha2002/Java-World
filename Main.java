public class Main {
    public static void main(String[] args) {

        RiddleFileReader rfr = new RiddleFileReader("riddles.txt");

        Riddle riddle = rfr.readRiddle();

        while (riddle != null) {
            rfr.displayRiddle(riddle);
            riddle = rfr.readRiddle();
        }
    }
}
