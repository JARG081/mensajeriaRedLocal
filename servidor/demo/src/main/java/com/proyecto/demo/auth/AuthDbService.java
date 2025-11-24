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
    public synchronized boolean login(String id, String usuario, String password) throws Exception {
        if (id == null || usuario == null || password == null) return false;
        Long lid = null;
        try { lid = Long.parseLong(id); } catch (NumberFormatException nfe) { log.warn("Login intent with non-numeric id: {}", id); return false; }

        // find by id and verify username + password hash match exactly
        var existing = userDao.findById(lid);
        if (existing.isEmpty()) {
            log.warn("Login intento para id desconocido: {}", lid);
            return false;
        }
        var u = existing.get();
        String dbUser = u.getUsername();
        String stored = u.getPasswordHash();
        if (dbUser == null || !dbUser.equals(usuario)) {
            log.warn("Login fallo: usuario proporcionado no coincide con BD (proporcionado='{}' vs bd='{}') for id={}", usuario, dbUser, lid);
            return false;
        }
        String provided = DigestUtils.sha256Hex(password);
        if (stored != null) stored = stored.trim();
        if (provided != null) provided = provided.trim();
        boolean ok = (provided == null && stored == null) || (provided != null && stored != null && provided.equalsIgnoreCase(stored));
        if (ok) log.info("Login exitoso para usuario='{}' id={}", usuario, lid);
        else log.warn("Login fallido por hash mismatch para usuario='{}' id={}", usuario, lid);
        return ok;
    }
}
