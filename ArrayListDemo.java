import java.util.ArrayList;
public class ArrayListDemo {
    public static void main(String[] args) {
        ArrayList a1=new ArrayList();
        System.out.println("Intial size of a1:"+a1.size());
        System.out.println("\n");
        a1.add("C");
        a1.add("A");
        a1.add("E");
        a1.add("B");
        a1.add("D");
        a1.add("F");
        a1.add(1,"A2");
        System.out.println("size of a1 after" +a1.size());
        System.out.println("\n");
        a1.remove("F");
        a1.remove("2");
        System.out.println("size after deletions" +a1.size());
        System.out.println("\n");
        System.out.println("controls of a1:"+a1);
    }
}
