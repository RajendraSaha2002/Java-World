import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/*
 * Defensive JWT validator without JWT libraries.
 *
 * Supports only HS256.
 * Rejects:
 * - alg: none
 * - RS256 / RSA-to-HMAC key confusion attempts
 * - unsafe key-selection headers: kid, jku, jwk, x5u, x5c
 * - invalid Base64URL, malformed JSON, expired payloads, bad signatures
 *
 * Educational example: use a reviewed JWT library in production.
 */
public final class JwtSecurityValidator {
    private static final String EXPECTED_ALGORITHM = "HS256";

    /*
     * Store outside source code in real applications:
     * environment variable, secret manager, HSM, etc.
     */
    private static final byte[] HMAC_SECRET =
            "replace-this-with-a-long-random-server-secret"
                    .getBytes(StandardCharsets.UTF_8);

    private JwtSecurityValidator() {
    }

    public static void main(String[] args) {
        long future = (System.currentTimeMillis() / 1000L) + 3600L;

        String validToken = createHs256Token(
                "{\"alg\":\"HS256\",\"typ\":\"JWT\"}",
                "{\"sub\":\"alice\",\"role\":\"user\",\"exp\":" + future + "}",
                HMAC_SECRET
        );

        System.out.println("Valid token:");
        System.out.println(validToken);
        printValidation("Valid HS256 token", validToken);

        demonstrateAlgNoneAttack(future);
        demonstrateRsaToHmacConfusionAttack(future);
        demonstrateKidInjectionAttack(future);
        demonstrateModifiedPayloadAttack(validToken);
        demonstrateExpiredToken();
    }

    // ================================================================
    // Validation
    // ================================================================

    public static ValidationResult validateHs256(String token, byte[] secret) {
        if (token == null || secret == null || secret.length == 0) {
            return ValidationResult.fail("Token or HMAC secret is missing");
        }

        String[] parts = token.split("\\.", -1);

        if (parts.length != 3) {
            return ValidationResult.fail("JWT must contain exactly three parts");
        }

        if (parts[0].length() == 0 || parts[1].length() == 0 || parts[2].length() == 0) {
            return ValidationResult.fail("JWT header, payload, and signature are required");
        }

        String headerJson;
        String payloadJson;

        try {
            headerJson = decodeBase64UrlText(parts[0]);
            payloadJson = decodeBase64UrlText(parts[1]);
        } catch (IllegalArgumentException e) {
            return ValidationResult.fail("Invalid Base64URL encoding");
        }

        if (!isSimpleJsonObject(headerJson) || !isSimpleJsonObject(payloadJson)) {
            return ValidationResult.fail("Header or payload is not a JSON object");
        }

        if (containsDuplicateField(headerJson, "alg")) {
            return ValidationResult.fail("Duplicate alg header rejected");
        }

        String algorithm = readJsonString(headerJson, "alg");

        if (algorithm == null) {
            return ValidationResult.fail("JWT alg header is missing");
        }

        if ("none".equalsIgnoreCase(algorithm)) {
            return ValidationResult.fail("alg:none attack rejected");
        }

        if (!EXPECTED_ALGORITHM.equals(algorithm)) {
            return ValidationResult.fail(
                    "Algorithm rejected: expected HS256, received " + algorithm);
        }

        String[] forbiddenHeaders = { "kid", "jku", "jwk", "x5u", "x5c", "crit" };

        for (int i = 0; i < forbiddenHeaders.length; i++) {
            if (containsJsonField(headerJson, forbiddenHeaders[i])) {
                return ValidationResult.fail(
                        "Unsafe JWT header rejected: " + forbiddenHeaders[i]);
            }
        }

        byte[] suppliedSignature;

        try {
            suppliedSignature = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            return ValidationResult.fail("JWT signature is not valid Base64URL");
        }

        byte[] expectedSignature;
        String signedData = parts[0] + "." + parts[1];

        try {
            expectedSignature = hmacSha256(
                    signedData.getBytes(StandardCharsets.US_ASCII),
                    secret
            );
        } catch (Exception e) {
            return ValidationResult.fail("Unable to calculate HMAC-SHA256");
        }

        if (!MessageDigest.isEqual(expectedSignature, suppliedSignature)) {
            return ValidationResult.fail("Invalid JWT signature");
        }

        Long expiration = readJsonLong(payloadJson, "exp");

        if (expiration != null) {
            long now = System.currentTimeMillis() / 1000L;

            if (now >= expiration.longValue()) {
                return ValidationResult.fail("JWT has expired");
            }
        }

        return ValidationResult.success(headerJson, payloadJson);
    }

    // ================================================================
    // Demonstrations of blocked attacks
    // ================================================================

    private static void demonstrateAlgNoneAttack(long future) {
        String token = createRawToken(
                "{\"alg\":\"none\",\"typ\":\"JWT\"}",
                "{\"sub\":\"attacker\",\"role\":\"admin\",\"exp\":" + future + "}",
                ""
        );

        printValidation("alg:none crafted token", token);
    }

    private static void demonstrateRsaToHmacConfusionAttack(long future) {
        /*
         * A vulnerable server may accept "RS256" and accidentally use
         * an RSA public key as an HMAC secret. This validator never does:
         * it only permits HS256 and receives a dedicated HMAC byte key.
         */
        String signingInput = base64Url(
                "{\"alg\":\"RS256\",\"typ\":\"JWT\"}"
                        .getBytes(StandardCharsets.UTF_8))
                + "."
                + base64Url(
                ("{\"sub\":\"attacker\",\"role\":\"admin\",\"exp\":" + future + "}")
                        .getBytes(StandardCharsets.UTF_8));

        String fakeSignature = base64Url(hmacSha256(
                signingInput.getBytes(StandardCharsets.US_ASCII),
                ascii("pretend-RSA-public-key")
        ));

        printValidation("RSA-to-HMAC key-confusion token",
                signingInput + "." + fakeSignature);
    }

