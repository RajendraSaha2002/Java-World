import java.math.BigInteger;

/*
 * Pure Java SHA-256 + Merkle proofs + educational blockchain PoW demo.
 * No JavaFX. SHA-256 itself is implemented from scratch using int[] arithmetic.
 */
public final class Sha256MerkleBlockchain {

    public static void main(String[] args) {
        runSha256Tests();
        runMerkleDemo();
        runBlockchainDemo();
    }

    // ------------------------- SHA-256 tests -------------------------

    private static void runSha256Tests() {
        String expected = "ba7816bf8f01cfea414140de5dae2223"
                + "b00361a396177a9cb410ff61f20015ad";

        require(toHex(Sha256.hash(ascii("abc"))).equals(expected),
                "SHA-256 test vector for 'abc' failed");

        System.out.println("SHA-256 self-test passed.");
        System.out.println("SHA-256(\"abc\") = " + expected);
        System.out.println();
    }

    // ------------------------- Merkle tree demo -------------------------

    private static void runMerkleDemo() {
        String[] transactions = {
                "Alice pays Bob 10",
                "Bob pays Carol 4",
                "Carol pays Dave 2",
                "Dave pays Eve 1",
                "Eve pays Frank 7"
        };

        MerkleTree tree = new MerkleTree(transactions);
        MerkleProof proof = tree.createProof(2);

        boolean verified = MerkleTree.verifyProof(
                transactions[2], proof, tree.getRoot());

        require(verified, "Merkle proof should verify");

        boolean rejected = !MerkleTree.verifyProof(
                "Carol pays Dave 2000", proof, tree.getRoot());

        require(rejected, "Modified Merkle leaf should be rejected");

        System.out.println("Merkle root: " + toHex(tree.getRoot()));
        System.out.println("Merkle proof for transaction #2 verified: " + verified);
        System.out.println();
    }

    // ------------------------- Blockchain demo -------------------------

    private static void runBlockchainDemo() {
        Blockchain chain = new Blockchain(14); // Low educational difficulty.

        chain.addBlock(new String[] {
                "Alice pays Bob 10",
                "Bob pays Carol 4"
        });

        chain.addBlock(new String[] {
                "Carol pays Dave 2",
                "Dave pays Eve 1",
                "Eve pays Frank 7"
        });

        require(chain.isValid(), "Blockchain validation failed");

        System.out.println();
        System.out.println("Blockchain is valid: " + chain.isValid());
        System.out.println("Blocks mined: " + chain.size());

        // Demonstrates tamper detection.
        chain.getBlock(1).transactions[0] = "Alice pays Bob 999999";
        System.out.println("After changing a transaction, chain valid: " + chain.isValid());
    }

    // =================================================================
    // SHA-256 from scratch
    // =================================================================

    static final class Sha256 {

        private static final int[] INITIAL_HASH = {
                0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
                0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
        };

        private static final int[] K = {
                0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5,
                0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
                0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
                0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
                0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc,
                0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
                0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
                0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
                0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
                0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
                0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
                0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
                0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
                0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
                0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
                0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
        };

