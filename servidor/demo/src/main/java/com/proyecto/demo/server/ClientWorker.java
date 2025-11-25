package com.proyecto.demo.server;

import com.proyecto.demo.auth.AuthService;
import com.proyecto.demo.auth.UserDao;
import com.proyecto.demo.dao.MessageDao;
import com.proyecto.demo.dao.ArchivoDao;
import com.proyecto.demo.model.MessageRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
// removed unused collections imports after tightening file-extension policy
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.sql.Timestamp;

@Component
@Scope("prototype")  // Cada conexión de cliente necesita su propia instancia
public class ClientWorker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ClientWorker.class);

    private final Socket socket;
    private final AuthService authService;
    private final JdbcTemplate jdbc;
    private final ConnectedClients connectedClients;
    private final MessageDao messageDao;
    private final ArchivoDao archivoDao;
    private final UserDao userDao;
    private final com.proyecto.demo.dao.JdbcSesionDao sesionDao;
    // configurable limits (injected from application.properties)
    @Value("${app.upload.maxSizeMb:200}")
    private long maxSizeMb;

    @Value("${app.upload.blockedExtensions:exe,dll,bat,sh,jar,msi,com,scr}")
    private String blockedExtensionsProp;
    @Value("${app.upload.allowedExtensions:txt,bin}")
    private String allowedExtensionsProp;
    // cached parsed set
    private java.util.Set<String> allowedExtensionsSet = null;

    private boolean isAllowedExtension(String ext) {
        if (ext == null) return false;
        if (allowedExtensionsSet == null) {
            allowedExtensionsSet = new java.util.HashSet<>();
            try {
                if (allowedExtensionsProp != null && !allowedExtensionsProp.isBlank()) {
                    for (String s : allowedExtensionsProp.split(",")) {
                        String t = s.trim().toLowerCase();
                        if (!t.isEmpty()) allowedExtensionsSet.add(t);
                    }
                }
            } catch (Exception e) {
                log.warn("No se pudo parsear app.upload.allowedExtensions='{}': {}", allowedExtensionsProp, e.getMessage());
            }
        }
        return allowedExtensionsSet.contains(ext.toLowerCase());
    }
    private String authenticatedUser = null;
    private String authenticatedUserIp = null;
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private String sessionId = null;

    public ClientWorker(Socket socket, AuthService authService, JdbcTemplate jdbc, ConnectedClients connectedClients,
                        MessageDao messageDao, ArchivoDao archivoDao, UserDao userDao, com.proyecto.demo.dao.JdbcSesionDao sesionDao) {
        this.socket = socket;
        this.authService = authService;
        this.jdbc = jdbc;
        this.connectedClients = connectedClients;
        this.messageDao = messageDao;
        this.archivoDao = archivoDao;
        this.userDao = userDao;
        this.sesionDao = sesionDao;
    }

    @Override
    public void run() {
       try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
           BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

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
                    // legacy single-step file transfer (payload included)
                    handleFile(line, out);
                    continue;
                }
                if (line.startsWith("FILE_HDR ")) {
                    // new two-step: header first (recipient|filename|size)
                    handleFileHeader(line, out);
                    continue;
                }
                if (line.startsWith("FILE_DATA ")) {
                    // new two-step: actual payload (recipient|filename|base64)
                    handleFileData(line, out);
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
                // remove from registry and notify others (use saved IP)
                try { connectedClients.unregister(authenticatedUser, authenticatedUserIp); } catch (Exception ignored) {}
                // close DB session if present
                try {
                    if (sessionId != null) {
                        sesionDao.closeSession(sessionId, Timestamp.from(Instant.now()), "CERRADA");
                    }
                } catch (Exception ignored) {}
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

        // ensure Archivos_enviados directory exists (centralized storage for accepted files)
        java.nio.file.Path uploadsDir = java.nio.file.Paths.get("Archivos_enviados");
        try {
            if (!java.nio.file.Files.exists(uploadsDir)) {
                java.nio.file.Files.createDirectories(uploadsDir);
            }
        } catch (Exception e) {
            log.error("No se pudo crear uploads dir: {}", e.toString(), e);
        }

        // decode and save file on server (prefix with sender and timestamp to avoid collisions)
        byte[] data;
        try {
            data = java.util.Base64.getDecoder().decode(base64);
            try { com.proyecto.demo.ui.UiServerWindow.publishMessageToUi("Intento de envío de archivo: " + authenticatedUser + " -> " + recipient + " : " + filename + " (" + data.length + " bytes)"); } catch (Exception ignored) {}
        } catch (IllegalArgumentException iae) {
            String reason = "base64_invalido";
            try { com.proyecto.demo.ui.UiServerWindow.publishMessageToUi("Rechazado: " + filename + " motivo: " + reason + " desde " + socket.getRemoteSocketAddress()); } catch (Exception ignored) {}
            out.write("FILE_STATUS " + filename + "|ERROR|" + reason + "\n");
            out.flush();
            return;
        }

        // Enforce size limit (configurable, default 200 MB)
        final long MAX_BYTES = Math.max(1L, maxSizeMb) * 1024L * 1024L;
        if (data.length > MAX_BYTES) {
            String reason = "tamano_excedido";
            log.warn("Rechazando archivo grande de {} bytes desde {} (límite {})", data.length, socket.getRemoteSocketAddress(), MAX_BYTES);
            try { com.proyecto.demo.ui.UiServerWindow.publishMessageToUi("Rechazado: " + filename + " motivo: " + reason + " desde " + socket.getRemoteSocketAddress()); } catch (Exception ignored) {}
            out.write("FILE_STATUS " + filename + "|ERROR|" + reason + "\n");
            out.flush();
            return;
        }

        // Sanitize filename to avoid path traversal
        filename = filename.replaceAll("[\\\\/]+", "_");

        // quick extension detection
        String ext = "";
        int idx = filename.lastIndexOf('.');
        if (idx >= 0 && idx < filename.length() - 1) {
            ext = filename.substring(idx + 1).toLowerCase();
        }
        // Only accept extensions that appear in the allowed list (default: txt,bin)
        boolean allowedByExt = isAllowedExtension(ext);
        if (!allowedByExt) {
            String reason = "ext_no_permitida";
            log.warn("Rechazando archivo por extensión no permitida (.{}) nombre='{}' desde {}", ext, filename, socket.getRemoteSocketAddress());
            try { com.proyecto.demo.ui.UiServerWindow.publishMessageToUi("Rechazado: " + filename + " motivo: " + reason + " desde " + socket.getRemoteSocketAddress()); } catch (Exception ignored) {}
            out.write("FILE_STATUS " + filename + "|ERROR|" + reason + "\n");
            out.flush();
            return;
        }

        // At this point extension is .txt or .bin. For .txt we prefer text MIME, but we accept as requested.
        try { com.proyecto.demo.ui.UiServerWindow.publishMessageToUi("Archivo recibido y aceptado para guardado: " + filename + " desde " + socket.getRemoteSocketAddress()); } catch (Exception ignored) {}

        String safeName = java.time.Instant.now().toEpochMilli() + "_" + authenticatedUser + "_" + filename;
        java.nio.file.Path target = uploadsDir.resolve(safeName);
        try {
            java.nio.file.Files.write(target, data);
            log.info("Archivo recibido guardado en {}", target.toString());
            // Inform sender of success
            out.write("FILE_STATUS " + filename + "|OK\n");
            out.flush();
            // persist archivo and mensaje
            try {
                var senderOpt = userDao.findByUsername(authenticatedUser);
                Long senderId = senderOpt.isPresent() ? senderOpt.get().getId() : null;
                long archivoId = archivoDao.insertArchivo(filename, target.toString(), data.length, senderId);
                if (archivoId <= 0) {
                    log.warn("insertArchivo devolvió id no válido ({}) para archivo {}", archivoId, filename);
                }
                if (senderId != null) {
                    if ("ALL".equalsIgnoreCase(recipient)) {
                        for (String r : connectedClients.getConnectedUsers()) {
                            if (r.equalsIgnoreCase(authenticatedUser)) continue;
                            var recipOpt = userDao.findByUsername(r);
                            if (recipOpt.isPresent()) {
                                MessageRecord mr = new MessageRecord();
                                mr.setEmisorId(senderId);
                                mr.setReceptorId(recipOpt.get().getId());
                                mr.setTipoMensaje("ARCHIVO");
                                mr.setContenido(filename);
                                mr.setArchivoId(archivoId);
                                mr.setSesionId(sessionId);
                                log.debug("Persistiendo mensaje archivo: emisorId={} receptorId={} archivoId={} sesionId={}", senderId, recipOpt.get().getId(), archivoId, sessionId);
                                long mid = messageDao.insertMessage(mr);
                                if (mid <= 0) log.warn("insertMessage devolvió id no válido ({}) para mensaje archivo (archivoId={})", mid, archivoId);
                            }
                        }
                    } else {
                        var recipOpt = userDao.findByUsername(recipient);
                        if (recipOpt.isPresent()) {
                            MessageRecord mr = new MessageRecord();
                            mr.setEmisorId(senderId);
                            mr.setReceptorId(recipOpt.get().getId());
                            mr.setTipoMensaje("ARCHIVO");
                            mr.setContenido(filename);
                            mr.setArchivoId(archivoId);
                            mr.setSesionId(sessionId);
                            log.debug("Persistiendo mensaje archivo: emisorId={} receptorId={} archivoId={} sesionId={}", senderId, recipOpt.get().getId(), archivoId, sessionId);
                            long mid = messageDao.insertMessage(mr);
                            if (mid <= 0) log.warn("insertMessage devolvió id no válido ({}) para mensaje archivo (archivoId={})", mid, archivoId);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("No se pudo persistir archivo/mensaje en BD", e);
            }
        } catch (Exception e) {
            log.error("Error guardando archivo: {}", e.toString(), e);
            String reason = "save_error";
            try { com.proyecto.demo.ui.UiServerWindow.publishMessageToUi("Rechazado: " + filename + " motivo: " + reason + " desde " + socket.getRemoteSocketAddress()); } catch (Exception ignored) {}
            out.write("FILE_STATUS " + filename + "|ERROR|" + reason + "\n");
            out.flush();
            return;
        }

        // forward to recipient(s): send FILEFROM sender|filename|base64
        String forward = "FILEFROM " + authenticatedUser + "|" + filename + "|" + base64 + "\n";
            if ("ALL".equalsIgnoreCase(recipient)) {
            connectedClients.broadcastMessage(authenticatedUser, "(archivo) " + filename);
            // broadcast the file itself
            connectedClients.broadcastRaw(forward);
        } else {
            boolean sent = connectedClients.sendTo(recipient, forward);
            if (!sent) {
                log.warn("No se pudo entregar archivo a {} (no conectado)", recipient);
            }
            // Also send a textual notification MSGFROM so the recipient sees a chat line like "(archivo) filename"
            try {
                String note = "MSGFROM " + authenticatedUser + "|" + "(archivo) " + filename + "\n";
                boolean noteSent = connectedClients.sendTo(recipient, note);
                if (noteSent) {
                    log.debug("Sent file notification MSGFROM to {} for file {}", recipient, filename);
                } else {
                    log.debug("Could not send file notification MSGFROM to {}", recipient);
                }
            } catch (Exception e) {
                log.warn("Error sending file notification to {}: {}", recipient, e.getMessage());
            }
            // send an immediate echo to the sender so their UI shows the outgoing file line
            try {
                String echo = "MSG_ECHO " + authenticatedUser + "|" + recipient + "|" + "(archivo) " + filename + "\n";
                out.write(echo);
                out.flush();
            } catch (Exception ee) {
                log.warn("No se pudo enviar echo de archivo al emisor {}: {}", authenticatedUser, ee.getMessage());
            }
        }

        out.write("SENT\n");
        out.flush();
    }

    private void handleFileHeader(String line, BufferedWriter out) throws IOException {
        // Format: FILE_HDR recipient|filename|sizeBytes
        log.info("mensaje llega a funcion de ClientWorker.handleFileHeader. Raw='{}'", line);
        if (authenticatedUser == null) {
            out.write("ERROR no_autenticado\n");
            out.flush();
            return;
        }
        String payload = line.substring(9);
        String[] parts = payload.split("\\|", 3);
        if (parts.length < 3) {
            out.write("FILE_HDR_STATUS " + "|ERROR|formato_hdr_invalido\n");
            out.flush();
            return;
        }
        String recipient = parts[0];
        String filename = parts[1];
        String sizeStr = parts[2];
    log.debug("FILE_HDR parsed recipient='{}' filename='{}' size='{}'", recipient, filename, sizeStr);
        long declaredSize = 0L;
        try {
            declaredSize = Long.parseLong(sizeStr);
        } catch (NumberFormatException nfe) {
            out.write("FILE_HDR_STATUS " + filename + "|ERROR|size_no_valido\n");
            out.flush();
            return;
        }

        // quick validation of extension and size
        String ext = "";
        int idx = filename.lastIndexOf('.');
        if (idx >= 0 && idx < filename.length() - 1) {
            ext = filename.substring(idx + 1).toLowerCase();
        }

        // Only accept extensions that appear in the allowed list (default: txt,bin)
        boolean allowedByExt = isAllowedExtension(ext);
        if (!allowedByExt) {
            String reason = "ext_no_permitida";
            try { com.proyecto.demo.ui.UiServerWindow.publishMessageToUi("Rechazado HDR: " + filename + " motivo: " + reason + " desde " + socket.getRemoteSocketAddress()); } catch (Exception ignored) {}
            out.write("FILE_HDR_STATUS " + filename + "|ERROR|" + reason + "\n");
            out.flush();
            return;
        }

        final long MAX_BYTES = Math.max(1L, maxSizeMb) * 1024L * 1024L;
        if (declaredSize > MAX_BYTES) {
            String reason = "tamano_excedido";
            try { com.proyecto.demo.ui.UiServerWindow.publishMessageToUi("Rechazado HDR: " + filename + " motivo: " + reason + " desde " + socket.getRemoteSocketAddress()); } catch (Exception ignored) {}
            out.write("FILE_HDR_STATUS " + filename + "|ERROR|" + reason + "\n");
            out.flush();
            return;
        }

        try { com.proyecto.demo.ui.UiServerWindow.publishMessageToUi("HDR aceptado: " + filename + " size=" + declaredSize + " desde " + socket.getRemoteSocketAddress()); } catch (Exception ignored) {}
        out.write("FILE_HDR_STATUS " + filename + "|OK\n");
        out.flush();
    }

    private void handleFileData(String line, BufferedWriter out) throws IOException {
        // Format: FILE_DATA recipient|filename|<base64payload>
        log.info("mensaje llega a funcion de ClientWorker.handleFileData. Raw='{}'", line);
        if (authenticatedUser == null) {
            out.write("ERROR no_autenticado\n");
            out.flush();
            return;
        }
        String payload = line.substring(10);
        String[] parts = payload.split("\\|", 3);
        if (parts.length < 3) {
            out.write("ERROR formato FILE_DATA recipient|filename|base64\n");
            out.flush();
            return;
        }
        String recipient = parts[0];
        String filename = parts[1];
        String base64 = parts[2];
        // ensure Archivos_enviados directory exists (centralized storage for accepted files)
        java.nio.file.Path uploadsDir = java.nio.file.Paths.get("Archivos_enviados");
        try {
            if (!java.nio.file.Files.exists(uploadsDir)) {
                java.nio.file.Files.createDirectories(uploadsDir);
            }
        } catch (Exception e) {
            log.error("No se pudo crear uploads dir: {}", e.toString(), e);
        }

        // decode and save file on server
        byte[] data;
        try {
            data = java.util.Base64.getDecoder().decode(base64);
            try { com.proyecto.demo.ui.UiServerWindow.publishMessageToUi("Archivo DATA recibido: " + authenticatedUser + " -> " + recipient + " : " + filename + " (" + data.length + " bytes)" ); } catch (Exception ignored) {}
        } catch (IllegalArgumentException iae) {
            String reason = "base64_invalido";
            try { com.proyecto.demo.ui.UiServerWindow.publishMessageToUi("Rechazado DATA: " + filename + " motivo: " + reason + " desde " + socket.getRemoteSocketAddress()); } catch (Exception ignored) {}
            out.write("FILE_STATUS " + filename + "|ERROR|" + reason + "\n");
            out.flush();
            return;
        }

        // Enforce size limit (configurable, default 200 MB)
        final long MAX_BYTES = Math.max(1L, maxSizeMb) * 1024L * 1024L;
        if (data.length > MAX_BYTES) {
            String reason = "tamano_excedido";
            log.warn("Rechazando archivo grande de {} bytes desde {} (límite {})", data.length, socket.getRemoteSocketAddress(), MAX_BYTES);
            try { com.proyecto.demo.ui.UiServerWindow.publishMessageToUi("Rechazado DATA: " + filename + " motivo: " + reason + " desde " + socket.getRemoteSocketAddress()); } catch (Exception ignored) {}
            out.write("FILE_STATUS " + filename + "|ERROR|" + reason + "\n");
            out.flush();
            return;
        }

        // Sanitize filename to avoid path traversal
        filename = filename.replaceAll("[\\\\/]+", "_");
        // save
        String safeName = java.time.Instant.now().toEpochMilli() + "_" + authenticatedUser + "_" + filename;
        java.nio.file.Path target = uploadsDir.resolve(safeName);
        try {
            java.nio.file.Files.write(target, data);
            log.info("Archivo DATA recibido guardado en {}", target.toString());
            // Inform sender of success (final)
            out.write("FILE_STATUS " + filename + "|OK\n");
            out.flush();
            // persist archivo and mensaje
            try {
                var senderOpt = userDao.findByUsername(authenticatedUser);
                Long senderId = senderOpt.isPresent() ? senderOpt.get().getId() : null;
                long archivoId = archivoDao.insertArchivo(filename, target.toString(), data.length, senderId);
                if (archivoId <= 0) {
                    log.warn("insertArchivo devolvió id no válido ({}) para archivo {}", archivoId, filename);
                }
                if (senderId != null) {
                    if ("ALL".equalsIgnoreCase(recipient)) {
                        for (String r : connectedClients.getConnectedUsers()) {
                            if (r.equalsIgnoreCase(authenticatedUser)) continue;
                            var recipOpt = userDao.findByUsername(r);
                            if (recipOpt.isPresent()) {
                                MessageRecord mr = new MessageRecord();
                                mr.setEmisorId(senderId);
                                mr.setReceptorId(recipOpt.get().getId());
                                mr.setTipoMensaje("ARCHIVO");
                                mr.setContenido(filename);
                                mr.setArchivoId(archivoId);
                                log.debug("Persistiendo mensaje archivo (DATA): emisorId={} receptorId={} archivoId={}", senderId, recipOpt.get().getId(), archivoId);
                                long mid = messageDao.insertMessage(mr);
                                if (mid <= 0) log.warn("insertMessage devolvió id no válido ({}) para mensaje archivo (archivoId={})", mid, archivoId);
                            }
                        }
                    } else {
                        var recipOpt = userDao.findByUsername(recipient);
                        if (recipOpt.isPresent()) {
                            MessageRecord mr = new MessageRecord();
                            mr.setEmisorId(senderId);
                            mr.setReceptorId(recipOpt.get().getId());
                            mr.setTipoMensaje("ARCHIVO");
                            mr.setContenido(filename);
                            mr.setArchivoId(archivoId);
                            log.debug("Persistiendo mensaje archivo (DATA): emisorId={} receptorId={} archivoId={}", senderId, recipOpt.get().getId(), archivoId);
                            long mid = messageDao.insertMessage(mr);
                            if (mid <= 0) log.warn("insertMessage devolvió id no válido ({}) para mensaje archivo (archivoId={})", mid, archivoId);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("No se pudo persistir archivo/mensaje en BD", e);
            }
        } catch (Exception e) {
            log.error("Error guardando archivo DATA: {}", e.toString(), e);
            String reason = "save_error";
            try { com.proyecto.demo.ui.UiServerWindow.publishMessageToUi("Rechazado DATA: " + filename + " motivo: " + reason + " desde " + socket.getRemoteSocketAddress()); } catch (Exception ignored) {}
            out.write("FILE_STATUS " + filename + "|ERROR|" + reason + "\n");
            out.flush();
            return;
        }

        // forward to recipient(s): send FILEFROM sender|filename|base64
        String forward = "FILEFROM " + authenticatedUser + "|" + filename + "|" + base64 + "\n";
        if ("ALL".equalsIgnoreCase(recipient)) {
            connectedClients.broadcastMessage(authenticatedUser, "(archivo) " + filename);
            // broadcast the file itself
            connectedClients.broadcastRaw(forward);
        } else {
            boolean sent = connectedClients.sendTo(recipient, forward);
            if (!sent) {
                log.warn("No se pudo entregar archivo a {} (no conectado)", recipient);
            }
            // Also send a textual notification MSGFROM so the recipient sees a chat line like "(archivo) filename"
            try {
                String note = "MSGFROM " + authenticatedUser + "|" + "(archivo) " + filename + "\n";
                boolean noteSent = connectedClients.sendTo(recipient, note);
                if (!noteSent) {
                    log.debug("Could not send file notification MSGFROM to {}", recipient);
                }
            } catch (Exception e) {
                log.warn("Error sending file notification to {}: {}", recipient, e.getMessage());
            }
        }

        out.write("SENT\n");
        out.flush();
    }

    private void handleRegister(String line, BufferedWriter out) throws Exception {
        log.info("mensaje llega a funcion de ClientWorker.handleRegister correctamente. Raw='{}'", line);
        String payload = line.substring(9);
        String[] p = payload.split("\\|", 3);
        if (p.length < 3) {
            out.write("ERROR formato REGISTER id|usuario|password\n");
            out.flush();
            return;
        }

        String id = p[0];
        String usuario = p[1];
        String password = p[2];

        boolean ok = authService.register(id, usuario, password);
        if (ok) {
            log.info("Registro exitoso de usuario '{}' (id={}) desde {}", usuario, id, socket.getRemoteSocketAddress());
            try { com.proyecto.demo.ui.UiServerWindow.publishMessageToUi("Registro exitoso: " + usuario + " (id=" + id + ") desde " + socket.getRemoteSocketAddress()); } catch (Exception ignored) {}
            out.write("REGISTERED\n");

            // perform DB sanity check (count users) if jdbc available
            try {
                if (jdbc != null) {
                    Long count = jdbc.queryForObject("SELECT COUNT(*) FROM usuarios", Long.class);
                    String msg = "Post-register DB check: usuarios count = " + (count == null ? 0 : count);
                    log.info(msg);
                    try { com.proyecto.demo.ui.UiServerWindow.publishMessageToUi(msg); } catch (Exception ignored) {}
                }
            } catch (Exception dbEx) {
                log.warn("Post-register DB check failed: {}", dbEx.getMessage());
                try { com.proyecto.demo.ui.UiServerWindow.publishMessageToUi("Post-register DB check failed: " + dbEx.getMessage()); } catch (Exception ignored) {}
            }

        } else {
            log.warn("Registro fallido (usuario existente o error): '{}' (id={}) desde {}", usuario, id, socket.getRemoteSocketAddress());
            try { com.proyecto.demo.ui.UiServerWindow.publishMessageToUi("Registro fallido (usuario existente o error): " + usuario + " (id=" + id + ") desde " + socket.getRemoteSocketAddress()); } catch (Exception ignored) {}
            out.write("ERROR usuario_existente\n");
        }
        out.flush();
    }

    private void handleLogin(String line, BufferedWriter out) throws Exception {
        log.info("mensaje llega a funcion de ClientWorker.handleLogin correctamente. Raw='{}'", line);
        String payload = line.substring(6);
        String[] p = payload.split("\\|", 3);
        if (p.length < 3) {
            out.write("ERROR formato LOGIN id|usuario|password\n");
            out.flush();
            return;
        }

        boolean ok = authService.login(p[0], p[1], p[2]);
        if (ok) {
            String user = p[1];
            String remoteIp = socket.getInetAddress() == null ? socket.getRemoteSocketAddress().toString() : socket.getInetAddress().getHostAddress();
            log.info("Login exitoso: usuario='{}' id={} desde {}", user, p[0], socket.getRemoteSocketAddress());
            try { com.proyecto.demo.ui.UiServerWindow.publishMessageToUi("Login exitoso: " + user + " (id=" + p[0] + ") desde " + socket.getRemoteSocketAddress()); } catch (Exception ignored) {}
            // attempt to register the authenticated user so we can broadcast connected users
            try {
                String regErr = connectedClients.register(user, out, remoteIp);
                if (regErr != null) {
                    log.warn("Registro rechazado para usuario '{}' desde {} motivo={}", user, remoteIp, regErr);
                    out.write("ERROR " + regErr + "\n");
                    out.flush();
                    return;
                }
                authenticatedUser = user;
                authenticatedUserIp = remoteIp;
                // create a DB session row and store sessionId
                try {
                    var uOpt = userDao.findByUsername(authenticatedUser);
                    if (uOpt.isPresent()) {
                        Long uid = uOpt.get().getId();
                        // token is optional for TCP sessions; leave null for now
                        sessionId = sesionDao.createSession(uid, authenticatedUserIp, null);
                        log.info("Sesion creada para usuario {} id={}", authenticatedUser, sessionId);
                    }
                } catch (Exception e) {
                    log.warn("No se pudo crear sesion en BD: {}", e.getMessage());
                }
            } catch (Exception e) {
                log.warn("Error registrando usuario en ConnectedClients: {}", e.getMessage());
            }
            out.write("LOGGED\n");
                out.flush();
                // After login, attempt to load recent messages for this user from DB and send them
                try {
                    var uOpt = userDao.findByUsername(user);
                    if (uOpt.isPresent()) {
                        Long uid = uOpt.get().getId();
                        // fetch up to 200 recent messages involving this user
                        java.util.List<com.proyecto.demo.model.MessageRecord> recent = messageDao.findForUser(uid, 200);
                        if (recent != null && !recent.isEmpty()) {
                            log.info("Enviando {} mensajes históricos desde BD a {}", recent.size(), user);
                            // Determine last closed session end time for this user (to avoid resending messages
                            // that the client already received during the previous session).
                            java.time.LocalDateTime lastLogout = null;
                            try {
                                var sessions = sesionDao.findActiveSessionsByUser(uid);
                                if (sessions != null && !sessions.isEmpty()) {
                                    for (var s : sessions) {
                                        if (s.desconectadoEn != null) {
                                            lastLogout = s.desconectadoEn.toLocalDateTime();
                                            break;
                                        }
                                    }
                                }
                            } catch (Exception sessEx) {
                                log.debug("No se pudo determinar última sesión cerrada para usuario {}: {}", user, sessEx.getMessage());
                            }
                            if (lastLogout != null) {
                                log.info("Se omitirá historial creado hasta {} para evitar duplicados al usuario {}", lastLogout, user);
                            }
                            for (com.proyecto.demo.model.MessageRecord mr : recent) {
                                try {
                                    // If we determined a lastLogout time, skip messages created at or before that time
                                    if (lastLogout != null && mr.getCreadoEn() != null && !mr.getCreadoEn().isAfter(lastLogout)) {
                                        // skip this message because it was likely already delivered in previous session
                                        continue;
                                    }
                                    // get sender username via JDBC (handle nombre/nombre_usuario)
                                    String sender = null;
                                    try {
                                        var senderOpt = userDao.findById(mr.getEmisorId());
                                        if (senderOpt.isPresent()) sender = senderOpt.get().getUsername(); else sender = String.valueOf(mr.getEmisorId());
                                    } catch (Exception exu) {
                                        try { sender = String.valueOf(mr.getEmisorId()); } catch (Exception ign) { sender = "?"; }
                                    }
                                    // resolve receptor username (prefer readable name over numeric id)
                                    String receptor = "";
                                    try {
                                        if (mr.getReceptorId() != null) {
                                            var recipOpt = userDao.findById(mr.getReceptorId());
                                            if (recipOpt.isPresent()) receptor = recipOpt.get().getUsername();
                                            else receptor = String.valueOf(mr.getReceptorId());
                                        }
                                    } catch (Exception exr) {
                                        try { receptor = mr.getReceptorId() == null ? "" : String.valueOf(mr.getReceptorId()); } catch (Exception ign) { receptor = ""; }
                                    }

                                    if ("ARCHIVO".equalsIgnoreCase(mr.getTipoMensaje()) && mr.getArchivoId() != null) {
                                        var ainfo = archivoDao.findById(mr.getArchivoId());
                                        if (ainfo != null) {
                                            java.nio.file.Path pathCandidate = null;
                                            try {
                                                if (ainfo.path != null && !ainfo.path.isBlank()) {
                                                    pathCandidate = java.nio.file.Paths.get(ainfo.path);
                                                    if (!java.nio.file.Files.exists(pathCandidate)) {
                                                        // try relative uploads dir
                                                        pathCandidate = java.nio.file.Paths.get("Archivos_enviados").resolve(ainfo.path);
                                                        if (!java.nio.file.Files.exists(pathCandidate)) pathCandidate = null;
                                                    }
                                                }
                                                if (pathCandidate == null && ainfo.filename != null) {
                                                    // try to find file in uploads dir by pattern
                                                    java.nio.file.Path uploads = java.nio.file.Paths.get("Archivos_enviados");
                                                    if (java.nio.file.Files.exists(uploads) && java.nio.file.Files.isDirectory(uploads)) {
                                                        try (java.util.stream.Stream<java.nio.file.Path> s = java.nio.file.Files.list(uploads)) {
                                                            java.util.Optional<java.nio.file.Path> found = s.filter(pp -> pp.getFileName().toString().endsWith("_" + ainfo.filename) || pp.getFileName().toString().equals(ainfo.filename)).findFirst();
                                                            if (found.isPresent()) pathCandidate = found.get();
                                                        }
                                                    }
                                                }
                                                if (pathCandidate != null) {
                                                    byte[] content = java.nio.file.Files.readAllBytes(pathCandidate);
                                                    String b64 = java.util.Base64.getEncoder().encodeToString(content);
                                                    // Send HISTFILE with emisor|receptor|filename|base64|timestamp
                                                    String ts = mr.getCreadoEn() == null ? java.time.Instant.now().toString() : mr.getCreadoEn().toString();
                                                    out.write("HISTFILE " + sender + "|" + receptor + "|" + ainfo.filename + "|" + b64 + "|" + ts + "\n");
                                                    out.flush();
                                                    // Also send a historical textual notification so the client shows a chat line like '(archivo) filename'
                                                    out.write("HISTMSG " + sender + "|" + receptor + "|" + "(archivo) " + ainfo.filename + "|" + ts + "\n");
                                                    out.flush();
                                                } else {
                                                    log.warn("Archivo histórico no encontrado en FS para archivo id={} nombre='{}'", ainfo.id, ainfo.filename);
                                                }
                                            } catch (Exception re) {
                                                log.warn("No se pudo leer archivo histórico {} desde path {}: {}", ainfo.filename, ainfo.path, re.getMessage());
                                            }
                                        }
                                    } else {
                                        String content = mr.getContenido() == null ? "" : mr.getContenido();
                                        // Send HISTMSG with emisor|receptor|content|timestamp
                                        String ts = mr.getCreadoEn() == null ? java.time.Instant.now().toString() : mr.getCreadoEn().toString();
                                        out.write("HISTMSG " + sender + "|" + receptor + "|" + content + "|" + ts + "\n");
                                        out.flush();
                                    }
                                } catch (Exception inner) {
                                    log.warn("Error enviando mensaje histórico al cliente {}: {}", user, inner.getMessage());
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("No se pudo enviar historial de mensajes desde BD tras login para user {}: {}", user, e.getMessage());
                }
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
            connectedClients.broadcastMessage(authenticatedUser, messageText);
        } else {
            boolean sent = connectedClients.sendTo(recipient, forwardLine);
            if (!sent) {
                log.warn("No se pudo entregar mensaje a {} (no conectado)", recipient);
            }
            // ensure the sender also receives an echo with recipient info so their UI can show the sent message
            try {
                String echo = "MSG_ECHO " + authenticatedUser + "|" + recipient + "|" + messageText + "\n";
                out.write(echo);
                out.flush();
            } catch (Exception ew) {
                log.warn("No se pudo enviar echo de mensaje al emisor {}: {}", authenticatedUser, ew.getMessage());
            }
        }

        // persist message(s) to DB (best-effort, non-blocking for the protocol)
        try {
            var senderOpt = userDao.findByUsername(authenticatedUser);
            if (senderOpt.isPresent()) {
                Long senderId = senderOpt.get().getId();
                if ("ALL".equalsIgnoreCase(recipient)) {
                    for (String r : connectedClients.getConnectedUsers()) {
                        if (r.equalsIgnoreCase(authenticatedUser)) continue;
                        var recipOpt = userDao.findByUsername(r);
                        if (recipOpt.isPresent()) {
                            MessageRecord mr = new MessageRecord();
                            mr.setEmisorId(senderId);
                            mr.setReceptorId(recipOpt.get().getId());
                            mr.setTipoMensaje("TEXTO");
                            mr.setContenido(messageText);
                            mr.setArchivoId(null);
                            mr.setSesionId(sessionId);
                            log.debug("Persistiendo mensaje: emisorId={} receptorId={} sesionId={} contenido='{}'", senderId, recipOpt.get().getId(), sessionId, messageText);
                            messageDao.insertMessage(mr);
                        }
                    }
                } else {
                    var recipOpt = userDao.findByUsername(recipient);
                    if (recipOpt.isPresent()) {
                        MessageRecord mr = new MessageRecord();
                        mr.setEmisorId(senderId);
                        mr.setReceptorId(recipOpt.get().getId());
                        mr.setTipoMensaje("TEXTO");
                        mr.setContenido(messageText);
                        mr.setArchivoId(null);
                        mr.setSesionId(sessionId);
                        log.debug("Persistiendo mensaje: emisorId={} receptorId={} sesionId={} contenido='{}'", senderId, recipOpt.get().getId(), sessionId, messageText);
                        messageDao.insertMessage(mr);
                    }
                }
            } else {
                log.warn("No se encontró usuario emisor en BD para persistencia: {}", authenticatedUser);
            }
        } catch (Exception e) {
            log.error("Error guardando mensaje en BD", e);
        }

        out.write("SENT\n");
        out.flush();
        System.out.println("[RESP] SENT -> " + authenticatedUser + " (" + socket.getRemoteSocketAddress() + ")");
    }
}
