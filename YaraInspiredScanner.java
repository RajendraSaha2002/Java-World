import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Defensive YARA-inspired scanner.
 *
 * - Parses compact YARA-like rules
 * - Supports hex, ASCII, wide, and nocase patterns
 * - Uses Aho-Corasick for simultaneous pattern matching
 * - Scores/ranks simulated byte-array samples
 *
 * This program scans only embedded simulated data. It does not execute,
 * download, modify, or interact with files or malware.
 */
public final class YaraInspiredScanner {
    public static void main(String[] args) {
        String rulesText =
                "rule Suspicious_Dropper {\n"
                        + "meta:\n"
                        + "  author = \"security-team\"\n"
                        + "  severity = \"high\"\n"
                        + "  description = \"Detects simulated loader indicators\"\n"
                        + "strings:\n"
                        + "  $mz = { 4D 5A }\n"
                        + "  $powershell = \"powershell\" ascii nocase\n"
                        + "  $download = \"download\" ascii nocase\n"
                        + "  $evil_wide = \"evil\" wide nocase\n"
                        + "condition:\n"
                        + "  2 of them\n"
                        + "}\n"
                        + "\n"
                        + "rule Benign_Office_Document {\n"
                        + "meta:\n"
                        + "  author = \"security-team\"\n"
                        + "  severity = \"low\"\n"
                        + "strings:\n"
                        + "  $pdf = \"%PDF\" ascii\n"
                        + "  $word = \"document\" ascii nocase\n"
                        + "condition:\n"
                        + "  all of them\n"
                        + "}\n";

        List<YaraRule> rules = RuleParser.parse(rulesText);

        List<Sample> samples = new ArrayList<Sample>();

        samples.add(new Sample(
                "simulated-loader.bin",
                concat(
                        hex("4D5A900003000000"),
                        ascii(" harmless padding POWERSHELL DOWNLOAD "),
                        wideAscii("evil")
                )
        ));

        samples.add(new Sample(
                "simulated-document.bin",
                ascii("%PDF-1.7 simulated document content")
        ));

        samples.add(new Sample(
                "clean-data.bin",
                ascii("This is ordinary test data without listed indicators.")
        ));

        List<ScanResult> results = new ArrayList<ScanResult>();

        for (int i = 0; i < samples.size(); i++) {
            results.add(scan(samples.get(i), rules));
        }

        Collections.sort(results, new Comparator<ScanResult>() {
            public int compare(ScanResult first, ScanResult second) {
                return second.score - first.score;
            }
        });

        printResults(results);
    }

    // ================================================================
    // Scanner
    // ================================================================

    private static ScanResult scan(Sample sample, List<YaraRule> rules) {
        AhoCorasick automaton = new AhoCorasick();
        Map<String, PatternReference> references =
                new HashMap<String, PatternReference>();

        for (int ruleIndex = 0; ruleIndex < rules.size(); ruleIndex++) {
            YaraRule rule = rules.get(ruleIndex);

            for (int stringIndex = 0; stringIndex < rule.strings.size(); stringIndex++) {
                RuleString ruleString = rule.strings.get(stringIndex);

                for (int variant = 0; variant < ruleString.variants.size(); variant++) {
                    String patternId = rule.name + ":" + ruleString.identifier + ":" + variant;

                    automaton.addPattern(
                            patternId,
                            ruleString.variants.get(variant)
                    );

                    references.put(
                            patternId,
                            new PatternReference(rule.name, ruleString.identifier)
                    );
                }
            }
        }

        automaton.buildFailureLinks();

        Set<String> rawMatches = automaton.search(sample.bytes);
        Map<String, Set<String>> matchesByRule =
                new HashMap<String, Set<String>>();

        for (String patternId : rawMatches) {
            PatternReference reference = references.get(patternId);

            if (reference == null) continue;

            Set<String> strings = matchesByRule.get(reference.ruleName);

            if (strings == null) {
                strings = new HashSet<String>();
                matchesByRule.put(reference.ruleName, strings);
            }

            strings.add(reference.stringIdentifier);
        }

        List<RuleMatch> ruleMatches = new ArrayList<RuleMatch>();
        int score = 0;

        for (int i = 0; i < rules.size(); i++) {
            YaraRule rule = rules.get(i);
            Set<String> matched = matchesByRule.get(rule.name);

            if (matched == null) {
                matched = new HashSet<String>();
            }

            if (rule.condition.matches(matched.size(), rule.strings.size())) {
                int ruleScore = severityScore(rule.metadata.get("severity"))
                        + matched.size() * 10;

                score += ruleScore;
                ruleMatches.add(new RuleMatch(rule, matched, ruleScore));
            }
        }

        return new ScanResult(sample, ruleMatches, score);
    }

