package com.proyecto.webmvc.dao;

import com.proyecto.webmvc.model.Usuario;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class UsuarioDao {
    private final JdbcTemplate jdbc;

    public UsuarioDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Usuario> findAll() {
        try {
            // select only ids to avoid depending on a specific username column name
            return jdbc.query("SELECT id FROM usuarios ORDER BY id", (rs, i) -> {
                Long id = rs.getLong("id");
                String nombre = resolveUsuarioNombre(id);
                return new Usuario(id, nombre);
            });
        } catch (org.springframework.dao.DataAccessException ex) {
            return java.util.Collections.emptyList();
        }
    }

    public Usuario findById(Long id) {
        try {
            return jdbc.queryForObject("SELECT id FROM usuarios WHERE id = ?", new Object[]{id}, (rs, i) -> {
                Long uid = rs.getLong("id");
                String nombre = resolveUsuarioNombre(uid);
                return new Usuario(uid, nombre);
            });
        } catch (org.springframework.dao.DataAccessException ex) {
            return null;
        }
    }

    private String resolveUsuarioNombre(Long usuarioId) {
        try {
            return jdbc.queryForObject("SELECT nombre_usuario FROM usuarios WHERE id = ?", new Object[]{usuarioId}, String.class);
        } catch (org.springframework.jdbc.BadSqlGrammarException ex) {
            try {
                return jdbc.queryForObject("SELECT nombre FROM usuarios WHERE id = ?", new Object[]{usuarioId}, String.class);
            } catch (org.springframework.dao.EmptyResultDataAccessException | org.springframework.jdbc.BadSqlGrammarException e) {
                return null;
            }
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            return null;
        }
    }

    public Long countMensajesEnviados(Long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM mensajes WHERE emisor_id = ?", new Object[]{userId}, Long.class);
    }
}
