import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class writer {
    public static void main(String[] args) throws IOException {
        String s="hello";
        byte b[]=s.getBytes();
        FileOutputStream f=new FileOutputStream("file.txt");
        int i=0;
        while(i<b.length){
            f.write(b[i]);
            i++;
        }
    }
}
