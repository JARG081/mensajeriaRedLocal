package com.cliente.cliente.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.cliente.cliente.connection.TcpConnection;
import com.cliente.cliente.events.UiEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import com.cliente.cliente.dto.MessageDTO;
import com.cliente.cliente.dto.FileDTO;
import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

@Component
public class MessageService {
    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final TcpConnection conn;
    private final LocalPersistenceService persistence;
    private final UiEventBus bus;
    private final ClientState clientState;
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    // Track whether the client is logged in (set when server responds LOGGED)
    private volatile boolean loggedIn = false;

    // Pending file send futures keyed by filename
    private final ConcurrentHashMap<String, CompletableFuture<String>> pendingFileFutures = new ConcurrentHashMap<>();
    // Pending header futures (FILE_HDR) keyed by filename. Value is either "OK" or "ERROR|reason"
    private final ConcurrentHashMap<String, CompletableFuture<String>> pendingHdrFutures = new ConcurrentHashMap<>();
    // recent sent messages fingerprints to avoid duplicate echo when server forwards our own message
    private final ConcurrentHashMap<String, Long> recentSent = new ConcurrentHashMap<>();

    // NOTE: validation is performed on the server side. The client will always send
    // the FILE_HDR and then the FILE_DATA; the server decides to accept or reject.

    @Autowired
    public MessageService(TcpConnection conn, LocalPersistenceService persistence, UiEventBus bus, ClientState clientState){
        this.conn = conn; this.persistence = persistence; this.bus = bus; this.clientState = clientState;
    }

