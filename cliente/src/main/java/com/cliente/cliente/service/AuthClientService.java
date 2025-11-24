package com.cliente.cliente.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.cliente.cliente.connection.TcpConnection;
import com.cliente.cliente.events.UiEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Component
public class AuthClientService {
    private static final Logger log = LoggerFactory.getLogger(AuthClientService.class);

    private final TcpConnection conn;
    private final ClientState clientState;
    private final UiEventBus bus;

    @Autowired
    public AuthClientService(TcpConnection conn, ClientState clientState, UiEventBus bus){
        this.conn = conn;
        this.clientState = clientState;
        this.bus = bus;
    }

    // Envía REGISTER y retorna true si comando fue enviado (la confirmación llega por MessageService)
    public boolean register(String id, String user, String pass) {
        try {
            log.info("Register request for user='{}' id='{}' (password masked)", user, id);
            log.info("mensaje llega a funcion de AuthClientService.register correctamente for user='{}' id='{}'", user, id);
            ensureConnected();
            String regLiteral = "REGISTER " + id + "|" + user + "|" + maskForLog(pass);
            log.info("Enviando REGISTER al servidor (masked): {}", regLiteral);
            // send the real password to the server; only mask it in logs
            try {
                conn.sendRaw("REGISTER " + id + "|" + user + "|" + pass);
            } catch (IOException ioe) {
                log.warn("Enviar REGISTER falló con IOException: {}. Intentando reconectar y reenviar...", ioe.getMessage());
                // marcar desconectado y reintentar una vez
                try { conn.connect(); conn.sendRaw("REGISTER " + id + "|" + user + "|" + pass); }
                catch (Exception retryEx) {
                    log.error("Reintento de REGISTER falló: {}", retryEx.getMessage(), retryEx);
                    throw retryEx;
                }
            }
            log.debug("REGISTER payload sent (masked) for user='{}' id='{}': {}", user, id, regLiteral);
            return true;
        } catch (Exception e) {
            log.error("register error for user='{}' id='{}': {}", user, id, e.getMessage(), e);
            return false;
        }
    }

    // Envía LOGIN y retorna true si el comando fue enviado
    public boolean login(String id, String user, String pass) {
        try {
            log.info("Login request for user='{}' id='{}' (password masked)", user, id);
            log.info("mensaje llega a funcion de AuthClientService.login correctamente for user='{}' id='{}'", user, id);
            ensureConnected();
            // prepare masked literal for logging (but send full password)
            String loginLiteral = "LOGIN " + id + "|" + user + "|" + maskForLog(pass);
            log.info("Enviando LOGIN al servidor (masked): {}", loginLiteral);
            // send the real password to the server; only mask it in logs
            try {
                // store the username locally so other components know who we attempted to login as
                clientState.setCurrentUser(user);
                // notify UI that login was requested
                bus.publish("LOGIN_REQUESTED", user);
                conn.sendRaw("LOGIN " + id + "|" + user + "|" + pass);
            } catch (IOException ioe) {
                log.warn("Enviar LOGIN falló con IOException: {}. Intentando reconectar y reenviar...", ioe.getMessage());
                try { conn.connect(); conn.sendRaw("LOGIN " + id + "|" + user + "|" + pass); }
                catch (Exception retryEx) {
                    log.error("Reintento de LOGIN falló: {}", retryEx.getMessage(), retryEx);
                    throw retryEx;
                }
            }
            log.debug("LOGIN payload sent (masked) for user='{}' id={} : {}", user, id, loginLiteral);
            return true;
        } catch (Exception e) {
            log.error("login error for user='{}' id='{}': {}", user, id, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Cerrar sesión: desconectar la conexión TCP, limpiar estado local y notificar la UI.
     */
    public void logout() {
        String prev = clientState.getCurrentUser();
        try {
            log.info("Logout request for user='{}'", prev);
            try {
                conn.disconnect();
            } catch (Exception e) {
                log.warn("Error during disconnect: {}", e.getMessage());
            }
        } finally {
            try { clientState.setCurrentUser(null); } catch (Exception ignored) {}
            try { bus.publish("USER_LOGOUT", prev); } catch (Exception ignored) {}
            log.info("Logout completed for user='{}'", prev);
        }
    }

    private void ensureConnected() throws IOException {
        // Intentos de conexión con reintentos cortos para mejorar resiliencia en redes inestables
        if (conn.isConnected()) {
            log.debug("Already connected to server");
            return;
        }

        log.info("No active connection, attempting to connect (retries=3)...");
        IOException lastEx = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                conn.connect();
                if (conn.isConnected()) {
                    log.info("Conexión establecida en intento {}/3", attempt);
                    return;
                }
            } catch (IOException ioe) {
                lastEx = ioe;
                log.warn("Intento {}/3 - connect() falló: {}", attempt, ioe.getMessage());
                try { Thread.sleep(300); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
        // Si llegamos aquí, no pudimos conectar
        log.error("No se pudo establecer conexión tras reintentos");
        if (lastEx != null) throw lastEx;
    }

    // helper to mask password for logging (keeps first char and length)
    private String maskForLog(String pass) {
        if (pass == null) return "";
        if (pass.length() <= 1) return "*";
        return pass.charAt(0) + "***(" + pass.length() + ")";
    }
}
