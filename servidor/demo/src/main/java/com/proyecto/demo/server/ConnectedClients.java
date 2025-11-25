package com.proyecto.demo.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registro simple de usuarios autenticados conectados.
 * Usado por cada ClientWorker para publicar la lista a todos los clientes.
 */
@Component
public class ConnectedClients {
    private static final Logger log = LoggerFactory.getLogger(ConnectedClients.class);

    // Instancia estática para acceso desde código UI que no está en el contexto de Spring
    private static ConnectedClients instance;

    // Map: username -> (ip -> BufferedWriter)
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, BufferedWriter>> clients = new ConcurrentHashMap<>();

    // max distinct IP connections per username
    @org.springframework.beans.factory.annotation.Value("${app.user.maxConnections:3}")
    private int maxConnectionsPerUser = 3;

    @PostConstruct
    public void init() {
        instance = this;
    }

    /**
     * Método estático para obtener la instancia desde código no gestionado por Spring.
     */
    public static ConnectedClients getInstance() {
        return instance;
    }

    /**
     * Register a connection for a username coming from a specific IP.
     * Returns true if registration accepted; false if rejected (same IP already connected or limit exceeded).
     */
    /**
     * Register a connection for a username coming from a specific IP.
     * Returns null if accepted; otherwise returns an error code string describing the rejection.
     */
    public String register(String user, BufferedWriter out, String ip) {
        if (user == null || out == null || ip == null) return "invalid_params";
        clients.putIfAbsent(user, new ConcurrentHashMap<>());
        var map = clients.get(user);
        // same IP already connected => reject (do not close previous connection)
        if (map.containsKey(ip)) {
            log.info("Rechazando registro para usuario '{}' desde la misma IP {} (ya conectado)", user, ip);
            return "Error: Limite de ips excedido, no puede ingresar en este equipo";
        }
        // enforce max distinct IPs per user
        if (map.size() >= maxConnectionsPerUser) {
            log.info("Rechazando registro para usuario '{}' desde {} (limite {} conexiones)", user, ip, maxConnectionsPerUser);
            return "Error: Limite de ips excedido, no puede ingresar en este equipo";
        }
        map.put(ip, out);
        log.info("Usuario registrado para broadcast: {} (user-conns={})", user, map.size());
        broadcastUserList();
        return null;
    }

    /**
     * Unregister the connection for a username coming from a specific IP.
     */
    public void unregister(String user, String ip) {
        if (user == null || ip == null) return;
        var map = clients.get(user);
        if (map == null) return;
        var removed = map.remove(ip);
        try { if (removed != null) removed.close(); } catch (Exception ignored) {}
        if (map.isEmpty()) {
            clients.remove(user);
        }
        log.info("Usuario {} desconectado desde {} (user-conns={})", user, ip, map == null ? 0 : map.size());
        broadcastUserList();
    }

    public void broadcastUserList() {
            try {
                List<String> users = new ArrayList<>(clients.keySet()).stream().sorted().collect(Collectors.toList());
            String csv = String.join(",", users);
            String line = "USERS " + csv + "\n";
            log.info("Broadcasting USERS list to {} clients: {}", clients.size(), csv);

            // iterate over a snapshot of entries to avoid concurrent modification issues
            // iterate over all writers for all users
            for (var userEntry : new ArrayList<>(clients.entrySet())) {
                String u = userEntry.getKey();
                var ipMap = userEntry.getValue();
                for (var e2 : new ArrayList<>(ipMap.entrySet())) {
                    BufferedWriter w = e2.getValue();
                    try {
                        w.write(line);
                        w.flush();
                    } catch (IOException ioe) {
                        log.warn("Failed to send USERS to {}@{}: {}. Removing.", u, e2.getKey(), ioe.getMessage());
                        try { w.close(); } catch (Exception ignored) {}
                        ipMap.remove(e2.getKey());
                    }
                }
                if (ipMap.isEmpty()) clients.remove(u);
            }
        } catch (Exception e) {
            log.error("Error broadcasting user list: {}", e.toString(), e);
        }
    }

    public boolean sendTo(String user, String line) {
        if (user == null || line == null) return false;
            if (user == null || line == null) return false;
            var map = clients.get(user);
            if (map == null || map.isEmpty()) return false;
            boolean atLeastOne = false;
            for (var entry : new ArrayList<>(map.entrySet())) {
                try {
                    entry.getValue().write(line);
                    entry.getValue().flush();
                    atLeastOne = true;
                } catch (IOException e) {
                    log.warn("Failed to send message to {}@{}: {}. Removing.", user, entry.getKey(), e.getMessage());
                    try { entry.getValue().close(); } catch (Exception ignored) {}
                    map.remove(entry.getKey());
                }
            }
            if (map.isEmpty()) clients.remove(user);
            return atLeastOne;
    }

    public void broadcastMessage(String sender, String text) {
        String payload = "MSGFROM " + sender + "|" + (text == null ? "" : text) + "\n";
        for (var userEntry : new ArrayList<>(clients.entrySet())) {
            String u = userEntry.getKey();
            var ipMap = userEntry.getValue();
            for (var e2 : new ArrayList<>(ipMap.entrySet())) {
                try {
                    e2.getValue().write(payload);
                    e2.getValue().flush();
                } catch (IOException ioe) {
                    log.warn("Failed to broadcast MSG to {}@{}: {}. Removing.", u, e2.getKey(), ioe.getMessage());
                    try { e2.getValue().close(); } catch (Exception ignored) {}
                    ipMap.remove(e2.getKey());
                }
            }
            if (ipMap.isEmpty()) clients.remove(u);
        }
    }

    public void broadcastRaw(String line) {
        if (line == null) return;
        String payload = line.endsWith("\n") ? line : line + "\n";
        for (var userEntry : new ArrayList<>(clients.entrySet())) {
            String u = userEntry.getKey();
            var ipMap = userEntry.getValue();
            for (var e2 : new ArrayList<>(ipMap.entrySet())) {
                try {
                    e2.getValue().write(payload);
                    e2.getValue().flush();
                } catch (IOException ioe) {
                    log.warn("Failed to broadcast raw to {}@{}: {}. Removing.", u, e2.getKey(), ioe.getMessage());
                    try { e2.getValue().close(); } catch (Exception ignored) {}
                    ipMap.remove(e2.getKey());
                }
            }
            if (ipMap.isEmpty()) clients.remove(u);
        }
    }

    /**
     * Retorna una lista (nueva instancia) con los usuarios actualmente registrados.
     */
    public List<String> getConnectedUsers() {
        return new ArrayList<>(clients.keySet());
    }

    /**
     * Return a map username -> list of active IPs for that user.
     */
    public java.util.Map<String, java.util.List<String>> getUserSessions() {
        var out = new java.util.HashMap<String, java.util.List<String>>();
        for (var e : clients.entrySet()) {
            out.put(e.getKey(), new java.util.ArrayList<>(e.getValue().keySet()));
        }
        return out;
    }
}
