package com.proyecto.demo.auth;

public interface AuthService {

    boolean register(String id, String usuario, String password) throws Exception;

    boolean login(String id, String usuario, String password) throws Exception;

}
