package com.proyecto.demo.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import jakarta.annotation.PostConstruct;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.sql.ResultSetMetaData;

@Repository
public class JdbcUserDao implements UserDao {

    private static final Logger log = LoggerFactory.getLogger(JdbcUserDao.class);

    private final JdbcTemplate jdbc;

    public JdbcUserDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void ensureTable() {
        // Create table if not exists using generated id (AUTO_INCREMENT)
        jdbc.execute("CREATE TABLE IF NOT EXISTS usuarios (" +
            "id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT, " +
            "nombre VARCHAR(100) NOT NULL UNIQUE, " +
            "contrasena_hash VARCHAR(512) NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    }

    private final RowMapper<UserDto> mapper = new RowMapper<>() {
        @Override
        public UserDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            UserDto u = new UserDto();
            u.setId(rs.getLong("id"));
            // attempt to read either 'nombre' or 'nombre_usuario'
            String user = null;
            try { user = rs.getString("nombre"); } catch (Exception ignored) {}
            if (user == null) {
                try { user = rs.getString("nombre_usuario"); } catch (Exception ignored) {}
            }
            u.setUsername(user);
            u.setPasswordHash(rs.getString("contrasena_hash"));
            return u;
        }
    };

    // detected username column in the DB: either 'nombre' or 'nombre_usuario'
    private volatile String usernameColumn = null;

    private synchronized void detectUsernameColumn() {
        if (usernameColumn != null) return;
        try {
            jdbc.query(conn -> conn.prepareStatement("SELECT * FROM usuarios LIMIT 1"), (ResultSet rs) -> {
                ResultSetMetaData md = rs.getMetaData();
                int cols = md.getColumnCount();
                for (int i = 1; i <= cols; i++) {
                    String col = md.getColumnName(i);
                    if ("nombre".equalsIgnoreCase(col)) { usernameColumn = "nombre"; return null; }
                    if ("nombre_usuario".equalsIgnoreCase(col)) { usernameColumn = "nombre_usuario"; return null; }
                }
                return null;
            });
        } catch (Exception e) {
            // ignore - we'll fallback to 'nombre'
        }
        if (usernameColumn == null) usernameColumn = "nombre";
    }

    @Override
    public Optional<UserDto> findByUsername(String username) {
        detectUsernameColumn();
        String col = usernameColumn;
        String sql = "SELECT id, " + col + " as uname, contrasena_hash FROM usuarios WHERE " + col + " = ?";
        try {
            log.debug("JdbcUserDao.findByUsername: buscando usuario='{}' usando columna='{}'", username, col);
            UserDto u = jdbc.queryForObject(sql, new Object[]{username}, (rs, rowNum) -> {
                UserDto ud = new UserDto();
                ud.setId(rs.getLong("id"));
                // read the aliased username column
                ud.setUsername(rs.getString("uname"));
                ud.setPasswordHash(rs.getString("contrasena_hash"));
                return ud;
            });
            log.debug("JdbcUserDao.findByUsername: encontrado id={} usuario='{}'", u.getId(), u.getUsername());
            return Optional.ofNullable(u);
        } catch (Exception e) {
            log.warn("JdbcUserDao.findByUsername: fallo buscando usuario='{}' usando '{}' - {}", username, col, e.getMessage());
            // if first attempt used 'nombre' and failed due to bad SQL, try the alternative column once
            if ("nombre".equalsIgnoreCase(col)) {
                try {
                    String alt = "nombre_usuario";
                    String sql2 = "SELECT id, " + alt + " as uname, contrasena_hash FROM usuarios WHERE " + alt + " = ?";
                    log.debug("JdbcUserDao.findByUsername: intentando alternativa usando='{}'", alt);
                    UserDto u2 = jdbc.queryForObject(sql2, new Object[]{username}, (rs, rowNum) -> {
                        UserDto ud = new UserDto();
                        ud.setId(rs.getLong("id"));
                        ud.setUsername(rs.getString("uname"));
                        ud.setPasswordHash(rs.getString("contrasena_hash"));
                        return ud;
                    });
                    usernameColumn = alt;
                    log.debug("JdbcUserDao.findByUsername: alternativa exitosa, detected usernameColumn='{}'", alt);
                    return Optional.ofNullable(u2);
                } catch (Exception ex2) {
                    log.warn("JdbcUserDao.findByUsername: alternativa fallida para usuario='{}' - {}", username, ex2.getMessage());
                }
            }
            return Optional.empty();
        }
    }
    
    @Override
    public Optional<UserDto> findById(Long id) {
        detectUsernameColumn();
        String col = usernameColumn;
        String sql = "SELECT id, " + col + " as uname, contrasena_hash FROM usuarios WHERE id = ?";
        try {
            UserDto u = jdbc.queryForObject(sql, new Object[]{id}, (rs, rowNum) -> {
                UserDto ud = new UserDto();
                ud.setId(rs.getLong("id"));
                ud.setUsername(rs.getString("uname"));
                ud.setPasswordHash(rs.getString("contrasena_hash"));
                return ud;
            });
            return Optional.ofNullable(u);
        } catch (Exception e) {
            log.warn("JdbcUserDao.findById: fallo buscando id='{}' usando '{}' - {}", id, col, e.getMessage());
            if ("nombre".equalsIgnoreCase(col)) {
                try {
                    String alt = "nombre_usuario";
                    String sql2 = "SELECT id, " + alt + " as uname, contrasena_hash FROM usuarios WHERE id = ?";
                    UserDto u2 = jdbc.queryForObject(sql2, new Object[]{id}, (rs, rowNum) -> {
                        UserDto ud = new UserDto();
                        ud.setId(rs.getLong("id"));
                        ud.setUsername(rs.getString("uname"));
                        ud.setPasswordHash(rs.getString("contrasena_hash"));
                        return ud;
                    });
                    usernameColumn = alt;
                    return Optional.ofNullable(u2);
                } catch (Exception ex2) {
                    log.warn("JdbcUserDao.findById: alternativa fallida para id='{}' - {}", id, ex2.getMessage());
                }
            }
            return Optional.empty();
        }
    }

    @Override
    public boolean createUser(Long id, String username, String passwordHash) {
        try {
            detectUsernameColumn();
            String col = usernameColumn == null ? "nombre" : usernameColumn;
            if (id == null) {
                // Let DB assign AUTO_INCREMENT id and return success if a key was generated
                KeyHolder keyHolder = new GeneratedKeyHolder();
                String sql = "INSERT INTO usuarios (" + col + ", contrasena_hash) VALUES (?, ?)";
                jdbc.update(conn -> {
                    PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    ps.setString(1, username);
                    ps.setString(2, passwordHash);
                    return ps;
                }, keyHolder);
                Number key = keyHolder.getKey();
                return key != null;
            } else {
                String sql = "INSERT INTO usuarios (id, " + col + ", contrasena_hash) VALUES (?, ?, ?)";
                int updated = jdbc.update(sql, id, username, passwordHash);
                return updated == 1;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
