import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class SecureBootSimulator {
    private static final byte[] ROOT_HMAC_KEY =
            "demo-root-key-change-in-real-hardware"
                    .getBytes(StandardCharsets.UTF_8);

    private static final Path STATE_DIRECTORY =
            Paths.get("secure-boot-demo-state");

    private static final Path VERSION_FILE =
            STATE_DIRECTORY.resolve("accepted-versions.properties");

    private SecureBootSimulator() {
    }

    public static void main(String[] args) {
        try {
            Files.createDirectories(STATE_DIRECTORY);

            SecureBoot boot = new SecureBoot(ROOT_HMAC_KEY, VERSION_FILE);

            BootArtifact stage1 = BootArtifact.sign(
                    "stage1", 1,
                    ascii("STAGE-1 BOOTLOADER IMAGE v1"),
                    ROOT_HMAC_KEY
            );

            BootArtifact kernel = BootArtifact.sign(
                    "kernel", 1,
                    ascii("KERNEL IMAGE v1: trusted operating system"),
                    ROOT_HMAC_KEY
            );

            System.out.println("=== Normal secure boot ===");
            boot.boot(stage1, kernel);

            System.out.println("\n=== Tampered-kernel test ===");

            BootArtifact tamperedKernel = kernel.copy();
            tamperedKernel.image[5] ^= 0x01;

            try {
                boot.boot(stage1, tamperedKernel);
            } catch (SecurityException expected) {
                System.out.println("Boot blocked: " + expected.getMessage());
            }

            System.out.println("\n=== Rollback-protection test ===");

            BootArtifact oldKernel = BootArtifact.sign(
                    "kernel", 0,
                    ascii("KERNEL IMAGE OLD VERSION"),
                    ROOT_HMAC_KEY
            );

            try {
                boot.boot(stage1, oldKernel);
            } catch (SecurityException expected) {
                System.out.println("Boot blocked: " + expected.getMessage());
            }

        } catch (Exception e) {
            System.out.println("Simulator failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static final class SecureBoot {
        private final byte[] rootKey;
        private final Path versionFile;
        private final MeasurementLog measurements = new MeasurementLog();

        SecureBoot(byte[] rootKey, Path versionFile) {
            this.rootKey = copyBytes(rootKey);
            this.versionFile = versionFile;
        }

        void boot(BootArtifact stage1, BootArtifact kernel) throws Exception {
            measurements.reset();

            System.out.println("ROM bootloader: verifying Stage-1...");
            verifyArtifact(stage1);
            measurements.extend(stage1.name, stage1.hash);

            System.out.println("Stage-1: verifying kernel...");
            verifyArtifact(kernel);
            measurements.extend(kernel.name, kernel.hash);

            updateAcceptedVersion(stage1.name, stage1.version);
            updateAcceptedVersion(kernel.name, kernel.version);

            System.out.println("Secure boot result: SUCCESS\n");
            measurements.print();
        }

        private void verifyArtifact(BootArtifact artifact) throws Exception {
            if (artifact == null) {
                throw new SecurityException("Boot artifact is missing");
            }

            long acceptedVersion = getAcceptedVersion(artifact.name);

            if (artifact.version < acceptedVersion) {
                throw new SecurityException(
                        artifact.name + " rollback detected: version "
                                + artifact.version + " is below accepted version "
                                + acceptedVersion
                );
            }

            byte[] calculatedHash = sha256(artifact.image);

            if (!MessageDigest.isEqual(calculatedHash, artifact.hash)) {
                throw new SecurityException(
                        artifact.name + " SHA-256 integrity check failed"
                );
            }

            byte[] signedData = signingData(
                    artifact.name, artifact.version, artifact.hash);

            byte[] calculatedSignature = hmacSha256(rootKey, signedData);

            if (!MessageDigest.isEqual(
                    calculatedSignature, artifact.signature)) {
                throw new SecurityException(
                        artifact.name + " HMAC-SHA256 signature check failed"
                );
            }

            System.out.println("  Verified " + artifact.name
                    + " version=" + artifact.version
                    + " hash=" + toHex(artifact.hash));
        }

        private long getAcceptedVersion(String name) throws Exception {
            Properties properties = loadVersions();
            String value = properties.getProperty(name);

            return value == null ? 0L : Long.parseLong(value);
        }

        private void updateAcceptedVersion(String name, long version)
                throws Exception {

            Properties properties = loadVersions();
            String oldValue = properties.getProperty(name);
            long oldVersion = oldValue == null ? 0L : Long.parseLong(oldValue);

            if (version > oldVersion) {
                properties.setProperty(name, String.valueOf(version));

                try (java.io.OutputStream output =
                             Files.newOutputStream(versionFile)) {
                    properties.store(output,
                            "Secure boot monotonic version counters");
                }
            }
        }

        private Properties loadVersions() throws Exception {
            Properties properties = new Properties();

            if (Files.exists(versionFile)) {
                try (java.io.InputStream input =
                             Files.newInputStream(versionFile)) {
                    properties.load(input);
                }
            }

            return properties;
        }
    }

    static final class BootArtifact {
        final String name;
        final long version;
        final byte[] image;
        final byte[] hash;
        final byte[] signature;

        BootArtifact(String name, long version, byte[] image,
                     byte[] hash, byte[] signature) {
            this.name = name;
            this.version = version;
            this.image = image;
            this.hash = hash;
            this.signature = signature;
        }

        static BootArtifact sign(String name, long version,
                                 byte[] image, byte[] rootKey) {

            byte[] imageCopy = SecureBootSimulator.copyBytes(image);
            byte[] hash = sha256(imageCopy);

            byte[] signature = hmacSha256(
                    rootKey,
                    signingData(name, version, hash)
            );

            return new BootArtifact(
                    name,
                    version,
                    imageCopy,
                    hash,
                    signature
            );
        }

        BootArtifact copy() {
            return new BootArtifact(
                    name,
                    version,
                    SecureBootSimulator.copyBytes(image),
                    SecureBootSimulator.copyBytes(hash),
                    SecureBootSimulator.copyBytes(signature)
            );
        }
    }

    static final class MeasurementLog {
        private final List<Measurement> entries =
                new ArrayList<Measurement>();

        private byte[] pcr = new byte[32];

        void reset() {
            entries.clear();
            pcr = new byte[32];
        }

        /*
         * PCR extend:
         * newPCR = SHA-256(oldPCR || measuredComponentHash)
         */
        void extend(String componentName, byte[] componentHash) {
            byte[] combined = new byte[pcr.length + componentHash.length];

            for (int i = 0; i < pcr.length; i++) {
                combined[i] = pcr[i];
            }

            for (int i = 0; i < componentHash.length; i++) {
                combined[pcr.length + i] = componentHash[i];
            }

            pcr = sha256(combined);

            entries.add(new Measurement(
                    componentName,
                    copyBytes(componentHash),
                    copyBytes(pcr)
            ));
        }

        void print() {
            System.out.println("TPM-like PCR measurement log:");
            System.out.println("Initial PCR: " + toHex(new byte[32]));

            for (int i = 0; i < entries.size(); i++) {
                Measurement item = entries.get(i);

                System.out.println("PCR extend #" + (i + 1));
                System.out.println("  Component: " + item.componentName);
                System.out.println("  Hash     : " + toHex(item.componentHash));
                System.out.println("  PCR      : " + toHex(item.pcrValue));
            }
        }
    }

    static final class Measurement {
        final String componentName;
        final byte[] componentHash;
        final byte[] pcrValue;

        Measurement(String componentName, byte[] componentHash,
                    byte[] pcrValue) {
            this.componentName = componentName;
            this.componentHash = componentHash;
            this.pcrValue = pcrValue;
        }
    }

    private static byte[] signingData(
            String name, long version, byte[] hash) {

        byte[] nameBytes = ascii(name);
        byte[] output = new byte[4 + nameBytes.length + 8 + hash.length];

        int offset = 0;
        storeInt(nameBytes.length, output, offset);
        offset += 4;

        for (int i = 0; i < nameBytes.length; i++) {
            output[offset++] = nameBytes[i];
        }

        storeLong(version, output, offset);
        offset += 8;

        for (int i = 0; i < hash.length; i++) {
            output[offset + i] = hash[i];
        }

        return output;
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] copyBytes(byte[] source) {
        byte[] result = new byte[source.length];

        for (int i = 0; i < source.length; i++) {
            result[i] = source[i];
        }

        return result;
    }

    private static void storeInt(int value, byte[] output, int offset) {
        output[offset] = (byte) (value >>> 24);
        output[offset + 1] = (byte) (value >>> 16);
        output[offset + 2] = (byte) (value >>> 8);
        output[offset + 3] = (byte) value;
    }

    private static void storeLong(long value, byte[] output, int offset) {
        for (int i = 7; i >= 0; i--) {
            output[offset + (7 - i)] = (byte) (value >>> (i * 8));
        }
    }

    private static String toHex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];

        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            result[i * 2] = digits[value >>> 4];
            result[i * 2 + 1] = digits[value & 15];
        }

        return new String(result);
    }
}