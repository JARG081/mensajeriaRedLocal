package com.proyecto.demo.auth;

public interface AuthService {
    /**
     * Registra un usuario con un id proporcionado (no autoincremental).
     * @param id identificador proporcionado por el cliente (string que representa un entero largo)
     */
    boolean register(String id, String usuario, String password) throws Exception;
    /**
     * Login comparing explicit id, username and password.
     * The implementation must verify that the row with the provided id exists
     * and that its username and password-hash match the provided values.
     */
    boolean login(String id, String usuario, String password) throws Exception;

}
