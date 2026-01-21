class MyClass {
    private int alpha; // private access
    int beta;           // default access
    public int gamma;   // public access

    public MyClass() {
    }

    // setter for alpha
    void setAlpha(int a) {
        alpha = a;
    }

    // getter for alpha
    int getAlpha() {
        return alpha;
    }
}

class AccessDemo {
    public static void main(String[] args) {
        MyClass ob = new MyClass();

        ob.setAlpha(-99);
        System.out.println("ob.alpha is " + ob.getAlpha());

        ob.beta = 88;
        ob.gamma = 99;
    }
}
