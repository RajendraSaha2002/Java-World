public class Phone {
    public static void main(String[] args) {
        String[][] numbers =
                {
                        {"Rajendra", "555-3322"},
                        {"Suchismita","555-8976"},
                        {"Sourav", "555-1037"},
                        {"Soumita", "555-1400"}
                };
        int i;
        if(args.length!=1)
            System.out.println("Usage: java Phone numbers");
        else {
            for(i=0;i<numbers.length;i++)
            {
                if(numbers[i][0].equals(args[0])){
                    System.out.println(numbers[i][0]+":"+numbers[i][1]);
                    break;
                }
            }
            if(i==numbers.length)
                System.out.println("Name not found.");
        }
    }
}
