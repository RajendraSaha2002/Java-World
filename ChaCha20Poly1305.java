/*
 * ChaCha20-Poly1305 AEAD from scratch (RFC 8439).
 *
 * No JavaFX and no external libraries.
 * Includes:
 * - ChaCha20 quarter-round and 20-round block function
 * - Poly1305 using 26-bit limbs stored in long values
 * - AEAD construction with AAD and length blocks
 * - RFC 8439 known-answer test
 * - System.nanoTime() throughput benchmark
 *
 * Educational implementation. Use a reviewed JCA provider in production.
 */
public final class ChaCha20Poly1305 {
    private static final int KEY_LENGTH = 32;
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH = 16;

    private ChaCha20Poly1305() {
    }

    public static void main(String[] args) {
        runRfc8439Test();
        runRoundTripTest();
        benchmark();
    }

    // ================================================================
    // AEAD public API
    // ================================================================

    /*
     * Returns ciphertext || 16-byte Poly1305 tag.
     */
    public static byte[] encrypt(byte[] key, byte[] nonce,
                                 byte[] aad, byte[] plaintext) {
        validateKeyAndNonce(key, nonce);

        if (aad == null) aad = new byte[0];
        if (plaintext == null) plaintext = new byte[0];

        byte[] oneTimeKey = chacha20Block(key, 0, nonce);
        byte[] ciphertext = chacha20Xor(key, nonce, 1, plaintext);
        byte[] macData = buildMacData(aad, ciphertext);
        byte[] tag = poly1305(macData, oneTimeKey);

        byte[] result = new byte[ciphertext.length + TAG_LENGTH];

        for (int i = 0; i < ciphertext.length; i++) {
            result[i] = ciphertext[i];
        }

        for (int i = 0; i < TAG_LENGTH; i++) {
            result[ciphertext.length + i] = tag[i];
        }

        wipe(oneTimeKey);
        wipe(macData);

        return result;
    }

    /*
     * Verifies the tag before returning plaintext.
     */
    public static byte[] decrypt(byte[] key, byte[] nonce,
                                 byte[] aad, byte[] ciphertextAndTag) {
        validateKeyAndNonce(key, nonce);

        if (aad == null) aad = new byte[0];

        if (ciphertextAndTag == null || ciphertextAndTag.length < TAG_LENGTH) {
            throw new IllegalArgumentException("Ciphertext must include a 16-byte tag");
        }

        int ciphertextLength = ciphertextAndTag.length - TAG_LENGTH;
        byte[] ciphertext = new byte[ciphertextLength];
        byte[] suppliedTag = new byte[TAG_LENGTH];

        for (int i = 0; i < ciphertextLength; i++) {
            ciphertext[i] = ciphertextAndTag[i];
        }

        for (int i = 0; i < TAG_LENGTH; i++) {
            suppliedTag[i] = ciphertextAndTag[ciphertextLength + i];
        }

        byte[] oneTimeKey = chacha20Block(key, 0, nonce);
        byte[] macData = buildMacData(aad, ciphertext);
        byte[] calculatedTag = poly1305(macData, oneTimeKey);

        boolean authentic = constantTimeEquals(calculatedTag, suppliedTag);

        wipe(oneTimeKey);
        wipe(macData);
        wipe(calculatedTag);

        if (!authentic) {
            throw new SecurityException("Authentication failed");
        }

        return chacha20Xor(key, nonce, 1, ciphertext);
    }

    // ================================================================
    // ChaCha20
    // ================================================================

    private static byte[] chacha20Xor(byte[] key, byte[] nonce,
                                      int initialCounter, byte[] input) {
        byte[] output = new byte[input.length];
        int counter = initialCounter;
        int offset = 0;

        while (offset < input.length) {
            byte[] block = chacha20Block(key, counter, nonce);
            int count = Math.min(64, input.length - offset);

            for (int i = 0; i < count; i++) {
                output[offset + i] = (byte) (input[offset + i] ^ block[i]);
            }

            wipe(block);
            offset += count;
            counter++;

            if (counter == 0 && offset < input.length) {
                throw new IllegalStateException("ChaCha20 counter exhausted");
            }
        }

        return output;
    }

