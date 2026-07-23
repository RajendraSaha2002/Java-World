import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * Stateful firewall rule-engine simulator.
 *
 * No real sockets, no packet capture, and no network traffic.
 * All Packet objects are simulated Java objects.
 *
 * Features:
 * - IPv4 headers and TCP flags
 * - Connection states: SYN, SYN-ACK, ACK, FIN, RST
 * - ALLOW/DENY rules by CIDR, port range, protocol, direction
 * - Stateful return-traffic handling
 * - Per-source sliding-window rate limiting
 * - Structured audit log
 */
public final class StatefulFirewallSimulator {
    public static void main(String[] args) {
        Firewall firewall = new Firewall(
                3,      // maximum packets per source
                1000L   // per 1,000 ms sliding window
        );

        firewall.addRule(new FirewallRule(
                "Allow outbound HTTPS",
                Action.ALLOW,
                Direction.OUTBOUND,
                Protocol.TCP,
                "10.0.0.0/24",
                "0.0.0.0/0",
                1, 65535,
                443, 443
        ));

        firewall.addRule(new FirewallRule(
                "Allow inbound SSH from admin network",
                Action.ALLOW,
                Direction.INBOUND,
                Protocol.TCP,
                "192.168.50.0/24",
                "10.0.0.10/32",
                1, 65535,
                22, 22
        ));

        firewall.addRule(new FirewallRule(
                "Deny all other traffic",
                Action.DENY,
                Direction.ANY,
                Protocol.ANY,
                "0.0.0.0/0",
                "0.0.0.0/0",
                1, 65535,
                1, 65535
        ));

        long time = 1_000_000L;

        // Simulated HTTPS TCP handshake.
        process(firewall, new Packet(
                "10.0.0.10", "93.184.216.34",
                50000, 443, Protocol.TCP, Direction.OUTBOUND,
                true, false, false, false, false, time += 50));

        process(firewall, new Packet(
                "93.184.216.34", "10.0.0.10",
                443, 50000, Protocol.TCP, Direction.INBOUND,
                true, true, false, false, false, time += 50));

        process(firewall, new Packet(
                "10.0.0.10", "93.184.216.34",
                50000, 443, Protocol.TCP, Direction.OUTBOUND,
                false, true, false, false, false, time += 50));

        process(firewall, new Packet(
                "93.184.216.34", "10.0.0.10",
                443, 50000, Protocol.TCP, Direction.INBOUND,
                false, true, false, false, false, time += 50));

        // Denied because port 23 is not allowed.
        process(firewall, new Packet(
                "10.0.0.10", "203.0.113.99",
                50100, 23, Protocol.TCP, Direction.OUTBOUND,
                true, false, false, false, false, time += 50));

        // Simulated allowed inbound SSH from the administration network.
        process(firewall, new Packet(
                "192.168.50.20", "10.0.0.10",
                51000, 22, Protocol.TCP, Direction.INBOUND,
                true, false, false, false, false, time += 2_000));

        // Rate-limit demonstration: fourth packet within one second is denied.
        System.out.println("\nRate-limit demonstration:");

        for (int i = 0; i < 4; i++) {
            process(firewall, new Packet(
                    "192.168.50.30", "10.0.0.10",
                    52000 + i, 22, Protocol.TCP, Direction.INBOUND,
                    true, false, false, false, false, time += 100));
        }

        System.out.println("\nStructured audit log:");
        firewall.printAuditLog();
    }

    private static void process(Firewall firewall, Packet packet) {
        Decision decision = firewall.process(packet);

        System.out.println(packet.summary());
        System.out.println("  Decision: " + decision.allowed
                + " | reason=" + decision.reason
                + " | state=" + decision.connectionState);
    }

    // ================================================================
    // Firewall engine
    // ================================================================

    static final class Firewall {
        private final List<FirewallRule> rules = new ArrayList<FirewallRule>();
        private final Map<String, Connection> connections =
                new HashMap<String, Connection>();
        private final Map<String, SlidingWindowCounter> rateCounters =
                new HashMap<String, SlidingWindowCounter>();
        private final List<AuditEvent> auditLog = new ArrayList<AuditEvent>();

        private final int rateLimit;
        private final long rateWindowMillis;

        Firewall(int rateLimit, long rateWindowMillis) {
            this.rateLimit = rateLimit;
            this.rateWindowMillis = rateWindowMillis;
        }

        void addRule(FirewallRule rule) {
            rules.add(rule);
        }

