
import java.util.*;

record Person(String name) implements Comparable<Person> {
    @Override
    public int compareTo(Person other) {
        return this.name.compareTo(other.name);
    }

    @Override
    public String toString() {
        return name;
    }
}

public class Collection_Interface {
    public static void main(String[] args) {
        Collection<Person> persons = new LinkedList<Person>();
        persons.add(new Person("Suchismita"));
        System.out.println(persons.size());
        Collection<Person> copy = new TreeSet<Person>(persons);
        Person[] array = copy.toArray(new Person[0]);
        System.out.println(array[0]);
    }
}