    /*
     * RFC 8439 ChaCha20 block function.
     */
    private static byte[] chacha20Block(byte[] key, int counter, byte[] nonce) {
        int[] state = new int[16];

        state[0] = 0x61707865;
        state[1] = 0x3320646e;
        state[2] = 0x79622d32;
        state[3] = 0x6b206574;

        for (int i = 0; i < 8; i++) {
            state[4 + i] = load32LE(key, i * 4);
        }

        state[12] = counter;
        state[13] = load32LE(nonce, 0);
        state[14] = load32LE(nonce, 4);
        state[15] = load32LE(nonce, 8);

        int[] working = new int[16];

        for (int i = 0; i < 16; i++) {
            working[i] = state[i];
        }

        for (int round = 0; round < 10; round++) {
            quarterRound(working, 0, 4, 8, 12);
            quarterRound(working, 1, 5, 9, 13);
            quarterRound(working, 2, 6, 10, 14);
            quarterRound(working, 3, 7, 11, 15);

            quarterRound(working, 0, 5, 10, 15);
            quarterRound(working, 1, 6, 11, 12);
            quarterRound(working, 2, 7, 8, 13);
            quarterRound(working, 3, 4, 9, 14);
        }

        byte[] result = new byte[64];

        for (int i = 0; i < 16; i++) {
            store32LE(working[i] + state[i], result, i * 4);
        }

        return result;
    }

    private static void quarterRound(int[] x, int a, int b, int c, int d) {
        x[a] += x[b];
        x[d] = rotateLeft(x[d] ^ x[a], 16);

        x[c] += x[d];
        x[b] = rotateLeft(x[b] ^ x[c], 12);

        x[a] += x[b];
        x[d] = rotateLeft(x[d] ^ x[a], 8);

        x[c] += x[d];
        x[b] = rotateLeft(x[b] ^ x[c], 7);
    }

    private static int rotateLeft(int value, int distance) {
        return (value << distance) | (value >>> (32 - distance));
    }

    // ================================================================
    // Poly1305 using five 26-bit limbs
    // ================================================================

