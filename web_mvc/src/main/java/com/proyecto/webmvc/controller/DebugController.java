package com.proyecto.webmvc.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/web/debug")
public class DebugController {
    private final JdbcTemplate jdbc;

    public DebugController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/sesiones")
    public Map<String,Object> sesiones() {
        Map<String,Object> out = new HashMap<>();
        // Try a simple count first; if schema differs, try to discover column names
        try {
            Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM sesiones", Integer.class);
            out.put("total", total);
        } catch (Exception ex) {
            out.put("total_error", ex.getClass().getName());
            out.put("total_message", ex.getMessage());
        }

        String[] fechaFinCandidates = new String[]{"fecha_fin", "desconectado_en", "fechaFin"};
        String[] fechaInicioCandidates = new String[]{"fecha_inicio", "creado_en", "creadoEn"};

        // try counts using candidate column names
        for (String ff : fechaFinCandidates) {
            try {
                Integer active = jdbc.queryForObject(String.format("SELECT COUNT(*) FROM sesiones WHERE %s IS NULL", ff), Integer.class);
                Integer disconnected = jdbc.queryForObject(String.format("SELECT COUNT(*) FROM sesiones WHERE %s IS NOT NULL", ff), Integer.class);
                out.put("fecha_fin_column", ff);
                out.put("active", active);
                out.put("disconnected", disconnected);
                break;
            } catch (Exception e) {
                // ignore and try next candidate
            }
        }

        // if counts not found, try to list columns from information_schema
        if (!out.containsKey("fecha_fin_column")) {
            try {
                List<Map<String,Object>> cols = jdbc.queryForList("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sesiones'");
                out.put("columns", cols);
                // try to return a sample of rows with whatever columns exist
                try {
                    List<Map<String,Object>> sample = jdbc.queryForList("SELECT * FROM sesiones ORDER BY 1 DESC LIMIT 20");
                    out.put("sample", sample);
                } catch (Exception e) {
                    out.put("sample_error", e.getClass().getName());
                    out.put("sample_message", e.getMessage());
                }
            } catch (Exception ex) {
                out.put("columns_error", ex.getClass().getName());
                out.put("columns_message", ex.getMessage());
            }
        } else {
            // if we identified a fecha_fin column, return a sample using identified candidates for fecha_inicio ordering
            String fi = fechaInicioCandidates[0];
            // try to find a working fecha_inicio candidate
            for (String cand : fechaInicioCandidates) {
                try {
                    List<Map<String,Object>> sample = jdbc.queryForList(String.format("SELECT id, usuario_id, ip, %s AS fecha_inicio, %s AS fecha_fin, estado FROM sesiones ORDER BY %s DESC LIMIT 20", cand, out.get("fecha_fin_column"), cand));
                    out.put("sample", sample);
                    break;
                } catch (Exception e) {
                    // try next
                }
            }
        }

        return out;
    }

    @GetMapping("/sesiones/full")
    public Map<String,Object> sesionesFull() {
        Map<String,Object> out = new HashMap<>();
        try {
            List<Map<String,Object>> rows = jdbc.queryForList("SELECT * FROM sesiones LIMIT 20");
            out.put("rows", rows);
            out.put("count", rows.size());
        } catch (Exception ex) {
            out.put("error", ex.getClass().getName());
            out.put("message", ex.getMessage());
        }
        return out;
    }
}