        static byte[] hash(byte[] message) {
            if (message == null) {
                throw new IllegalArgumentException("Message cannot be null");
            }

            byte[] padded = pad(message);
            int[] hash = copyIntArray(INITIAL_HASH);
            int[] words = new int[64];

            for (int offset = 0; offset < padded.length; offset += 64) {
                for (int i = 0; i < 16; i++) {
                    int p = offset + i * 4;
                    words[i] = ((padded[p] & 0xFF) << 24)
                            | ((padded[p + 1] & 0xFF) << 16)
                            | ((padded[p + 2] & 0xFF) << 8)
                            | (padded[p + 3] & 0xFF);
                }

                for (int i = 16; i < 64; i++) {
                    int s0 = rotateRight(words[i - 15], 7)
                            ^ rotateRight(words[i - 15], 18)
                            ^ (words[i - 15] >>> 3);

                    int s1 = rotateRight(words[i - 2], 17)
                            ^ rotateRight(words[i - 2], 19)
                            ^ (words[i - 2] >>> 10);

                    words[i] = words[i - 16] + s0 + words[i - 7] + s1;
                }

                int a = hash[0];
                int b = hash[1];
                int c = hash[2];
                int d = hash[3];
                int e = hash[4];
                int f = hash[5];
                int g = hash[6];
                int h = hash[7];

                for (int i = 0; i < 64; i++) {
                    int sigma1 = rotateRight(e, 6) ^ rotateRight(e, 11) ^ rotateRight(e, 25);
                    int choose = (e & f) ^ (~e & g);
                    int temp1 = h + sigma1 + choose + K[i] + words[i];

                    int sigma0 = rotateRight(a, 2) ^ rotateRight(a, 13) ^ rotateRight(a, 22);
                    int majority = (a & b) ^ (a & c) ^ (b & c);
                    int temp2 = sigma0 + majority;

                    h = g;
                    g = f;
                    f = e;
                    e = d + temp1;
                    d = c;
                    c = b;
                    b = a;
                    a = temp1 + temp2;
                }

                hash[0] += a;
                hash[1] += b;
                hash[2] += c;
                hash[3] += d;
                hash[4] += e;
                hash[5] += f;
                hash[6] += g;
                hash[7] += h;
            }

            byte[] output = new byte[32];

            for (int i = 0; i < 8; i++) {
                int value = hash[i];
                output[i * 4] = (byte) (value >>> 24);
                output[i * 4 + 1] = (byte) (value >>> 16);
                output[i * 4 + 2] = (byte) (value >>> 8);
                output[i * 4 + 3] = (byte) value;
            }

            return output;
        }

        private static byte[] pad(byte[] message) {
            long bitLength = (long) message.length * 8L;
            int zeroCount = (int) ((56 - ((message.length + 1) % 64) + 64) % 64);

            byte[] padded = new byte[message.length + 1 + zeroCount + 8];

            for (int i = 0; i < message.length; i++) {
                padded[i] = message[i];
            }

            padded[message.length] = (byte) 0x80;

            for (int i = 0; i < 8; i++) {
                padded[padded.length - 1 - i] = (byte) (bitLength >>> (i * 8));
            }

            return padded;
        }

        private static int rotateRight(int value, int bits) {
            return (value >>> bits) | (value << (32 - bits));
        }
    }

    // =================================================================
    // Merkle tree and inclusion proofs
    // =================================================================

    static final class MerkleTree {
        private final byte[][][] levels;

        MerkleTree(String[] transactions) {
            if (transactions == null || transactions.length == 0) {
                throw new IllegalArgumentException("At least one transaction is required");
            }

            levels = new byte[calculateLevelCount(transactions.length)][][];
            levels[0] = new byte[transactions.length][];

            for (int i = 0; i < transactions.length; i++) {
                levels[0][i] = Sha256.hash(ascii(transactions[i]));
            }

            int level = 0;

            while (levels[level].length > 1) {
                byte[][] current = levels[level];
                int parentCount = (current.length + 1) / 2;
                levels[level + 1] = new byte[parentCount][];

                for (int i = 0; i < parentCount; i++) {
                    byte[] left = current[i * 2];
                    byte[] right = (i * 2 + 1 < current.length)
                            ? current[i * 2 + 1]
                            : left;

                    levels[level + 1][i] = Sha256.hash(concatenate(left, right));
                }

                level++;
            }
        }

        byte[] getRoot() {
            return copyBytes(levels[levels.length - 1][0]);
        }