    private static byte[] poly1305(byte[] message, byte[] oneTimeKey) {
        if (oneTimeKey.length < 32) {
            throw new IllegalArgumentException("Poly1305 requires 32 bytes");
        }

        long t0 = load32LE(oneTimeKey, 0) & 0xFFFFFFFFL;
        long t1 = load32LE(oneTimeKey, 4) & 0xFFFFFFFFL;
        long t2 = load32LE(oneTimeKey, 8) & 0xFFFFFFFFL;
        long t3 = load32LE(oneTimeKey, 12) & 0xFFFFFFFFL;

        long r0 = t0 & 0x3FFFFFFL;
        long r1 = ((t0 >>> 26) | (t1 << 6)) & 0x3FFFF03L;
        long r2 = ((t1 >>> 20) | (t2 << 12)) & 0x3FFC0FFL;
        long r3 = ((t2 >>> 14) | (t3 << 18)) & 0x3F03FFFL;
        long r4 = (t3 >>> 8) & 0x00FFFFFL;

        long s1 = r1 * 5;
        long s2 = r2 * 5;
        long s3 = r3 * 5;
        long s4 = r4 * 5;

        long h0 = 0;
        long h1 = 0;
        long h2 = 0;
        long h3 = 0;
        long h4 = 0;

        int offset = 0;

        while (offset < message.length) {
            int remaining = Math.min(16, message.length - offset);
            byte[] block = new byte[16];

            for (int i = 0; i < remaining; i++) {
                block[i] = message[offset + i];
            }

            long b0 = load32LE(block, 0) & 0xFFFFFFFFL;
            long b1 = load32LE(block, 4) & 0xFFFFFFFFL;
            long b2 = load32LE(block, 8) & 0xFFFFFFFFL;
            long b3 = load32LE(block, 12) & 0xFFFFFFFFL;

            h0 += b0 & 0x3FFFFFFL;
            h1 += ((b0 >>> 26) | (b1 << 6)) & 0x3FFFFFFL;
            h2 += ((b1 >>> 20) | (b2 << 12)) & 0x3FFFFFFL;
            h3 += ((b2 >>> 14) | (b3 << 18)) & 0x3FFFFFFL;

            if (remaining == 16) {
                h4 += (b3 >>> 8) | (1L << 24);
            } else {
                // Poly1305 appends a 1 byte to an incomplete final block.
                block[remaining] = 1;

                b0 = load32LE(block, 0) & 0xFFFFFFFFL;
                b1 = load32LE(block, 4) & 0xFFFFFFFFL;
                b2 = load32LE(block, 8) & 0xFFFFFFFFL;
                b3 = load32LE(block, 12) & 0xFFFFFFFFL;

                h0 -= h0 & 0x3FFFFFFL;
                h1 -= h1 & 0x3FFFFFFL;
                h2 -= h2 & 0x3FFFFFFL;
                h3 -= h3 & 0x3FFFFFFL;

                h0 += b0 & 0x3FFFFFFL;
                h1 += ((b0 >>> 26) | (b1 << 6)) & 0x3FFFFFFL;
                h2 += ((b1 >>> 20) | (b2 << 12)) & 0x3FFFFFFL;
                h3 += ((b2 >>> 14) | (b3 << 18)) & 0x3FFFFFFL;
                h4 += (b3 >>> 8) & 0x3FFFFFFL;
            }

            long d0 = h0 * r0 + h1 * s4 + h2 * s3 + h3 * s2 + h4 * s1;
            long d1 = h0 * r1 + h1 * r0 + h2 * s4 + h3 * s3 + h4 * s2;
            long d2 = h0 * r2 + h1 * r1 + h2 * r0 + h3 * s4 + h4 * s3;
            long d3 = h0 * r3 + h1 * r2 + h2 * r1 + h3 * r0 + h4 * s4;
            long d4 = h0 * r4 + h1 * r3 + h2 * r2 + h3 * r1 + h4 * r0;

            long carry;

            carry = d0 >>> 26;
            h0 = d0 & 0x3FFFFFFL;
            d1 += carry;

            carry = d1 >>> 26;
            h1 = d1 & 0x3FFFFFFL;
            d2 += carry;

            carry = d2 >>> 26;
            h2 = d2 & 0x3FFFFFFL;
            d3 += carry;

            carry = d3 >>> 26;
            h3 = d3 & 0x3FFFFFFL;
            d4 += carry;

            carry = d4 >>> 26;
            h4 = d4 & 0x3FFFFFFL;
            h0 += carry * 5;

            carry = h0 >>> 26;
            h0 &= 0x3FFFFFFL;
            h1 += carry;

            wipe(block);
            offset += remaining;
        }

        long carry;

        carry = h1 >>> 26;
        h1 &= 0x3FFFFFFL;
        h2 += carry;

        carry = h2 >>> 26;
        h2 &= 0x3FFFFFFL;
        h3 += carry;

        carry = h3 >>> 26;
        h3 &= 0x3FFFFFFL;
        h4 += carry;

        carry = h4 >>> 26;
        h4 &= 0x3FFFFFFL;
        h0 += carry * 5;

        carry = h0 >>> 26;
        h0 &= 0x3FFFFFFL;
        h1 += carry;

        // Conditional reduction modulo 2^130 - 5.
        long g0 = h0 + 5;
        carry = g0 >>> 26;
        g0 &= 0x3FFFFFFL;

        long g1 = h1 + carry;
        carry = g1 >>> 26;
        g1 &= 0x3FFFFFFL;

        long g2 = h2 + carry;
        carry = g2 >>> 26;
        g2 &= 0x3FFFFFFL;

        long g3 = h3 + carry;
        carry = g3 >>> 26;
        g3 &= 0x3FFFFFFL;

        long g4 = h4 + carry - (1L << 26);

        long mask = (g4 >>> 63) - 1;
        long inverseMask = ~mask;

        h0 = (h0 & inverseMask) | (g0 & mask);
        h1 = (h1 & inverseMask) | (g1 & mask);
        h2 = (h2 & inverseMask) | (g2 & mask);
        h3 = (h3 & inverseMask) | (g3 & mask);
        h4 = (h4 & inverseMask) | (g4 & mask);

        long f0 = (h0 | (h1 << 26)) & 0xFFFFFFFFL;
        long f1 = ((h1 >>> 6) | (h2 << 20)) & 0xFFFFFFFFL;
        long f2 = ((h2 >>> 12) | (h3 << 14)) & 0xFFFFFFFFL;
        long f3 = ((h3 >>> 18) | (h4 << 8)) & 0xFFFFFFFFL;

        long pad0 = load32LE(oneTimeKey, 16) & 0xFFFFFFFFL;
        long pad1 = load32LE(oneTimeKey, 20) & 0xFFFFFFFFL;
        long pad2 = load32LE(oneTimeKey, 24) & 0xFFFFFFFFL;
        long pad3 = load32LE(oneTimeKey, 28) & 0xFFFFFFFFL;

        f0 += pad0;
        f1 += pad1 + (f0 >>> 32);
        f0 &= 0xFFFFFFFFL;

        f2 += pad2 + (f1 >>> 32);
        f1 &= 0xFFFFFFFFL;

        f3 += pad3 + (f2 >>> 32);
        f2 &= 0xFFFFFFFFL;
        f3 &= 0xFFFFFFFFL;

        byte[] tag = new byte[16];
        store32LE((int) f0, tag, 0);
        store32LE((int) f1, tag, 4);
        store32LE((int) f2, tag, 8);
        store32LE((int) f3, tag, 12);

        return tag;
    }

