package com.proyecto.webmvc.controller;

import com.proyecto.webmvc.model.Mensaje;
import com.proyecto.webmvc.model.Usuario;
import com.proyecto.webmvc.service.MensajeService;
import com.proyecto.webmvc.service.UsuarioService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class MensajeController {
    private final MensajeService service;
    private final UsuarioService usuarioService;

    public MensajeController(MensajeService service, UsuarioService usuarioService) { this.service = service; this.usuarioService = usuarioService; }

    @GetMapping("/web/usuarios/{id}/mensajes")
    public String mensajesPorUsuario(@PathVariable("id") Long id, @RequestParam(name="tipo", required=false) String tipo, Model m) {
        // If tipo not provided, show both Texto y Archivos
        List<Mensaje> mensajes;
        String tipoDisplay;
        if (tipo == null || tipo.isBlank()) {
            List<Mensaje> textos = service.mensajesPorEmisorYTipo(id, "TEXTO");
            List<Mensaje> archivos = service.mensajesPorEmisorYTipo(id, "ARCHIVO");
            mensajes = new java.util.ArrayList<>();
            if (textos != null) mensajes.addAll(textos);
            if (archivos != null) mensajes.addAll(archivos);
            tipoDisplay = "Texto y Archivos";
        } else {
            tipo = tipo.toUpperCase();
            mensajes = service.mensajesPorEmisorYTipo(id, tipo);
            tipoDisplay = "TEXTO".equals(tipo) ? "Texto" : ("ARCHIVO".equals(tipo) ? "Archivos" : tipo);
        }
        m.addAttribute("mensajes", mensajes);
        m.addAttribute("usuarioId", id);
        // resolve username for header
        String nombre = null;
        try {
            Usuario u = usuarioService.find(id);
            if (u != null) nombre = u.getNombre();
        } catch (Exception ignored) {}
        if (nombre == null) nombre = String.valueOf(id);
        m.addAttribute("usuarioNombre", nombre);
        m.addAttribute("tipoDisplay", tipoDisplay);
        return "mensajes";
    }

    @GetMapping("/web/mensajes/{id}")
    public String detalleMensaje(@PathVariable("id") Long id, Model m) {
        Mensaje mensaje = service.mensajeDetalle(id);
        m.addAttribute("mensaje", mensaje);
        return "mensaje_detalle";
    }
}
