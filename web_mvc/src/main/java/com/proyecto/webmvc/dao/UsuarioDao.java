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
        return jdbc.query("SELECT id, nombre_usuario FROM usuarios ORDER BY nombre_usuario", (rs, i) -> new Usuario(rs.getLong("id"), rs.getString("nombre_usuario")));
    }

    public Usuario findById(Long id) {
        return jdbc.queryForObject("SELECT id, nombre_usuario FROM usuarios WHERE id = ?", new Object[]{id}, (rs, i) -> new Usuario(rs.getLong("id"), rs.getString("nombre_usuario")));
    }

    public Long countMensajesEnviados(Long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM mensajes WHERE emisor_id = ?", new Object[]{userId}, Long.class);
    }
}