        Decision process(Packet packet) {
            if (!allowByRateLimit(packet)) {
                return deny(packet, "RATE_LIMIT_EXCEEDED", null);
            }

            String directKey = connectionKey(
                    packet.sourceIp, packet.sourcePort,
                    packet.destinationIp, packet.destinationPort,
                    packet.protocol);

            String reverseKey = connectionKey(
                    packet.destinationIp, packet.destinationPort,
                    packet.sourceIp, packet.sourcePort,
                    packet.protocol);

            Connection existing = connections.get(directKey);

            if (existing == null) {
                existing = connections.get(reverseKey);
            }

            if (existing != null && packet.protocol == Protocol.TCP) {
                return processExistingTcpConnection(packet, existing);
            }

            FirewallRule matchedRule = findFirstMatchingRule(packet);

            if (matchedRule == null) {
                return deny(packet, "NO_MATCHING_RULE", null);
            }

            if (matchedRule.action == Action.DENY) {
                return deny(packet, "RULE_DENY: " + matchedRule.name, null);
            }

            if (packet.protocol == Protocol.TCP) {
                if (!packet.syn || packet.ack) {
                    return deny(packet, "NEW_TCP_CONNECTION_REQUIRES_SYN", null);
                }

                Connection connection = new Connection(
                        packet.sourceIp, packet.sourcePort,
                        packet.destinationIp, packet.destinationPort,
                        ConnectionState.SYN_SENT
                );

                connections.put(directKey, connection);

                return allow(packet,
                        "RULE_ALLOW: " + matchedRule.name,
                        connection.state);
            }

            return allow(packet, "RULE_ALLOW: " + matchedRule.name, null);
        }

        private Decision processExistingTcpConnection(
                Packet packet, Connection connection) {

            if (packet.rst) {
                removeConnection(connection);
                return allow(packet, "TCP_RESET", ConnectionState.CLOSED);
            }

            boolean forward = packet.sourceIp.equals(connection.clientIp)
                    && packet.sourcePort == connection.clientPort
                    && packet.destinationIp.equals(connection.serverIp)
                    && packet.destinationPort == connection.serverPort;

            boolean reverse = packet.sourceIp.equals(connection.serverIp)
                    && packet.sourcePort == connection.serverPort
                    && packet.destinationIp.equals(connection.clientIp)
                    && packet.destinationPort == connection.clientPort;

            if (!forward && !reverse) {
                return deny(packet, "CONNECTION_DIRECTION_MISMATCH", connection.state);
            }

            if (connection.state == ConnectionState.SYN_SENT) {
                if (reverse && packet.syn && packet.ack) {
                    connection.state = ConnectionState.SYN_RECEIVED;
                    return allow(packet, "STATEFUL_SYN_ACK_ALLOWED", connection.state);
                }

                return deny(packet, "EXPECTED_SYN_ACK", connection.state);
            }

            if (connection.state == ConnectionState.SYN_RECEIVED) {
                if (forward && packet.ack && !packet.syn) {
                    connection.state = ConnectionState.ESTABLISHED;
                    return allow(packet, "STATEFUL_HANDSHAKE_COMPLETE", connection.state);
                }

                return deny(packet, "EXPECTED_FINAL_ACK", connection.state);
            }

            if (connection.state == ConnectionState.ESTABLISHED) {
                if (packet.fin) {
                    connection.state = ConnectionState.FIN_WAIT;
                    return allow(packet, "STATEFUL_FIN_ALLOWED", connection.state);
                }

                return allow(packet, "STATEFUL_ESTABLISHED_CONNECTION", connection.state);
            }

            if (connection.state == ConnectionState.FIN_WAIT) {
                if (packet.fin || packet.ack) {
                    connection.state = ConnectionState.CLOSED;
                    removeConnection(connection);
                    return allow(packet, "STATEFUL_CONNECTION_CLOSED", connection.state);
                }

                return deny(packet, "CONNECTION_CLOSING", connection.state);
            }

            return deny(packet, "CONNECTION_ALREADY_CLOSED", connection.state);
        }

        private boolean allowByRateLimit(Packet packet) {
            SlidingWindowCounter counter = rateCounters.get(packet.sourceIp);

            if (counter == null) {
                counter = new SlidingWindowCounter(rateLimit, rateWindowMillis);
                rateCounters.put(packet.sourceIp, counter);
            }

            return counter.allow(packet.timestampMillis);
        }

        private FirewallRule findFirstMatchingRule(Packet packet) {
            for (int i = 0; i < rules.size(); i++) {
                FirewallRule rule = rules.get(i);

                if (rule.matches(packet)) {
                    return rule;
                }
            }

            return null;
        }

