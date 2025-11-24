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
        // Detect which username column exists ('nombre' or 'nombre_usuario') and query accordingly
        String usernameCol = "nombre";
        try {
            var md = jdbc.getDataSource().getConnection().getMetaData();
            try (var rs = md.getColumns(null, null, "usuarios", null)) {
                boolean hasNombre = false;
                boolean hasNombreUsuario = false;
                while (rs.next()) {
                    String col = rs.getString("COLUMN_NAME");
                    if (col == null) continue;
                    if ("nombre".equalsIgnoreCase(col)) hasNombre = true;
                    if ("nombre_usuario".equalsIgnoreCase(col)) hasNombreUsuario = true;
                }
                if (hasNombreUsuario) usernameCol = "nombre_usuario";
                else if (hasNombre) usernameCol = "nombre";
            }
        } catch (Exception e) {
            // fallback to default 'nombre'
            usernameCol = "nombre";
        }

        String sql = String.format("SELECT id, %s AS username FROM usuarios ORDER BY %s ASC", usernameCol, usernameCol);
        List<Map<String,Object>> rows = jdbc.queryForList(sql);
        var sessions = connectedClients.getUserSessions();
        return rows.stream().map(r -> {
            Map<String,Object> m = new HashMap<>();
            m.put("id", r.get("id"));
            Object uname = r.get("username");
            String username = uname == null ? null : uname.toString();
            m.put("username", username);
            java.util.List<String> s = username == null ? java.util.List.of() : sessions.getOrDefault(username, java.util.List.of());
            m.put("connected", !s.isEmpty());
            m.put("sessions", s);
            return m;
        }).collect(Collectors.toList());
    }
}
