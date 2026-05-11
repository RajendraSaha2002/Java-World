import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClusterManager {
    private static final int PORT = 7070;
    private IntrusionEngine engine;
    private ConcurrentHashMap<String, PrintWriter> activeNodes = new ConcurrentHashMap<>();

    public ClusterManager(IntrusionEngine engine) {
        this.engine = engine;
    }

    public void startNetwork() {
        new Thread(() -> {
            try (ServerSocket server = new ServerSocket(PORT)) {
                while (true) {
                    Socket client = server.accept();
                    new Thread(() -> handleNode(client)).start();
                }
            } catch (IOException ignored) {}
        }).start();
    }

    private void handleNode(Socket client) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             PrintWriter out = new PrintWriter(client.getOutputStream(), true)) {

            String payload;
            while ((payload = in.readLine()) != null) {
                try {
                    String nodeId = parseJson(payload, "node_id");
                    activeNodes.put(nodeId, out);

                    double load = Double.parseDouble(parseJson(payload, "cpu_load"));
                    double temp = Double.parseDouble(parseJson(payload, "temp"));
                    String procId = parseJson(payload, "proc_id");

                    engine.analyzeTelemetry(nodeId, load, temp, procId);
                } catch (Exception e) {
                    // Prevent a bad parsing error from dropping the connection!
                    System.out.println("Data Parse Error, ignoring packet: " + payload);
                }
            }
        } catch (Exception ignored) {}
    }

    public void killNode(String nodeId) {
        PrintWriter out = activeNodes.get(nodeId);
        if (out != null) {
            out.println("{\"cmd\":\"SYS_EXIT\"}");
        }
    }

    // FIX: Bulletproof Native JSON Parser
    private String parseJson(String json, String key) {
        try {
            String search = "\"" + key + "\":";
            int start = json.indexOf(search);
            if (start == -1) return "0";

            start += search.length();

            // Check if the value is a String (starts with quotes) or a Number
            if (json.charAt(start) == '"') {
                start++; // Skip the opening quote
                int end = json.indexOf("\"", start);
                return json.substring(start, end);
            } else {
                // It's a number, read until the next comma or closing bracket
                int end = json.indexOf(",", start);
                if (end == -1) end = json.indexOf("}", start);
                return json.substring(start, end).trim();
            }
        } catch (Exception e) {
            return "0";
        }
    }
}