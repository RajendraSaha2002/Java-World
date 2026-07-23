import java.security.MessageDigest;
import java.util.LinkedList;

/*
 * Tamper-evident audit log using SHA-256 hash chaining.
 *
 * Features:
 * - LinkedList-backed audit entries
 * - SHA-256(content || previousHash)
 * - Append and verify-all
 * - Find exact first tampered entry
 * - Merkle-root batch verification
 * - Edit and deletion tampering demonstrations
 * - No JavaFX or external libraries
 */
public final class TamperEvidentAuditLog {

    public static void main(String[] args) {
        AuditLog log = createSampleLog();

        System.out.println("Initial audit log:");
        log.printEntries();

        VerificationResult result = log.verifyAll();
        System.out.println("\nChain valid: " + result.valid);

        MerkleSnapshot snapshot = log.createMerkleSnapshot();
        System.out.println("Merkle root: " + snapshot.rootHex);
        System.out.println("Merkle batch verification: "
                + log.verifyMerkleSnapshot(snapshot));

        demonstrateEditTampering();
        demonstrateDeletionTampering();
    }

    private static AuditLog createSampleLog() {
        AuditLog log = new AuditLog();

        log.append("LOGIN user=cyber ip=192.168.1.10");
        log.append("READ confidential-report.pdf");
        log.append("UPDATE report-status=reviewed");
        log.append("LOGOUT user=cyber");

        return log;
    }

    private static void demonstrateEditTampering() {
        AuditLog log = createSampleLog();
        MerkleSnapshot originalSnapshot = log.createMerkleSnapshot();

        System.out.println("\n--- Edit tampering demonstration ---");

        log.tamperContent(1, "READ confidential-report.pdf AND DELETE ALL FILES");

        VerificationResult result = log.verifyAll();

        System.out.println("Chain valid after edit: " + result.valid);
        System.out.println("First tampered entry index: " + result.entryIndex);
        System.out.println("Reason: " + result.reason);
        System.out.println("Merkle batch verification after edit: "
                + log.verifyMerkleSnapshot(originalSnapshot));
    }

    private static void demonstrateDeletionTampering() {
        AuditLog log = createSampleLog();
        MerkleSnapshot originalSnapshot = log.createMerkleSnapshot();

        System.out.println("\n--- Deletion tampering demonstration ---");

        log.deleteEntry(1);

        VerificationResult result = log.verifyAll();

        System.out.println("Chain valid after deletion: " + result.valid);
        System.out.println("First broken entry index: " + result.entryIndex);
        System.out.println("Reason: " + result.reason);
        System.out.println("Merkle batch verification after deletion: "
                + log.verifyMerkleSnapshot(originalSnapshot));
    }

    // ================================================================
    // Audit log
    // ================================================================

    static final class AuditLog {
        private final LinkedList<AuditEntry> entries = new LinkedList<AuditEntry>();

        public void append(String content) {
            if (content == null || content.length() == 0) {
                throw new IllegalArgumentException("Audit content cannot be empty");
            }

            String previousHash = entries.isEmpty()
                    ? zeros(64)
                    : entries.getLast().hash;

            int index = entries.size();
            String hash = calculateEntryHash(index, content, previousHash);

            entries.add(new AuditEntry(index, content, previousHash, hash));
        }

        /*
         * Verifies each entry in order.
         * Returns the exact first location where tampering is detected.
         */
        public VerificationResult verifyAll() {
            String expectedPreviousHash = zeros(64);

            for (int i = 0; i < entries.size(); i++) {
                AuditEntry entry = entries.get(i);

                if (entry.index != i) {
                    return new VerificationResult(
                            false,
                            i,
                            "Entry index was changed or an entry was deleted"
                    );
                }

                if (!entry.previousHash.equals(expectedPreviousHash)) {
                    return new VerificationResult(
                            false,
                            i,
                            "Previous-hash link is broken"
                    );
                }

                String expectedHash = calculateEntryHash(
                        entry.index,
                        entry.content,
                        entry.previousHash
                );

                if (!entry.hash.equals(expectedHash)) {
                    return new VerificationResult(
                            false,
                            i,
                            "Content or stored hash was changed"
                    );
                }

                expectedPreviousHash = entry.hash;
            }

            return new VerificationResult(true, -1, "Audit chain is valid");
        }

        /*
         * Creates a compact Merkle snapshot of all entry hashes.
         * A later comparison quickly shows whether any entry changed.
         */
        public MerkleSnapshot createMerkleSnapshot() {
            if (entries.isEmpty()) {
                return new MerkleSnapshot(sha256Hex("EMPTY_AUDIT_LOG"), 0);
            }

            String[] hashes = new String[entries.size()];

            for (int i = 0; i < entries.size(); i++) {
                hashes[i] = entries.get(i).hash;
            }

            return new MerkleSnapshot(calculateMerkleRoot(hashes), hashes.length);
        }

