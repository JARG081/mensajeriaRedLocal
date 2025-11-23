package com.proyecto.webmvc.controller;

import com.proyecto.webmvc.model.Mensaje;
import com.proyecto.webmvc.service.MensajeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class MensajeController {
    private final MensajeService service;

    public MensajeController(MensajeService service) { this.service = service; }

    @GetMapping("/web/usuarios/{id}/mensajes")
    public String mensajesPorUsuario(@PathVariable("id") Long id, @RequestParam(name="tipo", required=false, defaultValue="TEXTO") String tipo, Model m) {
        if (tipo == null) tipo = "TEXTO";
        tipo = tipo.toUpperCase();
        List<Mensaje> mensajes = service.mensajesPorEmisorYTipo(id, tipo);
        m.addAttribute("mensajes", mensajes);
        m.addAttribute("usuarioId", id);
        m.addAttribute("tipo", tipo);
        return "mensajes";
    }

    @GetMapping("/web/mensajes/{id}")
    public String detalleMensaje(@PathVariable("id") Long id, Model m) {
        Mensaje mensaje = service.mensajeDetalle(id);
        m.addAttribute("mensaje", mensaje);
        return "mensaje_detalle";
    }
}