        MerkleProof createProof(int transactionIndex) {
            if (transactionIndex < 0 || transactionIndex >= levels[0].length) {
                throw new IllegalArgumentException("Invalid transaction index");
            }

            byte[][] siblings = new byte[levels.length - 1][];
            boolean[] siblingOnLeft = new boolean[levels.length - 1];
            int currentIndex = transactionIndex;

            for (int level = 0; level < levels.length - 1; level++) {
                byte[][] currentLevel = levels[level];
                int siblingIndex;

                if ((currentIndex & 1) == 0) {
                    siblingIndex = currentIndex + 1;
                    siblingOnLeft[level] = false;
                } else {
                    siblingIndex = currentIndex - 1;
                    siblingOnLeft[level] = true;
                }

                if (siblingIndex >= currentLevel.length) {
                    siblingIndex = currentIndex;
                }

                siblings[level] = copyBytes(currentLevel[siblingIndex]);
                currentIndex /= 2;
            }

            return new MerkleProof(siblings, siblingOnLeft);
        }

        static boolean verifyProof(String transaction, MerkleProof proof, byte[] expectedRoot) {
            if (transaction == null || proof == null || expectedRoot == null) {
                return false;
            }

            byte[] current = Sha256.hash(ascii(transaction));

            for (int i = 0; i < proof.siblings.length; i++) {
                if (proof.siblingOnLeft[i]) {
                    current = Sha256.hash(concatenate(proof.siblings[i], current));
                } else {
                    current = Sha256.hash(concatenate(current, proof.siblings[i]));
                }
            }

            return constantTimeEquals(current, expectedRoot);
        }

        private static int calculateLevelCount(int leafCount) {
            int count = 1;

            while (leafCount > 1) {
                leafCount = (leafCount + 1) / 2;
                count++;
            }

            return count;
        }
    }

    static final class MerkleProof {
        private final byte[][] siblings;
        private final boolean[] siblingOnLeft;

        MerkleProof(byte[][] siblings, boolean[] siblingOnLeft) {
            this.siblings = siblings;
            this.siblingOnLeft = siblingOnLeft;
        }
    }

    // =================================================================
    // Educational blockchain with BigInteger PoW
    // =================================================================

    static final class Blockchain {
        private Block[] blocks = new Block[4];
        private int count;
        private final int difficultyBits;
        private final BigInteger target;

        Blockchain(int difficultyBits) {
            if (difficultyBits < 1 || difficultyBits > 248) {
                throw new IllegalArgumentException("Difficulty must be from 1 to 248 bits");
            }

            this.difficultyBits = difficultyBits;
            this.target = BigInteger.ONE.shiftLeft(256 - difficultyBits);
        }

        void addBlock(String[] transactions) {
            String previousHash = count == 0 ? zeros(64) : blocks[count - 1].hash;
            Block block = new Block(count, previousHash, transactions, target);
            block.mineWithProgress();

            if (count == blocks.length) {
                Block[] expanded = new Block[blocks.length * 2];
                for (int i = 0; i < blocks.length; i++) {
                    expanded[i] = blocks[i];
                }
                blocks = expanded;
            }

            blocks[count++] = block;

            System.out.println("Block " + block.index + " mined.");
            System.out.println("Hash: " + block.hash);
            System.out.println("Nonce: " + block.nonce);
        }

        boolean isValid() {
            for (int i = 0; i < count; i++) {
                Block block = blocks[i];

                String expectedPrevious = i == 0 ? zeros(64) : blocks[i - 1].hash;
                if (!block.previousHash.equals(expectedPrevious)) return false;

                String rebuiltMerkleRoot = toHex(new MerkleTree(block.transactions).getRoot());
                if (!block.merkleRoot.equals(rebuiltMerkleRoot)) return false;

                String rebuiltHash = block.calculateHash();
                if (!block.hash.equals(rebuiltHash)) return false;

                if (new BigInteger(1, fromHex(block.hash)).compareTo(target) >= 0) return false;
            }

            return true;
        }

        int size() {
            return count;
        }

        Block getBlock(int index) {
            if (index < 0 || index >= count) {
                throw new IllegalArgumentException("Invalid block index");
            }
            return blocks[index];
        }
    }

    static final class Block {
        final int index;
        final String previousHash;
        final String[] transactions;
        final String merkleRoot;
        final long timestamp;
        final BigInteger target;

