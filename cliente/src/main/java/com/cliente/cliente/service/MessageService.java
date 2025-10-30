package com.cliente.cliente.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.cliente.cliente.connection.TcpConnection;
import com.cliente.cliente.events.UiEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class MessageService {
    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final TcpConnection conn;
    private final LocalPersistenceService persistence;
    private final UiEventBus bus;
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    // Track whether the client is logged in (set when server responds LOGGED)
    private volatile boolean loggedIn = false;

    @Autowired
    public MessageService(TcpConnection conn, LocalPersistenceService persistence, UiEventBus bus){
        this.conn = conn; this.persistence = persistence; this.bus = bus;
    }

    public boolean sendMessage(String text){
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

            String lineToSend = "MSG " + (text == null ? "" : text);

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
            String stamped = "[" + LocalDateTime.now().format(fmt) + "] Yo: " + text;
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

    // MessageReceiver llamará a este método con cada línea cruda del servidor
    public void handleServerLine(String line) {
        if (line == null) return;
        String trimmed = line.trim();
        log.debug("Procesando línea del servidor: {}", trimmed);
        log.info("mensaje llega a funcion de MessageService.handleServerLine correctamente. Línea='{}'", trimmed);
        // publicar la línea completa para que ChatPanel muestre
        persistence.appendMessage("[SRV] " + trimmed);
        bus.publish("SERVER_LINE", "[SRV] " + trimmed);

        // manejar respuestas de control
        if ("LOGGED".equalsIgnoreCase(trimmed)) {
            // mark logged in so the UI and services can enable sending
            loggedIn = true;
            log.info("Server reported LOGGED (user successfully logged in)");
            bus.publish("USER_LOGGED", null);
            return;
        }
        if ("REGISTERED".equalsIgnoreCase(trimmed)) {
            log.info("Server reported REGISTERED (user successfully registered)");
            bus.publish("AUTH_INFO", "Registro exitoso");
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