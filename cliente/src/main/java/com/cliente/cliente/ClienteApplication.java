package com.cliente.cliente;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import com.cliente.cliente.ui.MainWindow;

import javax.swing.*;

@SpringBootApplication(scanBasePackages = "com.cliente")
public class ClienteApplication {
    public static void main(String[] args) {
        // Desactivar el modo headless para permitir Swing
        System.setProperty("java.awt.headless", "false");

        ConfigurableApplicationContext ctx = SpringApplication.run(ClienteApplication.class, args);
        SwingUtilities.invokeLater(() -> {
            MainWindow win = ctx.getBean(MainWindow.class);
            win.show();
        });
    }
}
