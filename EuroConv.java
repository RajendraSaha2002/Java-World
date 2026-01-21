public class EuroConv {
    public static void main(String[] args) {
        double eu;
        System.out.println("Euro conversion table:");
        System.out.println();
        for(eu=1;eu<101;eu++)
            System.out.println(eu+ "Euro is equivalent to Lm" + (eu*0.43));
    }
}
