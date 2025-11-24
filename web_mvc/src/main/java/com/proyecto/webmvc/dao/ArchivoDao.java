package com.proyecto.webmvc.dao;

import com.proyecto.webmvc.model.Archivo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ArchivoDao {
    private final JdbcTemplate jdbc;

    public ArchivoDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Archivo> findAllOrderBySizeDesc() {
        // avoid selecting usuario columns directly in case DB column names differ
        String sql = "SELECT a.id, a.nombre, a.tamano, a.propietario_id, a.ruta, a.creado_en " +
            "FROM archivos a ORDER BY a.tamano DESC";
        try {
            return jdbc.query(sql, (rs, i) -> mapArchivoWithOwnerName(rs));
        } catch (org.springframework.dao.DataAccessException ex) {
            return java.util.Collections.emptyList();
        }
    }

    public Archivo findById(Long id) {
        String sql = "SELECT a.id, a.nombre, a.tamano, a.propietario_id, a.ruta, a.creado_en " +
            "FROM archivos a WHERE a.id = ?";
        try {
            return jdbc.queryForObject(sql, new Object[]{id}, (rs, i) -> mapArchivoWithOwnerName(rs));
        } catch (org.springframework.dao.DataAccessException ex) {
            return null;
        }
    }

    public java.util.List<Archivo> findAllOrderByDateDesc() {
        String sql = "SELECT a.id, a.nombre, a.tamano, a.propietario_id, a.ruta, a.creado_en " +
            "FROM archivos a ORDER BY a.creado_en DESC";
        try {
            return jdbc.query(sql, (rs, i) -> mapArchivoWithOwnerName(rs));
        } catch (org.springframework.dao.DataAccessException ex) {
            return java.util.Collections.emptyList();
        }
    }

    public java.util.List<Archivo> findReceivedByUser(Long usuarioId) {
        // Join mensajes -> archivos to find files received by a given user
        String sql = "SELECT a.id, a.nombre, a.tamano, a.propietario_id, a.ruta, a.creado_en " +
            "FROM archivos a JOIN mensajes m ON m.archivo_id = a.id " +
            "WHERE m.receptor_id = ? AND m.tipo = 'ARCHIVO' " +
            "ORDER BY m.creado_en DESC";
        try {
            return jdbc.query(sql, new Object[]{usuarioId}, (rs, i) -> mapArchivoWithOwnerName(rs));
        } catch (org.springframework.dao.DataAccessException ex) {
            return java.util.Collections.emptyList();
        }
    }

    private Archivo mapArchivoWithOwnerName(java.sql.ResultSet rs) throws java.sql.SQLException {
        Archivo a = new Archivo();
        a.setId(rs.getLong("id"));
        a.setNombre(rs.getString("nombre"));
        a.setTamano(rs.getObject("tamano") == null ? 0L : rs.getLong("tamano"));
        a.setPropietarioId(rs.getObject("propietario_id") == null ? null : rs.getLong("propietario_id"));
        a.setRuta(rs.getString("ruta"));
        // try to resolve propietario nombre safely
        Long pid = a.getPropietarioId();
        if (pid != null) {
            String nombre = resolveUsuarioNombre(pid);
            a.setPropietarioNombre(nombre);
        }
        java.sql.Timestamp ts = rs.getTimestamp("creado_en");
        if (ts != null) a.setCreadoEn(ts.toLocalDateTime());
        return a;
    }

    private String resolveUsuarioNombre(Long usuarioId) {
        try {
            // first try the common column name
            return jdbc.queryForObject("SELECT nombre_usuario FROM usuarios WHERE id = ?", new Object[]{usuarioId}, String.class);
        } catch (org.springframework.jdbc.BadSqlGrammarException ex) {
            // try alternate column name used in some schemas
            try {
                return jdbc.queryForObject("SELECT nombre FROM usuarios WHERE id = ?", new Object[]{usuarioId}, String.class);
            } catch (org.springframework.dao.EmptyResultDataAccessException | org.springframework.jdbc.BadSqlGrammarException e) {
                return null;
            }
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            return null;
        }
    }
}
