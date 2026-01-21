public class Language {
    String name;
    Language(){
        System.out.println("Constructor method called.");
    }
    void Langauge(String t)
    {
        name=t;
    }
    public static void main(String[] args) {
        Language cpp = new Language();
        Language java = new Language();
        cpp.setName("C++");
        java.getName();
        cpp.getName();
    }
    private void getName() {
        System.out.println("Language name:" + name);
    }
    private void setName(String s) {
        name = s;
    }
}
