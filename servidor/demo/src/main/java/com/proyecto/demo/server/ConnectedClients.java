package com.proyecto.demo.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/*
 * Registro simple de usuarios autenticados conectados.
 * Usado por cada ClientWorker para publicar la lista a todos los clientes.
 */
public class ConnectedClients {
    private static final Logger log = LoggerFactory.getLogger(ConnectedClients.class);

    private static final ConcurrentHashMap<String, BufferedWriter> clients = new ConcurrentHashMap<>();

    public static void register(String user, BufferedWriter out) {
        if (user == null || out == null) return;
        clients.put(user, out);
        log.info("Usuario registrado para broadcast: {} (total={})", user, clients.size());
        broadcastUserList();
    }

    public static void unregister(String user) {
        if (user == null) return;
        clients.remove(user);
        log.info("Usuario removido del registro: {} (total={})", user, clients.size());
        broadcastUserList();
    }

    public static void broadcastUserList() {
        try {
            List<String> users = new ArrayList<>(clients.keySet()).stream().sorted().collect(Collectors.toList());
            String csv = String.join(",", users);
            String line = "USERS " + csv + "\n";
            log.info("Broadcasting USERS list to {} clients: {}", clients.size(), csv);

            // iterate over a snapshot of entries to avoid concurrent modification issues
            for (var entry : new ArrayList<>(clients.entrySet())) {
                String u = entry.getKey();
                BufferedWriter w = entry.getValue();
                try {
                    w.write(line);
                    w.flush();
                } catch (IOException ioe) {
                    log.warn("Failed to send USERS to {}: {}. Removing.", u, ioe.getMessage());
                    try { w.close(); } catch (Exception ignored) {}
                    clients.remove(u);
                }
            }
        } catch (Exception e) {
            log.error("Error broadcasting user list: {}", e.toString(), e);
        }
    }

    public static boolean sendTo(String user, String line) {
        if (user == null || line == null) return false;
        BufferedWriter w = clients.get(user);
        if (w == null) return false;
        try {
            w.write(line);
            w.flush();
            return true;
        } catch (IOException e) {
            log.warn("Failed to send message to {}: {}. Removing.", user, e.getMessage());
            try { w.close(); } catch (Exception ignored) {}
            clients.remove(user);
            return false;
        }
    }

    public static void broadcastMessage(String sender, String text) {
        String payload = "MSGFROM " + sender + "|" + (text == null ? "" : text) + "\n";
        for (var entry : new ArrayList<>(clients.entrySet())) {
            try {
                entry.getValue().write(payload);
                entry.getValue().flush();
            } catch (IOException ioe) {
                log.warn("Failed to broadcast MSG to {}: {}. Removing.", entry.getKey(), ioe.getMessage());
                try { entry.getValue().close(); } catch (Exception ignored) {}
                clients.remove(entry.getKey());
            }
        }
    }

    public static void broadcastRaw(String line) {
        if (line == null) return;
        String payload = line.endsWith("\n") ? line : line + "\n";
        for (var entry : new ArrayList<>(clients.entrySet())) {
            try {
                entry.getValue().write(payload);
                entry.getValue().flush();
            } catch (IOException ioe) {
                log.warn("Failed to broadcast raw to {}: {}. Removing.", entry.getKey(), ioe.getMessage());
                try { entry.getValue().close(); } catch (Exception ignored) {}
                clients.remove(entry.getKey());
            }
        }
    }
}