    private static int severityScore(String severity) {
        if (severity == null) return 10;

        if ("critical".equalsIgnoreCase(severity)) return 100;
        if ("high".equalsIgnoreCase(severity)) return 70;
        if ("medium".equalsIgnoreCase(severity)) return 40;
        if ("low".equalsIgnoreCase(severity)) return 10;

        return 20;
    }

    private static void printResults(List<ScanResult> results) {
        System.out.println("YARA-inspired Aho-Corasick scanner");
        System.out.println("Simulated samples only");
        System.out.println();

        for (int i = 0; i < results.size(); i++) {
            ScanResult result = results.get(i);

            System.out.println("Sample: " + result.sample.name);
            System.out.println("Size  : " + result.sample.bytes.length + " bytes");
            System.out.println("Score : " + result.score);

            if (result.matches.isEmpty()) {
                System.out.println("Result: No matching rules");
            } else {
                System.out.println("Result: MATCH");

                for (int j = 0; j < result.matches.size(); j++) {
                    RuleMatch match = result.matches.get(j);

                    System.out.println("  Rule     : " + match.rule.name);
                    System.out.println("  Severity : "
                            + match.rule.metadata.get("severity"));
                    System.out.println("  IOCs     : " + match.matchedIdentifiers);
                    System.out.println("  Score    : " + match.score);
                }
            }

            System.out.println();
        }
    }

    // ================================================================
    // Rule parser
    // ================================================================

    static final class RuleParser {
        private static final Pattern RULE_PATTERN = Pattern.compile(
                "(?ms)rule\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\{(.*?)^\\}");

        private static final Pattern META_PATTERN = Pattern.compile(
                "(?m)^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*\"([^\"]*)\"\\s*$");

        private static final Pattern STRING_PATTERN = Pattern.compile(
                "(?m)^\\s*\\$([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*"
                        + "(\\{([^}]*)\\}|\"([^\"]*)\")"
                        + "\\s*(ascii|wide)?\\s*(nocase)?\\s*$");

        static List<YaraRule> parse(String ruleText) {
            List<YaraRule> rules = new ArrayList<YaraRule>();
            Matcher ruleMatcher = RULE_PATTERN.matcher(ruleText);

            while (ruleMatcher.find()) {
                String ruleName = ruleMatcher.group(1);
                String body = ruleMatcher.group(2);

                String metaBlock = section(body, "meta:", "strings:");
                String stringsBlock = section(body, "strings:", "condition:");
                String conditionBlock = after(body, "condition:");

                Map<String, String> metadata = parseMetadata(metaBlock);
                List<RuleString> strings = parseStrings(stringsBlock);
                RuleCondition condition = parseCondition(conditionBlock);

                if (strings.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Rule " + ruleName + " contains no strings");
                }

                rules.add(new YaraRule(ruleName, metadata, strings, condition));
            }

            if (rules.isEmpty()) {
                throw new IllegalArgumentException("No valid rules found");
            }

            return rules;
        }

        private static Map<String, String> parseMetadata(String text) {
            Map<String, String> metadata = new HashMap<String, String>();
            Matcher matcher = META_PATTERN.matcher(text);

            while (matcher.find()) {
                metadata.put(matcher.group(1), matcher.group(2));
            }

            return metadata;
        }

        private static List<RuleString> parseStrings(String text) {
            List<RuleString> strings = new ArrayList<RuleString>();
            Matcher matcher = STRING_PATTERN.matcher(text);

            while (matcher.find()) {
                String identifier = matcher.group(1);
                String fullPattern = matcher.group(2);
                String hexBody = matcher.group(3);
                String textBody = matcher.group(4);
                String encoding = matcher.group(5);
                boolean noCase = matcher.group(6) != null;

                RuleString ruleString = new RuleString(identifier);

                if (fullPattern.startsWith("{")) {
                    ruleString.variants.add(parseHexPattern(hexBody));
                } else {
                    boolean wide = "wide".equalsIgnoreCase(encoding);

                    byte[] value = wide
                            ? wideAscii(textBody)
                            : ascii(textBody);

                    if (noCase && !wide) {
                        ruleString.variants.add(toLowerAscii(value));
                        ruleString.variants.add(toUpperAscii(value));
                    } else {
                        ruleString.variants.add(value);
                    }
                }

                strings.add(ruleString);
            }

            return strings;
        }

