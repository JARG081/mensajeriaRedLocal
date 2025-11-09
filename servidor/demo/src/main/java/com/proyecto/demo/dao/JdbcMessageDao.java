package com.proyecto.demo.dao;

import com.proyecto.demo.model.MessageRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class JdbcMessageDao implements MessageDao {

    private final JdbcTemplate jdbc;

    public JdbcMessageDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        ensureTables();
    }

    @Override
    public void ensureTables() {
        // create archivos table (referenced by mensajes)
        jdbc.execute("CREATE TABLE IF NOT EXISTS archivos (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "filename VARCHAR(512) NOT NULL, " +
                "path VARCHAR(1024), " +
                "size BIGINT, " +
                "creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        // create mensajes table according to the provided DDL
        jdbc.execute("CREATE TABLE IF NOT EXISTS mensajes (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "emisor_id BIGINT NOT NULL, " +
                "receptor_id BIGINT NOT NULL, " +
                "tipo_mensaje ENUM('TEXTO','ARCHIVO') NOT NULL DEFAULT 'TEXTO', " +
                "contenido TEXT, " +
                "archivo_id BIGINT, " +
                "creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), " +
                "INDEX idx_mensajes_emisor_receptor_creado (emisor_id, receptor_id, creado_en), " +
                "INDEX idx_mensajes_receptor_creado (receptor_id, creado_en), " +
                "CONSTRAINT fk_mensajes_emisor FOREIGN KEY (emisor_id) REFERENCES usuarios(id) ON DELETE CASCADE, " +
                "CONSTRAINT fk_mensajes_receptor FOREIGN KEY (receptor_id) REFERENCES usuarios(id) ON DELETE CASCADE, " +
                "CONSTRAINT fk_mensajes_archivo FOREIGN KEY (archivo_id) REFERENCES archivos(id) ON DELETE SET NULL" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    }

    private final RowMapper<MessageRecord> mapper = new RowMapper<>() {
        @Override
        public MessageRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            MessageRecord m = new MessageRecord();
            m.setId(rs.getLong("id"));
            m.setEmisorId(rs.getLong("emisor_id"));
            m.setReceptorId(rs.getLong("receptor_id"));
            m.setTipoMensaje(rs.getString("tipo_mensaje"));
            m.setContenido(rs.getString("contenido"));
            long a = rs.getLong("archivo_id");
            if (rs.wasNull()) m.setArchivoId(null); else m.setArchivoId(a);
            java.sql.Timestamp ts = rs.getTimestamp("creado_en");
            if (ts != null) m.setCreadoEn(ts.toLocalDateTime());
            return m;
        }
    };

    @Override
    public long insertMessage(MessageRecord m) {
        String sql = "INSERT INTO mensajes (emisor_id,receptor_id,tipo_mensaje,contenido,archivo_id) VALUES (?,?,?,?,?)";
        Object[] params = new Object[]{m.getEmisorId(), m.getReceptorId(), m.getTipoMensaje(), m.getContenido(), m.getArchivoId()};
        int[] types = new int[]{Types.BIGINT, Types.BIGINT, Types.VARCHAR, Types.CLOB, Types.BIGINT};
        var keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        org.springframework.jdbc.core.PreparedStatementCreator psc = conn -> {
            var ps = conn.prepareStatement(sql, new String[]{"id"});
            ps.setLong(1, m.getEmisorId());
            ps.setLong(2, m.getReceptorId());
            ps.setString(3, m.getTipoMensaje());
            if (m.getContenido() != null) ps.setString(4, m.getContenido()); else ps.setNull(4, Types.CLOB);
            if (m.getArchivoId() != null) ps.setLong(5, m.getArchivoId()); else ps.setNull(5, Types.BIGINT);
            return ps;
        };
        jdbc.update(psc, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? -1L : key.longValue();
    }

    @Override
    public List<MessageRecord> findBetweenUsers(long userAId, long userBId, int limit) {
        String sql = "SELECT m.* FROM mensajes m WHERE (m.emisor_id = ? AND m.receptor_id = ?) OR (m.emisor_id = ? AND m.receptor_id = ?) ORDER BY m.creado_en ASC LIMIT ?";
        return jdbc.query(sql, new Object[]{userAId, userBId, userBId, userAId, limit}, mapper);
    }
}
