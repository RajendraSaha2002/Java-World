import java.math.BigInteger;
import java.security.SecureRandom;

/*
 * Educational RSA implementation:
 * - 1024-bit RSA key generation
 * - Miller-Rabin primality testing
 * - Extended-Euclidean modular inverse
 * - PKCS#1 v1.5 encryption padding
 * - Textbook RSA chosen-ciphertext attack demonstration
 *
 * Do not use this source as a production cryptography library.
 * Modern applications should use a vetted provider and RSA-OAEP.
 */
public final class RsaFromScratch {
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final BigInteger ZERO = BigInteger.ZERO;
    private static final BigInteger ONE = BigInteger.ONE;
    private static final BigInteger TWO = BigInteger.valueOf(2);
    private static final BigInteger THREE = BigInteger.valueOf(3);
    private static final BigInteger PUBLIC_EXPONENT = BigInteger.valueOf(65537);

    public static void main(String[] args) {
        System.out.println("Generating 1024-bit RSA key pair...");
        RsaKeyPair keys = generateKeyPair(1024);

        System.out.println("Key generated.");
        System.out.println("Modulus bit length: " + keys.publicKey.n.bitLength());
        System.out.println();

        byte[] message = ascii("Secret message for RSA demonstration");

        // PKCS#1 v1.5 encryption/decryption.
        byte[] ciphertext = encryptPkcs1v15(message, keys.publicKey);
        byte[] recovered = decryptPkcs1v15(ciphertext, keys.privateKey);

        require(equal(message, recovered), "PKCS#1 v1.5 decryption failed");

        System.out.println("Original message : " + new String(message));
        System.out.println("Ciphertext       : " + toHex(ciphertext));
        System.out.println("Recovered message: " + new String(recovered));
        System.out.println();

        demonstrateTextbookRsaAttack(keys, message);
        demonstratePaddingProtection(keys, message);
    }

    // ================================================================
    // RSA key generation
    // ================================================================

    static RsaKeyPair generateKeyPair(int modulusBits) {
        if (modulusBits < 1024 || (modulusBits & 1) != 0) {
            throw new IllegalArgumentException("Use an even modulus size of at least 1024 bits");
        }

        int primeBits = modulusBits / 2;

        while (true) {
            BigInteger p = generatePrime(primeBits);
            BigInteger q;

            do {
                q = generatePrime(primeBits);
            } while (p.equals(q));

            BigInteger n = p.multiply(q);

            if (n.bitLength() != modulusBits) {
                continue;
            }

            BigInteger phi = p.subtract(ONE).multiply(q.subtract(ONE));

            if (!PUBLIC_EXPONENT.gcd(phi).equals(ONE)) {
                continue;
            }

            BigInteger d = modularInverse(PUBLIC_EXPONENT, phi);

            return new RsaKeyPair(
                    new RsaPublicKey(n, PUBLIC_EXPONENT),
                    new RsaPrivateKey(n, d)
            );
        }
    }

    static BigInteger generatePrime(int bits) {
        while (true) {
            BigInteger candidate = new BigInteger(bits, RANDOM)
                    .setBit(bits - 1)
                    .setBit(0);

            if (isProbablePrime(candidate, 32)) {
                return candidate;
            }
        }
    }

    /*
     * Miller-Rabin probabilistic primality test.
     * A composite number has at least a 75% chance of being detected per round.
     */
    static boolean isProbablePrime(BigInteger n, int rounds) {
        if (n.compareTo(TWO) < 0) return false;
        if (n.equals(TWO) || n.equals(THREE)) return true;
        if (!n.testBit(0)) return false;

        BigInteger d = n.subtract(ONE);
        int s = 0;

        while (!d.testBit(0)) {
            d = d.shiftRight(1);
            s++;
        }

        for (int round = 0; round < rounds; round++) {
            BigInteger a = randomInRange(TWO, n.subtract(TWO));
            BigInteger x = modPow(a, d, n);

            if (x.equals(ONE) || x.equals(n.subtract(ONE))) {
                continue;
            }

            boolean witnessPassed = false;

            for (int r = 1; r < s; r++) {
                x = x.multiply(x).mod(n);

                if (x.equals(n.subtract(ONE))) {
                    witnessPassed = true;
                    break;
                }
            }

            if (!witnessPassed) {
                return false;
            }
        }

        return true;
    }

    static BigInteger randomInRange(BigInteger minimum, BigInteger maximum) {
        BigInteger range = maximum.subtract(minimum).add(ONE);
        BigInteger value;

        do {
            value = new BigInteger(range.bitLength(), RANDOM);
        } while (value.compareTo(range) >= 0);

        return value.add(minimum);
    }

