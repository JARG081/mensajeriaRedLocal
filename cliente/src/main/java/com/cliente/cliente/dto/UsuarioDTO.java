package com.cliente.cliente.dto;

public class UsuarioDTO {
    public Double id;
    public String nombres;
    public String apellidos;
    public String email;

    public UsuarioDTO() {}

    public UsuarioDTO(Double id, String nombres, String apellidos, String email) {
        this.id = id;
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.email = email;
    }
}
