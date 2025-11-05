package com.proyecto.demo.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.Socket;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.proyecto.demo.server.ConnectedClients;

public class UiServerWindow {

    private static final Logger log = LoggerFactory.getLogger(UiServerWindow.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Componentes de la GUI compartida
    private static JFrame frame;
    private static JTextArea logArea;
    private static volatile boolean initialized = false;

    /**
     * Asegura que la ventana exista y sea visible. Ejecuta la creación en el EDT.
     */
    private static void ensureWindow() {
        if (initialized) return;
        try {
            // Diagnostic log for headless status (helpful when UI sometimes doesn't show)
            try {
                log.info("java.awt.headless={} | GraphicsEnvironment.isHeadless={}",
                        System.getProperty("java.awt.headless"),
                        java.awt.GraphicsEnvironment.isHeadless());
            } catch (Throwable t) {
                log.warn("No se pudo leer estado headless: {}", t.toString());
            }
            SwingUtilities.invokeAndWait(() -> {
                if (initialized) return;
                log.info("Creando ventana UI del servidor...");
                frame = new JFrame("Servidor - Panel de control");
                frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
                frame.setSize(640, 360);
                frame.setLocationRelativeTo(null);

                logArea = new JTextArea();
                logArea.setEditable(false);
                logArea.setLineWrap(true);
                logArea.setWrapStyleWord(true);
                logArea.setBorder(new EmptyBorder(8,8,8,8));
                JScrollPane sp = new JScrollPane(logArea);

                JLabel header = new JLabel("Logs de conexiones (aparecerá diálogo de aprobación en cada nueva conexión)");
                header.setBorder(new EmptyBorder(6,6,6,6));

                JPanel p = new JPanel(new BorderLayout(6,6));
                // Top bar with header and a button to show connected users
                JPanel topBar = new JPanel(new BorderLayout(6,6));
                topBar.add(header, BorderLayout.CENTER);
                JButton usersBtn = new JButton("Mostrar usuarios conectados");
                usersBtn.addActionListener(ae -> {
                    try {
                        var users = ConnectedClients.getConnectedUsers();
                        appendLog("Usuarios conectados: " + String.join(",", users));
                    } catch (Exception e) {
                        appendLog("Error obteniendo usuarios conectados: " + e.getMessage());
                    }
                });
                topBar.add(usersBtn, BorderLayout.EAST);

                p.add(topBar, BorderLayout.NORTH);
                p.add(sp, BorderLayout.CENTER);

                frame.getContentPane().add(p);
                frame.setVisible(true);
                initialized = true;
                appendLog("Panel iniciado");
                log.info("Ventana UI visible");
            });
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Inicialización de UI interrumpida");
        } catch (InvocationTargetException ite) {
            log.error("Error inicializando UI: {}", ite.getCause() == null ? ite.toString() : ite.getCause().toString(), ite);
        }
    }

    private static void appendLog(String s) {
        try {
            String ts = LocalDateTime.now().format(FMT);
            String line = "[" + ts + "] " + s + "\n";
            if (logArea != null) {
                SwingUtilities.invokeLater(() -> {
                    logArea.append(line);
                    logArea.setCaretPosition(logArea.getDocument().getLength());
                });
            }
            log.info(s);
        } catch (Exception e) {
            log.warn("appendLog fallo: {}", e.toString());
        }
    }

    /**
     * Public entry to attempt to initialize the UI from non-EDT threads.
     * This will log headless state and any initialization errors.
     */
    public static void initialize() {
        try {
            ensureWindow();
            log.info("UI inicializada correctamente (initialize)");
        } catch (Throwable t) {
            log.warn("Fallo al inicializar UI vía initialize(): {}", t.toString());
        }
    }

    /**
     * Public helper to publish an arbitrary message to the server UI log area.
     * Useful for diagnostic traces from non-EDT threads.
     */
    public static void publishMessageToUi(String msg) {
        try {
            ensureWindow();
            appendLog(msg);
        } catch (Exception e) {
            log.warn("publishMessageToUi fallo: {}", e.toString());
        }
    }

    /**
     * Muestra diálogo de aprobación. Crea/trae la ventana principal y luego muestra
     * JOptionPane en el EDT. Devuelve true si el usuario aprueba.
     *
     * Nota: Retorna false si ocurre algún error al mostrar el diálogo (fallback seguro).
     */
    public static boolean requestApproval(Socket socket) {
        // Si headless, no intentamos GUI
        if (GraphicsEnvironment.isHeadless()) {
            log.warn("Entorno headless detectado, aprobación automática habilitada.");
            return true;
        }

        // Crear o mostrar la ventana principal
        ensureWindow();

        final boolean[] resultHolder = new boolean[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                String msg = "Nueva conexión desde: " + socket.getInetAddress().getHostAddress()
                        + "\nPuerto remoto: " + socket.getPort()
                        + "\n¿Aprobar conexión?";
                // Llevar la ventana principal al frente para que el diálogo sea visible
                if (frame != null) {
                    try {
                        frame.toFront();
                        frame.requestFocus();
                    } catch (Exception ignored) {}
                }
                appendLog("Solicitud de conexión desde " + socket.getRemoteSocketAddress());
                int answer = JOptionPane.showConfirmDialog(
                        frame,
                        msg,
                        "Solicitud de conexión",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                );
                resultHolder[0] = answer == JOptionPane.YES_OPTION;
                appendLog("Decisión: " + (resultHolder[0] ? "APROBADA" : "RECHAZADA") + " para " + socket.getRemoteSocketAddress());
            });
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Solicitud de aprobación interrumpida para {}", socket.getRemoteSocketAddress());
            return false;
        } catch (InvocationTargetException ite) {
            log.error("Error mostrando diálogo de aprobación: {}", ite.getCause() == null ? ite.toString() : ite.getCause().toString(), ite);
            return false;
        }

        boolean approved = resultHolder[0];
        log.info("Conexión {} desde {}", (approved ? "aprobada" : "rechazada"), socket.getRemoteSocketAddress());
        return approved;
    }
}