        private Decision allow(Packet packet, String reason,
                               ConnectionState state) {
            auditLog.add(new AuditEvent(packet, true, reason, state));
            return new Decision(true, reason, state);
        }

        private Decision deny(Packet packet, String reason,
                              ConnectionState state) {
            auditLog.add(new AuditEvent(packet, false, reason, state));
            return new Decision(false, reason, state);
        }

        private void removeConnection(Connection connection) {
            connections.remove(connectionKey(
                    connection.clientIp, connection.clientPort,
                    connection.serverIp, connection.serverPort,
                    Protocol.TCP));
        }

        void printAuditLog() {
            System.out.println("+------+-----------+---------------------+"
                    + "---------------------+--------+-------------------------------+");

            System.out.println("| Time | Direction | Source              | Destination         "
                    + "| Result | Reason                        |");

            System.out.println("+------+-----------+---------------------+"
                    + "---------------------+--------+-------------------------------+");

            for (int i = 0; i < auditLog.size(); i++) {
                AuditEvent event = auditLog.get(i);

                String source = event.packet.sourceIp + ":" + event.packet.sourcePort;
                String destination = event.packet.destinationIp + ":"
                        + event.packet.destinationPort;

                System.out.printf(
                        "| %-4d | %-9s | %-19s | %-19s | %-6s | %-29s |%n",
                        event.packet.timestampMillis,
                        event.packet.direction,
                        limit(source, 19),
                        limit(destination, 19),
                        event.allowed ? "ALLOW" : "DENY",
                        limit(event.reason, 29)
                );
            }

            System.out.println("+------+-----------+---------------------+"
                    + "---------------------+--------+-------------------------------+");
        }
    }

    // ================================================================
    // Rules
    // ================================================================

    static final class FirewallRule {
        final String name;
        final Action action;
        final Direction direction;
        final Protocol protocol;
        final Cidr sourceRange;
        final Cidr destinationRange;
        final int sourcePortStart;
        final int sourcePortEnd;
        final int destinationPortStart;
        final int destinationPortEnd;

        FirewallRule(
                String name,
                Action action,
                Direction direction,
                Protocol protocol,
                String sourceRange,
                String destinationRange,
                int sourcePortStart,
                int sourcePortEnd,
                int destinationPortStart,
                int destinationPortEnd) {

            this.name = name;
            this.action = action;
            this.direction = direction;
            this.protocol = protocol;
            this.sourceRange = new Cidr(sourceRange);
            this.destinationRange = new Cidr(destinationRange);
            this.sourcePortStart = sourcePortStart;
            this.sourcePortEnd = sourcePortEnd;
            this.destinationPortStart = destinationPortStart;
            this.destinationPortEnd = destinationPortEnd;
        }

        boolean matches(Packet packet) {
            boolean directionMatches = direction == Direction.ANY
                    || direction == packet.direction;

            boolean protocolMatches = protocol == Protocol.ANY
                    || protocol == packet.protocol;

            return directionMatches
                    && protocolMatches
                    && sourceRange.contains(packet.sourceIp)
                    && destinationRange.contains(packet.destinationIp)
                    && packet.sourcePort >= sourcePortStart
                    && packet.sourcePort <= sourcePortEnd
                    && packet.destinationPort >= destinationPortStart
                    && packet.destinationPort <= destinationPortEnd;
        }
    }

    // ================================================================
    // Packet, connection, rate limiting, and audit records
    // ================================================================

    static final class Packet {
        final String sourceIp;
        final String destinationIp;
        final int sourcePort;
        final int destinationPort;
        final Protocol protocol;
        final Direction direction;
        final boolean syn;
        final boolean ack;
        final boolean fin;
        final boolean rst;
        final boolean psh;
        final long timestampMillis;

        Packet(
                String sourceIp, String destinationIp,
                int sourcePort, int destinationPort,
                Protocol protocol, Direction direction,
                boolean syn, boolean ack, boolean fin,
                boolean rst, boolean psh, long timestampMillis) {

            this.sourceIp = sourceIp;
            this.destinationIp = destinationIp;
            this.sourcePort = sourcePort;
            this.destinationPort = destinationPort;
            this.protocol = protocol;
            this.direction = direction;
            this.syn = syn;
            this.ack = ack;
            this.fin = fin;
            this.rst = rst;
            this.psh = psh;
            this.timestampMillis = timestampMillis;
        }