    // ================================================================
    // AEAD MAC input construction
    // ================================================================

    private static byte[] buildMacData(byte[] aad, byte[] ciphertext) {
        int aadPadding = paddingLength(aad.length);
        int ciphertextPadding = paddingLength(ciphertext.length);

        byte[] data = new byte[
                aad.length + aadPadding
                        + ciphertext.length + ciphertextPadding
                        + 16
                ];

        int offset = 0;

        for (int i = 0; i < aad.length; i++) {
            data[offset++] = aad[i];
        }

        offset += aadPadding;

        for (int i = 0; i < ciphertext.length; i++) {
            data[offset++] = ciphertext[i];
        }

        offset += ciphertextPadding;

        store64LE((long) aad.length, data, offset);
        store64LE((long) ciphertext.length, data, offset + 8);

        return data;
    }

    private static int paddingLength(int length) {
        int remainder = length % 16;
        return remainder == 0 ? 0 : 16 - remainder;
    }

    // ================================================================
    // RFC 8439 test and benchmark
    // ================================================================

    private static void runRfc8439Test() {
        byte[] key = hex(
                "808182838485868788898a8b8c8d8e8f"
                        + "909192939495969798999a9b9c9d9e9f");

        byte[] nonce = hex("070000004041424344454647");
        byte[] aad = hex("50515253c0c1c2c3c4c5c6c7");

        byte[] plaintext = ascii(
                "Ladies and Gentlemen of the class of '99: If I could offer you "
                        + "only one tip for the future, sunscreen would be it.");

        byte[] expectedCiphertextAndTag = hex(
                "d31a8d34648e60db7b86afbc53ef7ec2"
                        + "a4aded51296e08fea9e2b5a736ee62d6"
                        + "3dbea45e8ca9671282fafb69da92728b"
                        + "1a71de0a9e060b2905d6a5b67ecd3b36"
                        + "92ddbd7f2d778b8c9803aee328091b58"
                        + "fab324e4fad675945585808b4831d7bc"
                        + "3ff4def08e4b7a9de576d26586cec64b"
                        + "6116"
                        + "1ae10b594f09e26a7e902ecbd0600691");

        byte[] actual = encrypt(key, nonce, aad, plaintext);

        require(equal(actual, expectedCiphertextAndTag),
                "RFC 8439 ChaCha20-Poly1305 test vector failed");

        byte[] recovered = decrypt(key, nonce, aad, actual);

        require(equal(recovered, plaintext),
                "RFC 8439 decryption test failed");

        System.out.println("RFC 8439 AEAD test passed.");
    }

