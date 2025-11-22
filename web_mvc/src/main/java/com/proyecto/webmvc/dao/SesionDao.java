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
        String sql = "SELECT s.id, s.usuario_id, s.ip, s.fecha_inicio, s.fecha_fin FROM sesiones s WHERE s.fecha_fin IS NULL ORDER BY s.fecha_inicio DESC";
        return jdbc.queryForList(sql);
    }

    public List<Map<String,Object>> findDisconnectedSessions() {
        String sql = "SELECT s.id, s.usuario_id, s.ip, s.fecha_inicio, s.fecha_fin FROM sesiones s WHERE s.fecha_fin IS NOT NULL ORDER BY s.fecha_inicio DESC";
        return jdbc.queryForList(sql);
    }
}
