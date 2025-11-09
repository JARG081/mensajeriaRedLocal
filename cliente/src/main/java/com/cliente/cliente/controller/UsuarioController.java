package com.cliente.cliente.controller;

import com.cliente.cliente.model.Usuario;
import com.cliente.cliente.service.UsuarioService;
import com.cliente.cliente.dto.UsuarioDTO;
import com.cliente.cliente.mapper.UsuarioMapper;
import javax.swing.table.DefaultTableModel;
import java.util.List;

public class UsuarioController {

    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    private static Double parseDouble(String s) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException("ID es obligatorio");
        try {
            Double v = Double.valueOf(s.trim());
            if (v < 0) throw new IllegalArgumentException("ID debe ser positivo");
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ID no es numérico");
        }
    }


    public void insertar(String id, String nombres, String apellidos, String email) {
        service.registrar(parseDouble(id), nombres, apellidos, email);
    }


    public boolean actualizar(String id, String nombres, String apellidos, String email) {
        return service.actualizar(parseDouble(id), nombres, apellidos, email);
    }

    public boolean eliminar(String id) {
        return service.eliminar(parseDouble(id));
    }

    public Usuario buscar(String id) {
        return service.buscar(parseDouble(id));
    }

    public List<Usuario> listar() {
        return service.listar();
    }

    public DefaultTableModel modeloTablaTodas() {
        DefaultTableModel m = new DefaultTableModel(
                new Object[]{"ID","Nombres","Apellidos","Email"}, 0) {
            @Override public boolean isCellEditable(int r,int c){return false;}
        };
        for (Usuario u : listar()) {
            var d = UsuarioMapper.toDTO(u);
            m.addRow(new Object[]{ d.id, d.nombres, d.apellidos, d.email });
        }
        return m;
    }

    public void insertar(UsuarioDTO dto) {
        var e = UsuarioMapper.toEntity(dto);
        service.registrar(e.getId(), e.getNombres(), e.getApellidos(), e.getEmail());
    }

    public boolean actualizar(UsuarioDTO dto) {
        var e = UsuarioMapper.toEntity(dto);
        return service.actualizar(e.getId(), e.getNombres(), e.getApellidos(), e.getEmail());
    }
}
