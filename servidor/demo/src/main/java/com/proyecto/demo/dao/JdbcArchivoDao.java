package com.proyecto.demo.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.stereotype.Component;
import java.sql.PreparedStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
 

@Component
public class JdbcArchivoDao implements ArchivoDao {
    private final JdbcTemplate jdbc;
    private static final Logger log = LoggerFactory.getLogger(JdbcArchivoDao.class);

    public JdbcArchivoDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        ensureTable();
        try { detectColumns(); } catch (Exception ignored) {}
    }

    // detected column names
    private volatile String filenameColumn = "nombre"; // logical filename
    private volatile String pathColumn = "ruta"; // stored path

    private void detectColumns() {
        try {
            var md = jdbc.getDataSource().getConnection().getMetaData();
            try (var rs = md.getColumns(null, null, "archivos", null)) {
                boolean hasNombre = false;
                boolean hasRuta = false;
                boolean hasNombreOriginal = false;
                boolean hasNombreAlmacenado = false;
                while (rs.next()) {
                    String col = rs.getString("COLUMN_NAME");
                    if (col == null) continue;
                    if ("nombre".equalsIgnoreCase(col)) hasNombre = true;
                    if ("ruta".equalsIgnoreCase(col)) hasRuta = true;
                    if ("nombre_original".equalsIgnoreCase(col)) hasNombreOriginal = true;
                    if ("nombre_almacenado".equalsIgnoreCase(col)) hasNombreAlmacenado = true;
                }
                if (hasNombre) filenameColumn = "nombre";
                else if (hasNombreOriginal) filenameColumn = "nombre_original";
                if (hasRuta) pathColumn = "ruta";
                else if (hasNombreAlmacenado) pathColumn = "nombre_almacenado";
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private void ensureTable() {
        // Skipping DDL execution for 'archivos' table; schema must be created externally
        log.info("ensureTable skipped for 'archivos' - DDL is managed outside the application");
    }

    @Override
    public long insertArchivo(String filename, String path, long size, Long propietarioId) {
        // Try the detected column names first, then fall back to known variants if insert fails.
        String[] filenameCandidates = new String[] { filenameColumn, "nombre", "nombre_original" };
        String[] pathCandidates = new String[] { pathColumn, "ruta", "nombre_almacenado" };

        for (String fnCol : filenameCandidates) {
            for (String pCol : pathCandidates) {
                final String sql = "INSERT INTO archivos (" + fnCol + "," + pCol + ",tamano,propietario_id) VALUES (?,?,?,?)";
                try {
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
                    long id = key == null ? -1L : key.longValue();
                    if (id > 0) {
                        // update our detected columns so future operations use the successful names
                        filenameColumn = fnCol;
                        pathColumn = pCol;
                        log.info("insertArchivo: inserted file '{}' using columns {} / {}, id={}", filename, fnCol, pCol, id);
                        return id;
                    }
                } catch (DataAccessException dae) {
                    // try next candidate pair
                    log.debug("insertArchivo: failed insert with columns {} / {} : {}", fnCol, pCol, dae.getMessage());
                } catch (Exception ex) {
                    log.debug("insertArchivo: unexpected error with columns {} / {} : {}", fnCol, pCol, ex.getMessage());
                }
            }
        }
        log.warn("insertArchivo: could not insert archivo row for filename='{}' path='{}'. All attempts failed.", filename, path);
        return -1L;
    }

    @Override
    public ArchivoInfo findById(long id) {
        // Try a couple of SELECT variants to support schema differences (creado_en vs subido_en, filename/path column names)
        String[] selects = new String[] {
                "SELECT id, " + filenameColumn + " as filename, " + pathColumn + " as path, tamano as size, COALESCE(creado_en, subido_en) AS creado_en FROM archivos WHERE id = ?",
                "SELECT id, " + filenameColumn + " as filename, " + pathColumn + " as path, tamano as size, creado_en FROM archivos WHERE id = ?",
                "SELECT id, " + filenameColumn + " as filename, " + pathColumn + " as path, tamano as size FROM archivos WHERE id = ?"
        };
        for (String select : selects) {
            try {
                return jdbc.queryForObject(
                        select,
                        new Object[]{id},
                        (rs, rn) -> {
                            ArchivoInfo a = new ArchivoInfo();
                            a.id = rs.getLong("id");
                            try { a.filename = rs.getString("filename"); } catch (Exception ign) { a.filename = null; }
                            try { a.path = rs.getString("path"); } catch (Exception ign) { a.path = null; }
                            try { a.size = rs.getLong("size"); } catch (Exception ign) { a.size = 0L; }
                            java.sql.Timestamp ts = null;
                            try { ts = rs.getTimestamp("creado_en"); } catch (Exception ignore) { ts = null; }
                            if (ts != null) a.creadoEn = ts.toLocalDateTime();
                            return a;
                        }
                );
            } catch (Exception e) {
                log.debug("findById: select failed with SQL [{}]: {}", select, e.getMessage());
            }
        }
        return null;
    }
}
