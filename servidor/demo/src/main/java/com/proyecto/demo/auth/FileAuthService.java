package com.proyecto.demo.auth;

import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.io.*;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FileAuthService implements AuthService {
    private static final Logger log = LoggerFactory.getLogger(FileAuthService.class);

    @Value("${auth.users.file:usuarios.txt}")
    private String usersFile;

    private final Map<String,String> users = new ConcurrentHashMap<>();

    public FileAuthService() {
        // constructor vacío, carga se hace en init
    }

    @PostConstruct
    private void init() {
        try {
            log.info("Iniciando servicio de autenticación. Archivo usuarios configurado en '{}'", usersFile);
            loadUsers();
            log.info("Carga de usuarios completada. Usuarios cargados: {}", users.size());
        } catch (IOException e) {
            log.error("No se pudo cargar usuarios desde '{}': {}", usersFile, e.toString(), e);
        }
    }

    private synchronized void loadUsers() throws IOException {
        Path p = Paths.get(usersFile);
        if (Files.notExists(p)) {
            Files.createFile(p);
            log.warn("Archivo de usuarios no existía. Se creó archivo vacío en: {}", p.toAbsolutePath());
            return;
        }
        // Volver a cargar todo el archivo para reflejar cualquier cambio externo
        users.clear();
        try (BufferedReader r = Files.newBufferedReader(p)) {
            String line;
            int count = 0;
            while ((line = r.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    users.put(parts[0], parts[1]);
                    count++;
                } else {
                    log.warn("Línea inválida en archivo de usuarios (omitida): {}", line);
                }
            }
            log.info("Lectura de archivo '{}' finalizada. {} registros leídos.", p.toAbsolutePath(), count);
        }
    }

    private synchronized void persistUser(String usuario, String hash) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(Paths.get(usersFile), StandardOpenOption.APPEND)) {
            w.write(usuario + ":" + hash);
            w.newLine();
            w.flush();
        }
        users.put(usuario, hash);
        log.info("Usuario '{}' persistido en '{}'. Total usuarios ahora: {}", usuario, Paths.get(usersFile).toAbsolutePath(), users.size());
    }

    @Override
    public synchronized boolean register(String usuario, String password) throws Exception {
        if (users.containsKey(usuario)) return false;
        String hash = DigestUtils.sha256Hex(password);
        persistUser(usuario, hash);
        log.info("Alerta: nuevo usuario registrado -> {}", usuario);
        return true;
    }

    @Override
    public synchronized boolean login(String usuario, String password) throws Exception {
        try {
            // Antes de intentar login, recargar usuarios desde el archivo para captar nuevas entradas
            loadUsers();
        } catch (IOException ioe) {
            log.warn("No se pudo recargar archivo de usuarios antes del login: {}. Usando cache en memoria.", ioe.getMessage());
        }

        String hash = users.get(usuario);
        if (hash == null) return false;
        String provided = DigestUtils.sha256Hex(password);
        return hash.equals(provided);
    }
}
