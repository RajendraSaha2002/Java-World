import java.security.MessageDigest;
import java.security.SecureRandom;

/*
 * Shamir Secret Sharing over GF(256).
 *
 * Features:
 * - (k, n) secret sharing
 * - GF(2^8) arithmetic with precomputed log/antilog tables
 * - Lagrange interpolation at x = 0
 * - SHA-256 commitment for every share
 * - Corrupted/malicious share detection
 * - No JavaFX or external libraries
 *
 * This is an educational implementation. Keep shares and the secret protected.
 */
public final class ShamirSecretSharing {
    private static final SecureRandom RANDOM = new SecureRandom();

    public static void main(String[] args) {
        byte[] secret = ascii("My 256-bit secret key: 0123456789ABCDEF");

        int threshold = 3;
        int shareCount = 5;

        System.out.println("Original secret: " + new String(secret));
        System.out.println("Creating " + shareCount + " shares with threshold k = " + threshold);
        System.out.println();

        Scheme scheme = split(secret, threshold, shareCount);

        for (int i = 0; i < scheme.shares.length; i++) {
            Share share = scheme.shares[i];

            System.out.println("Share " + share.x
                    + " valid: " + verifyShare(share, scheme.commitments[i]));
            System.out.println("  value      : " + toHex(share.y));
            System.out.println("  commitment : " + toHex(scheme.commitments[i]));
        }

        System.out.println();

        // Any 3 valid shares reconstruct the secret.
        Share[] selected = {
                scheme.shares[0],
                scheme.shares[2],
                scheme.shares[4]
        };

        verifySelectedShares(selected, scheme);

        byte[] reconstructed = reconstruct(selected, threshold);

        require(equal(secret, reconstructed), "Secret reconstruction failed");

        System.out.println("Reconstructed secret: " + new String(reconstructed));
        System.out.println("Reconstruction successful: " + equal(secret, reconstructed));
        System.out.println();

        // Demonstrate corruption detection.
        Share corrupted = copyShare(scheme.shares[1]);
        corrupted.y[0] ^= 0x01;

        System.out.println("Corruption test:");
        System.out.println("Original share valid : "
                + verifyShare(scheme.shares[1], scheme.commitments[1]));
        System.out.println("Modified share valid : "
                + verifyShare(corrupted, scheme.commitments[1]));

        try {
            require(verifyShare(corrupted, scheme.commitments[1]),
                    "Rejected corrupted share");
        } catch (IllegalArgumentException expected) {
            System.out.println("Corrupted share rejected successfully.");
        }
    }

    // ================================================================
    // Shamir split and reconstruction
    // ================================================================

    /*
     * Splits a secret into n shares where any k shares reconstruct it.
     */
    public static Scheme split(byte[] secret, int k, int n) {
        if (secret == null || secret.length == 0) {
            throw new IllegalArgumentException("Secret cannot be empty");
        }

        if (k < 2 || k > n || n > 255) {
            throw new IllegalArgumentException("Require 2 <= k <= n <= 255");
        }

        Share[] shares = new Share[n];

        for (int i = 0; i < n; i++) {
            shares[i] = new Share(i + 1, new byte[secret.length]);
        }

        /*
         * For each secret byte, make a random polynomial:
         *
         * f(x) = secret + a1*x + a2*x^2 + ... + a(k-1)*x^(k-1)
         */
        for (int byteIndex = 0; byteIndex < secret.length; byteIndex++) {
            int[] coefficients = new int[k];
            coefficients[0] = secret[byteIndex] & 0xFF;

            for (int degree = 1; degree < k; degree++) {
                coefficients[degree] = RANDOM.nextInt(256);
            }

            for (int shareIndex = 0; shareIndex < n; shareIndex++) {
                int x = shares[shareIndex].x;
                shares[shareIndex].y[byteIndex] = (byte) evaluatePolynomial(coefficients, x);
            }
        }

        byte[][] commitments = new byte[n][];

        for (int i = 0; i < n; i++) {
            commitments[i] = commitment(shares[i]);
        }

        return new Scheme(k, shares, commitments);
    }

    /*
     * Reconstructs f(0), which is the original secret.
     */
    public static byte[] reconstruct(Share[] selectedShares, int k) {
        if (selectedShares == null || selectedShares.length < k) {
            throw new IllegalArgumentException("At least k shares are required");
        }

        int secretLength = selectedShares[0].y.length;

        for (int i = 0; i < k; i++) {
            if (selectedShares[i] == null || selectedShares[i].y.length != secretLength) {
                throw new IllegalArgumentException("Invalid share lengths");
            }
        }

        ensureDistinctXValues(selectedShares, k);

        byte[] secret = new byte[secretLength];

        /*
         * Lagrange interpolation at x = 0:
         *
         * secret = Sum(yi * Product(xj / (xi + xj)))
         *
         * Addition/subtraction in GF(256) is XOR.
         */
        for (int byteIndex = 0; byteIndex < secretLength; byteIndex++) {
            int value = 0;

            for (int i = 0; i < k; i++) {
                int xi = selectedShares[i].x;
                int yi = selectedShares[i].y[byteIndex] & 0xFF;

                int numerator = 1;
                int denominator = 1;

                for (int j = 0; j < k; j++) {
                    if (i == j) continue;

                    int xj = selectedShares[j].x;

                    numerator = GF256.multiply(numerator, xj);
                    denominator = GF256.multiply(denominator, xi ^ xj);
                }

                int lagrangeCoefficient = GF256.divide(numerator, denominator);
                value ^= GF256.multiply(yi, lagrangeCoefficient);
            }

            secret[byteIndex] = (byte) value;
        }

        return secret;
    }

