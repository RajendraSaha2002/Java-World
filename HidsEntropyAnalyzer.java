import java.util.ArrayList;
import java.util.List;


public final class HidsEntropyAnalyzer {
    private static final int SAMPLE_SIZE = 4096;
    private static final int BASELINE_BATCHES = 10;

    private HidsEntropyAnalyzer() {
    }

    public static void main(String[] args) {
        System.out.println("HIDS Entropy Analyzer");
        System.out.println("Timing jitter source: System.nanoTime()");
        System.out.println();

        Baseline baseline = buildBaseline();

        System.out.println("=== Baseline established ===");
        System.out.println("Mean entropy : " + format(baseline.meanEntropy) + " bits/byte");
        System.out.println("Std deviation: " + format(baseline.standardDeviation));
        System.out.println("Alert limit  : " + format(baseline.minimumEntropy) + " bits/byte");
        System.out.println();

        System.out.println("=== Normal jitter monitoring ===");

        for (int i = 1; i <= 3; i++) {
            byte[] sample = collectTimingJitter(SAMPLE_SIZE);
            Analysis analysis = analyze(sample);

            printAnalysis("Normal sample #" + i, analysis);
            checkForAlert(analysis, baseline);
            System.out.println();
        }

        System.out.println("=== Simulated low-entropy attack ===");

        byte[] simulatedAttack = new byte[SAMPLE_SIZE];

        for (int i = 0; i < simulatedAttack.length; i++) {
            simulatedAttack[i] = 0;
        }

        Analysis attackAnalysis = analyze(simulatedAttack);

        printAnalysis("Simulated attack sample", attackAnalysis);
        checkForAlert(attackAnalysis, baseline);
    }

    // ================================================================
    // Baseline collection and monitoring
    // ================================================================

    private static Baseline buildBaseline() {
        List<Double> entropyValues = new ArrayList<Double>();

        for (int i = 0; i < BASELINE_BATCHES; i++) {
            byte[] sample = collectTimingJitter(SAMPLE_SIZE);
            Analysis analysis = analyze(sample);
            entropyValues.add(Double.valueOf(analysis.shannonEntropy));
        }

        double total = 0.0;

        for (int i = 0; i < entropyValues.size(); i++) {
            total += entropyValues.get(i).doubleValue();
        }

        double mean = total / entropyValues.size();
        double variance = 0.0;

        for (int i = 0; i < entropyValues.size(); i++) {
            double difference = entropyValues.get(i).doubleValue() - mean;
            variance += difference * difference;
        }

        variance /= entropyValues.size();
        double standardDeviation = Math.sqrt(variance);

        /*
         * Alert if entropy falls below three standard deviations
         * from the normal learned baseline.
         */
        double minimumEntropy = mean - (3.0 * standardDeviation);

        if (minimumEntropy < 1.0) {
            minimumEntropy = 1.0;
        }

        return new Baseline(mean, standardDeviation, minimumEntropy);
    }

    private static void checkForAlert(Analysis analysis, Baseline baseline) {
        if (analysis.shannonEntropy < baseline.minimumEntropy) {
            System.out.println("ALERT: Entropy dropped below the learned threshold.");
        } else {
            System.out.println("Status: Entropy is within the learned baseline.");
        }
    }

    /*
     * Samples differences between high-resolution timestamps.
     * Only the low byte of each timing delta is retained.
     */
    private static byte[] collectTimingJitter(int count) {
        byte[] data = new byte[count];

        long previous = System.nanoTime();

        for (int i = 0; i < count; i++) {
            long current = System.nanoTime();
            long delta = current - previous;

            data[i] = (byte) delta;
            previous = current;
        }

        return data;
    }

    // ================================================================
    // Statistical analysis
    // ================================================================

    private static Analysis analyze(byte[] data) {
        return new Analysis(
                shannonEntropy(data),
                chiSquare(data),
                monobitFrequency(data),
                serialCorrelation(data),
                runsTest(data)
        );
    }

    /*
     * Shannon entropy:
     * H = -sum(p(x) * log2(p(x)))
     *
     * Maximum for uniformly distributed bytes is 8 bits per byte.
     */
    private static double shannonEntropy(byte[] data) {
        int[] frequency = histogram(data);
        double entropy = 0.0;

        for (int i = 0; i < frequency.length; i++) {
            if (frequency[i] == 0) {
                continue;
            }

            double probability = (double) frequency[i] / data.length;
            entropy -= probability * (Math.log(probability) / Math.log(2.0));
        }

        return entropy;
    }

    /*
     * Chi-square uniformity statistic:
     * chi2 = sum((observed - expected)^2 / expected)
     */
    private static double chiSquare(byte[] data) {
        int[] frequency = histogram(data);
        double expected = (double) data.length / 256.0;
        double chiSquare = 0.0;

        for (int i = 0; i < frequency.length; i++) {
            double difference = frequency[i] - expected;
            chiSquare += (difference * difference) / expected;
        }

        return chiSquare;
    }

