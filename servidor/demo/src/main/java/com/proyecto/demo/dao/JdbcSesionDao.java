package com.proyecto.demo.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Component
public class JdbcSesionDao {
    private final JdbcTemplate jdbc;

    public JdbcSesionDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        ensureTable();
    }

    private void ensureTable() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS sesiones (" +
            "id CHAR(36) NOT NULL PRIMARY KEY, " +
            "usuario_id BIGINT NOT NULL, " +
            "token VARCHAR(1024), " +
            "ip VARCHAR(45) NOT NULL, " +
            "fecha_inicio DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), " +
            "fecha_fin DATETIME(6), " +
            "estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVA', " +
            "INDEX idx_sesiones_usuario_ip (usuario_id, ip)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    }

    public String createSession(long usuarioId, String ip, String token) {
        String id = UUID.randomUUID().toString();
        String sql = "INSERT INTO sesiones (id,usuario_id,token,ip) VALUES (?,?,?,?)";
        jdbc.update(sql, id, usuarioId, token, ip);
        return id;
    }

    public void closeSession(String sessionId, Timestamp desconectadoEn, String estado) {
        if (sessionId == null) return;
        String sql = "UPDATE sesiones SET fecha_fin = ?, estado = ? WHERE id = ?";
        jdbc.update(sql, desconectadoEn, estado, sessionId);
    }

    public List<SessionInfo> findActiveSessionsByUser(long usuarioId) {
        return jdbc.query("SELECT id,usuario_id,token,ip,fecha_inicio,fecha_fin,estado FROM sesiones WHERE usuario_id = ? ORDER BY fecha_inicio DESC",
                new Object[]{usuarioId}, (rs, rn) -> {
                    SessionInfo s = new SessionInfo();
                    s.id = rs.getString("id");
                    s.usuarioId = rs.getLong("usuario_id");
                    s.token = rs.getString("token");
                    s.direccionIp = rs.getString("ip");
                    s.creadoEn = rs.getTimestamp("fecha_inicio");
                    s.desconectadoEn = rs.getTimestamp("fecha_fin");
                    s.estado = rs.getString("estado");
                    return s;
                });
    }

    public static class SessionInfo {
        public String id;
        public long usuarioId;
        public String token;
        public String direccionIp;
        public Timestamp creadoEn;
        public Timestamp desconectadoEn;
        public String estado;
    }
}
