package com.proyecto.webmvc.controller;

import com.proyecto.webmvc.service.MensajeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class InformesController {
    private final MensajeService service;

    public InformesController(MensajeService service) { this.service = service; }

    @GetMapping("/web/informes")
    public String informes(Model m) {
        // usuario con mas mensajes
        m.addAttribute("topUser", service.usuarioMasEnvios());
        m.addAttribute("archivos", service.archivosPorTamano());
        return "informes";
    }

    @GetMapping("/web/conectados")
    public String conectados(Model m) {
        m.addAttribute("sesiones", service.usuariosConectados());
        return "conectados";
    }

    @GetMapping("/web/desconectados")
    public String desconectados(Model m) {
        m.addAttribute("sesiones", service.usuariosDesconectados());
        return "desconectados";
    }
}
