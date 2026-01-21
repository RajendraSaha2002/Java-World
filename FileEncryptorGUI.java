import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.KeySpec;
import java.security.spec.InvalidKeySpecException;
import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * FileEncryptorGUI.java
 *
 * Swing GUI wrapper for AES-GCM file encryption using PBKDF2 key derivation.
 * Compatible with the CLI header format described in FileEncryptor.
 *
 * Header format (binary):
 * [4 bytes magic] 'FENC'
 * [1 byte version] (0x01)
 * [4 bytes iterations - int big-endian]
 * [1 byte salt length N] [N bytes salt]
 * [1 byte iv length M] [M bytes iv]
 * [ciphertext...]
 *
 * Features:
 * - Select input & output file
 * - Choose Encrypt / Decrypt
 * - Secure password input (masked)
 * - PBKDF2 iterations spinner
 * - ProgressBar and cancel capability (uses SwingWorker)
 *
 * How to run:
 * javac FileEncryptorGUI.java
 * java FileEncryptorGUI
 *
 * Security notes: Uses PBKDF2WithHmacSHA256 and AES/GCM/NoPadding.
 */
public class FileEncryptorGUI extends JFrame {
    private static final byte[] MAGIC = new byte[] {0x46, 0x45, 0x4E, 0x43}; // "FENC"
    private static final byte VERSION = 0x01;
    private static final int DEFAULT_ITERS = 200_000;
    private static final int SALT_LEN = 16;
    private static final int IV_LEN = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int BUFFER = 16 * 1024;

    // UI components
    private final JRadioButton rbEncrypt = new JRadioButton("Encrypt", true);
    private final JRadioButton rbDecrypt = new JRadioButton("Decrypt");
    private final JTextField tfIn = new JTextField(40);
    private final JTextField tfOut = new JTextField(40);
    private final JButton btnIn = new JButton("Browse…");
    private final JButton btnOut = new JButton("Browse…");
    private final JPasswordField pfPassword = new JPasswordField(30);
    private final JSpinner spIters = new JSpinner(new SpinnerNumberModel(DEFAULT_ITERS, 1000, 2_000_000, 1000));
    private final JCheckBox cbOverwrite = new JCheckBox("Overwrite output if exists", false);
    private final JButton btnStart = new JButton("Start");
    private final JButton btnCancel = new JButton("Cancel");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel lblStatus = new JLabel("Idle");

