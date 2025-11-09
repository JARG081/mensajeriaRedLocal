package com.cliente.cliente.mapper;

import com.cliente.cliente.model.Usuario;
import com.cliente.cliente.dto.UsuarioDTO;

public final class UsuarioMapper {
    private UsuarioMapper() {}

    public static UsuarioDTO toDTO(Usuario u) {
        if (u == null) return null;
        return new UsuarioDTO(u.getId(), u.getNombres(), u.getApellidos(), u.getEmail());
    }

    public static Usuario toEntity(UsuarioDTO d) {
        if (d == null) return null;
        return new Usuario(d.id, d.nombres, d.apellidos, d.email);
    }
}
