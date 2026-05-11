public class IntrusionEngine {
    private TitanDB db;
    private TitanDashboard gui;

    public IntrusionEngine(TitanDashboard gui, TitanDB db) {
        this.gui = gui;
        this.db = db;
    }

    public void analyzeTelemetry(String nodeId, double cpuLoad, double temp, String procId) {
        boolean authorized = db.isJobAuthorized(nodeId);

        // Rules Engine
        int state = 0; // 0 = Idle, 1 = Authorized Running, 2 = Hacked/Alarm

        if (cpuLoad > 80.0 && !authorized) {
            state = 2; // UNAUTHORIZED LOAD (Cryptojacking)
            gui.log("[ALARM] RESOURCE HIJACK ON " + nodeId + " | Process: " + procId + " | Load: " + cpuLoad + "%");
            db.logIntrusion(nodeId, "UNAUTHORIZED_CRYPTO_MINING", cpuLoad, temp);
        } else if (cpuLoad > 10.0 && authorized) {
            state = 1; // Authorized
        }

        if (temp > 85.0) {
            state = 2;
            gui.log("[CRITICAL] THERMAL RUNAWAY ON " + nodeId + "! Hardware damage imminent!");
            db.logIntrusion(nodeId, "THERMAL_SIDE_CHANNEL_ATTACK", cpuLoad, temp);
        }

        gui.updateNode(nodeId, state, temp, procId);
    }
}