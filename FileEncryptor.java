import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Scanner;

/**
 * FileEncryptor.java
 *
 * CLI tool for encrypting and decrypting files using AES-GCM with password-based keys (PBKDF2WithHmacSHA256).
 *
 * Header format (binary, at beginning of encrypted file):
 * [4 bytes magic] 'FENC' (0x46 45 4E 43)
 * [1 byte version] (0x01)
 * [4 bytes iterations - int big-endian]
 * [1 byte salt length N] [N bytes salt]
 * [1 byte iv length M] [M bytes iv]
 * [then ciphertext bytes...]
 *
 * Usage:
 *  Encrypt:
 *    java FileEncryptor -e -in plain.txt -out plain.txt.enc
 *    (will prompt for password)
 *
 *  Decrypt:
 *    java FileEncryptor -d -in plain.txt.enc -out plain_decrypted.txt
 *    (will prompt for password)
 *
 *  Other flags:
 *    -p "password"     (not recommended on shared machines; prefer prompt)
 *    -iters 200000     (PBKDF2 iterations, default 200000)
 *    -help
 *
 * Notes:
 *  - Uses AES-GCM with 128-bit auth tag (default), 12-byte IV.
 *  - Key length is 256 bits when available; will fall back to 128 if necessary.
 *  - Header stores salt and iv so decryption can derive the same key/params.
 *  - Streams encryption/decryption in 16KB chunks and prints progress to stdout.
 */
public class FileEncryptor {
    private static final byte[] MAGIC = new byte[] {0x46, 0x45, 0x4E, 0x43}; // "FENC"
    private static final byte VERSION = 0x01;
    private static final int DEFAULT_ITERS = 200_000;
    private static final int SALT_LEN = 16; // bytes
    private static final int IV_LEN = 12;   // bytes for GCM recommended
    private static final int GCM_TAG_BITS = 128;
    private static final int BUFFER = 16 * 1024;