    private static int evaluatePolynomial(int[] coefficients, int x) {
        int result = coefficients[coefficients.length - 1];

        // Horner's rule: f(x) = (...((a_n*x + a_n-1)*x + ...) + a0)
        for (int i = coefficients.length - 2; i >= 0; i--) {
            result = GF256.multiply(result, x) ^ coefficients[i];
        }

        return result;
    }

    private static void ensureDistinctXValues(Share[] shares, int count) {
        boolean[] seen = new boolean[256];

        for (int i = 0; i < count; i++) {
            int x = shares[i].x;

            if (x < 1 || x > 255 || seen[x]) {
                throw new IllegalArgumentException("Shares must have distinct x values from 1 to 255");
            }

            seen[x] = true;
        }
    }

    // ================================================================
    // SHA-256 per-share commitments
    // ================================================================

    /*
     * Commitment = SHA-256(x || shareBytes)
     */
    public static byte[] commitment(Share share) {
        if (share == null) {
            throw new IllegalArgumentException("Share cannot be null");
        }

        byte[] data = new byte[share.y.length + 1];
        data[0] = (byte) share.x;

        for (int i = 0; i < share.y.length; i++) {
            data[i + 1] = share.y[i];
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public static boolean verifyShare(Share share, byte[] expectedCommitment) {
        if (share == null || expectedCommitment == null) {
            return false;
        }

        return constantTimeEquals(commitment(share), expectedCommitment);
    }

    private static void verifySelectedShares(Share[] selected, Scheme scheme) {
        for (int i = 0; i < selected.length; i++) {
            boolean found = false;

            for (int j = 0; j < scheme.shares.length; j++) {
                if (scheme.shares[j].x == selected[i].x) {
                    found = true;

                    if (!verifyShare(selected[i], scheme.commitments[j])) {
                        throw new IllegalArgumentException(
                                "Share " + selected[i].x + " failed commitment verification");
                    }

                    break;
                }
            }

            if (!found) {
                throw new IllegalArgumentException(
                        "Share " + selected[i].x + " does not belong to this scheme");
            }
        }
    }

    // ================================================================
    // GF(256) arithmetic using log/antilog tables
    // Irreducible polynomial: x^8 + x^4 + x^3 + x^2 + 1 = 0x11D
    // ================================================================

    static final class GF256 {
        private static final int[] LOG = new int[256];
        private static final int[] EXP = new int[512];

        static {
            int value = 1;

            for (int i = 0; i < 255; i++) {
                EXP[i] = value;
                LOG[value] = i;

                value <<= 1;

                if ((value & 0x100) != 0) {
                    value ^= 0x11D;
                }
            }

            // Avoid modulus 255 during multiplication/division.
            for (int i = 255; i < EXP.length; i++) {
                EXP[i] = EXP[i - 255];
            }
        }

        static int multiply(int a, int b) {
            if (a == 0 || b == 0) return 0;

            return EXP[LOG[a] + LOG[b]];
        }

        static int divide(int a, int b) {
            if (b == 0) {
                throw new ArithmeticException("Division by zero in GF(256)");
            }

            if (a == 0) return 0;

            int exponent = LOG[a] - LOG[b];

            if (exponent < 0) {
                exponent += 255;
            }

            return EXP[exponent];
        }
    }

    // ================================================================
    // Data classes and small utility methods
    // ================================================================

    public static final class Share {
        public final int x;
        public final byte[] y;

        Share(int x, byte[] y) {
            this.x = x;
            this.y = y;
        }
    }

    public static final class Scheme {
        public final int threshold;
        public final Share[] shares;
        public final byte[][] commitments;

        Scheme(int threshold, Share[] shares, byte[][] commitments) {
            this.threshold = threshold;
            this.shares = shares;
            this.commitments = commitments;
        }
    }

    private static Share copyShare(Share original) {
        byte[] copiedY = new byte[original.y.length];

        for (int i = 0; i < original.y.length; i++) {
            copiedY[i] = original.y[i];
        }

        return new Share(original.x, copiedY);
    }

    private static byte[] ascii(String text) {
        byte[] result = new byte[text.length()];

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c > 127) {
                throw new IllegalArgumentException("This example accepts ASCII text only");
            }

            result[i] = (byte) c;
        }

        return result;
    }

    private static boolean equal(byte[] first, byte[] second) {
        if (first == null || second == null || first.length != second.length) {
            return false;
        }

        int difference = 0;

        for (int i = 0; i < first.length; i++) {
            difference |= first[i] ^ second[i];
        }

        return difference == 0;
    }

    private static boolean constantTimeEquals(byte[] first, byte[] second) {
        return equal(first, second);
    }

    private static String toHex(byte[] data) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] result = new char[data.length * 2];

        for (int i = 0; i < data.length; i++) {
            int value = data[i] & 0xFF;
            result[i * 2] = digits[value >>> 4];
            result[i * 2 + 1] = digits[value & 0x0F];
        }

        return new String(result);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}