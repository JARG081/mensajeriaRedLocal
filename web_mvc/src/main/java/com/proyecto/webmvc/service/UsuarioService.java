package com.proyecto.webmvc.service;

import com.proyecto.webmvc.dao.UsuarioDao;
import com.proyecto.webmvc.model.Usuario;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UsuarioService {
    private final UsuarioDao dao;

    public UsuarioService(UsuarioDao dao) { this.dao = dao; }

    public List<Usuario> listAll() { return dao.findAll(); }

    public Usuario find(Long id) { return dao.findById(id); }

    public Long countSent(Long userId) { return dao.countMensajesEnviados(userId); }
}
