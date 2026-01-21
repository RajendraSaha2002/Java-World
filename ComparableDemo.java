import java.util.*;

public class ComparableDemo {
    public static void main(String[] args) {

        List<Employee> list = new ArrayList<>();

        list.add(new Employee("Rajendra", 40000));
        list.add(new Employee("Suchismita", 20000));
        list.add(new Employee("Samrita", 50000));
        list.add(new Employee("Sanny", 70000));

        Collections.sort(list);

        for (Employee e : list) {
            System.out.println(e);
        }
    }
}
