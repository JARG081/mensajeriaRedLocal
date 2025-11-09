package com.cliente.cliente.service;

import com.cliente.cliente.model.Usuario;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Implementación simple en memoria de UsuarioService útil para pruebas locales.
 * No la uses en producción; reemplaza por una implementación JDBC/REST.
 */
public class UsuarioServiceImpl implements UsuarioService {

    private final List<Usuario> lista = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void registrar(Double id, String nombres, String apellidos, String email) {
        if (id == null) throw new IllegalArgumentException("ID es obligatorio");
        synchronized (lista) {
            Optional<Usuario> ex = lista.stream().filter(u -> id.equals(u.getId())).findFirst();
            if (ex.isPresent()) throw new IllegalArgumentException("ID ya existe");
            lista.add(new Usuario(id, nombres, apellidos, email));
        }
    }

    @Override
    public boolean actualizar(Double id, String nombres, String apellidos, String email) {
        synchronized (lista) {
            for (int i = 0; i < lista.size(); i++) {
                Usuario u = lista.get(i);
                if (u.getId().equals(id)) {
                    u.setNombres(nombres);
                    u.setApellidos(apellidos);
                    u.setEmail(email);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean eliminar(Double id) {
        synchronized (lista) {
            return lista.removeIf(u -> u.getId().equals(id));
        }
    }

    @Override
    public Usuario buscar(Double id) {
        synchronized (lista) {
            return lista.stream().filter(u -> u.getId().equals(id)).findFirst().orElse(null);
        }
    }

    @Override
    public List<Usuario> listar() {
        synchronized (lista) {
            return new ArrayList<>(lista);
        }
    }
}
