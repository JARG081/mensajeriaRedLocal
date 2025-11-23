package com.proyecto.demo.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.stereotype.Component;
import java.sql.PreparedStatement;
 

@Component
public class JdbcArchivoDao implements ArchivoDao {
    private final JdbcTemplate jdbc;

    public JdbcArchivoDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        ensureTable();
    }

    private void ensureTable() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS archivos (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "nombre VARCHAR(512) NOT NULL, " +
                "ruta VARCHAR(1024), " +
                "tamano BIGINT, " +
                "propietario_id BIGINT, " +
                "creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    @Override
    public long insertArchivo(String filename, String path, long size, Long propietarioId) {
        final String sql = "INSERT INTO archivos (nombre,ruta,tamano,propietario_id) VALUES (?,?,?,?)";
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        PreparedStatementCreator psc = con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setString(1, filename);
            ps.setString(2, path);
            ps.setLong(3, size);
            if (propietarioId != null) ps.setLong(4, propietarioId); else ps.setNull(4, java.sql.Types.BIGINT);
            return ps;
        };
        jdbc.update(psc, kh);
        Number key = kh.getKey();
        return key == null ? -1L : key.longValue();
    }

    @Override
    public ArchivoInfo findById(long id) {
        try {
            return jdbc.queryForObject(
                    "SELECT id, nombre as filename, ruta as path, tamano as size, creado_en FROM archivos WHERE id = ?",
                    new Object[]{id},
                    (rs, rn) -> {
                        ArchivoInfo a = new ArchivoInfo();
                        a.id = rs.getLong("id");
                        a.filename = rs.getString("filename");
                        a.path = rs.getString("path");
                        a.size = rs.getLong("size");
                        java.sql.Timestamp ts = rs.getTimestamp("creado_en");
                        if (ts != null) a.creadoEn = ts.toLocalDateTime();
                        return a;
                    }
            );
        } catch (Exception e) {
            return null;
        }
    }
}
