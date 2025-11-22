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
        // join with usuarios to get propietario nombre if available
        String sql = "SELECT a.id, a.nombre, a.tamano, a.propietario_id, a.ruta, u.nombre_usuario as propietario_nombre " +
                "FROM archivos a LEFT JOIN usuarios u ON a.propietario_id = u.id ORDER BY a.tamano DESC";
        return jdbc.query(sql, (rs, i) -> {
            Archivo a = new Archivo();
            a.setId(rs.getLong("id"));
            a.setNombre(rs.getString("nombre"));
            a.setTamano(rs.getObject("tamano") == null ? 0L : rs.getLong("tamano"));
            a.setPropietarioId(rs.getObject("propietario_id") == null ? null : rs.getLong("propietario_id"));
            a.setRuta(rs.getString("ruta"));
            a.setPropietarioNombre(rs.getString("propietario_nombre"));
            return a;
        });
    }

    public Archivo findById(Long id) {
        String sql = "SELECT a.id, a.nombre, a.tamano, a.propietario_id, a.ruta, u.nombre_usuario as propietario_nombre " +
                "FROM archivos a LEFT JOIN usuarios u ON a.propietario_id = u.id WHERE a.id = ?";
        return jdbc.queryForObject(sql, new Object[]{id}, (rs, i) -> {
            Archivo a = new Archivo();
            a.setId(rs.getLong("id"));
            a.setNombre(rs.getString("nombre"));
            a.setTamano(rs.getObject("tamano") == null ? 0L : rs.getLong("tamano"));
            a.setPropietarioId(rs.getObject("propietario_id") == null ? null : rs.getLong("propietario_id"));
            a.setRuta(rs.getString("ruta"));
            a.setPropietarioNombre(rs.getString("propietario_nombre"));
            return a;
        });
    }
}
