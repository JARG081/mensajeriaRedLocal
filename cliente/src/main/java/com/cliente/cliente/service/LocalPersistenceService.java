package com.cliente.cliente.service;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.util.Set;

@Component
public class LocalPersistenceService {
    private static final Logger log = LoggerFactory.getLogger(LocalPersistenceService.class);
    private static final Path LOG = Paths.get("cliente-mensajes.log");

    public synchronized void appendMessage(String line){
        try {
            Files.writeString(LOG, LocalDateTime.now() + " " + line + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            // Intentar aplicar permisos POSIX restrictivos si el FS lo soporta
            try {
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
                Files.setPosixFilePermissions(LOG, perms);
            } catch (UnsupportedOperationException ignored) {
                // Filesystem no soporta POSIX permissions (Windows, etc). Ignorar.
            } catch (IOException e) {
                log.debug("No se pudieron establecer permisos POSIX en el log: {}", e.toString());
            }
        } catch (IOException e) {
            log.error("Persist error: {}", e.toString(), e);
        }
    }

    /**
     * Lee todas las líneas del fichero de persistencia local y las devuelve.
     * Si el fichero no existe, devuelve una lista vacía.
     */
    public synchronized java.util.List<String> readAllMessages() {
        try {
            if (!Files.exists(LOG)) return java.util.Collections.emptyList();
            return Files.readAllLines(LOG);
        } catch (IOException e) {
            log.error("Read history error: {}", e.toString(), e);
            return java.util.Collections.emptyList();
        }
    }
}