    /*
     * Repeated-squaring modular exponentiation.
     * Equivalent purpose to BigInteger.modPow(), but written explicitly.
     */
    static BigInteger modPow(BigInteger base, BigInteger exponent, BigInteger modulus) {
        BigInteger result = ONE;
        BigInteger factor = base.mod(modulus);
        BigInteger power = exponent;

        while (power.signum() > 0) {
            if (power.testBit(0)) {
                result = result.multiply(factor).mod(modulus);
            }

            factor = factor.multiply(factor).mod(modulus);
            power = power.shiftRight(1);
        }

        return result;
    }

    /*
     * Extended Euclidean algorithm.
     * Returns a^-1 mod modulus when gcd(a, modulus) = 1.
     */
    static BigInteger modularInverse(BigInteger a, BigInteger modulus) {
        BigInteger oldR = a;
        BigInteger r = modulus;
        BigInteger oldS = ONE;
        BigInteger s = ZERO;

        while (!r.equals(ZERO)) {
            BigInteger quotient = oldR.divide(r);

            BigInteger nextR = oldR.subtract(quotient.multiply(r));
            oldR = r;
            r = nextR;

            BigInteger nextS = oldS.subtract(quotient.multiply(s));
            oldS = s;
            s = nextS;
        }

        if (!oldR.equals(ONE)) {
            throw new ArithmeticException("Modular inverse does not exist");
        }

        return oldS.mod(modulus);
    }

    // ================================================================
    // Raw textbook RSA
    // ================================================================

    static BigInteger textbookEncrypt(BigInteger message, RsaPublicKey key) {
        if (message.signum() < 0 || message.compareTo(key.n) >= 0) {
            throw new IllegalArgumentException("Message representative is outside RSA range");
        }

        return modPow(message, key.e, key.n);
    }

    static BigInteger textbookDecrypt(BigInteger ciphertext, RsaPrivateKey key) {
        if (ciphertext.signum() < 0 || ciphertext.compareTo(key.n) >= 0) {
            throw new IllegalArgumentException("Ciphertext representative is outside RSA range");
        }

        return modPow(ciphertext, key.d, key.n);
    }

    // ================================================================
    // RSAES-PKCS1-v1_5 encryption padding
    // Encoded message: 00 || 02 || PS || 00 || M
    // ================================================================

    static byte[] encryptPkcs1v15(byte[] message, RsaPublicKey key) {
        int blockLength = modulusByteLength(key.n);
        byte[] encoded = pkcs1v15Encode(message, blockLength);

        BigInteger m = new BigInteger(1, encoded);
        BigInteger c = textbookEncrypt(m, key);

        return toFixedLength(c, blockLength);
    }

    static byte[] decryptPkcs1v15(byte[] ciphertext, RsaPrivateKey key) {
        int blockLength = modulusByteLength(key.n);

        if (ciphertext == null || ciphertext.length != blockLength) {
            throw new IllegalArgumentException("Ciphertext length is invalid");
        }

        BigInteger c = new BigInteger(1, ciphertext);
        BigInteger m = textbookDecrypt(c, key);

        return pkcs1v15Decode(toFixedLength(m, blockLength));
    }

    static byte[] pkcs1v15Encode(byte[] message, int blockLength) {
        if (message == null) throw new IllegalArgumentException("Message cannot be null");
        if (message.length > blockLength - 11) {
            throw new IllegalArgumentException("Message is too long for RSA PKCS#1 v1.5");
        }

        int paddingLength = blockLength - message.length - 3;
        byte[] encoded = new byte[blockLength];

        encoded[0] = 0x00;
        encoded[1] = 0x02;

        for (int i = 0; i < paddingLength; i++) {
            byte randomByte;

            do {
                randomByte = (byte) RANDOM.nextInt(256);
            } while (randomByte == 0);

            encoded[2 + i] = randomByte;
        }

        encoded[2 + paddingLength] = 0x00;

        for (int i = 0; i < message.length; i++) {
            encoded[3 + paddingLength + i] = message[i];
        }

        return encoded;
    }

    static byte[] pkcs1v15Decode(byte[] encoded) {
        if (encoded == null || encoded.length < 11
                || encoded[0] != 0x00 || encoded[1] != 0x02) {
            throw new SecurityException("Invalid PKCS#1 v1.5 block");
        }

        int separator = -1;

        for (int i = 2; i < encoded.length; i++) {
            if (encoded[i] == 0x00) {
                separator = i;
                break;
            }
        }

        if (separator < 10) {
            throw new SecurityException("Invalid PKCS#1 v1.5 padding");
        }

        int messageLength = encoded.length - separator - 1;
        byte[] message = new byte[messageLength];

        for (int i = 0; i < messageLength; i++) {
            message[i] = encoded[separator + 1 + i];
        }

        return message;
    }

    // ================================================================
    // Chosen-ciphertext demonstrations
    // ================================================================