    private static void runRoundTripTest() {
        byte[] key = hex(
                "000102030405060708090a0b0c0d0e0f"
                        + "101112131415161718191a1b1c1d1e1f");

        byte[] nonce = hex("000000090000004a00000000");
        byte[] aad = ascii("authenticated metadata");
        byte[] message = ascii("Pure Java ChaCha20-Poly1305 AEAD.");

        byte[] sealed = encrypt(key, nonce, aad, message);
        byte[] recovered = decrypt(key, nonce, aad, sealed);

        require(equal(message, recovered), "Round-trip test failed");

        sealed[0] ^= 1;

        boolean rejected = false;

        try {
            decrypt(key, nonce, aad, sealed);
        } catch (SecurityException expected) {
            rejected = true;
        }

        require(rejected, "Tampered ciphertext was accepted");

        System.out.println("Round-trip and tamper tests passed.");
    }

    private static void benchmark() {
        byte[] key = new byte[KEY_LENGTH];
        byte[] nonce = new byte[NONCE_LENGTH];
        byte[] aad = ascii("benchmark-aad");
        byte[] data = new byte[1024 * 1024];

        for (int i = 0; i < key.length; i++) key[i] = (byte) i;
        for (int i = 0; i < nonce.length; i++) nonce[i] = (byte) (i + 32);
        for (int i = 0; i < data.length; i++) data[i] = (byte) i;

        long start = System.nanoTime();
        byte[] result = encrypt(key, nonce, aad, data);
        long elapsed = System.nanoTime() - start;

        double seconds = elapsed / 1_000_000_000.0;
        double mibPerSecond = (data.length / 1024.0 / 1024.0) / seconds;

        System.out.println("ChaCha20-Poly1305 benchmark:");
        System.out.println("  Data size : " + data.length + " bytes");
        System.out.println("  Time      : " + (elapsed / 1_000_000.0) + " ms");
        System.out.println("  Throughput: " + mibPerSecond + " MiB/s");

        wipe(result);
        wipe(data);
    }

    // ================================================================
    // Byte helpers
    // ================================================================

    private static int load32LE(byte[] input, int offset) {
        return (input[offset] & 0xFF)
                | ((input[offset + 1] & 0xFF) << 8)
                | ((input[offset + 2] & 0xFF) << 16)
                | ((input[offset + 3] & 0xFF) << 24);
    }

    private static void store32LE(int value, byte[] output, int offset) {
        output[offset] = (byte) value;
        output[offset + 1] = (byte) (value >>> 8);
        output[offset + 2] = (byte) (value >>> 16);
        output[offset + 3] = (byte) (value >>> 24);
    }

    private static void store64LE(long value, byte[] output, int offset) {
        for (int i = 0; i < 8; i++) {
            output[offset + i] = (byte) (value >>> (8 * i));
        }
    }

    private static void validateKeyAndNonce(byte[] key, byte[] nonce) {
        if (key == null || key.length != KEY_LENGTH) {
            throw new IllegalArgumentException("ChaCha20 key must contain 32 bytes");
        }

        if (nonce == null || nonce.length != NONCE_LENGTH) {
            throw new IllegalArgumentException("ChaCha20 nonce must contain 12 bytes");
        }
    }

    private static boolean constantTimeEquals(byte[] first, byte[] second) {
        if (first == null || second == null || first.length != second.length) {
            return false;
        }

        int difference = 0;

        for (int i = 0; i < first.length; i++) {
            difference |= first[i] ^ second[i];
        }

        return difference == 0;
    }

    private static boolean equal(byte[] first, byte[] second) {
        return constantTimeEquals(first, second);
    }

    private static byte[] ascii(String text) {
        byte[] result = new byte[text.length()];

        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);

            if (character > 127) {
                throw new IllegalArgumentException("ASCII text only");
            }

            result[i] = (byte) character;
        }

        return result;
    }

    private static byte[] hex(String text) {
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

    private static void wipe(byte[] bytes) {
        if (bytes == null) return;

        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = 0;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}