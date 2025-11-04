package com.proyecto.demo.server;

import com.proyecto.demo.auth.FileAuthService;
import com.proyecto.demo.factory.ServerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ClientWorker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ClientWorker.class);

    private final Socket socket;
    private final FileAuthService authService;
    private String authenticatedUser = null;
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ClientWorker(Socket socket, FileAuthService authService) {
        this.socket = socket;
        this.authService = authService;
    }

    @Override
    public void run() {
       try (BufferedReader in = ServerFactory.createBufferedReader(socket);
           BufferedWriter out = ServerFactory.createBufferedWriter(socket)) {

            out.write("WELCOME\n");
            out.flush();
            log.info("Cliente {} conectado y saludado.", socket.getRemoteSocketAddress());

            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("REGISTER ")) {
                    handleRegister(line, out);
                    continue;
                }
                if (line.startsWith("LOGIN ")) {
                    handleLogin(line, out);
                    continue;
                }
                if (line.startsWith("MSG ")) {
                    handleMessage(line, out);
                    continue;
                }
                if (line.startsWith("FILE ")) {
                    handleFile(line, out);
                    continue;
                }
                if (line.equals("QUIT")) {
                    out.write("BYE\n");
                    out.flush();
                    break;
                }

                out.write("ERROR comando_desconocido\n");
                out.flush();
            }

        } catch (Exception e) {
            log.error("Error en worker para cliente {}: {}", socket.getRemoteSocketAddress(), e.toString());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            if (authenticatedUser != null) {
                log.info("Usuario '{}' desconectado.", authenticatedUser);
                // remove from registry and notify others
                try { ConnectedClients.unregister(authenticatedUser); } catch (Exception ignored) {}
            } else {
                log.info("Cliente {} desconectado sin autenticarse.", socket.getRemoteSocketAddress());
            }
        }
    }

    private void handleFile(String line, BufferedWriter out) throws IOException {
        // Format: FILE recipient|filename|<base64payload>
        log.info("mensaje llega a funcion de ClientWorker.handleFile correctamente. Raw='{}'", line);
        if (authenticatedUser == null) {
            out.write("ERROR no_autenticado\n");
            out.flush();
            return;
        }
        String payload = line.substring(5);
        String[] parts = payload.split("\\|", 3);
        if (parts.length < 3) {
            out.write("ERROR formato FILE recipient|filename|base64\n");
            out.flush();
            return;
        }
        String recipient = parts[0];
        String filename = parts[1];
        String base64 = parts[2];

        // ensure uploads directory exists
        java.nio.file.Path uploadsDir = java.nio.file.Paths.get("uploads");
        try {
            if (!java.nio.file.Files.exists(uploadsDir)) {
                java.nio.file.Files.createDirectories(uploadsDir);
            }
        } catch (Exception e) {
            log.error("No se pudo crear uploads dir: {}", e.toString(), e);
        }

        // decode and save file on server (prefix with sender and timestamp to avoid collisions)
        byte[] data = java.util.Base64.getDecoder().decode(base64);
        String safeName = java.time.Instant.now().toEpochMilli() + "_" + authenticatedUser + "_" + filename;
        java.nio.file.Path target = uploadsDir.resolve(safeName);
        try {
            java.nio.file.Files.write(target, data);
            log.info("Archivo recibido guardado en {}", target.toString());
        } catch (Exception e) {
            log.error("Error guardando archivo: {}", e.toString(), e);
        }

        // forward to recipient(s): send FILEFROM sender|filename|base64
        String forward = "FILEFROM " + authenticatedUser + "|" + filename + "|" + base64 + "\n";
            if ("ALL".equalsIgnoreCase(recipient)) {
            ConnectedClients.broadcastMessage(authenticatedUser, "(file) " + filename);
            // broadcast the file itself
            ConnectedClients.broadcastRaw(forward);
        } else {
            boolean sent = ConnectedClients.sendTo(recipient, forward);
            if (!sent) {
                log.warn("No se pudo entregar archivo a {} (no conectado)", recipient);
            }
        }

        out.write("SENT\n");
        out.flush();
    }

    private void handleRegister(String line, BufferedWriter out) throws Exception {
        log.info("mensaje llega a funcion de ClientWorker.handleRegister correctamente. Raw='{}'", line);
        String payload = line.substring(9);
        String[] p = payload.split("\\|", 2);
        if (p.length < 2) {
            out.write("ERROR formato REGISTER usuario|password\n");
            out.flush();
            return;
        }

        boolean ok = authService.register(p[0], p[1]);
        if (ok) {
            log.info("Registro exitoso de usuario '{}' desde {}", p[0], socket.getRemoteSocketAddress());
            try { com.proyecto.demo.ui.UiServerWindow.publishMessageToUi("Registro exitoso: " + p[0] + " desde " + socket.getRemoteSocketAddress()); } catch (Exception ignored) {}
            out.write("REGISTERED\n");
        } else {
            log.warn("Registro fallido (usuario existente): '{}' desde {}", p[0], socket.getRemoteSocketAddress());
            try { com.proyecto.demo.ui.UiServerWindow.publishMessageToUi("Registro fallido (usuario existente): " + p[0] + " desde " + socket.getRemoteSocketAddress()); } catch (Exception ignored) {}
            out.write("ERROR usuario_existente\n");
        }
        out.flush();
    }

    private void handleLogin(String line, BufferedWriter out) throws Exception {
        log.info("mensaje llega a funcion de ClientWorker.handleLogin correctamente. Raw='{}'", line);
        String payload = line.substring(6);
        String[] p = payload.split("\\|", 2);
        if (p.length < 2) {
            out.write("ERROR formato LOGIN usuario|password\n");
            out.flush();
            return;
        }

        boolean ok = authService.login(p[0], p[1]);

        if (ok) {
            authenticatedUser = p[0];
            log.info("Login exitoso: usuario='{}' desde {}", authenticatedUser, socket.getRemoteSocketAddress());
            try { com.proyecto.demo.ui.UiServerWindow.publishMessageToUi("Login exitoso: " + authenticatedUser + " desde " + socket.getRemoteSocketAddress()); } catch (Exception ignored) {}
            // register the authenticated user so we can broadcast connected users
            try { ConnectedClients.register(authenticatedUser, out); } catch (Exception ignored) {}
            out.write("LOGGED\n");
        } else {
            log.warn("Login fallido: usuario='{}' desde {}", p[0], socket.getRemoteSocketAddress());
            try { com.proyecto.demo.ui.UiServerWindow.publishMessageToUi("Login fallido: " + p[0] + " desde " + socket.getRemoteSocketAddress()); } catch (Exception ignored) {}
            out.write("ERROR credenciales\n");
        }
        out.flush();
    }

    private void handleMessage(String line, BufferedWriter out) throws IOException {
        log.info("mensaje llega a funcion de ClientWorker.handleMessage correctamente. Raw='{}'", line);
        if (authenticatedUser == null) {
            log.warn("Intento de envío sin autenticación desde {}", socket.getRemoteSocketAddress());
            try { com.proyecto.demo.ui.UiServerWindow.publishMessageToUi("Intento de mensaje sin autenticacion desde " + socket.getRemoteSocketAddress()); } catch (Exception ignored) {}
            out.write("ERROR no_autenticado\n");
            out.flush();
            return;
        }
        String payload = line.substring(4);
        String recipient = "";
        String messageText = "";
        String[] parts = payload.split("\\|", 2);
        if (parts.length >= 2) {
            recipient = parts[0];
            messageText = parts[1];
        } else {
            // fallback: no recipient included
            recipient = "ALL";
            messageText = payload;
        }

        String hora = LocalDateTime.now().format(fmt);
        // Log in required format
        String registro = String.format("Mensaje enviado por: %s Para %s Contenido: %s", authenticatedUser, recipient, messageText);
        log.info("[{}] {}", hora, registro);
        // also print to console and server UI
        System.out.println("[MSG] " + hora + " " + registro + " (desde " + socket.getRemoteSocketAddress() + ")");
        try { com.proyecto.demo.ui.UiServerWindow.publishMessageToUi("[MSG] " + hora + " " + registro + " (desde " + socket.getRemoteSocketAddress() + ")"); } catch (Exception ignored) {}

        // Deliver message: if recipient == ALL => broadcast; else send only to recipient (if connected)
        String forwardLine = "MSGFROM " + authenticatedUser + "|" + messageText + "\n";
        if ("ALL".equalsIgnoreCase(recipient)) {
            ConnectedClients.broadcastMessage(authenticatedUser, messageText);
        } else {
            boolean sent = ConnectedClients.sendTo(recipient, forwardLine);
            if (!sent) {
                log.warn("No se pudo entregar mensaje a {} (no conectado)", recipient);
            }
        }

        out.write("SENT\n");
        out.flush();
        System.out.println("[RESP] SENT -> " + authenticatedUser + " (" + socket.getRemoteSocketAddress() + ")");
    }
}