    static void demonstrateTextbookRsaAttack(RsaKeyPair keys, byte[] message) {
        BigInteger originalMessage = new BigInteger(1, message);
        BigInteger ciphertext = textbookEncrypt(originalMessage, keys.publicKey);

        /*
         * Attacker chooses r, computes c' = c * r^e mod n,
         * and obtains m' from a decryption oracle.
         */
        BigInteger r = TWO;
        BigInteger manipulatedCiphertext = ciphertext
                .multiply(modPow(r, keys.publicKey.e, keys.publicKey.n))
                .mod(keys.publicKey.n);

        BigInteger oracleResponse = textbookDecrypt(
                manipulatedCiphertext, keys.privateKey);

        BigInteger recovered = oracleResponse
                .multiply(modularInverse(r, keys.publicKey.n))
                .mod(keys.publicKey.n);

        require(recovered.equals(originalMessage),
                "Textbook RSA attack demonstration failed");

        System.out.println("Textbook RSA attack:");
        System.out.println("  Original message recovered through chosen ciphertext: "
                + new String(toUnsignedBytes(recovered)));
        System.out.println();
    }

    static void demonstratePaddingProtection(RsaKeyPair keys, byte[] message) {
        int blockLength = modulusByteLength(keys.publicKey.n);

        byte[] encoded = pkcs1v15Encode(message, blockLength);
        BigInteger paddedMessage = new BigInteger(1, encoded);
        BigInteger ciphertext = textbookEncrypt(paddedMessage, keys.publicKey);

        BigInteger r = TWO;
        BigInteger changedCiphertext = ciphertext
                .multiply(modPow(r, keys.publicKey.e, keys.publicKey.n))
                .mod(keys.publicKey.n);

        BigInteger changedPlaintext = textbookDecrypt(
                changedCiphertext, keys.privateKey);

        boolean rejected;

        try {
            pkcs1v15Decode(toFixedLength(changedPlaintext, blockLength));
            rejected = false;
        } catch (SecurityException expected) {
            rejected = true;
        }

        System.out.println("PKCS#1 v1.5 padding demonstration:");
        System.out.println("  Same multiplicative ciphertext modification rejected: " + rejected);
        System.out.println("  Padding prevents the simple textbook-RSA recovery attack.");
        System.out.println("  Note: PKCS#1 v1.5 is legacy; real systems should prefer RSA-OAEP.");
    }

    // ================================================================
    // Data classes and utilities
    // ================================================================

    static final class RsaPublicKey {
        final BigInteger n;
        final BigInteger e;

        RsaPublicKey(BigInteger n, BigInteger e) {
            this.n = n;
            this.e = e;
        }
    }

    static final class RsaPrivateKey {
        final BigInteger n;
        final BigInteger d;

        RsaPrivateKey(BigInteger n, BigInteger d) {
            this.n = n;
            this.d = d;
        }
    }

    static final class RsaKeyPair {
        final RsaPublicKey publicKey;
        final RsaPrivateKey privateKey;

        RsaKeyPair(RsaPublicKey publicKey, RsaPrivateKey privateKey) {
            this.publicKey = publicKey;
            this.privateKey = privateKey;
        }
    }

    static int modulusByteLength(BigInteger modulus) {
        return (modulus.bitLength() + 7) / 8;
    }

    static byte[] toFixedLength(BigInteger number, int length) {
        byte[] source = number.toByteArray();
        byte[] result = new byte[length];

        int sourceStart = 0;

        if (source.length > 1 && source[0] == 0) {
            sourceStart = 1;
        }

        int sourceLength = source.length - sourceStart;

        if (sourceLength > length) {
            throw new IllegalArgumentException("Integer does not fit in requested length");
        }

        for (int i = 0; i < sourceLength; i++) {
            result[length - sourceLength + i] = source[sourceStart + i];
        }

        return result;
    }

    static byte[] toUnsignedBytes(BigInteger number) {
        byte[] source = number.toByteArray();

        if (source.length > 1 && source[0] == 0) {
            byte[] result = new byte[source.length - 1];

            for (int i = 1; i < source.length; i++) {
                result[i - 1] = source[i];
            }

            return result;
        }

        return source;
    }

    static byte[] ascii(String text) {
        byte[] result = new byte[text.length()];

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c > 127) {
                throw new IllegalArgumentException("Only ASCII text is used in this demo");
            }

            result[i] = (byte) c;
        }

        return result;
    }

    static boolean equal(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;

        int difference = 0;

        for (int i = 0; i < a.length; i++) {
            difference |= a[i] ^ b[i];
        }

        return difference == 0;
    }

    static String toHex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];

        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            result[i * 2] = digits[value >>> 4];
            result[i * 2 + 1] = digits[value & 15];
        }

        return new String(result);
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}