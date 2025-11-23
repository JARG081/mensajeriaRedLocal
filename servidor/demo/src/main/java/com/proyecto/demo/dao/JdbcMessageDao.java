package com.proyecto.demo.dao;

import com.proyecto.demo.model.MessageRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

@Component
public class JdbcMessageDao implements MessageDao {

    private final JdbcTemplate jdbc;
    private static final Logger log = LoggerFactory.getLogger(JdbcMessageDao.class);

    public JdbcMessageDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.ensureTables();
        // detect runtime column names (best-effort)
        try { detectColumns(); } catch (Exception ignored) {}
    }

    @Override
    public void ensureTables() {
        // create sesiones table to track user sessions (UUID primary key)
        jdbc.execute("CREATE TABLE IF NOT EXISTS sesiones (" +
            "id CHAR(36) NOT NULL PRIMARY KEY, " +
            "usuario_id BIGINT NOT NULL, " +
            "token VARCHAR(1024), " +
            "ip VARCHAR(45) NOT NULL, " +
            "fecha_inicio DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), " +
            "fecha_fin DATETIME(6), " +
            "estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVA', " +
            "INDEX idx_sesiones_usuario_ip (usuario_id, ip), " +
            "CONSTRAINT fk_sesiones_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

        // create archivos table (referenced by mensajes)
        jdbc.execute("CREATE TABLE IF NOT EXISTS archivos (" +
            "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
            "propietario_id BIGINT, " +
            "nombre VARCHAR(1000) NOT NULL, " +
            "ruta VARCHAR(2000) NOT NULL, " +
            "tipo_mime VARCHAR(255), " +
            "tamano BIGINT, " +
            "creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), " +
            "INDEX idx_archivos_propietario (propietario_id), " +
            "CONSTRAINT fk_archivos_propietario FOREIGN KEY (propietario_id) REFERENCES usuarios(id) ON DELETE SET NULL" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

    // create mensajes table according to the provided DDL (includes sesion_id FK)
    jdbc.execute("CREATE TABLE IF NOT EXISTS mensajes (" +
        "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
        "emisor_id BIGINT NOT NULL, " +
        "receptor_id BIGINT NOT NULL, " +
        "tipo ENUM('TEXTO','ARCHIVO') NOT NULL DEFAULT 'TEXTO', " +
        "contenido TEXT, " +
        "archivo_id BIGINT, " +
        "sesion_id CHAR(36), " +
        "creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), " +
        "INDEX idx_mensajes_emisor_receptor_creado (emisor_id, receptor_id, creado_en), " +
        "INDEX idx_mensajes_receptor_creado (receptor_id, creado_en), " +
        "INDEX idx_mensajes_sesion (sesion_id), " +
        "CONSTRAINT fk_mensajes_emisor FOREIGN KEY (emisor_id) REFERENCES usuarios(id) ON DELETE CASCADE, " +
        "CONSTRAINT fk_mensajes_receptor FOREIGN KEY (receptor_id) REFERENCES usuarios(id) ON DELETE CASCADE, " +
        "CONSTRAINT fk_mensajes_archivo FOREIGN KEY (archivo_id) REFERENCES archivos(id) ON DELETE SET NULL, " +
        "CONSTRAINT fk_mensajes_sesion FOREIGN KEY (sesion_id) REFERENCES sesiones(id) ON DELETE SET NULL" +
        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

        // If the mensajes table existed before we added sesion_id, try to add the column now
        try {
            // MySQL supports ADD COLUMN IF NOT EXISTS in recent versions
            jdbc.execute("ALTER TABLE mensajes ADD COLUMN IF NOT EXISTS sesion_id CHAR(36) NULL");
        } catch (Exception ex) {
            log.debug("No se pudo ejecutar ALTER TABLE para agregar 'sesion_id' (posiblemente versión antigua de MySQL). Continuando: {}", ex.getMessage());
            try {
                // As a fallback, check if column exists and if not, attempt to add without IF NOT EXISTS
                var md = jdbc.getDataSource().getConnection().getMetaData();
                try (var rs = md.getColumns(null, null, "mensajes", "sesion_id")) {
                    if (!rs.next()) {
                        try { jdbc.execute("ALTER TABLE mensajes ADD COLUMN sesion_id CHAR(36) NULL"); }
                        catch (Exception e2) { log.debug("ALTER TABLE ADD COLUMN sesion_id falló: {}", e2.getMessage()); }
                    }
                }
            } catch (Exception ignore) {
                log.debug("No se pudo comprobar columnas de la tabla mensajes: {}", ignore.getMessage());
            }
        }
    }

    private final RowMapper<MessageRecord> mapper = new RowMapper<>() {
        @Override
        public MessageRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            MessageRecord m = new MessageRecord();
            m.setId(rs.getLong("id"));
            m.setEmisorId(rs.getLong("emisor_id"));
            m.setReceptorId(rs.getLong("receptor_id"));
            // Tipo column may be 'tipo' or 'tipo_mensaje'
            String tipoVal = null;
            try { tipoVal = rs.getString(tipoColumn); } catch (Exception ignore) { tipoVal = rs.getString("tipo"); }
            m.setTipoMensaje(tipoVal);
            m.setContenido(rs.getString("contenido"));
            long a = rs.getLong("archivo_id");
            if (rs.wasNull()) m.setArchivoId(null); else m.setArchivoId(a);
            // sesion_id might not exist in older schemas
            try {
                String sid = rs.getString("sesion_id");
                if (sid != null) m.setSesionId(sid);
            } catch (Exception ignore) {}
            java.sql.Timestamp ts = rs.getTimestamp("creado_en");
            if (ts != null) m.setCreadoEn(ts.toLocalDateTime());
            return m;
        }
    };

    // detected runtime metadata
    private volatile String tipoColumn = "tipo";
    private volatile boolean hasSesionColumn = true;

    private void detectColumns() {
        try {
            var md = jdbc.getDataSource().getConnection().getMetaData();
            try (var rs = md.getColumns(null, null, "mensajes", null)) {
                boolean foundTipo = false;
                boolean foundSesion = false;
                while (rs.next()) {
                    String col = rs.getString("COLUMN_NAME");
                    if (col == null) continue;
                    if ("tipo_mensaje".equalsIgnoreCase(col)) { tipoColumn = "tipo_mensaje"; foundTipo = true; }
                    if ("tipo".equalsIgnoreCase(col)) { if (!foundTipo) tipoColumn = "tipo"; foundTipo = true; }
                    if ("sesion_id".equalsIgnoreCase(col)) { foundSesion = true; }
                }
                hasSesionColumn = foundSesion;
            }
        } catch (Exception e) {
            // default values already set
            log.debug("detectColumns mensajes failed: {}", e.getMessage());
        }
    }

    @Override
    public long insertMessage(MessageRecord m) {
        String sql;
        if (hasSesionColumn) {
            sql = "INSERT INTO mensajes (emisor_id,receptor_id," + tipoColumn + ",contenido,archivo_id,sesion_id) VALUES (?,?,?,?,?,?)";
        } else {
            sql = "INSERT INTO mensajes (emisor_id,receptor_id," + tipoColumn + ",contenido,archivo_id) VALUES (?,?,?,?,?)";
        }
        var keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        org.springframework.jdbc.core.PreparedStatementCreator psc = conn -> {
            var ps = conn.prepareStatement(sql, new String[]{"id"});
            int idx = 1;
            ps.setLong(idx++, m.getEmisorId());
            ps.setLong(idx++, m.getReceptorId());
            ps.setString(idx++, m.getTipoMensaje() == null ? "TEXTO" : m.getTipoMensaje());
            if (m.getContenido() != null) ps.setString(idx++, m.getContenido()); else ps.setNull(idx++, Types.VARCHAR);
            if (m.getArchivoId() != null) ps.setLong(idx++, m.getArchivoId()); else ps.setNull(idx++, Types.BIGINT);
            if (hasSesionColumn) {
                if (m.getSesionId() != null) ps.setString(idx++, m.getSesionId()); else ps.setNull(idx++, Types.VARCHAR);
            }
            return ps;
        };
        try {
            log.debug("Executing SQL: {} with emisorId={}, receptorId={}, tipo={}, sesionId={}", sql, m.getEmisorId(), m.getReceptorId(), m.getTipoMensaje(), m.getSesionId());
            jdbc.update(psc, keyHolder);
            Number key = keyHolder.getKey();
            return key == null ? -1L : key.longValue();
        } catch (DataAccessException dae) {
            log.error("Error inserting message into DB (emisorId={}, receptorId={}, sesionId={})", m.getEmisorId(), m.getReceptorId(), m.getSesionId(), dae);
            return -1L;
        }
    }

    @Override
    public List<MessageRecord> findBetweenUsers(long userAId, long userBId, int limit) {
        String sql = "SELECT m.* FROM mensajes m WHERE (m.emisor_id = ? AND m.receptor_id = ?) OR (m.emisor_id = ? AND m.receptor_id = ?) ORDER BY m.creado_en ASC LIMIT ?";
        return jdbc.query(sql, new Object[]{userAId, userBId, userBId, userAId, limit}, mapper);
    }

    @Override
    public List<MessageRecord> findForUser(long userId, int limit) {
        String sql = "SELECT m.* FROM mensajes m WHERE m.emisor_id = ? OR m.receptor_id = ? ORDER BY m.creado_en ASC LIMIT ?";
        return jdbc.query(sql, new Object[]{userId, userId, limit}, mapper);
    }
}