    private SwingWorker<Void, Void> worker = null;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FileEncryptorGUI win = new FileEncryptorGUI();
            win.setVisible(true);
        });
    }

    public FileEncryptorGUI() {
        super("File Encryptor — AES-GCM (PBKDF2)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        initLayout();
        pack();
        setLocationRelativeTo(null);
        setResizable(false);

        // actions
        btnIn.addActionListener(e -> chooseFile(tfIn, false));
        btnOut.addActionListener(e -> chooseFile(tfOut, false));
        btnStart.addActionListener(e -> onStart());
        btnCancel.addActionListener(e -> onCancel());
    }

    private void initLayout() {
        JPanel root = new JPanel(new BorderLayout(8,8));
        root.setBorder(new EmptyBorder(12,12,12,12));
        setContentPane(root);

        // Mode
        JPanel modeP = new JPanel(new FlowLayout(FlowLayout.LEFT));
        ButtonGroup bg = new ButtonGroup();
        bg.add(rbEncrypt); bg.add(rbDecrypt);
        modeP.add(rbEncrypt); modeP.add(rbDecrypt);
        root.add(modeP, BorderLayout.NORTH);

        // center grid
        JPanel center = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6,6,6,6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0; c.gridy = 0; c.weightx = 0.0;
        center.add(new JLabel("Input file:"), c);
        c.gridx = 1; c.gridy = 0; c.weightx = 1.0;
        center.add(tfIn, c);
        c.gridx = 2; c.gridy = 0; c.weightx = 0.0;
        center.add(btnIn, c);

        c.gridx = 0; c.gridy = 1; c.weightx = 0.0;
        center.add(new JLabel("Output file:"), c);
        c.gridx = 1; c.gridy = 1; c.weightx = 1.0;
        center.add(tfOut, c);
        c.gridx = 2; c.gridy = 1; c.weightx = 0.0;
        center.add(btnOut, c);

        c.gridx = 0; c.gridy = 2; c.weightx = 0.0;
        center.add(new JLabel("Password:"), c);
        c.gridx = 1; c.gridy = 2; c.weightx = 1.0;
        center.add(pfPassword, c);
        c.gridx = 2; c.gridy = 2; c.weightx = 0.0;
        JCheckBox show = new JCheckBox("Show");
        center.add(show, c);
        show.addActionListener(e -> pfPassword.setEchoChar(show.isSelected() ? (char)0 : '\u2022'));

        c.gridx = 0; c.gridy = 3; c.weightx = 0.0;
        center.add(new JLabel("PBKDF2 iterations:"), c);
        c.gridx = 1; c.gridy = 3; c.weightx = 1.0;
        center.add(spIters, c);
        c.gridx = 2; c.gridy = 3; c.weightx = 0.0;
        center.add(cbOverwrite, c);

        root.add(center, BorderLayout.CENTER);

        // bottom controls
        JPanel bottom = new JPanel(new GridBagLayout());
        GridBagConstraints b = new GridBagConstraints();
        b.insets = new Insets(6,6,6,6);
        b.fill = GridBagConstraints.HORIZONTAL;
        b.gridx = 0; b.gridy = 0; b.weightx = 1.0;
        bottom.add(progressBar, b);
        b.gridx = 1; b.gridy = 0; b.weightx = 0.0;
        bottom.add(lblStatus, b);

        b.gridx = 0; b.gridy = 1; b.weightx = 0.0;
        bottom.add(btnStart, b);
        b.gridx = 1; b.gridy = 1; b.weightx = 0.0;
        bottom.add(btnCancel, b);

        root.add(bottom, BorderLayout.SOUTH);

        progressBar.setStringPainted(true);
        btnCancel.setEnabled(false);
    }

    private void chooseFile(JTextField target, boolean directoriesOnly) {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(directoriesOnly ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
        int res = fc.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            target.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void onStart() {
        String inS = tfIn.getText().trim();
        String outS = tfOut.getText().trim();
        if (inS.isEmpty() || outS.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Choose input and output files first.");
            return;
        }
        Path in = Paths.get(inS);
        Path out = Paths.get(outS);
        if (!Files.exists(in)) {
            JOptionPane.showMessageDialog(this, "Input file not found: " + in);
            return;
        }
        if (Files.exists(out) && !cbOverwrite.isSelected()) {
            int r = JOptionPane.showConfirmDialog(this, "Output file exists. Overwrite?", "Confirm overwrite", JOptionPane.YES_NO_OPTION);
            if (r != JOptionPane.YES_OPTION) return;
        }

        char[] password = pfPassword.getPassword();
        if (password == null || password.length == 0) {
            JOptionPane.showMessageDialog(this, "Enter password.");
            return;
        }

        int iters = (int) spIters.getValue();
        boolean doEncrypt = rbEncrypt.isSelected();

        // disable UI
        setUiEnabled(false);
        lblStatus.setText(doEncrypt ? "Encrypting..." : "Decrypting...");
        progressBar.setValue(0);
        progressBar.setIndeterminate(true);
        btnCancel.setEnabled(true);

        worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    if (doEncrypt) encryptFile(in, out, password, iters);
                    else decryptFile(in, out, password);
                } catch (Exception ex) {
                    throw ex;
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get(); // rethrow exceptions if any
                    lblStatus.setText("Completed at " + LocalDateTime.now());
                    JOptionPane.showMessageDialog(FileEncryptorGUI.this, "Operation completed successfully.");
                } catch (Exception ex) {
                    lblStatus.setText("Error");
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    JOptionPane.showMessageDialog(FileEncryptorGUI.this, "Operation failed: " + msg, "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    setUiEnabled(true);
                    btnCancel.setEnabled(false);
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(0);
                    // clear password
                    Arrays.fill(password, '\0');
                    pfPassword.setText("");
                }
            }
        };
        worker.execute();
    }

    private void onCancel() {
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
            lblStatus.setText("Cancelling...");
        }
    }

    private void setUiEnabled(boolean enabled) {
        rbEncrypt.setEnabled(enabled);
        rbDecrypt.setEnabled(enabled);
        tfIn.setEnabled(enabled);
        tfOut.setEnabled(enabled);
        btnIn.setEnabled(enabled);
        btnOut.setEnabled(enabled);
        pfPassword.setEnabled(enabled);
        spIters.setEnabled(enabled);
        cbOverwrite.setEnabled(enabled);
        btnStart.setEnabled(enabled);
    }

    // ----------------- Crypto operations (same header format as CLI) -----------------

    private SecretKey deriveKey(char[] password, byte[] salt, int iterations, int keyLenBits) throws NoSuchAlgorithmException, InvalidKeySpecException {
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password, salt, iterations, keyLenBits);
        byte[] keyBytes = skf.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    private int bestAvailableKeyLength() {
        try {
            int max = Cipher.getMaxAllowedKeyLength("AES");
            return Math.min(max, 256);
        } catch (NoSuchAlgorithmException e) {
            return 128;
        }
    }

    private void encryptFile(Path in, Path out, char[] password, int iterations) throws Exception {
        if (isCancelled()) throw new InterruptedException("Cancelled");
        long total = Files.size(in);

        SecureRandom rnd = SecureRandom.getInstanceStrong();
        byte[] salt = new byte[SALT_LEN]; rnd.nextBytes(salt);
        byte[] iv = new byte[IV_LEN]; rnd.nextBytes(iv);

        int keyLen = bestAvailableKeyLength();
        SecretKey key = deriveKey(password, salt, iterations, keyLen);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));

        // write header + ciphertext via CipherOutputStream
        try (OutputStream fout = Files.newOutputStream(out, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
             BufferedOutputStream bout = new BufferedOutputStream(fout);
             CipherOutputStream cout = new CipherOutputStream(bout, cipher);
             InputStream fin = Files.newInputStream(in);
             BufferedInputStream bin = new BufferedInputStream(fin)) {

            writeHeader(bout, iterations, salt, iv);

            byte[] buf = new byte[BUFFER];
            long processed = 0;
            int read;
            long lastUpdate = System.currentTimeMillis();

            while ((read = bin.read(buf)) != -1) {
                if (isCancelled()) throw new InterruptedException("Cancelled");
                cout.write(buf, 0, read);
                processed += read;
                long now = System.currentTimeMillis();
                if (now - lastUpdate > 300) {
                    updateProgress((int)((processed * 100) / Math.max(1, total)));
                    lastUpdate = now;
                }
            }
            cout.flush();
            updateProgress(100);
        }
    }

    private void decryptFile(Path in, Path out, char[] password) throws Exception {
        if (isCancelled()) throw new InterruptedException("Cancelled");
        long total = Files.size(in);

        try (InputStream fin = Files.newInputStream(in);
             BufferedInputStream bin = new BufferedInputStream(fin)) {

            HeaderInfo header = readHeader(bin);
            long headerLen = header.headerLength;
            long ciphertextLen = Math.max(0, total - headerLen);

            int keyLen = bestAvailableKeyLength();
            SecretKey key = deriveKey(password, header.salt, header.iterations, keyLen);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, header.iv));

            try (CipherInputStream cin = new CipherInputStream(bin, cipher);
                 OutputStream fout = Files.newOutputStream(out, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                 BufferedOutputStream bout = new BufferedOutputStream(fout)) {

                byte[] buf = new byte[BUFFER];
                long processed = 0;
                int read;
                long lastUpdate = System.currentTimeMillis();

                while ((read = cin.read(buf)) != -1) {
                    if (isCancelled()) throw new InterruptedException("Cancelled");
                    bout.write(buf, 0, read);
                    processed += read;
                    long now = System.currentTimeMillis();
                    if (now - lastUpdate > 300) {
                        updateProgress((int)((processed * 100) / Math.max(1, ciphertextLen)));
                        lastUpdate = now;
                    }
                }
                bout.flush();
                updateProgress(100);
            }
        }
    }

    // header helpers
    private static class HeaderInfo {
        int iterations;
        byte[] salt;
        byte[] iv;
        long headerLength;
    }

    private void writeHeader(OutputStream out, int iterations, byte[] salt, byte[] iv) throws IOException {
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        header.write(MAGIC);
        header.write(VERSION);
        header.write(ByteBuffer.allocate(4).putInt(iterations).array());
        header.write((byte) salt.length);
        header.write(salt);
        header.write((byte) iv.length);
        header.write(iv);
        byte[] h = header.toByteArray();
        out.write(h);
        out.flush();
    }

    private HeaderInfo readHeader(InputStream in) throws IOException {
        HeaderInfo info = new HeaderInfo();
        byte[] buf4 = new byte[4];
        if (readFully(in, buf4) != 4) throw new IOException("Invalid file header (too short)");
        if (!Arrays.equals(buf4, MAGIC)) throw new IOException("Invalid file format (magic mismatch)");
        int v = in.read();
        if (v != VERSION) throw new IOException("Unsupported file version: " + v);
        byte[] itb = new byte[4];
        if (readFully(in, itb) != 4) throw new IOException("Invalid header (missing iterations)");
        int iters = ByteBuffer.wrap(itb).getInt();
        info.iterations = iters;
        int saltLen = in.read();
        if (saltLen <= 0) throw new IOException("Invalid salt length");
        byte[] salt = new byte[saltLen];
        if (readFully(in, salt) != saltLen) throw new IOException("Incomplete salt");
        info.salt = salt;
        int ivLen = in.read();
        if (ivLen <= 0) throw new IOException("Invalid iv length");
        byte[] iv = new byte[ivLen];
        if (readFully(in, iv) != ivLen) throw new IOException("Incomplete iv");
        info.iv = iv;
        info.headerLength = 4 + 1 + 4 + 1 + saltLen + 1 + ivLen;
        return info;
    }

    private int readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int r = in.read(buf, off, buf.length - off);
            if (r == -1) break;
            off += r;
        }
        return off;
    }

    // SwingWorker helpers
    private void updateProgress(int pct) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setIndeterminate(false);
            progressBar.setValue(Math.max(0, Math.min(100, pct)));
            progressBar.setString(progressBar.getValue() + " %");
        });
    }

    private boolean isCancelled() {
        return worker != null && worker.isCancelled();
    }
}