    /*
     * Monobit frequency:
     * reports the proportion of 1 bits in the sample.
     * A balanced stream should be close to 0.5.
     */
    private static double monobitFrequency(byte[] data) {
        long oneBits = 0;

        for (int i = 0; i < data.length; i++) {
            int value = data[i] & 0xFF;

            for (int bit = 0; bit < 8; bit++) {
                oneBits += (value >>> bit) & 1;
            }
        }

        return (double) oneBits / (data.length * 8L);
    }

    /*
     * Serial correlation coefficient for adjacent byte values.
     * Values near zero are preferable for this simple indicator.
     */
    private static double serialCorrelation(byte[] data) {
        if (data.length < 2) {
            return 0.0;
        }

        double sum = 0.0;
        double sumSquares = 0.0;
        double adjacentProductSum = 0.0;

        for (int i = 0; i < data.length; i++) {
            double value = data[i] & 0xFF;

            sum += value;
            sumSquares += value * value;

            double next = data[(i + 1) % data.length] & 0xFF;
            adjacentProductSum += value * next;
        }

        double count = data.length;
        double numerator = count * adjacentProductSum - sum * sum;
        double denominator = count * sumSquares - sum * sum;

        if (denominator == 0.0) {
            return 0.0;
        }

        return numerator / denominator;
    }

    /*
     * NIST-inspired runs calculation:
     * A run is a consecutive sequence of equal bits.
     */
    private static RunsResult runsTest(byte[] data) {
        int totalBits = data.length * 8;
        int oneBits = 0;
        int runs = 0;

        int previousBit = -1;

        for (int i = 0; i < data.length; i++) {
            int value = data[i] & 0xFF;

            for (int bit = 7; bit >= 0; bit--) {
                int currentBit = (value >>> bit) & 1;

                oneBits += currentBit;

                if (currentBit != previousBit) {
                    runs++;
                    previousBit = currentBit;
                }
            }
        }

        double proportionOfOnes = (double) oneBits / totalBits;
        double expectedRuns = 2.0 * totalBits
                * proportionOfOnes * (1.0 - proportionOfOnes);

        double difference = Math.abs(runs - expectedRuns);

        return new RunsResult(runs, expectedRuns, difference);
    }

    private static int[] histogram(byte[] data) {
        int[] frequency = new int[256];

        for (int i = 0; i < data.length; i++) {
            frequency[data[i] & 0xFF]++;
        }

        return frequency;
    }

    // ================================================================
    // Output
    // ================================================================

    private static void printAnalysis(String title, Analysis analysis) {
        System.out.println(title);
        System.out.println("  Shannon entropy       : "
                + format(analysis.shannonEntropy) + " bits/byte");

        System.out.println("  Chi-square statistic  : "
                + format(analysis.chiSquare));

        System.out.println("  Monobit one ratio     : "
                + format(analysis.monobitOneRatio));

        System.out.println("  Serial correlation    : "
                + format(analysis.serialCorrelation));

        System.out.println("  Observed runs         : "
                + analysis.runsResult.observedRuns);

        System.out.println("  Expected runs         : "
                + format(analysis.runsResult.expectedRuns));

        System.out.println("  Runs difference       : "
                + format(analysis.runsResult.difference));
    }

    private static String format(double value) {
        return String.format("%.6f", value);
    }

    // ================================================================
    // Data classes
    // ================================================================

    static final class Baseline {
        final double meanEntropy;
        final double standardDeviation;
        final double minimumEntropy;

        Baseline(double meanEntropy,
                 double standardDeviation,
                 double minimumEntropy) {

            this.meanEntropy = meanEntropy;
            this.standardDeviation = standardDeviation;
            this.minimumEntropy = minimumEntropy;
        }
    }

    static final class Analysis {
        final double shannonEntropy;
        final double chiSquare;
        final double monobitOneRatio;
        final double serialCorrelation;
        final RunsResult runsResult;

        Analysis(double shannonEntropy,
                 double chiSquare,
                 double monobitOneRatio,
                 double serialCorrelation,
                 RunsResult runsResult) {

            this.shannonEntropy = shannonEntropy;
            this.chiSquare = chiSquare;
            this.monobitOneRatio = monobitOneRatio;
            this.serialCorrelation = serialCorrelation;
            this.runsResult = runsResult;
        }
    }

    static final class RunsResult {
        final int observedRuns;
        final double expectedRuns;
        final double difference;

        RunsResult(int observedRuns,
                   double expectedRuns,
                   double difference) {

            this.observedRuns = observedRuns;
            this.expectedRuns = expectedRuns;
            this.difference = difference;
        }
    }
}