        private static RuleCondition parseCondition(String text) {
            String normalized = text.trim().toLowerCase();

            if ("all of them".equals(normalized)) {
                return new RuleCondition(ConditionType.ALL, 0);
            }

            if ("any of them".equals(normalized)) {
                return new RuleCondition(ConditionType.ANY, 0);
            }

            Matcher threshold = Pattern.compile("(\\d+)\\s+of\\s+them")
                    .matcher(normalized);

            if (threshold.matches()) {
                return new RuleCondition(
                        ConditionType.THRESHOLD,
                        Integer.parseInt(threshold.group(1))
                );
            }

            throw new IllegalArgumentException(
                    "Supported conditions: all of them, any of them, N of them");
        }

        private static String section(String body, String start, String end) {
            int startIndex = body.indexOf(start);

            if (startIndex < 0) return "";

            startIndex += start.length();

            int endIndex = body.indexOf(end, startIndex);

            if (endIndex < 0) return body.substring(startIndex);

            return body.substring(startIndex, endIndex);
        }

        private static String after(String body, String marker) {
            int index = body.indexOf(marker);

            return index < 0 ? "" : body.substring(index + marker.length());
        }
    }

    // ================================================================
    // Aho-Corasick multi-pattern matcher
    // ================================================================

    static final class AhoCorasick {
        private final List<Node> nodes = new ArrayList<Node>();

        AhoCorasick() {
            nodes.add(new Node());
        }

        void addPattern(String patternId, byte[] pattern) {
            if (pattern == null || pattern.length == 0) {
                throw new IllegalArgumentException("Patterns cannot be empty");
            }

            int state = 0;

            for (int i = 0; i < pattern.length; i++) {
                int value = pattern[i] & 0xFF;
                Integer next = nodes.get(state).next.get(value);

                if (next == null) {
                    next = Integer.valueOf(nodes.size());
                    nodes.get(state).next.put(value, next);
                    nodes.add(new Node());
                }

                state = next.intValue();
            }

            nodes.get(state).outputs.add(patternId);
        }

        void buildFailureLinks() {
            ArrayDeque<Integer> queue = new ArrayDeque<Integer>();

            for (Integer child : nodes.get(0).next.values()) {
                nodes.get(child.intValue()).failure = 0;
                queue.addLast(child);
            }

            while (!queue.isEmpty()) {
                int current = queue.removeFirst().intValue();

                for (Map.Entry<Integer, Integer> edge
                        : nodes.get(current).next.entrySet()) {

                    int byteValue = edge.getKey().intValue();
                    int child = edge.getValue().intValue();

                    queue.addLast(Integer.valueOf(child));

                    int failure = nodes.get(current).failure;

                    while (failure != 0
                            && !nodes.get(failure).next.containsKey(byteValue)) {
                        failure = nodes.get(failure).failure;
                    }

                    Integer fallback = nodes.get(failure).next.get(byteValue);

                    if (fallback != null && fallback.intValue() != child) {
                        nodes.get(child).failure = fallback.intValue();
                    } else {
                        nodes.get(child).failure = 0;
                    }

                    nodes.get(child).outputs.addAll(
                            nodes.get(nodes.get(child).failure).outputs);
                }
            }
        }

        Set<String> search(byte[] data) {
            Set<String> matches = new HashSet<String>();
            int state = 0;

            for (int i = 0; i < data.length; i++) {
                int value = data[i] & 0xFF;

                while (state != 0 && !nodes.get(state).next.containsKey(value)) {
                    state = nodes.get(state).failure;
                }

                Integer next = nodes.get(state).next.get(value);

                if (next != null) {
                    state = next.intValue();
                }

                matches.addAll(nodes.get(state).outputs);
            }

            return matches;
        }

        static final class Node {
            final Map<Integer, Integer> next = new HashMap<Integer, Integer>();
            final Set<String> outputs = new HashSet<String>();
            int failure;
        }
    }

    // ================================================================
    // Model classes
    // ================================================================

