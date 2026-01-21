import java.nio.file.*;
import java.util.Arrays;

/**
 * Basic test runner for FileEncryptor.
 *  - Creates a temporary file with sample text
 *  - Encrypts -> Decrypts -> Verifies the decrypted content equals original
 *
 * Run:
 *   javac FileEncryptorTest.java
 *   java FileEncryptorTest
 */
public class FileEncryptorTest {
    public static void main(String[] args) throws Exception {
        Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir"));
        Path plain = tmpDir.resolve("fe_test_plain.txt");
        Path enc = tmpDir.resolve("fe_test_plain.txt.enc");
        Path dec = tmpDir.resolve("fe_test_plain.txt.dec.txt");

        String sample = "Hello — this is a sample text for testing FileEncryptor.\nLine2: ☺\n";
        Files.write(plain, sample.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        char[] password = "TestPassword123!".toCharArray();

        System.out.println("Plain file: " + plain);
        FileEncryptor.encryptFile(plain, enc, password, 100_000);
        System.out.println();

        FileEncryptor.decryptFile(enc, dec, password);
        System.out.println();

        byte[] orig = Files.readAllBytes(plain);
        byte[] got = Files.readAllBytes(dec);

        if (Arrays.equals(orig, got)) {
            System.out.println("TEST PASSED: decrypted content matches original.");
        } else {
            System.err.println("TEST FAILED: mismatch!");
        }

        // cleanup (optional)
        // Files.deleteIfExists(plain); Files.deleteIfExists(enc); Files.deleteIfExists(dec);
    }
}
