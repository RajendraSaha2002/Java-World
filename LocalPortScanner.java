import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*
 * Local-only multi-threaded TCP connect scanner with banner grabbing.
 *
 * Safety restriction:
 * This program refuses to scan non-loopback targets.
 *
 * Example:
 * java LocalPortScanner 127.0.0.1 1 1024 50 800
 */
public final class LocalPortScanner {
    private LocalPortScanner() {
    }

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int startPort = args.length > 1 ? parsePort(args[1], "start port") : 1;
        int endPort = args.length > 2 ? parsePort(args[2], "end port") : 1024;
        int concurrency = args.length > 3 ? parsePositive(args[3], "concurrency") : 50;
        int timeoutMs = args.length > 4 ? parsePositive(args[4], "timeout") : 800;

        if (startPort > endPort) {
            throw new IllegalArgumentException("Start port must not exceed end port");
        }

        if (concurrency > 256) {
            throw new IllegalArgumentException("Concurrency must not exceed 256");
        }

        InetAddress address = InetAddress.getByName(host);

        if (!address.isLoopbackAddress()) {
            throw new SecurityException(
                    "Safety restriction: only localhost / loopback scanning is permitted");
        }

        System.out.println("Local TCP port scanner");
        System.out.println("Target      : " + address.getHostAddress());
        System.out.println("Port range  : " + startPort + "-" + endPort);
        System.out.println("Workers     : " + concurrency);
        System.out.println("Timeout     : " + timeoutMs + " ms");
        System.out.println();

        long startTime = System.nanoTime();

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        ExecutorCompletionService<ScanResult> completion =
                new ExecutorCompletionService<ScanResult>(executor);

        int totalPorts = endPort - startPort + 1;

        for (int port = startPort; port <= endPort; port++) {
            completion.submit(new PortScanTask(address, port, timeoutMs));
        }

        List<ScanResult> openPorts = new ArrayList<ScanResult>();

        try {
            for (int completed = 1; completed <= totalPorts; completed++) {
                ScanResult result = completion.take().get();

                if (result.open) {
                    openPorts.add(result);
                    System.out.println("\rCompleted " + completed + "/" + totalPorts
                            + " - open port found: " + result.port + "        ");
                } else {
                    System.out.print("\rCompleted " + completed + "/" + totalPorts + "        ");
                }
            }
        } finally {
            executor.shutdownNow();
        }

        long elapsed = System.nanoTime() - startTime;

        Collections.sort(openPorts, new Comparator<ScanResult>() {
            public int compare(ScanResult first, ScanResult second) {
                return first.port - second.port;
            }
        });

        System.out.println();
        System.out.println();
        printResults(openPorts);

