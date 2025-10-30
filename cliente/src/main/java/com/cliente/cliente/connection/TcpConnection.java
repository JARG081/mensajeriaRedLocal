package com.cliente.cliente.connection;

import com.cliente.cliente.config.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class TcpConnection {

    private static final Logger log = LoggerFactory.getLogger(TcpConnection.class);

    private final ServerConfig serverConfig;
    private Socket socket;
    private BufferedReader in;
    // usamos PrintWriter con autoFlush=true para evitar problemas de flush/buffering
    private PrintWriter pw;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    // separate locks for read and write to avoid blocking send when a read is waiting
    private final Object readLock = new Object();
    private final Object writeLock = new Object();

    public TcpConnection(ServerConfig serverConfig) {
        this.serverConfig = serverConfig;
    }

    public synchronized void connect() throws IOException {
        if (connected.get()) {
            log.warn("Intento de reconexión ignorado: ya hay una conexión activa");
            return;
        }
        try {
            log.info("Intentando conectar con servidor {}:{}", serverConfig.getServerIp(), serverConfig.getServerPort());
            socket = new Socket();
            socket.setReuseAddress(true);
            socket.connect(new InetSocketAddress(serverConfig.getServerIp(), serverConfig.getServerPort()), 5000);
            // Establecer timeout de lectura para detectar peers muertos y evitar bloqueos indefinidos
            socket.setSoTimeout(60_000); // 60 segundos
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            // PrintWriter autoFlush garantiza que println() haga flush
            pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            connected.set(true);
            log.info("Conexión establecida correctamente con el servidor {}:{}", serverConfig.getServerIp(), serverConfig.getServerPort());
        } catch (IOException e) {
            try { if (socket != null) socket.close(); } catch (Exception ignored) {}
            connected.set(false);
            log.error("Error al conectar con el servidor: {}", e.toString(), e);
            throw e;
        }
    }

    public synchronized void disconnect() {
        if (!connected.get()) {
            log.debug("disconnect() llamado pero no había conexión");
            return;
        }
        try {
            try {
                // close output side if possible (best-effort)
                synchronized (writeLock) {
                    if (pw != null) {
                        try { pw.println("QUIT"); pw.flush(); } catch (Exception ignored) {}
                        try { socket.shutdownOutput(); } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
            try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        } finally {
            connected.set(false);
            in = null;
            pw = null;
            socket = null;
            log.info("Conexión cerrada y recursos liberados");
        }
    }

    /**
     * Envío garantizado de una línea terminada en '\n' (usando println).
     */
    public void sendRaw(String line) throws IOException {
        if (!isConnected()) {
            log.warn("Intento de envío sin conexión activa");
            throw new IllegalStateException("No conectado");
        }
        // traza de debug específica para seguimiento de flujo
        log.info("mensaje llega a funcion de TcpConnection.sendRaw correctamente. Preparando a enviar literal de {} chars", line == null ? 0 : line.length());
        synchronized (writeLock) {
            try {
                // registramos el literal exacto que vamos a mandar (sin newline visible)
                log.debug("-> sendRaw() literal: [{}]", line.replace("\n","\\n"));
                PrintWriter pwLocal = this.pw;
                if (pwLocal == null) {
                    log.error("sendRaw: PrintWriter es null al intentar enviar");
                    throw new IOException("No writer available");
                }
                pwLocal.println(line);
                // println con autoFlush=true ya hace flush; registramos confirmación
                log.info("Mensaje enviado (local): {}", sanitizeLog(line));
            } catch (Exception e) {
                // si la escritura falla, marcamos desconectado y reenviamos la excepción como IOException
                log.error("Error enviando mensaje (cerrando conexión): {}", e.toString(), e);
                try { socket.close(); } catch (Exception ignored) {}
                connected.set(false);
                throw new IOException(e);
            }
        }
    }

    public String readLine() throws IOException {
        if (!isConnected()) {
            log.warn("Intento de lectura sin conexión activa");
            return null;
        }
        synchronized (readLock) {
            try {
                BufferedReader inLocal = this.in;
                if (inLocal == null) {
                    log.warn("readLine: reader es null");
                    return null;
                }
                String line = inLocal.readLine();
                if (line == null) {
                    log.info("readLine() devolvió null -> cierre remoto");
                    try { socket.close(); } catch (Exception ignored) {}
                    connected.set(false);
                    return null;
                }
                log.info("Mensaje recibido: {}", sanitizeLog(line));
                return line;
            } catch (java.net.SocketTimeoutException ste) {
                // Timeout de lectura. No considerarlo desconexión inmediata.
                log.debug("Socket timeout leyendo (sin datos): {}", ste.getMessage());
                throw ste;
            } catch (java.net.SocketException se) {
                log.warn("SocketException en readLine(): {}. Cerrando conexión localmente.", se.getMessage());
                try { socket.close(); } catch (Exception ignored) {}
                connected.set(false);
                throw se;
            } catch (IOException e) {
                log.error("Error leyendo mensaje: {}", e.toString(), e);
                try { socket.close(); } catch (Exception ignored) {}
                connected.set(false);
                throw e;
            }
        }
    }

    public boolean isConnected() {
        try {
            return connected.get() && socket != null && socket.isConnected() && !socket.isClosed();
        } catch (Exception e) {
            return false;
        }
    }

    private String sanitizeLog(String s) {
        if (s == null) return "";
        if (s.length() > 200) return s.substring(0, 200) + "...(trunc)";
        return s;
    }
}
