package com.proyecto.webmvc.controller;

import com.proyecto.webmvc.model.Usuario;
import com.proyecto.webmvc.service.UsuarioService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class UsuarioController {
    private final UsuarioService service;

    public UsuarioController(UsuarioService service) { this.service = service; }

    @GetMapping({"/web/usuarios", "/usuarios"})
    public String usuarios(Model m) {
        List<Usuario> usuarios = service.listAll();
        // build list with counts
        java.util.List<java.util.Map<String,Object>> rows = new java.util.ArrayList<>();
        for (Usuario u : usuarios) {
            Long cnt = service.countSent(u.getId());
            rows.add(java.util.Map.of("id", u.getId(), "nombre", u.getNombreUsuario(), "enviados", cnt == null ? 0L : cnt));
        }
        m.addAttribute("usuarios", rows);
        return "usuarios";
    }
}
