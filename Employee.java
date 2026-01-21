class Employee implements Comparable<Employee> {
    String name;
    double salary;

    Employee(String name, double salary) {
        this.name = name;
        this.salary = salary;
    }

    @Override
    public int compareTo(Employee e) {
        return Double.compare(this.salary, e.salary);
    }

    @Override
    public String toString() {
        return name + " - " + salary;
    }
}
