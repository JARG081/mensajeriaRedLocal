package com.proyecto.demo.auth;

import java.util.Optional;

public interface UserDao {
    Optional<UserDto> findByUsername(String username);
    /**
     * Crea un usuario con un id opcional (si id es null se debe fallar en este proyecto porque no queremos auto_increment).
     */
    boolean createUser(Long id, String username, String passwordHash);
}