        System.out.println();
        System.out.println("Scan completed in "
                + (elapsed / 1_000_000.0) + " ms");
    }

    private static void printResults(List<ScanResult> results) {
        System.out.println("+-------+----------+----------------------+-----------------------------------+");
        System.out.println("| Port  | State    | Service guess        | Banner / response                 |");
        System.out.println("+-------+----------+----------------------+-----------------------------------+");

        if (results.isEmpty()) {
            System.out.println("| No open local TCP ports found.                                           |");
        } else {
            for (int i = 0; i < results.size(); i++) {
                ScanResult result = results.get(i);

                String port = String.valueOf(result.port);
                String state = "OPEN";
                String service = limit(result.service, 20);
                String banner = limit(result.banner, 33);

                System.out.printf("| %-5s | %-8s | %-20s | %-33s |%n",
                        port, state, service, banner);
            }
        }

        System.out.println("+-------+----------+----------------------+-----------------------------------+");
    }

    static final class PortScanTask implements Callable<ScanResult> {
        private final InetAddress address;
        private final int port;
        private final int timeoutMs;

        PortScanTask(InetAddress address, int port, int timeoutMs) {
            this.address = address;
            this.port = port;
            this.timeoutMs = timeoutMs;
        }

        public ScanResult call() {
            Socket socket = new Socket();

            try {
                socket.connect(new java.net.InetSocketAddress(address, port), timeoutMs);
                socket.setSoTimeout(timeoutMs);

                String response = grabBanner(socket, port);
                String service = identifyService(port, response);

                return new ScanResult(port, true, service, response);
            } catch (Exception ignored) {
                return new ScanResult(port, false, "", "");
            } finally {
                try {
                    socket.close();
                } catch (Exception ignored) {
                    // Nothing to do.
                }
            }
        }
    }

    /*
     * Sends harmless protocol-specific probes for common local services.
     */
    private static String grabBanner(Socket socket, int port) {
        try {
            OutputStream output = socket.getOutputStream();

            if (port == 80 || port == 8080 || port == 8000 || port == 8888) {
                output.write(ascii(
                        "HEAD / HTTP/1.0\r\n"
                                + "Host: localhost\r\n"
                                + "Connection: close\r\n\r\n"));
                output.flush();
            } else if (port == 21) {
                // FTP normally sends its banner immediately.
            } else if (port == 25 || port == 587) {
                // SMTP normally sends its banner immediately.
            } else if (port == 22) {
                // SSH normally sends its banner immediately.
            }

            InputStream input = socket.getInputStream();
            byte[] buffer = new byte[512];
            int count;

            try {
                count = input.read(buffer);
            } catch (SocketTimeoutException ignored) {
                return "(connected; no banner received)";
            }

            if (count <= 0) {
                return "(connected; no banner received)";
            }

            String text = asciiFromBytes(buffer, count);
            return cleanBanner(text);
        } catch (Exception ignored) {
            return "(connected; banner unavailable)";
        }
    }

    private static String identifyService(int port, String banner) {
        String lower = banner.toLowerCase();

        if (lower.indexOf("ssh-") >= 0 || port == 22) return "SSH";
        if (lower.indexOf("ftp") >= 0 || port == 21) return "FTP";
        if (lower.indexOf("smtp") >= 0 || port == 25 || port == 587) return "SMTP";
        if (lower.indexOf("http/") >= 0 || port == 80 || port == 8080
                || port == 8000 || port == 8888) return "HTTP";
        if (port == 3306) return "MySQL";
        if (port == 5432) return "PostgreSQL";
        if (port == 6379) return "Redis";
        if (port == 27017) return "MongoDB";

        return "Unknown TCP service";
    }

    private static String cleanBanner(String text) {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '\r' || c == '\n' || c == '\t') {
                builder.append(' ');
            } else if (c >= 32 && c <= 126) {
                builder.append(c);
            }
        }

        String cleaned = builder.toString().trim();

        return cleaned.length() == 0
                ? "(connected; no readable banner)"
                : cleaned;
    }

    private static int parsePort(String text, String name) {
        int value = parsePositive(text, name);

        if (value < 1 || value > 65535) {
            throw new IllegalArgumentException(name + " must be from 1 to 65535");
        }

        return value;
    }

    private static int parsePositive(String text, String name) {
        try {
            int value = Integer.parseInt(text);

            if (value <= 0) {
                throw new IllegalArgumentException(name + " must be positive");
            }

            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + name + ": " + text);
        }
    }

    private static byte[] ascii(String text) {
        byte[] result = new byte[text.length()];

        for (int i = 0; i < text.length(); i++) {
            result[i] = (byte) text.charAt(i);
        }

        return result;
    }

    private static String asciiFromBytes(byte[] bytes, int length) {
        char[] result = new char[length];

        for (int i = 0; i < length; i++) {
            result[i] = (char) (bytes[i] & 0xFF);
        }

        return new String(result);
    }

    private static String limit(String text, int maximumLength) {
        if (text == null || text.length() == 0) {
            return "-";
        }

        if (text.length() <= maximumLength) {
            return text;
        }

        return text.substring(0, maximumLength - 3) + "...";
    }

    static final class ScanResult {
        final int port;
        final boolean open;
        final String service;
        final String banner;

        ScanResult(int port, boolean open, String service, String banner) {
            this.port = port;
            this.open = open;
            this.service = service;
            this.banner = banner;
        }
    }
}