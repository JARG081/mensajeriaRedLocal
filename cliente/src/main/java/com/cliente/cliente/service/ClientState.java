package com.cliente.cliente.service;

import org.springframework.stereotype.Component;

/**
 * Simple shared client state: current username.
 */
@Component
public class ClientState {
    private volatile String currentUser;

    public String getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(String currentUser) {
        this.currentUser = currentUser;
    }
}
