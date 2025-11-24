package com.proyecto.webmvc.dao;

import com.proyecto.webmvc.model.Mensaje;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class MensajeDao {
    private final JdbcTemplate jdbc;

    public MensajeDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Mensaje> findByEmisorAndTipo(Long emisorId, String tipo) {
        String sql = "SELECT m.id, m.emisor_id, m.receptor_id, m.tipo, m.contenido, m.archivo_id, m.creado_en, a.nombre AS archivo_nombre " +
                "FROM mensajes m LEFT JOIN archivos a ON a.id = m.archivo_id " +
                "WHERE m.emisor_id = ? AND m.tipo = ? ORDER BY m.creado_en DESC LIMIT 500";
        return jdbc.query(sql, new Object[]{emisorId, tipo}, (rs, i) -> {
            Mensaje m = new Mensaje();
            m.setId(rs.getLong("id"));
            m.setEmisorId(rs.getLong("emisor_id"));
            m.setReceptorId(rs.getLong("receptor_id"));
            m.setTipoMensaje(rs.getString("tipo"));
            m.setContenido(rs.getString("contenido"));
            m.setArchivoId(rs.getObject("archivo_id") == null ? null : rs.getLong("archivo_id"));
            try { m.setArchivoNombre(rs.getString("archivo_nombre")); } catch (Exception e) { m.setArchivoNombre(null); }
            Timestamp t = rs.getTimestamp("creado_en");
            if (t != null) m.setCreadoEn(t.toLocalDateTime());
            return m;
        });
    }

    public java.util.Map<String,Object> topSender() {
        String sql = "SELECT emisor_id, COUNT(*) AS cnt FROM mensajes GROUP BY emisor_id ORDER BY cnt DESC LIMIT 1";
        return jdbc.queryForList(sql).stream().findFirst().orElse(null);
    }

    public Mensaje findById(Long id) {
        String sql = "SELECT m.id, m.emisor_id, m.receptor_id, m.tipo, m.contenido, m.archivo_id, m.creado_en, a.nombre AS archivo_nombre " +
                "FROM mensajes m LEFT JOIN archivos a ON a.id = m.archivo_id WHERE m.id = ?";
        return jdbc.queryForObject(sql, new Object[]{id}, (rs, i) -> {
            Mensaje m = new Mensaje();
            m.setId(rs.getLong("id"));
            m.setEmisorId(rs.getLong("emisor_id"));
            m.setReceptorId(rs.getLong("receptor_id"));
            m.setTipoMensaje(rs.getString("tipo"));
            m.setContenido(rs.getString("contenido"));
            m.setArchivoId(rs.getObject("archivo_id") == null ? null : rs.getLong("archivo_id"));
            try { m.setArchivoNombre(rs.getString("archivo_nombre")); } catch (Exception e) { m.setArchivoNombre(null); }
            Timestamp t = rs.getTimestamp("creado_en");
            if (t != null) m.setCreadoEn(t.toLocalDateTime());
            return m;
        });
    }
}
