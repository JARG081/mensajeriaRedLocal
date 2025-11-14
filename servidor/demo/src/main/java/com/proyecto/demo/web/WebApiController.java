package com.proyecto.demo.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.jdbc.core.JdbcTemplate;
import com.proyecto.demo.server.ConnectedClients;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class WebApiController {

    private final JdbcTemplate jdbc;
    private final ConnectedClients connectedClients;

    public WebApiController(JdbcTemplate jdbc, ConnectedClients connectedClients) {
        this.jdbc = jdbc;
        this.connectedClients = connectedClients;
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users() {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT id, nombre_usuario FROM usuarios ORDER BY nombre_usuario ASC");
        var sessions = connectedClients.getUserSessions();
        return rows.stream().map(r -> {
            Map<String,Object> m = new HashMap<>();
            m.put("id", r.get("id"));
            m.put("username", r.get("nombre_usuario"));
            Object u = r.get("nombre_usuario");
            java.util.List<String> s = u == null ? java.util.List.of() : sessions.getOrDefault(u.toString(), java.util.List.of());
            m.put("connected", !s.isEmpty());
            m.put("sessions", s);
            return m;
        }).collect(Collectors.toList());
    }
}
