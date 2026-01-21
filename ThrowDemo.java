public class ThrowDemo {
    ThrowDemo() {
        try
        {
            throw new NullPointerException();
        } catch (NullPointerException e) {
            System.out.println("Caught in constructor");
            throw e;
        }
    }
    public static void main(String[] args) {
        try {
            ThrowDemo obj = new ThrowDemo();
        } catch (Exception e) {
            System.out.println("Caught in main");
        }
    }
}
 class ThrowDemo2 {
   void ThrowDemo() throws NullPointerException{
       System.out.println("in constructor");
       throw new NullPointerException();
   }
   public static void main(String[] args) {
       try {
            ThrowDemo2 obj = new ThrowDemo2();
       } catch (Exception e) {
           System.out.println("Caught in main");
       }
   }
 }
