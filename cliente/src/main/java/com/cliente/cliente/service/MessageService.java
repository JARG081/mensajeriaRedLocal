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

            // traza inmediata de post-envío
            log.info("Mensaje enviado localmente: [{}] (no confundir con confirmación del servidor)", lineToSend);

            // persistir y publicar localmente para que el ChatPanel muestre el propio mensaje
            String stamped = "[" + LocalDateTime.now().format(fmt) + "] Yo -> " + (recipient == null || recipient.isEmpty() ? "ALL" : recipient) + ": " + text;
            persistence.appendMessage(stamped);
            bus.publish("SERVER_LINE", stamped);

            log.debug("Message persisted and published locally: {}", stamped);
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
            byte[] data = Files.readAllBytes(file.toPath());
            String b64 = Base64.getEncoder().encodeToString(data);
            String to = (recipient == null || recipient.isEmpty()) ? "ALL" : recipient;
            String line = "FILE " + to + "|" + file.getName() + "|" + b64;
            conn.sendRaw(line);

            String stamped = "[" + LocalDateTime.now().format(fmt) + "] Yo -> " + to + ": (archivo) " + file.getName();
            persistence.appendMessage(stamped);
            bus.publish("SERVER_LINE", stamped);
            return true;
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
            MessageDTO dto = new MessageDTO(sender, text, System.currentTimeMillis());
            log.info("Mensaje entrante de {}: {}", sender, text);
            // publicar como mensaje entrante para que la UI lo ubique en la conversación correcta
            bus.publish("INCOMING_MSG", dto);
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
                bus.publish("INCOMING_FILE", fileDto);
            } catch (Exception e) {
                log.error("Error decodificando archivo entrante: {}", e.toString(), e);
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
            bus.publish("AUTH_ERROR", trimmed);
            return;
        }
        // otros casos: SENT, etc. ya se publicaron como SERVER_LINE
    }
}