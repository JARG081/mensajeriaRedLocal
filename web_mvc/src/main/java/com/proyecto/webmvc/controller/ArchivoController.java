package com.proyecto.webmvc.controller;

import com.proyecto.webmvc.dao.ArchivoDao;
import com.proyecto.webmvc.model.Archivo;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.File;
import java.io.FileInputStream;

@Controller
public class ArchivoController {
    private final ArchivoDao archivoDao;

    public ArchivoController(ArchivoDao archivoDao) { this.archivoDao = archivoDao; }

    @GetMapping("/web/archivos/{id}/download")
    public ResponseEntity<?> download(@PathVariable("id") Long id) {
        try {
            Archivo a = archivoDao.findById(id);
            if (a == null) return ResponseEntity.notFound().build();
            String ruta = a.getRuta();
            if (ruta == null || ruta.isBlank()) return ResponseEntity.status(404).body("Archivo no disponible en filesystem");
            File f = new File(ruta);
            if (!f.exists()) return ResponseEntity.notFound().build();
            InputStreamResource resource = new InputStreamResource(new FileInputStream(f));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + a.getNombre() + "\"")
                    .contentLength(f.length())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error descargando archivo");
        }
    }

    @GetMapping("/web/archivos")
    public String listArchivos(org.springframework.ui.Model m) {
        try {
            java.util.List<Archivo> archivos = archivoDao.findAllOrderByDateDesc();
            m.addAttribute("archivos", archivos);
            return "archivos";
        } catch (Exception e) {
            m.addAttribute("error", "No se pudo listar archivos: " + e.getMessage());
            return "archivos";
        }
    }

    @GetMapping("/web/archivos/usuario/{id}")
    public String listArchivosRecibidosPorUsuario(@PathVariable("id") Long id, org.springframework.ui.Model m) {
        try {
            java.util.List<Archivo> archivos = archivoDao.findReceivedByUser(id);
            m.addAttribute("archivos", archivos);
            m.addAttribute("filtroUsuarioId", id);
            return "archivos";
        } catch (Exception e) {
            m.addAttribute("error", "No se pudo listar archivos recibidos: " + e.getMessage());
            return "archivos";
        }
    }
}
