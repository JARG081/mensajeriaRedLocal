package com.cliente.cliente.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.cliente.cliente.connection.TcpConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Component
public class AuthClientService {
    private static final Logger log = LoggerFactory.getLogger(AuthClientService.class);

    private final TcpConnection conn;

    @Autowired
    public AuthClientService(TcpConnection conn){
        this.conn = conn;
    }

    // Envía REGISTER y retorna true si comando fue enviado (la confirmación llega por MessageService)
    public boolean register(String user, String pass) {
        try {
            log.info("Register request for user='{}' (password masked)", user);
            log.info("mensaje llega a funcion de AuthClientService.register correctamente for user='{}'", user);
            ensureConnected();
            String regLiteral = "REGISTER " + user + "|" + maskForLog(pass);
            log.info("Enviando REGISTER al servidor (masked): {}", regLiteral);
            // send the real password to the server; only mask it in logs
            try {
                conn.sendRaw("REGISTER " + user + "|" + pass);
            } catch (IOException ioe) {
                log.warn("Enviar REGISTER falló con IOException: {}. Intentando reconectar y reenviar...", ioe.getMessage());
                // marcar desconectado y reintentar una vez
                try { conn.connect(); conn.sendRaw("REGISTER " + user + "|" + pass); }
                catch (Exception retryEx) {
                    log.error("Reintento de REGISTER falló: {}", retryEx.getMessage(), retryEx);
                    throw retryEx;
                }
            }
            log.debug("REGISTER payload sent (masked) for user='{}': {}", user, regLiteral);
            return true;
        } catch (Exception e) {
            log.error("register error for user='{}': {}", user, e.getMessage(), e);
            return false;
        } finally {
            // Cerrar la conexión tras el intento de registro (éxito o fallo)
            try {
                if (conn != null && conn.isConnected()) {
                    log.info("Cerrando conexión al servidor tras intento de registro para '{}'", user);
                    conn.disconnect();
                }
            } catch (Exception ex) {
                log.warn("Error al cerrar la conexión después de register: {}", ex.getMessage());
            }
        }
    }

    // Envía LOGIN y retorna true si el comando fue enviado
    public boolean login(String user, String pass) {
        try {
            log.info("Login request for user='{}' (password masked)", user);
            log.info("mensaje llega a funcion de AuthClientService.login correctamente for user='{}'", user);
            ensureConnected();
            // prepare masked literal for logging (but send full password)
            String loginLiteral = "LOGIN " + user + "|" + maskForLog(pass);
            log.info("Enviando LOGIN al servidor (masked): {}", loginLiteral);
            // send the real password to the server; only mask it in logs
            try {
                conn.sendRaw("LOGIN " + user + "|" + pass);
            } catch (IOException ioe) {
                log.warn("Enviar LOGIN falló con IOException: {}. Intentando reconectar y reenviar...", ioe.getMessage());
                try { conn.connect(); conn.sendRaw("LOGIN " + user + "|" + pass); }
                catch (Exception retryEx) {
                    log.error("Reintento de LOGIN falló: {}", retryEx.getMessage(), retryEx);
                    throw retryEx;
                }
            }
            log.debug("LOGIN payload sent (masked) for user='{}': {}", user, loginLiteral);
            return true;
        } catch (Exception e) {
            log.error("login error for user='{}': {}", user, e.getMessage(), e);
            return false;
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
