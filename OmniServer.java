import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class OmniServer {
    private static final int PORT = 8080;
    private DatabaseManager dbManager;
    private DashboardUI ui;

    public OmniServer() {
        this.dbManager = new DatabaseManager();
        this.ui = new DashboardUI();
    }

    public void startListening() {
        ui.logToConsole("Java IDS Server listening on port " + PORT);
        ExecutorService executor = Executors.newCachedThreadPool();

        // Background thread to poll the Database for APTs
        ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor();
        poller.scheduleAtFixedRate(() -> {
            boolean isApt = dbManager.checkForAptAttack("127.0.0.1");
            ui.setAptAlert(isApt);
        }, 1, 1, TimeUnit.SECONDS);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                executor.submit(() -> handleClient(clientSocket));
            }
        } catch (IOException e) {
            ui.logToConsole("Server Exception: " + e.getMessage());
        }
    }

    private void handleClient(Socket socket) {
        String clientIp = socket.getInetAddress().getHostAddress();
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String payload = in.readLine();

            if (payload == null || payload.isEmpty()) {
                // Connection opened and dropped with no data -> Layer 4 TCP Anomaly
                dbManager.logThreat(clientIp, 4, "TCP SYN Flood / Connection Drop");
                ui.triggerFlash(4);
                ui.logToConsole("Layer 4 Attack Blocked from " + clientIp);
            } else {
                // Data received -> Check for Layer 7 Exploits
                if (payload.contains("SQLi") || payload.contains("XSS")) {
                    dbManager.logThreat(clientIp, 7, payload);
                    ui.triggerFlash(7);
                    ui.logToConsole("Layer 7 Exploit Blocked: " + payload);
                }
            }
        } catch (IOException e) {
            // Expected during aggressive Layer 4 connection drops
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    public static void main(String[] args) {
        new OmniServer().startListening();
    }
}