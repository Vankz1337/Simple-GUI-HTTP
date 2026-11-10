package com.vankz.simpleguihttp;

import com.sun.net.httpserver.*;
import javax.net.ssl.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.*;

public final class SimpleHttpGui extends JFrame {
    private final JTextField hostField = new JTextField("localhost", 18);
    private final JTextField portField = new JTextField("80", 8);
    private final JCheckBox httpsCheck = new JCheckBox("HTTPS");
    private final JTextField certField = new JTextField("cert.pem", 24);
    private final JTextField keyField = new JTextField("key.pem", 24);
    private final JButton startButton = new JButton("Start");
    private final JButton stopButton = new JButton("Stop");
    private final JButton exitButton = new JButton("Exit");
    private final JTextArea output = new JTextArea(14, 64);
    private final JLabel statusLabel = new JLabel("● Stopped");
    private volatile HttpServer server;
    private volatile ExecutorService requestExecutor;
    private volatile boolean closing;

    public SimpleHttpGui() {
        super("Simple HTTP Server With GUI");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        JPanel settings = new JPanel(new GridBagLayout());
        settings.setBorder(BorderFactory.createTitledBorder("Server settings"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 6, 5, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        addRow(settings, c, 0, "Host:", hostField, null);
        addRow(settings, c, 1, "Port:", portField, httpsCheck);
        addRow(settings, c, 2, "Certificate:", certField, browseButton(certField, "Certificate files", "pem", "crt"));
        addRow(settings, c, 3, "Private key:", keyField, browseButton(keyField, "Private key files", "pem", "key"));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.add(startButton); actions.add(stopButton); actions.add(exitButton); actions.add(statusLabel);
        output.setEditable(false);
        output.setLineWrap(true);
        output.setWrapStyleWord(true);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(output);
        logScroll.setBorder(BorderFactory.createTitledBorder("Output"));
        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.add(settings, BorderLayout.CENTER); top.add(actions, BorderLayout.SOUTH);
        top.setBorder(new EmptyBorder(10, 10, 0, 10));
        add(top, BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout());
        center.setBorder(new EmptyBorder(0, 10, 10, 10));
        center.add(logScroll, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        stopButton.setEnabled(false);
        setHttpsFieldsEnabled(false);
        httpsCheck.addActionListener(e -> setHttpsFieldsEnabled(httpsCheck.isSelected()));
        startButton.addActionListener(e -> startServer());
        stopButton.addActionListener(e -> stopServer());
        exitButton.addActionListener(e -> exitApplication());
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { exitApplication(); }
        });
        pack(); setMinimumSize(getSize()); setLocationRelativeTo(null);
        appendLog("Ready.");
    }

    private static void addRow(JPanel panel, GridBagConstraints c, int row, String label, JTextField field, Component trailing) {
        c.gridy = row; c.gridx = 0; c.weightx = 0; panel.add(new JLabel(label), c);
        c.gridx = 1; c.weightx = 1; panel.add(field, c);
        if (trailing != null) { c.gridx = 2; c.weightx = 0; panel.add(trailing, c); }
    }

    private JButton browseButton(JTextField target, String title, String... extensions) {
        JButton button = new JButton("Browse...");
        button.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(title);
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(title, extensions));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
                target.setText(chooser.getSelectedFile().getAbsolutePath());
        });
        return button;
    }

    private void setHttpsFieldsEnabled(boolean enabled) {
        certField.setEnabled(enabled); keyField.setEnabled(enabled);
    }

    private void startServer() {
        if (server != null) return;
        String host = hostField.getText().trim();
        boolean useHttps = httpsCheck.isSelected();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65535 || host.isBlank()) throw new IllegalArgumentException();
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "Enter a valid host and port (1-65535).", "Invalid settings", JOptionPane.ERROR_MESSAGE);
            return;
        }
        setRunningUi(true);
        appendLog("Starting " + (useHttps ? "HTTPS" : "HTTP") + " server on " + host + ":" + port + "...");
        Thread starter = new Thread(() -> {
            HttpServer created = null;
            ExecutorService pool = null;
            try {
                InetSocketAddress address = new InetSocketAddress(InetAddress.getByName(host), port);
                if (useHttps) {
                    SSLContext sslContext = createSslContext(Path.of(certField.getText().trim()), Path.of(keyField.getText().trim()));
                    HttpsServer secure = HttpsServer.create(address, 0);
                    secure.setHttpsConfigurator(new HttpsConfigurator(sslContext));
                    created = secure;
                } else created = HttpServer.create(address, 0);
                pool = Executors.newCachedThreadPool(new NamedThreadFactory());
                created.setExecutor(pool);
                created.createContext("/", new HelloHandler());
                synchronized (this) {
                    if (closing) { created.stop(0); pool.shutdownNow(); return; }
                    server = created; requestExecutor = pool;
                }
                created.start();
                appendLog("Server started: " + (useHttps ? "https" : "http") + "://" + host + ":" + port + "/");
                appendLog("Waiting for connections...");
            } catch (Exception ex) {
                if (created != null) created.stop(0);
                if (pool != null) pool.shutdownNow();
                appendLog("ERROR: " + usefulMessage(ex));
                SwingUtilities.invokeLater(() -> setRunningUi(false));
            }
        }, "http-server-starter");
        starter.setDaemon(true); starter.start();
    }

    private SSLContext createSslContext(Path certPath, Path keyPath) throws Exception {
        if (!Files.isRegularFile(certPath)) throw new IOException("Certificate file not found: " + certPath);
        if (!Files.isRegularFile(keyPath)) throw new IOException("Private key file not found: " + keyPath);
        X509Certificate certificate;
        try (InputStream in = Files.newInputStream(certPath)) {
            certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
        PrivateKey privateKey = readPrivateKey(Files.readString(keyPath, StandardCharsets.US_ASCII));
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        char[] password = "simple-http-gui".toCharArray();
        store.setKeyEntry("server", privateKey, password, new Certificate[]{certificate});
        KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        managers.init(store, password);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(managers.getKeyManagers(), null, null);
        return context;
    }

    private PrivateKey readPrivateKey(String pem) throws Exception {
        Matcher matcher = Pattern.compile("-----BEGIN ([A-Z ]*PRIVATE KEY)-----(.*?)-----END \\1-----", Pattern.DOTALL).matcher(pem);
        if (!matcher.find()) throw new IOException("Unsupported private key PEM format.");
        String type = matcher.group(1);
        byte[] der = Base64.getMimeDecoder().decode(matcher.group(2));
        if (type.equals("RSA PRIVATE KEY")) der = wrapRsaPkcs1(der);
        else if (!type.equals("PRIVATE KEY")) throw new IOException("Use an unencrypted RSA PRIVATE KEY or PKCS8 PRIVATE KEY.");
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private byte[] wrapRsaPkcs1(byte[] pkcs1) throws IOException {
        byte[] algorithm = new byte[]{0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00};
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(new byte[]{0x02, 0x01, 0x00}); body.write(algorithm); body.write(0x04);
        writeDerLength(body, pkcs1.length); body.write(pkcs1);
        byte[] content = body.toByteArray();
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(0x30); writeDerLength(result, content.length); result.write(content);
        return result.toByteArray();
    }

    private void writeDerLength(ByteArrayOutputStream out, int length) throws IOException {
        if (length < 128) { out.write(length); return; }
        int bytes = 0, value = length;
        while (value > 0) { bytes++; value >>>= 8; }
        out.write(0x80 | bytes);
        for (int i = bytes - 1; i >= 0; i--) out.write((length >>> (8 * i)) & 0xff);
    }

    private void stopServer() {
        HttpServer active = server;
        ExecutorService pool = requestExecutor;
        if (active == null) return;
        server = null; requestExecutor = null;
        Thread stopper = new Thread(() -> {
            try { active.stop(0); } finally { if (pool != null) pool.shutdownNow(); }
            appendLog("Server stopped.");
            SwingUtilities.invokeLater(() -> setRunningUi(false));
        }, "http-server-stopper");
        stopper.setDaemon(true); stopper.start();
    }

    private void exitApplication() {
        closing = true;
        HttpServer active = server; ExecutorService pool = requestExecutor;
        server = null; requestExecutor = null;
        if (active != null) active.stop(0);
        if (pool != null) pool.shutdownNow();
        dispose();
    }

    private void setRunningUi(boolean running) {
        hostField.setEnabled(!running); portField.setEnabled(!running); httpsCheck.setEnabled(!running);
        setHttpsFieldsEnabled(!running && httpsCheck.isSelected());
        startButton.setEnabled(!running); stopButton.setEnabled(running); exitButton.setEnabled(true);
        statusLabel.setText(running ? "● Running" : "● Stopped");
    }

    private void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            output.append("[" + java.time.LocalTime.now().withNano(0) + "] " + message + System.lineSeparator());
            output.setCaretPosition(output.getDocument().getLength());
        });
    }

    private static String usefulMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private final class HelloHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            String client = exchange.getRemoteAddress().toString();
            appendLog("Connected by " + client + " | " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
            byte[] body = "Hello World".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.getResponseHeaders().set("Connection", "close");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream response = exchange.getResponseBody()) { response.write(body); }
            appendLog("Response 200 OK -> " + client);
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger count = new AtomicInteger();
        @Override public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "http-request-" + count.incrementAndGet());
            thread.setDaemon(true); return thread;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) { }
            new SimpleHttpGui().setVisible(true);
        });
    }
}