    // send a message to a specific recipient (recipient may be null or empty for broadcast)
    public boolean sendMessage(String recipient, String text){
        try {
            if (!loggedIn) {
                String err = "Debe iniciar sesión antes de enviar mensajes";
                persistence.appendMessage(err);
                bus.publish("AUTH_ERROR", err);
                log.warn("Intento de enviar mensaje sin estar autenticado");
                return false;
            }
            if (!conn.isConnected()) {
                log.info("No hay conexión activa al enviar mensaje; intentando conectar...");
                conn.connect();
            }

            String to = (recipient == null || recipient.isEmpty()) ? "ALL" : recipient;
            String safeText = text == null ? "" : text;
            String lineToSend = "MSG " + to + "|" + safeText;

            // trazas adicionales: antes de enviar y el literal que se envía
            log.info("Preparando envío. isConnected={}, longitud={} chars", conn.isConnected(), lineToSend.length());
            log.debug("Contenido literal a enviar (visible como raw): [{}]", lineToSend.replace("\n","\\n"));

            // intentar enviar y capturar cualquier excepción
            try {
                conn.sendRaw(lineToSend);
                log.info("sendRaw() completado sin excepción");
            } catch (Exception e) {
                // log detallado y re-lanzar para manejo exterior
                log.error("Fallo en conn.sendRaw(): {}", e.toString(), e);
                throw e;
            }

            // store fingerprint of recently sent message to avoid duplicate when server echoes our own message
            try {
                String who = clientState == null ? "" : (clientState.getCurrentUser() == null ? "" : clientState.getCurrentUser());
                String key = who + "|" + (text == null ? "" : text).trim();
                if (!key.isEmpty()) recentSent.put(key, System.currentTimeMillis());
            } catch (Exception ignored) {}
            log.debug("Message sent; stored fingerprint for dedupe: {}", (clientState==null?"":clientState.getCurrentUser()));
            return true;
        } catch (Exception e) {
            String err = "Error enviar: " + e.getMessage();
            persistence.appendMessage(err);
            bus.publish("AUTH_ERROR", err);
            log.error("Error enviando mensaje: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean sendFile(String recipient, File file) {
        try {
            if (!loggedIn) {
                String err = "Debe iniciar sesión antes de enviar archivos";
                persistence.appendMessage(err);
                bus.publish("AUTH_ERROR", err);
                log.warn("Intento de enviar archivo sin estar autenticado");
                return false;
            }
            if (!conn.isConnected()) conn.connect();
            String to = (recipient == null || recipient.isEmpty()) ? "ALL" : recipient;

            long size = Files.size(file.toPath());

            // Step 1: send header and wait for header acceptance (server will validate extension & size)
            String hdrLine = "FILE_HDR " + to + "|" + file.getName() + "|" + size;
            CompletableFuture<String> hdrFut = new CompletableFuture<>();
            pendingHdrFutures.put(file.getName(), hdrFut);
            try {
                conn.sendRaw(hdrLine);
                String hdrResp = hdrFut.get(30, TimeUnit.SECONDS); // e.g. "OK" or "ERROR|reason"
                if (hdrResp == null) {
                    String err = "Envio de archivo: timeout en etapa de cabecera";
                    persistence.appendMessage(err);
                    bus.publish("AUTH_ERROR", err);
                    return false;
                }
                if (!hdrResp.startsWith("OK")) {
                    String reason = hdrResp.contains("|") ? hdrResp.substring(hdrResp.indexOf('|')+1) : hdrResp;
                    String err = "Envio de archivo rechazado en cabecera: " + reason;
                    persistence.appendMessage(err);
                    bus.publish("AUTH_ERROR", err);
                    log.warn("Archivo HEADER rechazado por servidor: {} -> {} reason={}", file.getName(), to, reason);
                    return false;
                }
            } finally {
                pendingHdrFutures.remove(file.getName());
            }

            // Step 2: server accepted header, now send payload and wait final FILE_STATUS
            byte[] data = Files.readAllBytes(file.toPath());
            String b64 = Base64.getEncoder().encodeToString(data);
            String dataLine = "FILE_DATA " + to + "|" + file.getName() + "|" + b64;

            CompletableFuture<String> fut = new CompletableFuture<>();
            pendingFileFutures.put(file.getName(), fut);
            try {
                conn.sendRaw(dataLine);
                // wait up to 30 seconds for server response
                String resp = fut.get(30, TimeUnit.SECONDS);
                if (resp != null && resp.startsWith("OK")) {
                    String stamped = "[" + LocalDateTime.now().format(fmt) + "] Yo -> " + to + ": (archivo) " + file.getName();
                    persistence.appendMessage(stamped);
                    // publish a dedicated event so the UI can add the file to the conversation
                    com.cliente.cliente.dto.FileDTO fileDto = new FileDTO(clientState == null ? "Me" : clientState.getCurrentUser(), file.getName(), data, System.currentTimeMillis());
                    bus.publish("FILE_SENT", fileDto);
                    bus.publish("SERVER_LINE", stamped);
                    return true;
                } else {
                    // resp format: ERROR|reason
                    String reason = (resp == null) ? "timeout" : (resp.contains("|") ? resp.substring(resp.indexOf('|')+1) : resp);
                    String err = "Envio de archivo rechazado: " + reason;
                    persistence.appendMessage(err);
                    bus.publish("AUTH_ERROR", err);
                    log.warn("Archivo rechazado por servidor: {} -> {} reason={}", file.getName(), to, reason);
                    return false;
                }
            } finally {
                pendingFileFutures.remove(file.getName());
            }
        } catch (Exception e) {
            String err = "Error enviar archivo: " + e.getMessage();
            persistence.appendMessage(err);
            bus.publish("AUTH_ERROR", err);
            log.error("Error enviando archivo: {}", e.getMessage(), e);
            return false;
        }
    }

    // MessageReceiver llamará a este método con cada línea cruda del servidor
    public void handleServerLine(String line) {
        if (line == null) return;
        String trimmed = line.trim();
        log.debug("Procesando línea del servidor: {}", trimmed);
        log.info("mensaje llega a funcion de MessageService.handleServerLine correctamente. Línea='{}'", trimmed);
        // publicar la línea completa para que ChatPanel muestre
    persistence.appendMessage("[SRV] " + trimmed);
    // bus.publish("SERVER_LINE", "[SRV] " + trimmed); // evitar log global en UI

        // manejar respuestas de control
        // lista de usuarios conectados enviada por el servidor: "USERS user1,user2"
        if (trimmed.startsWith("USERS ")) {
            String payload = trimmed.length() > 6 ? trimmed.substring(6).trim() : "";
            List<String> users = new ArrayList<>();
            if (!payload.isEmpty()) {
                for (String u : payload.split(",")) {
                    users.add(u.trim());
                }
            }
            log.info("Recibida lista de usuarios: {}", users);
            bus.publish("USERS_LIST", users);
            return;
        }

        // mensajes entrantes reenviados por el servidor: "MSGFROM sender|text"
        if (trimmed.startsWith("MSGFROM ")) {
            String payload = trimmed.length() > 8 ? trimmed.substring(8) : "";
            String[] p = payload.split("\\|", 2);
            String sender = p.length > 0 ? p[0] : "";
            String text = p.length > 1 ? p[1] : "";
            // deduplicate: if this message comes from myself and matches a recently-sent message, ignore it
                try {
                    String key = sender + "|" + (text == null ? "" : text).trim();
                    Long t = recentSent.get(key);
                    if (t != null && (System.currentTimeMillis() - t) < 5_000L) {
                        recentSent.remove(key);
                        log.debug("Ignorando echo de mensaje propio detectado (sender={}, text={})", sender, key);
                        return;
                    }
                } catch (Exception ex) {
                    // fall back to publishing
                }
            MessageDTO dto = new MessageDTO(sender, text, System.currentTimeMillis());
            log.info("Mensaje entrante de {}: {}", sender, text);
            // publicar como mensaje entrante para que la UI lo ubique en la conversación correcta
            bus.publish("INCOMING_MSG", dto);
            return;
        }

        // Echo for messages we sent, with receptor info: "MSG_ECHO sender|receptor|text"
        if (trimmed.startsWith("MSG_ECHO ")) {
            String payload = trimmed.length() > 9 ? trimmed.substring(9) : "";
            String[] p = payload.split("\\|", 3);
            String receptor = p.length > 1 ? p[1] : "";
            String text = p.length > 2 ? p[2] : "";
            try {
                // publish as outgoing so UI places it under the receptor conversation
                MessageDTO dto = new MessageDTO("Yo", text, System.currentTimeMillis());
                java.util.Map<String, Object> m = new java.util.HashMap<>();
                m.put("receptor", receptor == null ? "" : receptor);
                m.put("dto", dto);
                bus.publish("OUTGOING_MSG", m);
            } catch (Exception ex) {
                log.debug("Failed processing MSG_ECHO: {}", ex.getMessage());
            }
            return;
        }

        // historical messages from DB: "HISTMSG sender|receptor|content|timestamp"
        if (trimmed.startsWith("HISTMSG ")) {
            String payload = trimmed.length() > 8 ? trimmed.substring(8) : "";
            String[] p = payload.split("\\|", 4);
            String sender = p.length > 0 ? p[0] : "";
            String receptor = p.length > 1 ? p[1] : "";
            String text = p.length > 2 ? p[2] : "";
            // deduplicate if this is our own recent message
            try {
                String me = clientState == null ? null : clientState.getCurrentUser();
                String key = sender + "|" + (text == null ? "" : text).trim();
                if (me != null && me.equals(sender)) {
                    Long t = recentSent.get(key);
                    if (t != null && (System.currentTimeMillis() - t) < 5_000L) {
                        recentSent.remove(key);
                        log.debug("Ignorando HISTMSG echo propio (sender={}, text={})", sender, key);
                        return;
                    }
                }
            } catch (Exception ex) {
                // fallthrough to publish
            }

            String me = clientState == null ? null : clientState.getCurrentUser();
            // If I'm the receptor -> incoming message, else if I'm the sender -> it's my historic sent message
            if (me != null && me.equals(receptor)) {
                MessageDTO dto = new MessageDTO(sender, text, System.currentTimeMillis());
                bus.publish("INCOMING_MSG", dto);
            } else if (me != null && me.equals(sender)) {
                // historic message sent by me: publish a HIST_SENT event with receptor and dto
                MessageDTO dto = new MessageDTO("Yo", text, System.currentTimeMillis());
                Map<String, Object> m = new HashMap<>();
                m.put("receptor", receptor == null ? "" : receptor);
                m.put("dto", dto);
                bus.publish("HIST_SENT", m);
            } else {
                // Not directly related to this client session (shouldn't happen), ignore
            }
            return;
        }

        // archivos entrantes reenviados por el servidor: "FILEFROM sender|filename|base64"
        if (trimmed.startsWith("FILEFROM ")) {
            String payload = trimmed.length() > 9 ? trimmed.substring(9) : "";
            String[] p = payload.split("\\|", 3);
            String sender = p.length > 0 ? p[0] : "";
            String filename = p.length > 1 ? p[1] : "";
            String b64 = p.length > 2 ? p[2] : "";
            try {
                byte[] data = Base64.getDecoder().decode(b64);
                com.cliente.cliente.dto.FileDTO fileDto = new FileDTO(sender, filename, data, System.currentTimeMillis());
                // If the FILEFROM comes from ourselves (the sender), ignore it to avoid duplicate entries
                String me = clientState == null ? null : clientState.getCurrentUser();
                if (me != null && me.equals(sender)) {
                    log.debug("Ignorando FILEFROM propio para archivo {}", filename);
                } else {
                    bus.publish("INCOMING_FILE", fileDto);
                }
            } catch (Exception e) {
                log.error("Error decodificando archivo entrante: {}", e.toString(), e);
            }
            return;
        }

        // historical files from DB: "HISTFILE sender|receptor|filename|base64|timestamp"
        if (trimmed.startsWith("HISTFILE ")) {
            String payload = trimmed.length() > 9 ? trimmed.substring(9) : "";
            String[] p = payload.split("\\|", 5);
            String sender = p.length > 0 ? p[0] : "";
            String receptor = p.length > 1 ? p[1] : "";
            String filename = p.length > 2 ? p[2] : "";
            String b64 = p.length > 3 ? p[3] : "";
            try {
                byte[] data = Base64.getDecoder().decode(b64);
                com.cliente.cliente.dto.FileDTO fileDto = new FileDTO(sender, filename, data, System.currentTimeMillis());
                String me = clientState == null ? null : clientState.getCurrentUser();
                if (me != null && me.equals(receptor)) {
                    // incoming historic file for me
                    bus.publish("INCOMING_FILE", fileDto);
                } else if (me != null && me.equals(sender)) {
                    // historic file I sent previously: publish HIST_FILE_SENT with receptor and file DTO
                    Map<String, Object> fm = new HashMap<>();
                    fm.put("receptor", receptor == null ? "" : receptor);
                    // set sender on FileDTO as "Yo" to indicate outgoing
                    com.cliente.cliente.dto.FileDTO f2 = new com.cliente.cliente.dto.FileDTO("Yo", filename, data, System.currentTimeMillis());
                    fm.put("file", f2);
                    bus.publish("HIST_FILE_SENT", fm);
                } else {
                    // not relevant to this user session
                }
            } catch (Exception e) {
                log.error("Error decodificando HISTFILE entrante: {}", e.toString(), e);
            }
            return;
        }

        // FILE_STATUS filename|OK  OR FILE_STATUS filename|ERROR|reason
        if (trimmed.startsWith("FILE_STATUS ")) {
            String payload = trimmed.substring(12);
            String[] parts = payload.split("\\|", 3);
            String fname = parts.length > 0 ? parts[0] : "";
            String status = parts.length > 1 ? parts[1] : "";
            String reason = parts.length > 2 ? parts[2] : "";
            CompletableFuture<String> fut = pendingFileFutures.get(fname);
            if (fut != null) {
                if ("OK".equalsIgnoreCase(status)) fut.complete("OK");
                else fut.complete("ERROR|" + reason);
            } else {
                log.debug("FILE_STATUS recibido pero no hay futuro pendiente para {}: {}", fname, status);
            }
            return;
        }

        // FILE_HDR_STATUS filename|OK OR FILE_HDR_STATUS filename|ERROR|reason
        if (trimmed.startsWith("FILE_HDR_STATUS ")) {
            String payload = trimmed.substring(16);
            String[] parts = payload.split("\\|", 3);
            String fname = parts.length > 0 ? parts[0] : "";
            String status = parts.length > 1 ? parts[1] : "";
            String reason = parts.length > 2 ? parts[2] : "";
            CompletableFuture<String> hf = pendingHdrFutures.get(fname);
            if (hf != null) {
                if ("OK".equalsIgnoreCase(status)) hf.complete("OK");
                else hf.complete("ERROR|" + reason);
            } else {
                log.debug("FILE_HDR_STATUS recibido pero no hay futuro pendiente para {}: {}", fname, status);
            }
            return;
        }

        if ("LOGGED".equalsIgnoreCase(trimmed)) {
            // mark logged in so the UI and services can enable sending
            loggedIn = true;
            log.info("Server reported LOGGED (user successfully logged in)");
            // publish username so UI can update title and other components
            String user = clientState == null ? null : clientState.getCurrentUser();
            bus.publish("USER_LOGGED", user);
            // Note: Do not preload local persistence here. Server will send historical messages
            // from the DB after login (MSGFROM/FILEFROM). This ensures UI reflects DB state.
            return;
        }
        if ("REGISTERED".equalsIgnoreCase(trimmed)) {
            log.info("Server reported REGISTERED (user successfully registered)");
            bus.publish("AUTH_INFO", "Registro exitoso");
            // publish a dedicated event so UI can clear registration fields
            bus.publish("REGISTERED_SUCCESS", null);
            return;
        }
        if (trimmed.startsWith("ERROR")) {
            log.warn("Server returned error: {}", trimmed);
            // publish only the payload part after the 'ERROR ' prefix so UI dialogs don't duplicate 'Error:'
            String payload = trimmed.length() > 6 ? trimmed.substring(6).trim() : trimmed;
            bus.publish("AUTH_ERROR", payload);
            return;
        }
        // otros casos: SENT, etc. ya se publicaron como SERVER_LINE
    }
}