    static final class YaraRule {
        final String name;
        final Map<String, String> metadata;
        final List<RuleString> strings;
        final RuleCondition condition;

        YaraRule(String name, Map<String, String> metadata,
                 List<RuleString> strings, RuleCondition condition) {
            this.name = name;
            this.metadata = metadata;
            this.strings = strings;
            this.condition = condition;
        }
    }

    static final class RuleString {
        final String identifier;
        final List<byte[]> variants = new ArrayList<byte[]>();

        RuleString(String identifier) {
            this.identifier = identifier;
        }
    }

    static final class RuleCondition {
        final ConditionType type;
        final int threshold;

        RuleCondition(ConditionType type, int threshold) {
            this.type = type;
            this.threshold = threshold;
        }

        boolean matches(int matched, int total) {
            if (type == ConditionType.ALL) return matched == total;
            if (type == ConditionType.ANY) return matched > 0;
            return matched >= threshold;
        }
    }

    enum ConditionType {
        ALL, ANY, THRESHOLD
    }

    static final class Sample {
        final String name;
        final byte[] bytes;

        Sample(String name, byte[] bytes) {
            this.name = name;
            this.bytes = bytes;
        }
    }

    static final class PatternReference {
        final String ruleName;
        final String stringIdentifier;

        PatternReference(String ruleName, String stringIdentifier) {
            this.ruleName = ruleName;
            this.stringIdentifier = stringIdentifier;
        }
    }

    static final class RuleMatch {
        final YaraRule rule;
        final Set<String> matchedIdentifiers;
        final int score;

        RuleMatch(YaraRule rule, Set<String> matchedIdentifiers, int score) {
            this.rule = rule;
            this.matchedIdentifiers = matchedIdentifiers;
            this.score = score;
        }
    }

    static final class ScanResult {
        final Sample sample;
        final List<RuleMatch> matches;
        final int score;

        ScanResult(Sample sample, List<RuleMatch> matches, int score) {
            this.sample = sample;
            this.matches = matches;
            this.score = score;
        }
    }

    // ================================================================
    // Byte helpers
    // ================================================================

    private static byte[] parseHexPattern(String text) {
        String cleaned = text.replaceAll("\\s+", "");

        if ((cleaned.length() & 1) != 0) {
            throw new IllegalArgumentException("Hex pattern has odd length: " + text);
        }

        byte[] result = new byte[cleaned.length() / 2];

        for (int i = 0; i < result.length; i++) {
            int high = Character.digit(cleaned.charAt(i * 2), 16);
            int low = Character.digit(cleaned.charAt(i * 2 + 1), 16);

            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Invalid hex pattern: " + text);
            }

            result[i] = (byte) ((high << 4) | low);
        }

        return result;
    }

    private static byte[] ascii(String text) {
        byte[] result = new byte[text.length()];

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c > 127) {
                throw new IllegalArgumentException("ASCII strings only");
            }

            result[i] = (byte) c;
        }

        return result;
    }

    private static byte[] wideAscii(String text) {
        byte[] result = new byte[text.length() * 2];

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c > 127) {
                throw new IllegalArgumentException("ASCII strings only");
            }

            result[i * 2] = (byte) c;
            result[i * 2 + 1] = 0;
        }

        return result;
    }

    private static byte[] toLowerAscii(byte[] source) {
        byte[] result = new byte[source.length];

        for (int i = 0; i < source.length; i++) {
            int value = source[i] & 0xFF;

            if (value >= 'A' && value <= 'Z') {
                value += 32;
            }

            result[i] = (byte) value;
        }

        return result;
    }

    private static byte[] toUpperAscii(byte[] source) {
        byte[] result = new byte[source.length];

        for (int i = 0; i < source.length; i++) {
            int value = source[i] & 0xFF;

            if (value >= 'a' && value <= 'z') {
                value -= 32;
            }

            result[i] = (byte) value;
        }

        return result;
    }

    private static byte[] hex(String text) {
        return parseHexPattern(text);
    }

    private static byte[] concat(byte[]... values) {
        int totalLength = 0;

        for (int i = 0; i < values.length; i++) {
            totalLength += values[i].length;
        }

        byte[] result = new byte[totalLength];
        int offset = 0;

        for (int i = 0; i < values.length; i++) {
            for (int j = 0; j < values[i].length; j++) {
                result[offset++] = values[i][j];
            }
        }

        return result;
    }
}