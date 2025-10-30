package com.proyecto.demo.auth;

public interface AuthService {
    boolean register(String usuario, String password) throws Exception;
    boolean login(String usuario, String password) throws Exception;

}
