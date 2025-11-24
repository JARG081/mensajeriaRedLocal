package com.proyecto.webmvc.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class InputPageController {

    @GetMapping({"/web/input-usuario-id", "/input-usuario-id"})
    public String inputUsuarioId() { return "input_usuario_id"; }

    @GetMapping("/web/")
    public String webRoot() { return "redirect:/web/usuarios"; }

    @GetMapping({"/web/input-mensaje-id", "/input-mensaje-id"})
    public String inputMensajeId() { return "input_mensaje_id"; }

    @GetMapping({"/web/input-archivo-id", "/input-archivo-id"})
    public String inputArchivoId() { return "input_archivo_id"; }

    @GetMapping({"/web/input-archivos-usuario-id", "/input-archivos-usuario-id"})
    public String inputArchivosUsuarioId() { return "input_archivos_usuario_id"; }
}