        volatile long attempts;
        long nonce;
        String hash;

        Block(int index, String previousHash, String[] transactions, BigInteger target) {
            if (transactions == null || transactions.length == 0) {
                throw new IllegalArgumentException("A block needs at least one transaction");
            }

            this.index = index;
            this.previousHash = previousHash;
            this.transactions = copyStrings(transactions);
            this.merkleRoot = toHex(new MerkleTree(this.transactions).getRoot());
            this.timestamp = System.currentTimeMillis();
            this.target = target;
        }

        void mineWithProgress() {
            final boolean[] mining = { true };

            Thread progress = new Thread(new Runnable() {
                public void run() {
                    char[] spin = { '|', '/', '-', '\\' };
                    int position = 0;

                    while (mining[0]) {
                        System.out.print("\rMining block " + index
                                + " " + spin[position++ % spin.length]
                                + "  attempts: " + attempts);
                        System.out.flush();

                        try {
                            Thread.sleep(120);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            });

            progress.setDaemon(true);
            progress.start();

            try {
                for (nonce = 0; nonce >= 0; nonce++) {
                    hash = calculateHash();
                    attempts++;

                    if (new BigInteger(1, fromHex(hash)).compareTo(target) < 0) {
                        return;
                    }
                }

                throw new IllegalStateException("Nonce range exhausted");
            } finally {
                mining[0] = false;

                try {
                    progress.join();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }

                System.out.println();
            }
        }

        String calculateHash() {
            String header = index + "|" + previousHash + "|" + merkleRoot
                    + "|" + timestamp + "|" + nonce;
            return toHex(Sha256.hash(ascii(header)));
        }
    }

    // =================================================================
    // Small utility methods
    // =================================================================

    private static byte[] ascii(String text) {
        if (text == null) throw new IllegalArgumentException("Text cannot be null");

        byte[] result = new byte[text.length()];

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c > 127) {
                throw new IllegalArgumentException("This demo accepts ASCII text only");
            }

            result[i] = (byte) c;
        }

        return result;
    }

    private static byte[] concatenate(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];

        for (int i = 0; i < first.length; i++) result[i] = first[i];
        for (int i = 0; i < second.length; i++) result[first.length + i] = second[i];

        return result;
    }

    private static byte[] copyBytes(byte[] source) {
        byte[] result = new byte[source.length];
        for (int i = 0; i < source.length; i++) result[i] = source[i];
        return result;
    }

    private static int[] copyIntArray(int[] source) {
        int[] result = new int[source.length];
        for (int i = 0; i < source.length; i++) result[i] = source[i];
        return result;
    }

    private static String[] copyStrings(String[] source) {
        String[] result = new String[source.length];
        for (int i = 0; i < source.length; i++) {
            if (source[i] == null) throw new IllegalArgumentException("Transaction cannot be null");
            result[i] = source[i];
        }
        return result;
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;

        int difference = 0;
        for (int i = 0; i < a.length; i++) difference |= a[i] ^ b[i];

        return difference == 0;
    }

    private static String toHex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] output = new char[bytes.length * 2];

        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            output[i * 2] = digits[value >>> 4];
            output[i * 2 + 1] = digits[value & 15];
        }

        return new String(output);
    }

    private static byte[] fromHex(String text) {
        if ((text.length() & 1) != 0) {
            throw new IllegalArgumentException("Hex text length must be even");
        }

        byte[] result = new byte[text.length() / 2];

        for (int i = 0; i < result.length; i++) {
            int high = Character.digit(text.charAt(i * 2), 16);
            int low = Character.digit(text.charAt(i * 2 + 1), 16);

            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Invalid hexadecimal text");
            }

            result[i] = (byte) ((high << 4) | low);
        }

        return result;
    }

    private static String zeros(int count) {
        char[] result = new char[count];
        for (int i = 0; i < count; i++) result[i] = '0';
        return new String(result);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}