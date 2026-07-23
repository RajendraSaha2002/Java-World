/**
 * NIST SP 800-232 Ascon-AEAD128.
 *
 * Pure Java: no imports, no JavaFX, no external libraries.
 * Security rule: never reuse the same (key, nonce) pair.
 */
public final class Ascon128 {
    public static final int KEY_BYTES = 16;
    public static final int NONCE_BYTES = 16;
    public static final int TAG_BYTES = 16;
    private static final int RATE = 16;

    // NIST SP 800-232 Ascon-AEAD128 initialization value.
    private static final long IV = 0x00001000808c0001L;

    private Ascon128() {
    }

    /**
     * Encrypts plaintext and returns ciphertext || 16-byte authentication tag.
     *
     * @param key 16-byte secret key
     * @param nonce 16-byte unique nonce; do not reuse it with this key
     * @param associatedData authenticated but not encrypted data; may be null
     * @param plaintext data to encrypt; may be null
     */
    public static byte[] encrypt(byte[] key, byte[] nonce,
                                 byte[] associatedData, byte[] plaintext) {
        checkKeyAndNonce(key, nonce);

        if (associatedData == null) associatedData = new byte[0];
        if (plaintext == null) plaintext = new byte[0];

        if (plaintext.length > Integer.MAX_VALUE - TAG_BYTES) {
            throw new IllegalArgumentException("Plaintext is too large");
        }

        long[] state = initialize(key, nonce);
        absorbAssociatedData(state, associatedData);

        byte[] result = new byte[plaintext.length + TAG_BYTES];
        encryptMessage(state, plaintext, result);
        finalizeAndWriteTag(state, key, result, plaintext.length);

        return result;
    }

    /**
     * Verifies ciphertext || tag and returns the plaintext.
     *
     * @throws SecurityException when the authentication tag is invalid
     */
    public static byte[] decrypt(byte[] key, byte[] nonce,
                                 byte[] associatedData, byte[] ciphertextAndTag) {
        checkKeyAndNonce(key, nonce);

        if (associatedData == null) associatedData = new byte[0];

        if (ciphertextAndTag == null || ciphertextAndTag.length < TAG_BYTES) {
            throw new IllegalArgumentException("Ciphertext must include a 16-byte tag");
        }

        int ciphertextLength = ciphertextAndTag.length - TAG_BYTES;
        long[] state = initialize(key, nonce);
        absorbAssociatedData(state, associatedData);

        byte[] plaintext = new byte[ciphertextLength];
        decryptMessage(state, ciphertextAndTag, ciphertextLength, plaintext);

        byte[] calculatedTag = new byte[TAG_BYTES];
        finalizeAndWriteTag(state, key, calculatedTag, 0);

        if (!constantTimeEquals(calculatedTag, 0, ciphertextAndTag, ciphertextLength, TAG_BYTES)) {
            wipe(plaintext);
            throw new SecurityException("Authentication failed: invalid key, nonce, data, or tag");
        }

        return plaintext;
    }

    private static long[] initialize(byte[] key, byte[] nonce) {
        long k0 = load64LE(key, 0);
        long k1 = load64LE(key, 8);
        long n0 = load64LE(nonce, 0);
        long n1 = load64LE(nonce, 8);

        long[] state = new long[5];
        state[0] = IV;
        state[1] = k0;
        state[2] = k1;
        state[3] = n0;
        state[4] = n1;

        permutation(state, 12);

        state[3] ^= k0;
        state[4] ^= k1;
        return state;
    }

    private static void absorbAssociatedData(long[] state, byte[] ad) {
        int offset = 0;

        // Full associated-data blocks.
        while (ad.length - offset >= RATE) {
            state[0] ^= load64LE(ad, offset);
            state[1] ^= load64LE(ad, offset + 8);
            permutation(state, 8);
            offset += RATE;
        }

        // Last padded block, including when AD length is an exact multiple of 16.
        int remaining = ad.length - offset;
        for (int i = 0; i < remaining; i++) {
            xorByte(state, i, ad[offset + i]);
        }
        xorByte(state, remaining, (byte) 0x01);
        permutation(state, 8);

        // Domain separation between associated data and plaintext.
        state[4] ^= 1L;
    }

