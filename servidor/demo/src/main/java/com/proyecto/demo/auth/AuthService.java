package com.proyecto.demo.auth;

public interface AuthService {
    /**
     * Registra un usuario con un id proporcionado (no autoincremental).
     * @param id identificador proporcionado por el cliente (string que representa un entero largo)
     */
    boolean register(String id, String usuario, String password) throws Exception;
    boolean login(String usuario, String password) throws Exception;

}
