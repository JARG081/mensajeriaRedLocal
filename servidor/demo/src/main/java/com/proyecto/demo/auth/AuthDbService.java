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
        if (existing.isEmpty()) return false;
        String provided = DigestUtils.sha256Hex(password);
        return provided.equals(existing.get().getPasswordHash());
    }
}
