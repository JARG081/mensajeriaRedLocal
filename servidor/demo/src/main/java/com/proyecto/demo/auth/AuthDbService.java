package com.proyecto.demo.auth;

import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class AuthDbService implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthDbService.class);

    private final UserDao userDao;

    public AuthDbService(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public synchronized boolean register(String id, String usuario, String password) throws Exception {
        if (usuario == null || password == null) return false;
        var existing = userDao.findByUsername(usuario);
        if (existing.isPresent()) return false;
        String hash = DigestUtils.sha256Hex(password);
        Long lid = null;
        try {
            if (id != null && !id.isBlank()) lid = Long.parseLong(id);
        } catch (NumberFormatException nfe) {
            log.warn("ID de registro no numérico: {}", id);
            return false;
        }
        boolean ok = userDao.createUser(lid, usuario, hash);
        if (ok) log.info("Usuario '{}' (id={}) registrado en DB", usuario, lid);
        else log.warn("Fallo al insertar usuario '{}' en DB", usuario);
        return ok;
    }

    @Override
    public synchronized boolean login(String usuario, String password) throws Exception {
        if (usuario == null || password == null) return false;
        var existing = userDao.findByUsername(usuario);
        if (existing.isEmpty()) {
            log.warn("Login intento para usuario desconocido: {}", usuario);
            return false;
        }
        String provided = DigestUtils.sha256Hex(password);
        String stored = existing.get().getPasswordHash();
        if (stored != null) stored = stored.trim();
        if (provided != null) provided = provided.trim();
        // Log only safe metadata (do not log full hashes or passwords)
        try {
            String provPrefix = provided.length() > 8 ? provided.substring(0, 8) : provided;
            int storedLen = stored == null ? 0 : stored.length();
            log.debug("Auth attempt user='{}' providedHashPrefix='{}' storedHashLen={}", usuario, provPrefix, storedLen);
        } catch (Exception ignored) {}
        // Compare case-insensitively to avoid hex-case mismatches
        boolean ok = (provided == null && stored == null) || (provided != null && stored != null && provided.equalsIgnoreCase(stored));
        if (ok) log.info("Login exitoso para usuario='{}'", usuario);
        else log.warn("Login fallido por hash mismatch para usuario='{}'", usuario);
        return ok;
    }
}
