package com.proyecto.api.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.io.IOException;

@RestController
@RequestMapping("/api")
public class ApiUsuarioController {

    private final JdbcTemplate jdbc;
    private final RestTemplate rest;

    @Value("${server.external.baseUrl:http://localhost:9010}")
    private String serverBaseUrl;

    public ApiUsuarioController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.rest = new RestTemplate();
    }

    @GetMapping(value = "/response", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> obtenerResponseFixture() {
        try {
            ClassPathResource r = new ClassPathResource("response.json");
            if (!r.exists()) return ResponseEntity.notFound().build();
            try (InputStream is = r.getInputStream()) {
                byte[] data = is.readAllBytes();
                String s = new String(data, StandardCharsets.UTF_8);
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(s);
            }
        } catch (IOException e) {
            return ResponseEntity.status(500).body("{\"error\":\"no se pudo leer fixture\"}");
        }
    }

    @GetMapping("/usuarios/{id}")
    public ResponseEntity<?> obtenerInfoUsuario(@PathVariable("id") long id) {
        try {
            Map<String, Object> user = jdbc.queryForMap("SELECT id, nombre_usuario FROM usuarios WHERE id = ?", id);
            if (user == null || user.isEmpty()) return ResponseEntity.notFound().build();

            String username = (String) user.get("nombre_usuario");

            Long enviados = jdbc.queryForObject("SELECT COUNT(*) FROM mensajes WHERE emisor_id = ?", new Object[]{id}, Long.class);
            Long recibidos = jdbc.queryForObject("SELECT COUNT(*) FROM mensajes WHERE receptor_id = ?", new Object[]{id}, Long.class);

            Timestamp last = jdbc.queryForObject("SELECT MAX(creado_en) FROM mensajes WHERE emisor_id = ? OR receptor_id = ?", new Object[]{id, id}, Timestamp.class);

            boolean conectado = false;
            LocalDateTime ultimaActividad = null;
            if (last != null) ultimaActividad = last.toLocalDateTime();

            // intentar consultar el servidor principal para estado en vivo
            try {
                String url = serverBaseUrl + "/api/users";
                List<Map> rows = rest.getForObject(url, List.class);
                if (rows != null) {
                    for (Object o : rows) {
                        Map r = (Map) o;
                        Object uid = r.get("id");
                        if (uid != null && Long.valueOf(uid.toString()) == id) {
                            Object c = r.get("connected");
                            conectado = c != null && Boolean.parseBoolean(c.toString());
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {}

            Map<String, Object> out = new HashMap<>();
            out.put("id", id);
            out.put("nombre", username);
            out.put("mensajes_enviados", enviados == null ? 0L : enviados);
            out.put("mensajes_recibidos", recibidos == null ? 0L : recibidos);
            out.put("ultima_actividad", ultimaActividad == null ? null : ultimaActividad.toString());
            out.put("estado", conectado ? "conectado" : "desconectado");

            return ResponseEntity.ok(out);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Error interno"));
        }
    }

    @GetMapping("/usuarios/{id}/mensajes/enviados")
    public List<Map<String, Object>> mensajesEnviados(@PathVariable("id") long id) {
        String sql = "SELECT m.id, m.emisor_id, m.receptor_id, m.tipo_mensaje, m.contenido, m.archivo_id, m.creado_en " +
                "FROM mensajes m WHERE m.emisor_id = ? ORDER BY m.creado_en DESC LIMIT 500";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, id);
        for (Map<String, Object> r : rows) {
            r.put("ip_emisor", null);
            r.put("ip_receptor", null);
        }
        return rows;
    }

    @GetMapping("/usuarios/{id}/mensajes/recibidos")
    public List<Map<String, Object>> mensajesRecibidos(@PathVariable("id") long id) {
        String sql = "SELECT m.id, m.emisor_id, m.receptor_id, m.tipo_mensaje, m.contenido, m.archivo_id, m.creado_en " +
                "FROM mensajes m WHERE m.receptor_id = ? ORDER BY m.creado_en DESC LIMIT 500";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, id);
        for (Map<String, Object> r : rows) {
            r.put("ip_emisor", null);
            r.put("ip_receptor", null);
        }
        return rows;
    }
}