        public boolean verifyMerkleSnapshot(MerkleSnapshot snapshot) {
            if (snapshot == null) {
                return false;
            }

            MerkleSnapshot current = createMerkleSnapshot();

            return current.entryCount == snapshot.entryCount
                    && constantTimeEquals(current.rootHex, snapshot.rootHex);
        }

        public void printEntries() {
            for (int i = 0; i < entries.size(); i++) {
                AuditEntry entry = entries.get(i);

                System.out.println("Entry #" + entry.index);
                System.out.println("  Content       : " + entry.content);
                System.out.println("  Previous hash : " + entry.previousHash);
                System.out.println("  Entry hash    : " + entry.hash);
            }
        }

        // Methods below simulate an attacker modifying stored history.

        public void tamperContent(int index, String replacementContent) {
            checkIndex(index);
            entries.get(index).content = replacementContent;
        }

        public void deleteEntry(int index) {
            checkIndex(index);
            entries.remove(index);
        }

        private void checkIndex(int index) {
            if (index < 0 || index >= entries.size()) {
                throw new IllegalArgumentException("Invalid audit entry index");
            }
        }
    }

    static final class AuditEntry {
        int index;
        String content;
        String previousHash;
        String hash;

        AuditEntry(int index, String content, String previousHash, String hash) {
            this.index = index;
            this.content = content;
            this.previousHash = previousHash;
            this.hash = hash;
        }
    }

    static final class VerificationResult {
        final boolean valid;
        final int entryIndex;
        final String reason;

        VerificationResult(boolean valid, int entryIndex, String reason) {
            this.valid = valid;
            this.entryIndex = entryIndex;
            this.reason = reason;
        }
    }

    static final class MerkleSnapshot {
        final String rootHex;
        final int entryCount;

        MerkleSnapshot(String rootHex, int entryCount) {
            this.rootHex = rootHex;
            this.entryCount = entryCount;
        }
    }

    // ================================================================
    // Hash chain and Merkle tree
    // ================================================================

    /*
     * SHA-256(index || content || previousHash).
     * Length separators prevent ambiguous concatenation.
     */
    private static String calculateEntryHash(
            int index, String content, String previousHash) {

        String encoded = "index=" + index
                + "|contentLength=" + content.length()
                + "|content=" + content
                + "|previousHash=" + previousHash;

        return sha256Hex(encoded);
    }

    private static String calculateMerkleRoot(String[] hashes) {
        if (hashes.length == 0) {
            return sha256Hex("EMPTY_AUDIT_LOG");
        }

        String[] currentLevel = new String[hashes.length];

        for (int i = 0; i < hashes.length; i++) {
            currentLevel[i] = hashes[i];
        }

        int currentLength = currentLevel.length;

        while (currentLength > 1) {
            int nextLength = (currentLength + 1) / 2;
            String[] nextLevel = new String[nextLength];

            for (int i = 0; i < nextLength; i++) {
                String left = currentLevel[i * 2];

                String right = (i * 2 + 1 < currentLength)
                        ? currentLevel[i * 2 + 1]
                        : left;

                nextLevel[i] = sha256Hex(left + right);
            }

            currentLevel = nextLevel;
            currentLength = nextLength;
        }

        return currentLevel[0];
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(utf8LikeBytes(text));
            return toHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /*
     * Java-only conversion without JavaFX.
     * For normal ASCII audit content this is equivalent to UTF-8.
     */
    private static byte[] utf8LikeBytes(String text) {
        byte[] result = new byte[text.length()];

        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);

            if (character > 127) {
                throw new IllegalArgumentException(
                        "This example accepts ASCII audit content only");
            }

            result[i] = (byte) character;
        }

        return result;
    }

    private static boolean constantTimeEquals(String first, String second) {
        if (first == null || second == null || first.length() != second.length()) {
            return false;
        }

        int difference = 0;

        for (int i = 0; i < first.length(); i++) {
            difference |= first.charAt(i) ^ second.charAt(i);
        }

        return difference == 0;
    }

    private static String zeros(int count) {
        char[] result = new char[count];

        for (int i = 0; i < count; i++) {
            result[i] = '0';
        }

        return new String(result);
    }

    private static String toHex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];

        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            result[i * 2] = digits[value >>> 4];
            result[i * 2 + 1] = digits[value & 0x0F];
        }

        return new String(result);
    }
}