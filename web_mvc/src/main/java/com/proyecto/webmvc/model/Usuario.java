package com.proyecto.webmvc.model;

public class Usuario {
    private Long id;
    private String nombreUsuario;

    public Usuario() {}

    public Usuario(Long id, String nombreUsuario) {
        this.id = id;
        this.nombreUsuario = nombreUsuario;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombreUsuario() { return nombreUsuario; }
    public void setNombreUsuario(String nombreUsuario) { this.nombreUsuario = nombreUsuario; }

    // compatibility accessor used in templates/services expecting 'nombre'
    public String getNombre() { return nombreUsuario; }
    public void setNombre(String nombre) { this.nombreUsuario = nombre; }
}