    private static void encryptMessage(long[] state, byte[] plaintext, byte[] output) {
        int offset = 0;

        while (plaintext.length - offset >= RATE) {
            state[0] ^= load64LE(plaintext, offset);
            state[1] ^= load64LE(plaintext, offset + 8);

            store64LE(state[0], output, offset);
            store64LE(state[1], output, offset + 8);

            permutation(state, 8);
            offset += RATE;
        }

        int remaining = plaintext.length - offset;

        for (int i = 0; i < remaining; i++) {
            byte cipherByte = (byte) (plaintext[offset + i] ^ getByte(state, i));
            output[offset + i] = cipherByte;

            // State absorbs plaintext; this produces the ciphertext in state.
            xorByte(state, i, plaintext[offset + i]);
        }

        // pad10*: append byte 0x01 immediately after the last plaintext byte.
        xorByte(state, remaining, (byte) 0x01);
    }

    private static void decryptMessage(long[] state, byte[] ciphertext,
                                       int ciphertextLength, byte[] plaintext) {
        int offset = 0;

        while (ciphertextLength - offset >= RATE) {
            long c0 = load64LE(ciphertext, offset);
            long c1 = load64LE(ciphertext, offset + 8);

            store64LE(state[0] ^ c0, plaintext, offset);
            store64LE(state[1] ^ c1, plaintext, offset + 8);

            state[0] = c0;
            state[1] = c1;

            permutation(state, 8);
            offset += RATE;
        }

        int remaining = ciphertextLength - offset;

        for (int i = 0; i < remaining; i++) {
            byte c = ciphertext[offset + i];
            plaintext[offset + i] = (byte) (c ^ getByte(state, i));

            // State must contain ciphertext after decryption.
            setByte(state, i, c);
        }

        xorByte(state, remaining, (byte) 0x01);
    }

    private static void finalizeAndWriteTag(long[] state, byte[] key,
                                            byte[] destination, int destinationOffset) {
        long k0 = load64LE(key, 0);
        long k1 = load64LE(key, 8);

        state[2] ^= k0;
        state[3] ^= k1;
        permutation(state, 12);
        state[3] ^= k0;
        state[4] ^= k1;

        store64LE(state[3], destination, destinationOffset);
        store64LE(state[4], destination, destinationOffset + 8);
    }

    /**
     * Ascon-p permutation. Uses 12 rounds for initialization/finalization
     * and 8 rounds during AD/message processing.
     */
    private static void permutation(long[] s, int rounds) {
        long x0 = s[0];
        long x1 = s[1];
        long x2 = s[2];
        long x3 = s[3];
        long x4 = s[4];

        int firstRound = 12 - rounds;

        for (int round = firstRound; round < 12; round++) {
            long roundConstant = (long) (((0xF - round) << 4) | round);
            x2 ^= roundConstant;

            // Substitution layer.
            x0 ^= x4;
            x4 ^= x3;
            x2 ^= x1;

            long t0 = ~x0 & x1;
            long t1 = ~x1 & x2;
            long t2 = ~x2 & x3;
            long t3 = ~x3 & x4;
            long t4 = ~x4 & x0;

            x0 ^= t1;
            x1 ^= t2;
            x2 ^= t3;
            x3 ^= t4;
            x4 ^= t0;

            x1 ^= x0;
            x0 ^= x4;
            x3 ^= x2;
            x2 = ~x2;

            // Linear diffusion layer.
            x0 ^= rotateRight(x0, 19) ^ rotateRight(x0, 28);
            x1 ^= rotateRight(x1, 61) ^ rotateRight(x1, 39);
            x2 ^= rotateRight(x2, 1) ^ rotateRight(x2, 6);
            x3 ^= rotateRight(x3, 10) ^ rotateRight(x3, 17);
            x4 ^= rotateRight(x4, 7) ^ rotateRight(x4, 41);
        }

        s[0] = x0;
        s[1] = x1;
        s[2] = x2;
        s[3] = x3;
        s[4] = x4;
    }

    private static long rotateRight(long value, int distance) {
        return (value >>> distance) | (value << (64 - distance));
    }

    // NIST Ascon-AEAD128 uses little-endian byte order.
    private static long load64LE(byte[] data, int offset) {
        return ((long) data[offset] & 0xFF)
                | (((long) data[offset + 1] & 0xFF) << 8)
                | (((long) data[offset + 2] & 0xFF) << 16)
                | (((long) data[offset + 3] & 0xFF) << 24)
                | (((long) data[offset + 4] & 0xFF) << 32)
                | (((long) data[offset + 5] & 0xFF) << 40)
                | (((long) data[offset + 6] & 0xFF) << 48)
                | (((long) data[offset + 7] & 0xFF) << 56);
    }

