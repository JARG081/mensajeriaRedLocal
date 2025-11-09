package com.cliente.cliente.service;

import com.cliente.cliente.model.Usuario;
import java.util.List;

public interface UsuarioService {
    void registrar(Double id, String nombres, String apellidos, String email);
    boolean actualizar(Double id, String nombres, String apellidos, String email);
    boolean eliminar(Double id);
    Usuario buscar(Double id);
    List<Usuario> listar();
}