    private static void demonstrateKidInjectionAttack(long future) {
        String token = createRawToken(
                "{\"alg\":\"HS256\",\"typ\":\"JWT\",\"kid\":\"../../attacker.key\"}",
                "{\"sub\":\"attacker\",\"role\":\"admin\",\"exp\":" + future + "}",
                "invalid-signature"
        );

        printValidation("kid header-injection token", token);
    }

    private static void demonstrateModifiedPayloadAttack(String originalToken) {
        String[] parts = originalToken.split("\\.", -1);

        String changedPayload = base64Url(
                "{\"sub\":\"alice\",\"role\":\"admin\",\"exp\":4102444800}"
                        .getBytes(StandardCharsets.UTF_8)
        );

        String forged = parts[0] + "." + changedPayload + "." + parts[2];

        printValidation("Payload-modification token", forged);
    }

    private static void demonstrateExpiredToken() {
        long past = (System.currentTimeMillis() / 1000L) - 60L;

        String token = createHs256Token(
                "{\"alg\":\"HS256\",\"typ\":\"JWT\"}",
                "{\"sub\":\"alice\",\"exp\":" + past + "}",
                HMAC_SECRET
        );

        printValidation("Expired token", token);
    }

    private static void printValidation(String title, String token) {
        ValidationResult result = validateHs256(token, HMAC_SECRET);

        System.out.println();
        System.out.println(title + ":");
        System.out.println("  Accepted: " + result.valid);
        System.out.println("  Reason  : " + result.message);

        if (result.valid) {
            System.out.println("  Header  : " + result.headerJson);
            System.out.println("  Payload : " + result.payloadJson);
        }
    }

    // ================================================================
    // Token creation for this console demonstration
    // ================================================================

    private static String createHs256Token(
            String headerJson, String payloadJson, byte[] secret) {

        String signingInput = base64Url(headerJson.getBytes(StandardCharsets.UTF_8))
                + "."
                + base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));

        byte[] signature = hmacSha256(
                signingInput.getBytes(StandardCharsets.US_ASCII),
                secret
        );

        return signingInput + "." + base64Url(signature);
    }

    private static String createRawToken(
            String headerJson, String payloadJson, String signaturePart) {

        return base64Url(headerJson.getBytes(StandardCharsets.UTF_8))
                + "."
                + base64Url(payloadJson.getBytes(StandardCharsets.UTF_8))
                + "."
                + signaturePart;
    }

    // ================================================================
    // HMAC-SHA256
    // ================================================================

    private static byte[] hmacSha256(byte[] data, byte[] secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 is unavailable", e);
        }
    }

    // ================================================================
    // Small strict JSON helpers
    // Suitable for this controlled educational JWT example.
    // ================================================================

    private static boolean isSimpleJsonObject(String json) {
        if (json == null) return false;

        String trimmed = json.trim();

        return trimmed.length() >= 2
                && trimmed.charAt(0) == '{'
                && trimmed.charAt(trimmed.length() - 1) == '}';
    }

    private static boolean containsJsonField(String json, String field) {
        return json.indexOf("\"" + field + "\"") >= 0;
    }

    private static boolean containsDuplicateField(String json, String field) {
        String needle = "\"" + field + "\"";
        int first = json.indexOf(needle);

        if (first < 0) {
            return false;
        }

        return json.indexOf(needle, first + needle.length()) >= 0;
    }

    private static String readJsonString(String json, String field) {
        String needle = "\"" + field + "\"";
        int fieldPosition = json.indexOf(needle);

        if (fieldPosition < 0) {
            return null;
        }

        int colon = json.indexOf(':', fieldPosition + needle.length());

        if (colon < 0) {
            return null;
        }

        int quoteStart = json.indexOf('"', colon + 1);

        if (quoteStart < 0) {
            return null;
        }

        int quoteEnd = json.indexOf('"', quoteStart + 1);

        if (quoteEnd < 0) {
            return null;
        }

        return json.substring(quoteStart + 1, quoteEnd);
    }

    private static Long readJsonLong(String json, String field) {
        String needle = "\"" + field + "\"";
        int fieldPosition = json.indexOf(needle);

        if (fieldPosition < 0) {
            return null;
        }

        int colon = json.indexOf(':', fieldPosition + needle.length());

        if (colon < 0) {
            return null;
        }

        int start = colon + 1;

        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }

        int end = start;

        while (end < json.length()) {
            char c = json.charAt(end);

            if ((c >= '0' && c <= '9') || c == '-') {
                end++;
            } else {
                break;
            }
        }

        if (start == end) {
            return null;
        }

        try {
            return Long.valueOf(json.substring(start, end));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ================================================================
    // Encoding helpers
    // ================================================================

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private static String decodeBase64UrlText(String text) {
        return new String(
                Base64.getUrlDecoder().decode(text),
                StandardCharsets.UTF_8
        );
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    // ================================================================
    // Result type
    // ================================================================

    static final class ValidationResult {
        final boolean valid;
        final String message;
        final String headerJson;
        final String payloadJson;

        private ValidationResult(
                boolean valid, String message,
                String headerJson, String payloadJson) {

            this.valid = valid;
            this.message = message;
            this.headerJson = headerJson;
            this.payloadJson = payloadJson;
        }

        static ValidationResult success(String headerJson, String payloadJson) {
            return new ValidationResult(
                    true,
                    "Valid HS256 JWT",
                    headerJson,
                    payloadJson
            );
        }

        static ValidationResult fail(String message) {
            return new ValidationResult(false, message, null, null);
        }
    }
}