        String summary() {
            return "[" + direction + "] "
                    + sourceIp + ":" + sourcePort
                    + " -> "
                    + destinationIp + ":" + destinationPort
                    + " " + protocol
                    + " flags=" + flags();
        }

        String flags() {
            StringBuilder flags = new StringBuilder();

            if (syn) flags.append("SYN ");
            if (ack) flags.append("ACK ");
            if (fin) flags.append("FIN ");
            if (rst) flags.append("RST ");
            if (psh) flags.append("PSH ");

            return flags.length() == 0 ? "-" : flags.toString().trim();
        }
    }

    static final class Connection {
        final String clientIp;
        final int clientPort;
        final String serverIp;
        final int serverPort;
        ConnectionState state;

        Connection(
                String clientIp, int clientPort,
                String serverIp, int serverPort,
                ConnectionState state) {

            this.clientIp = clientIp;
            this.clientPort = clientPort;
            this.serverIp = serverIp;
            this.serverPort = serverPort;
            this.state = state;
        }
    }

    static final class SlidingWindowCounter {
        private final int maximumEvents;
        private final long windowMillis;
        private final ArrayDeque<Long> timestamps = new ArrayDeque<Long>();

        SlidingWindowCounter(int maximumEvents, long windowMillis) {
            this.maximumEvents = maximumEvents;
            this.windowMillis = windowMillis;
        }

        boolean allow(long now) {
            while (!timestamps.isEmpty()
                    && now - timestamps.peekFirst() >= windowMillis) {
                timestamps.removeFirst();
            }

            if (timestamps.size() >= maximumEvents) {
                return false;
            }

            timestamps.addLast(now);
            return true;
        }
    }

    static final class AuditEvent {
        final Packet packet;
        final boolean allowed;
        final String reason;
        final ConnectionState connectionState;

        AuditEvent(Packet packet, boolean allowed, String reason,
                   ConnectionState connectionState) {
            this.packet = packet;
            this.allowed = allowed;
            this.reason = reason;
            this.connectionState = connectionState;
        }
    }

    static final class Decision {
        final boolean allowed;
        final String reason;
        final ConnectionState connectionState;

        Decision(boolean allowed, String reason,
                 ConnectionState connectionState) {
            this.allowed = allowed;
            this.reason = reason;
            this.connectionState = connectionState;
        }
    }

    // ================================================================
    // IPv4 CIDR matching
    // ================================================================

    static final class Cidr {
        final long network;
        final long mask;

        Cidr(String notation) {
            String[] parts = notation.split("/");

            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid CIDR: " + notation);
            }

            int prefixLength;

            try {
                prefixLength = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid CIDR prefix: " + notation);
            }

            if (prefixLength < 0 || prefixLength > 32) {
                throw new IllegalArgumentException("CIDR prefix must be 0 to 32");
            }

            mask = prefixLength == 0
                    ? 0L
                    : (0xFFFFFFFFL << (32 - prefixLength)) & 0xFFFFFFFFL;

            network = ipv4ToLong(parts[0]) & mask;
        }

        boolean contains(String ipAddress) {
            return (ipv4ToLong(ipAddress) & mask) == network;
        }

        private static long ipv4ToLong(String ipAddress) {
            String[] parts = ipAddress.split("\\.");

            if (parts.length != 4) {
                throw new IllegalArgumentException("Invalid IPv4 address: " + ipAddress);
            }

            long value = 0;

            for (int i = 0; i < 4; i++) {
                int octet;

                try {
                    octet = Integer.parseInt(parts[i]);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "Invalid IPv4 address: " + ipAddress);
                }

                if (octet < 0 || octet > 255) {
                    throw new IllegalArgumentException(
                            "Invalid IPv4 address: " + ipAddress);
                }

                value = (value << 8) | octet;
            }

            return value;
        }
    }

    static String connectionKey(
            String sourceIp, int sourcePort,
            String destinationIp, int destinationPort,
            Protocol protocol) {

        return sourceIp + ":" + sourcePort
                + ">" + destinationIp + ":" + destinationPort
                + "/" + protocol;
    }

    static String limit(String text, int maximumLength) {
        return text.length() <= maximumLength
                ? text
                : text.substring(0, maximumLength - 3) + "...";
    }

    enum Action {
        ALLOW, DENY
    }

    enum Direction {
        INBOUND, OUTBOUND, ANY
    }

    enum Protocol {
        TCP, UDP, ANY
    }

    enum ConnectionState {
        SYN_SENT, SYN_RECEIVED, ESTABLISHED, FIN_WAIT, CLOSED
    }
}