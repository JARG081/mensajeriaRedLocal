package com.proyecto.demo.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import jakarta.annotation.PostConstruct;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Repository
public class JdbcUserDao implements UserDao {

    private final JdbcTemplate jdbc;

    public JdbcUserDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void ensureTable() {
        // Create table if not exists using explicit id (no AUTO_INCREMENT)
        jdbc.execute("CREATE TABLE IF NOT EXISTS usuarios (" +
                "id BIGINT NOT NULL PRIMARY KEY, " +
                "nombre_usuario VARCHAR(100) NOT NULL UNIQUE, " +
                "contrasena_hash VARCHAR(512) NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8");
    }

    private final RowMapper<UserDto> mapper = new RowMapper<>() {
        @Override
        public UserDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            UserDto u = new UserDto();
            u.setId(rs.getLong("id"));
            u.setUsername(rs.getString("nombre_usuario"));
            u.setPasswordHash(rs.getString("contrasena_hash"));
            return u;
        }
    };

    @Override
    public Optional<UserDto> findByUsername(String username) {
        try {
            UserDto u = jdbc.queryForObject("SELECT id, nombre_usuario, contrasena_hash FROM usuarios WHERE nombre_usuario = ?", new Object[]{username}, mapper);
            return Optional.ofNullable(u);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean createUser(Long id, String username, String passwordHash) {
        if (id == null) return false; // we do not allow null id in this schema
        try {
            int updated = jdbc.update("INSERT INTO usuarios (id, nombre_usuario, contrasena_hash) VALUES (?, ?, ?)", id, username, passwordHash);
            return updated == 1;
        } catch (Exception e) {
            return false;
        }
    }
}