    public static void main(String[] args) {
        if (args.length == 0) {
            usage();
            return;
        }

        boolean encrypt = false, decrypt = false;
        Path in = null, out = null;
        String passwordArg = null;
        int iterations = DEFAULT_ITERS;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-e": encrypt = true; break;
                case "-d": decrypt = true; break;
                case "-in":
                    if (i + 1 < args.length) in = Paths.get(args[++i]);
                    break;
                case "-out":
                    if (i + 1 < args.length) out = Paths.get(args[++i]);
                    break;
                case "-p":
                    if (i + 1 < args.length) passwordArg = args[++i];
                    break;
                case "-iters":
                    if (i + 1 < args.length) iterations = Integer.parseInt(args[++i]);
                    break;
                case "-help":
                case "--help":
                    usage(); return;
                default:
                    System.err.println("Unknown arg: " + args[i]);
                    usage();
                    return;
            }
        }

        if (encrypt == decrypt) {
            System.err.println("Specify exactly one of -e (encrypt) or -d (decrypt).");
            usage();
            return;
        }
        if (in == null || out == null) {
            System.err.println("Missing -in or -out.");
            usage();
            return;
        }

        try {
            char[] password = getPassword(passwordArg);
            if (encrypt) {
                encryptFile(in, out, password, iterations);
            } else {
                decryptFile(in, out, password);
            }
            // clear password chars
            Arrays.fill(password, '\0');
        } catch (Exception ex) {
            System.err.println("Error: " + ex.getMessage());
            ex.printStackTrace(System.err);
        }
    }

    private static char[] getPassword(String passwordArg) throws IOException {
        if (passwordArg != null) return passwordArg.toCharArray();
        Console console = System.console();
        if (console != null) {
            return console.readPassword("Enter password: ");
        } else {
            // fallback for IDEs (not secure as echo)
            System.out.print("Enter password (input will be visible): ");
            Scanner sc = new Scanner(System.in);
            String s = sc.nextLine();
            return s.toCharArray();
        }
    }

    private static void usage() {
        System.out.println("FileEncryptor - simple AES-GCM file encryptor (PBKDF2 key derivation)\n");
        System.out.println("Usage examples:");
        System.out.println("  Encrypt: java FileEncryptor -e -in secret.txt -out secret.txt.enc");
        System.out.println("  Decrypt: java FileEncryptor -d -in secret.txt.enc -out secret.txt.dec");
        System.out.println("\nOptions:");
        System.out.println("  -e               Encrypt");
        System.out.println("  -d               Decrypt");
        System.out.println("  -in <path>       Input file");
        System.out.println("  -out <path>      Output file");
        System.out.println("  -p <password>    Provide password inline (not recommended)");
        System.out.println("  -iters <n>       PBKDF2 iterations (default " + DEFAULT_ITERS + ")");
        System.out.println("  -help            Show this help");
    }

    // ---------- Encryption ----------
    public static void encryptFile(Path in, Path out, char[] password, int iterations) throws Exception {
        if (!Files.exists(in)) throw new FileNotFoundException("Input not found: " + in);
        long total = Files.size(in);

        SecureRandom rnd = SecureRandom.getInstanceStrong();
        byte[] salt = new byte[SALT_LEN]; rnd.nextBytes(salt);
        byte[] iv = new byte[IV_LEN]; rnd.nextBytes(iv);

        int keyLen = bestAvailableKeyLength();
        SecretKey key = deriveKey(password, salt, iterations, keyLen);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec gspec = new GCMParameterSpec(GCM_TAG_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, gspec);

        // Write header then ciphertext stream
        try (OutputStream fout = Files.newOutputStream(out, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
             BufferedOutputStream bout = new BufferedOutputStream(fout);
             CipherOutputStream cout = new CipherOutputStream(bout, cipher);
             InputStream fin = Files.newInputStream(in);
             BufferedInputStream bin = new BufferedInputStream(fin)) {

            writeHeader(bout, iterations, salt, iv);

            byte[] buffer = new byte[BUFFER];
            long processed = 0;
            int read;
            long lastPrint = System.currentTimeMillis();
            while ((read = bin.read(buffer)) != -1) {
                cout.write(buffer, 0, read);
                processed += read;
                long now = System.currentTimeMillis();
                if (now - lastPrint > 700) {
                    printProgress(processed, total, "Encrypting");
                    lastPrint = now;
                }
            }
            // ensure all data flushed & tag written
            cout.flush();
            // final progress
            printProgress(processed, total, "Encrypting");
            System.out.println("\nEncryption completed. Output: " + out.toAbsolutePath());
        }
    }

    // ---------- Decryption ----------
    public static void decryptFile(Path in, Path out, char[] password) throws Exception {
        if (!Files.exists(in)) throw new FileNotFoundException("Input not found: " + in);
        long total = Files.size(in);

        try (InputStream fin = Files.newInputStream(in);
             BufferedInputStream bin = new BufferedInputStream(fin)) {

            HeaderInfo header = readHeader(bin);
            long headerLen = header.headerLength;
            long ciphertextLen = Math.max(0, total - headerLen);

            SecretKey key = deriveKey(password, header.salt, header.iterations, bestAvailableKeyLength());

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec gspec = new GCMParameterSpec(GCM_TAG_BITS, header.iv);
            cipher.init(Cipher.DECRYPT_MODE, key, gspec);

            try (CipherInputStream cin = new CipherInputStream(bin, cipher);
                 OutputStream fout = Files.newOutputStream(out, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                 BufferedOutputStream bout = new BufferedOutputStream(fout)) {

                byte[] buffer = new byte[BUFFER];
                long processed = 0;
                int read;
                long lastPrint = System.currentTimeMillis();
                while ((read = cin.read(buffer)) != -1) {
                    bout.write(buffer, 0, read);
                    processed += read;
                    long now = System.currentTimeMillis();
                    if (now - lastPrint > 700) {
                        printProgress(processed, ciphertextLen, "Decrypting");
                        lastPrint = now;
                    }
                }
                bout.flush();
                printProgress(processed, ciphertextLen, "Decrypting");
                System.out.println("\nDecryption completed. Output: " + out.toAbsolutePath());
            }
        }
    }

    // ---------- Header helpers ----------
    private static class HeaderInfo {
        int iterations;
        byte[] salt;
        byte[] iv;
        long headerLength;
    }

    private static void writeHeader(OutputStream out, int iterations, byte[] salt, byte[] iv) throws IOException {
        // magic + version + iterations(int) + saltLen(1) + salt + ivLen(1) + iv
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        header.write(MAGIC);
        header.write(VERSION);
        header.write(ByteBuffer.allocate(4).putInt(iterations).array());
        header.write((byte) salt.length);
        header.write(salt);
        header.write((byte) iv.length);
        header.write(iv);
        byte[] h = header.toByteArray();
        out.write(h);
        out.flush();
    }

    private static HeaderInfo readHeader(InputStream in) throws IOException {
        // read magic (4) + version (1)
        HeaderInfo info = new HeaderInfo();
        byte[] buf4 = new byte[4];
        if (readFully(in, buf4) != 4) throw new IOException("Invalid file header (too short)");
        if (!Arrays.equals(buf4, MAGIC)) throw new IOException("Invalid file format (magic mismatch)");
        int v = in.read();
        if (v != VERSION) throw new IOException("Unsupported file version: " + v);

        // iterations (4 bytes)
        byte[] itb = new byte[4];
        if (readFully(in, itb) != 4) throw new IOException("Invalid header (missing iterations)");
        int iters = ByteBuffer.wrap(itb).getInt();
        info.iterations = iters;

        int saltLen = in.read();
        if (saltLen <= 0) throw new IOException("Invalid salt length");
        byte[] salt = new byte[saltLen];
        if (readFully(in, salt) != saltLen) throw new IOException("Incomplete salt");
        info.salt = salt;

        int ivLen = in.read();
        if (ivLen <= 0) throw new IOException("Invalid iv length");
        byte[] iv = new byte[ivLen];
        if (readFully(in, iv) != ivLen) throw new IOException("Incomplete iv");
        info.iv = iv;

        // compute header length: 4 +1 +4 +1 + saltLen +1 + ivLen
        info.headerLength = 4 + 1 + 4 + 1 + saltLen + 1 + ivLen;
        return info;
    }

    private static int readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int r = in.read(buf, off, buf.length - off);
            if (r == -1) break;
            off += r;
        }
        return off;
    }

    // ---------- KDF & key helpers ----------
    private static SecretKey deriveKey(char[] password, byte[] salt, int iterations, int keyLenBits) throws NoSuchAlgorithmException, InvalidKeySpecException {
        String algo = "PBKDF2WithHmacSHA256";
        SecretKeyFactory skf = SecretKeyFactory.getInstance(algo);
        KeySpec spec = new PBEKeySpec(password, salt, iterations, keyLenBits);
        byte[] keyBytes = skf.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    private static int bestAvailableKeyLength() {
        try {
            // request a 256-bit key; check max allowed
            int max = Cipher.getMaxAllowedKeyLength("AES");
            return Math.min(max, 256);
        } catch (NoSuchAlgorithmException e) {
            return 128;
        }
    }

    // ---------- Utils ----------
    private static void printProgress(long processed, long total, String label) {
        if (total <= 0) {
            System.out.printf("\r%s: %d bytes processed", label, processed);
        } else {
            int pct = (int) ((processed * 100) / Math.max(1, total));
            System.out.printf("\r%s: %s / %s (%d%%)", label, humanizeBytes(processed), humanizeBytes(total), pct);
        }
        System.out.flush();
    }

    private static String humanizeBytes(long b) {
        if (b < 1024) return b + " B";
        int z = (63 - Long.numberOfLeadingZeros(b)) / 10;
        return String.format("%.1f %sB", b / Math.pow(1024, z), " KMGTPE".charAt(z));
    }
}