    private static void store64LE(long value, byte[] output, int offset) {
        for (int i = 0; i < 8; i++) {
            output[offset + i] = (byte) (value >>> (8 * i));
        }
    }

    private static byte getByte(long[] state, int byteIndex) {
        int word = byteIndex >>> 3;
        int shift = (byteIndex & 7) << 3;
        return (byte) (state[word] >>> shift);
    }

    private static void setByte(long[] state, int byteIndex, byte value) {
        int word = byteIndex >>> 3;
        int shift = (byteIndex & 7) << 3;
        long mask = 0xFFL << shift;
        state[word] = (state[word] & ~mask) | (((long) value & 0xFFL) << shift);
    }

    private static void xorByte(long[] state, int byteIndex, byte value) {
        int word = byteIndex >>> 3;
        int shift = (byteIndex & 7) << 3;
        state[word] ^= ((long) value & 0xFFL) << shift;
    }

    private static void checkKeyAndNonce(byte[] key, byte[] nonce) {
        if (key == null || key.length != KEY_BYTES) {
            throw new IllegalArgumentException("Key must be exactly 16 bytes");
        }
        if (nonce == null || nonce.length != NONCE_BYTES) {
            throw new IllegalArgumentException("Nonce must be exactly 16 bytes");
        }
    }

    private static boolean constantTimeEquals(byte[] a, int aOffset,
                                              byte[] b, int bOffset, int length) {
        int difference = 0;
        for (int i = 0; i < length; i++) {
            difference |= (a[aOffset + i] ^ b[bOffset + i]);
        }
        return difference == 0;
    }

    private static void wipe(byte[] data) {
        for (int i = 0; i < data.length; i++) {
            data[i] = 0;
        }
    }

    // --------------------- Console demo / assertion tests ---------------------

    public static void main(String[] args) {
        byte[] key = hex("000102030405060708090A0B0C0D0E0F");
        byte[] nonce = hex("101112131415161718191A1B1C1D1E1F");
        byte[] ad = ascii("header: user=cyber");
        byte[] message = ascii("ASCON-128 AEAD in pure Java.");

        byte[] sealed = encrypt(key, nonce, ad, message);
        byte[] recovered = decrypt(key, nonce, ad, sealed);

        require(equal(message, recovered), "Round-trip encryption/decryption failed");

        // Tamper test: an altered ciphertext/tag must never decrypt successfully.
        byte[] altered = copyOf(sealed);
        altered[0] ^= 1;

        boolean rejected = false;
        try {
            decrypt(key, nonce, ad, altered);
        } catch (SecurityException expected) {
            rejected = true;
        }
        require(rejected, "Tampered ciphertext was accepted");

        System.out.println("Ascon-AEAD128 self-test passed.");
        System.out.println("Plaintext : " + new String(message));
        System.out.println("Sealed hex: " + toHex(sealed));
        System.out.println("Recovered : " + new String(recovered));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static boolean equal(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;

        int difference = 0;
        for (int i = 0; i < a.length; i++) {
            difference |= a[i] ^ b[i];
        }
        return difference == 0;
    }

    private static byte[] copyOf(byte[] source) {
        byte[] result = new byte[source.length];
        for (int i = 0; i < source.length; i++) result[i] = source[i];
        return result;
    }

    private static byte[] ascii(String text) {
        byte[] result = new byte[text.length()];
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c > 0x7F) {
                throw new IllegalArgumentException("ASCII text only");
            }
            result[i] = (byte) c;
        }
        return result;
    }

    private static byte[] hex(String text) {
        if ((text.length() & 1) != 0) {
            throw new IllegalArgumentException("Hex string length must be even");
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

    private static String toHex(byte[] data) {
        char[] alphabet = "0123456789ABCDEF".toCharArray();
        char[] result = new char[data.length * 2];

        for (int i = 0; i < data.length; i++) {
            int value = data[i] & 0xFF;
            result[i * 2] = alphabet[value >>> 4];
            result[i * 2 + 1] = alphabet[value & 0x0F];
        }

        return new String(result);
    }
}