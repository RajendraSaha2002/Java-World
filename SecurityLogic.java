import java.util.regex.Pattern;

public class SecurityLogic {
    private static final Pattern SQLI_PATTERN = Pattern.compile(
            "(?i)(\\bOR\\b\\s+1=1|--|/\\*|\\*/|;\\s*DROP\\b|\\bUNION\\b\\s+SELECT\\b)"
    );

    public static boolean isSuspiciousInput(String text) {
        if (text == null) return false;
        return SQLI_PATTERN.matcher(text).find();
    }

    public static String assessTelemetry(String assetKey, Double tempC, Double humidity, Double voltageV, Double loadPct) {
        if ("HVAC-01".equals(assetKey) && tempC != null && tempC > 30.0) {
            return "OVERHEAT";
        }
        if ("UPS-01".equals(assetKey) && loadPct != null && loadPct > 80.0) {
            return "POWER_STRESS";
        }
        if (voltageV != null && (voltageV < 220.0 || voltageV > 240.0)) {
            return "VOLTAGE_ANOMALY";
        }
        return "OK";
    }
}