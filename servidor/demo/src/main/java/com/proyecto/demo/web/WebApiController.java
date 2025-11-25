package com.proyecto.demo.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.jdbc.core.JdbcTemplate;
import com.proyecto.demo.server.ConnectedClients;
import org.springframework.beans.factory.annotation.Value;
import com.proyecto.demo.db.DatabaseSelectorService;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class WebApiController {

    private final JdbcTemplate jdbc;
    private final ConnectedClients connectedClients;
    private final DatabaseSelectorService selectorService;

    @Value("${app.db.exposeCredentials:false}")
    private boolean exposeCredentials;
    @Value("${spring.datasource.password:}")
    private String configuredPassword;
    @Value("${spring.datasource.postgres.password:}")
    private String configuredPgPassword;

    public WebApiController(JdbcTemplate jdbc, ConnectedClients connectedClients, DatabaseSelectorService selectorService) {
        this.jdbc = jdbc;
        this.connectedClients = connectedClients;
        this.selectorService = selectorService;
    }

    @GetMapping("/dbinfo")
    public java.util.Map<String,Object> dbinfo() {
        java.util.Map<String,Object> m = new java.util.HashMap<>();
        try {
            var ds = jdbc.getDataSource();
            if (ds != null) {
                var conn = ds.getConnection();
                try (conn) {
                    var meta = conn.getMetaData();
                    m.put("dbProduct", meta.getDatabaseProductName());
                    m.put("dbProductVersion", meta.getDatabaseProductVersion());
                    m.put("url", meta.getURL());
                    m.put("user", meta.getUserName());
                }
            } else {
                m.put("error","no datasource");
            }
        } catch (Exception e) {
            m.put("error", e.getMessage());
        }
        // Optionally include credentials if explicitly allowed in properties
        try {
            if (exposeCredentials) {
                String dbType = null;
                try {
                    var adapter = selectorService.getAdapter();
                    if (adapter != null) dbType = adapter.getDatabaseType();
                } catch (Exception ignored) {}
                // choose best available configured password
                String pass = null;
                if (dbType != null && dbType.toLowerCase().contains("postgres")) pass = configuredPgPassword;
                if ((pass == null || pass.isBlank())) pass = configuredPassword;
                if (pass != null && !pass.isBlank()) m.put("password", pass);
            }
        } catch (Exception ignored) {}

        return m;
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users() {
        // Detect which username column exists ('nombre' or 'nombre_usuario') and query accordingly
        String usernameCol = "nombre";
        try {
            var ds = jdbc.getDataSource();
            if (ds != null) {
                try (var conn = ds.getConnection()) {
                    var md = conn.getMetaData();
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
                }
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
