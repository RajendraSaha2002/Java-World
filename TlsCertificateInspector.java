import java.net.InetAddress;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/*
 * TLS certificate-chain inspector.
 *
 * Uses the JDK default trust store:
 * - TLS handshake validates the chain.
 * - HTTPS hostname verification is enabled.
 * - Prints subject, issuer, SANs, validity, key details,
 *   key usage, and signature algorithm.
 *
 * No JavaFX or external libraries.
 */
public final class TlsCertificateInspector {
    private TlsCertificateInspector() {
    }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "example.com";
        int port = args.length > 1 ? parsePort(args[1]) : 443;

        inspect(host, port);
    }

    public static void inspect(String host, int port) {
        System.out.println("TLS certificate chain inspector");
        System.out.println("Host: " + host + ":" + port);
        System.out.println();

        try {
            InetAddress address = InetAddress.getByName(host);
            System.out.println("Resolved address: " + address.getHostAddress());

            SSLSocketFactory factory =
                    (SSLSocketFactory) SSLSocketFactory.getDefault();

            SSLSocket socket = (SSLSocket) factory.createSocket(host, port);

            try {
                socket.setSoTimeout(10_000);

                // Enable hostname verification for HTTPS-style connections.
                SSLParameters parameters = socket.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                socket.setSSLParameters(parameters);

                /*
                 * The handshake validates the certificate chain against
                 * the Java default trust store.
                 */
                socket.startHandshake();

                SSLSession session = socket.getSession();

                System.out.println("TLS handshake: VALID");
                System.out.println("Protocol     : " + session.getProtocol());
                System.out.println("Cipher suite : " + session.getCipherSuite());
                System.out.println();

                Certificate[] certificates = session.getPeerCertificates();

                for (int i = 0; i < certificates.length; i++) {
                    if (!(certificates[i] instanceof X509Certificate)) {
                        System.out.println("Certificate #" + (i + 1)
                                + ": not an X.509 certificate");
                        continue;
                    }

                    printCertificate((X509Certificate) certificates[i], i,
                            certificates.length);
                }
            } finally {
                socket.close();
            }
        } catch (javax.net.ssl.SSLHandshakeException e) {
            System.out.println("TLS handshake: FAILED");
            System.out.println("Reason: certificate chain or hostname validation failed.");
            System.out.println("Details: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Connection failed: " + e.getMessage());
        }
    }

    private static void printCertificate(
            X509Certificate certificate, int position, int chainLength) {

        System.out.println("==================================================");
        System.out.println("Certificate #" + (position + 1)
                + " of " + chainLength);

        System.out.println("Subject         : "
                + certificate.getSubjectX500Principal().getName());
        System.out.println("Issuer          : "
                + certificate.getIssuerX500Principal().getName());
        System.out.println("Serial number   : "
                + certificate.getSerialNumber().toString(16));
        System.out.println("Version         : v" + certificate.getVersion());

        System.out.println("Valid from      : " + certificate.getNotBefore());
        System.out.println("Valid until     : " + certificate.getNotAfter());

        printValidityStatus(certificate);

        PublicKey key = certificate.getPublicKey();

        System.out.println("Public key type : " + key.getAlgorithm());
        System.out.println("Public key bits : " + estimateKeyBits(key));
        System.out.println("Signature alg.  : " + certificate.getSigAlgName());
        System.out.println("Signature OID   : " + certificate.getSigAlgOID());

        printKeyUsage(certificate);
        printExtendedKeyUsage(certificate);
        printSubjectAlternativeNames(certificate);

        boolean selfSigned = isSelfSigned(certificate);

        System.out.println("Self-signed     : " + selfSigned);

        if ("RSA".equalsIgnoreCase(key.getAlgorithm())
                && estimateKeyBits(key) > 0
                && estimateKeyBits(key) < 2048) {
            System.out.println("WARNING         : Weak RSA key: below 2048 bits");
        }

        if (isWeakSignatureAlgorithm(certificate.getSigAlgName())) {
            System.out.println("WARNING         : Weak signature algorithm");
        }

        if (selfSigned && position == chainLength - 1) {
            System.out.println("INFO            : Self-signed root certificate");
        }

        System.out.println();
    }

    private static void printValidityStatus(X509Certificate certificate) {
        try {
            certificate.checkValidity(new Date());
            System.out.println("Validity status : Currently valid");
        } catch (java.security.cert.CertificateExpiredException e) {
            System.out.println("Validity status : EXPIRED");
        } catch (java.security.cert.CertificateNotYetValidException e) {
            System.out.println("Validity status : NOT YET VALID");
        }
    }

    private static void printKeyUsage(X509Certificate certificate) {
        boolean[] usage = certificate.getKeyUsage();

        if (usage == null) {
            System.out.println("Key usage       : Not specified");
            return;
        }

        String[] labels = {
                "digitalSignature",
                "nonRepudiation",
                "keyEncipherment",
                "dataEncipherment",
                "keyAgreement",
                "keyCertSign",
                "cRLSign",
                "encipherOnly",
                "decipherOnly"
        };

        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < usage.length && i < labels.length; i++) {
            if (usage[i]) {
                if (builder.length() > 0) {
                    builder.append(", ");
                }

                builder.append(labels[i]);
            }
        }

        System.out.println("Key usage       : "
                + (builder.length() == 0 ? "None" : builder.toString()));
    }

    private static void printExtendedKeyUsage(X509Certificate certificate) {
        try {
            List<String> usages = certificate.getExtendedKeyUsage();

            if (usages == null) {
                System.out.println("Extended usage  : Not specified");
                return;
            }

            System.out.println("Extended usage  : " + usages.toString());
        } catch (Exception e) {
            System.out.println("Extended usage  : Unavailable");
        }
    }

    private static void printSubjectAlternativeNames(X509Certificate certificate) {
        try {
            Collection<List<?>> names = certificate.getSubjectAlternativeNames();

            if (names == null || names.isEmpty()) {
                System.out.println("SAN             : Not specified");
                return;
            }

            StringBuilder builder = new StringBuilder();

            for (List<?> name : names) {
                if (name.size() >= 2) {
                    if (builder.length() > 0) {
                        builder.append(", ");
                    }

                    builder.append(sanTypeName((Integer) name.get(0)));
                    builder.append("=");
                    builder.append(name.get(1));
                }
            }

            System.out.println("SAN             : " + builder.toString());
        } catch (Exception e) {
            System.out.println("SAN             : Unavailable");
        }
    }

    private static String sanTypeName(int type) {
        switch (type) {
            case 0:
                return "otherName";
            case 1:
                return "email";
            case 2:
                return "DNS";
            case 4:
                return "directoryName";
            case 6:
                return "URI";
            case 7:
                return "IP";
            default:
                return "type-" + type;
        }
    }

    private static boolean isSelfSigned(X509Certificate certificate) {
        if (!certificate.getSubjectX500Principal()
                .equals(certificate.getIssuerX500Principal())) {
            return false;
        }

        try {
            certificate.verify(certificate.getPublicKey());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isWeakSignatureAlgorithm(String algorithm) {
        String upper = algorithm.toUpperCase();

        return upper.indexOf("MD2") >= 0
                || upper.indexOf("MD5") >= 0
                || upper.indexOf("SHA1") >= 0;
    }

    private static int estimateKeyBits(PublicKey key) {
        String algorithm = key.getAlgorithm();

        if ("RSA".equalsIgnoreCase(algorithm)) {
            try {
                java.security.interfaces.RSAPublicKey rsaKey =
                        (java.security.interfaces.RSAPublicKey) key;

                return rsaKey.getModulus().bitLength();
            } catch (Exception ignored) {
                return -1;
            }
        }

        if ("EC".equalsIgnoreCase(algorithm)) {
            try {
                java.security.interfaces.ECPublicKey ecKey =
                        (java.security.interfaces.ECPublicKey) key;

                return ecKey.getParams().getCurve().getField().getFieldSize();
            } catch (Exception ignored) {
                return -1;
            }
        }

        return -1;
    }

    private static int parsePort(String text) {
        try {
            int port = Integer.parseInt(text);

            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port must be 1 to 65535");
            }

            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port: " + text);
        }
    }
}