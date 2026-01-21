/*
 PasswordGeneratorAdvanced.java
 - Secure password generation using SecureRandom
 - Options: include/exclude uppercase, lowercase, digits, symbols
 - Option to exclude confusing chars (O,0,l,1)
 - Generate multiple passwords
 - Save generated passwords to a file (append)
 - Basic password strength checker (length + character variety + entropy estimate)
 - Copy a chosen password to clipboard
 Usage:
   javac PasswordGeneratorAdvanced.java
   java PasswordGeneratorAdvanced
*/

import java.awt.*;
import java.awt.datatransfer.*;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class PasswordGeneratorAdvanced {

    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SYMBOLS = "!@#$%^&*()-_=+[]{};:,.<>?/|";

    // confusing characters commonly excluded
    private static final String CONFUSING = "O0l1I";

    private static final SecureRandom random = new SecureRandom();

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println("=== Advanced Password Generator ===");

        System.out.print("Password length (recommended >= 12): ");
        int length = safeReadInt(sc, 12);

        System.out.print("How many passwords to generate? ");
        int count = safeReadInt(sc, 1);

        boolean useUpper = askYesNo(sc, "Include uppercase letters (A-Z)? (y/n): ");
        boolean useLower = askYesNo(sc, "Include lowercase letters (a-z)? (y/n): ");
        boolean useDigits = askYesNo(sc, "Include digits (0-9)? (y/n): ");
        boolean useSymbols = askYesNo(sc, "Include symbols (!@#$...)? (y/n): ");
        boolean excludeConfusing = askYesNo(sc, "Exclude confusing characters (O,0,l,1,I)? (y/n): ");

        if (!useUpper && !useLower && !useDigits && !useSymbols) {
            System.out.println("Error: you must select at least one character type.");
            sc.close();
            return;
        }

        String pool = buildPool(useUpper, useLower, useDigits, useSymbols, excludeConfusing);
        if (pool.isEmpty()) {
            System.out.println("Character pool empty after applying exclude rules. Aborting.");
            sc.close();
            return;
        }

        List<String> generated = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String pwd = generatePassword(length, useUpper, useLower, useDigits, useSymbols, excludeConfusing, pool);
            generated.add(pwd);
        }

        System.out.println("\nGenerated passwords:");
        for (int i = 0; i < generated.size(); i++) {
            System.out.printf("[%d] %s%n", i + 1, generated.get(i));
        }

        // strength checks
        System.out.println("\nPassword strength summary:");
        for (int i = 0; i < generated.size(); i++) {
            String p = generated.get(i);
            StrengthResult r = checkStrength(p);
            System.out.printf("[%d] Strength: %s | Entropy(bits): %.1f | Suggestions: %s%n",
                    i + 1, r.label, r.entropyBits, r.suggestions);
        }

        // save to file?
        if (askYesNo(sc, "\nSave all generated passwords to file passwords.txt? (y/n): ")) {
            if (appendToFile("passwords.txt", generated)) {
                System.out.println("Saved to passwords.txt (appended).");
            } else {
                System.out.println("Failed to save file.");
            }
        }

        // copy one to clipboard?
        if (askYesNo(sc, "Copy one password to clipboard? (y/n): ")) {
            System.out.print("Enter index to copy (1 - " + generated.size() + "): ");
            int idx = safeReadInt(sc, 1);
            if (idx >= 1 && idx <= generated.size()) {
                boolean ok = copyToClipboard(generated.get(idx - 1));
                System.out.println(ok ? "Copied to clipboard." : "Failed to copy to clipboard.");
            } else {
                System.out.println("Invalid index.");
            }
        }

        System.out.println("Done. Stay secure!");
        sc.close();
    }

    // ---------- helpers ----------

    private static int safeReadInt(Scanner sc, int defaultVal) {
        String line = sc.nextLine().trim();
        if (line.isEmpty()) return defaultVal;
        try {
            return Math.max(1, Integer.parseInt(line));
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private static boolean askYesNo(Scanner sc, String prompt) {
        System.out.print(prompt);
        String res = sc.nextLine().trim();
        return res.equalsIgnoreCase("y") || res.equalsIgnoreCase("yes");
    }

    private static String buildPool(boolean u, boolean l, boolean d, boolean s, boolean excludeConfusing) {
        StringBuilder sb = new StringBuilder();
        if (u) sb.append(UPPER);
        if (l) sb.append(LOWER);
        if (d) sb.append(DIGITS);
        if (s) sb.append(SYMBOLS);
        String pool = sb.toString();
        if (excludeConfusing) {
            for (char c : CONFUSING.toCharArray()) {
                pool = pool.replace(String.valueOf(c), "");
            }
        }
        return pool;
    }

    private static String generatePassword(int length, boolean u, boolean l, boolean d, boolean s, boolean excludeConfusing, String pool) {
        StringBuilder pwd = new StringBuilder(length);

        // Guarantee at least one char from each selected type to improve strength
        List<Character> guaranteed = new ArrayList<>();
        if (u) guaranteed.add(randomCharFrom(filterString(UPPER, excludeConfusing)));
        if (l) guaranteed.add(randomCharFrom(filterString(LOWER, excludeConfusing)));
        if (d) guaranteed.add(randomCharFrom(filterString(DIGITS, excludeConfusing)));
        if (s) guaranteed.add(randomCharFrom(filterString(SYMBOLS, excludeConfusing)));

        // Add guaranteed chars first
        for (char ch : guaranteed) pwd.append(ch);

        // Fill remaining chars from common pool
        for (int i = pwd.length(); i < length; i++) {
            pwd.append(pool.charAt(random.nextInt(pool.length())));
        }

        // Shuffle the result to avoid predictable guaranteed positions
        return shuffleString(pwd.toString());
    }

    private static String filterString(String s, boolean excludeConfusing) {
        if (!excludeConfusing) return s;
        String res = s;
        for (char c : CONFUSING.toCharArray()) {
            res = res.replace(String.valueOf(c), "");
        }
        return res;
    }

    private static char randomCharFrom(String s) {
        if (s == null || s.isEmpty()) return 'x';
        return s.charAt(random.nextInt(s.length()));
    }

    private static String shuffleString(String input) {
        char[] a = input.toCharArray();
        for (int i = a.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = a[i];
            a[i] = a[j];
            a[j] = tmp;
        }
        return new String(a);
    }

    private static boolean appendToFile(String filename, List<String> lines) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filename, true))) {
            for (String l : lines) {
                bw.write(l);
                bw.newLine();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean copyToClipboard(String text) {
        try {
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            Clipboard clipboard = toolkit.getSystemClipboard();
            StringSelection strSel = new StringSelection(text);
            clipboard.setContents(strSel, null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- simple strength estimator ----------
    // Returns approximate entropy bits and a human label + suggestion
    private static StrengthResult checkStrength(String password) {
        int poolSize = 0;
        if (password.matches(".*[A-Z].*")) poolSize += 26;
        if (password.matches(".*[a-z].*")) poolSize += 26;
        if (password.matches(".*[0-9].*")) poolSize += 10;
        if (password.matches(".*[!@#$%^&*()\\-_=+\\[\\]{};:,.<>?/|].*")) poolSize += 32; // approximate symbol count

        // entropy approx: log2(poolSize^len) = len * log2(poolSize)
        double entropyBits = password.length() * (poolSize > 0 ? (Math.log(poolSize) / Math.log(2)) : 0);
        String label;
        String suggestions = "";

        if (entropyBits < 28) {
            label = "Very Weak";
            suggestions = "Make it longer and add variety (uppercase/lowercase/digits/symbols).";
        } else if (entropyBits < 36) {
            label = "Weak";
            suggestions = "Add length and character classes.";
        } else if (entropyBits < 60) {
            label = "Fair";
            suggestions = "Longer passphrases (>= 12 chars) or more symbol classes recommended.";
        } else if (entropyBits < 80) {
            label = "Strong";
            suggestions = "Good — still prefer >= 14 chars for long-term accounts.";
        } else {
            label = "Very Strong";
            suggestions = "Excellent.";
        }

        // small checks for human-readable feedback
        if (password.length() < 12) suggestions += " Use at least 12 characters.";
        if (password.matches("^[0-9]+$")) suggestions = "Do not use all digits. Use letters and symbols.";

        return new StrengthResult(label, entropyBits, suggestions.trim());
    }

    private static class StrengthResult {
        String label;
        double entropyBits;
        String suggestions;
        StrengthResult(String label, double entropyBits, String suggestions) {
            this.label = label;
            this.entropyBits = entropyBits;
            this.suggestions = suggestions;
        }
    }
}
