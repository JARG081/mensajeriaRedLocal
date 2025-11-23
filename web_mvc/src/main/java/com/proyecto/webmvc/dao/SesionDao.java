package com.proyecto.webmvc.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class SesionDao {
    private final JdbcTemplate jdbc;

    public SesionDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String,Object>> findConnectedSessions() {
        String[] fechaInicioCandidates = new String[]{"fecha_inicio", "creado_en", "creadoEn"};
        String[] fechaFinCandidates = new String[]{"fecha_fin", "desconectado_en", "fechaFin"};
        String[] ipCandidates = new String[]{"ip", "direccion_ip", "direccionIp"};
        for (String ipCol : ipCandidates) {
            for (String fi : fechaInicioCandidates) {
                for (String ff : fechaFinCandidates) {
                    String sql = String.format("SELECT s.id, s.usuario_id, s.%s AS ip, s.%s AS fecha_inicio, " +
                            "(SELECT COUNT(*) FROM mensajes m WHERE m.emisor_id = s.usuario_id AND m.creado_en >= s.%s) AS mensajes_enviados " +
                            "FROM sesiones s WHERE s.%s IS NULL ORDER BY s.%s DESC", ipCol, fi, fi, ff, fi);
                    try {
                        return jdbc.queryForList(sql);
                    } catch (org.springframework.jdbc.BadSqlGrammarException ex) {
                        // try next candidate
                    }
                }
            }
        }
        return java.util.Collections.emptyList();
    }

    public List<Map<String,Object>> findDisconnectedSessions() {
        String[] fechaInicioCandidates = new String[]{"fecha_inicio", "creado_en", "creadoEn"};
        String[] fechaFinCandidates = new String[]{"fecha_fin", "desconectado_en", "fechaFin"};
        String[] ipCandidates = new String[]{"ip", "direccion_ip", "direccionIp"};
        for (String ipCol : ipCandidates) {
            for (String fi : fechaInicioCandidates) {
                for (String ff : fechaFinCandidates) {
                    String sql = String.format("SELECT s.id, s.usuario_id, s.%s AS ip, s.%s AS fecha_inicio, s.%s AS fecha_fin, " +
                            "(SELECT COUNT(*) FROM mensajes m WHERE m.emisor_id = s.usuario_id AND m.creado_en BETWEEN s.%s AND s.%s) AS mensajes_enviados " +
                            "FROM sesiones s WHERE s.%s IS NOT NULL ORDER BY s.%s DESC", ipCol, fi, ff, fi, ff, ff, fi);
                    try {
                        return jdbc.queryForList(sql);
                    } catch (org.springframework.jdbc.BadSqlGrammarException ex) {
                        // try next candidate
                    }
                }
            }
        }
        return java.util.Collections.emptyList();
    }

    public List<Map<String,Object>> findLastDisconnectionsPerUser() {
        // returns rows with usuario_id and ultima_desconexion (MAX of desconectado_en)
        String sql = "SELECT s.usuario_id, MAX(s.desconectado_en) AS ultima_desconexion " +
                "FROM sesiones s WHERE s.desconectado_en IS NOT NULL GROUP BY s.usuario_id ORDER BY ultima_desconexion DESC";
        try {
            return jdbc.queryForList(sql);
        } catch (org.springframework.dao.DataAccessException ex) {
            return java.util.Collections.emptyList();
        }
    }
}
