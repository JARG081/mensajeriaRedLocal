package com.cliente.cliente.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import com.cliente.cliente.service.AuthClientService;
import com.cliente.cliente.events.UiEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Lazy
public class LoginPanel {
    private static final Logger log = LoggerFactory.getLogger(LoginPanel.class);

    private final AuthClientService authService;
    @SuppressWarnings("unused")
    private final UiEventBus bus;
    private final JPanel panel;
    private final JTextField userField;
    private final JPasswordField passField;
    private final JButton loginBtn;
    private final JButton registerBtn;

    @Autowired
    public LoginPanel(AuthClientService authService, UiEventBus bus) {
        this.authService = authService;
        this.bus = bus;

        panel = new JPanel(new GridLayout(3, 3, 6, 6));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));
        Font fieldFont = new Font("Segoe UI", Font.PLAIN, 14);

        userField = new JTextField();
        userField.setFont(fieldFont);
        userField.setBorder(BorderFactory.createCompoundBorder(userField.getBorder(), new EmptyBorder(6,6,6,6)));

        passField = new JPasswordField();
        passField.setFont(fieldFont);
        passField.setBorder(BorderFactory.createCompoundBorder(passField.getBorder(), new EmptyBorder(6,6,6,6)));

        loginBtn = new JButton("Login");
        registerBtn = new JButton("Registrar");

        Dimension btnSize = new Dimension(120, 36);
        loginBtn.setPreferredSize(btnSize);
        registerBtn.setPreferredSize(btnSize);

        loginBtn.setBackground(new Color(0, 123, 255));
        loginBtn.setForeground(Color.WHITE);
        loginBtn.setFocusPainted(false);
        loginBtn.setOpaque(true);
        loginBtn.setBorder(BorderFactory.createEmptyBorder(6,12,6,12));
        loginBtn.setToolTipText("Iniciar sesión (Alt+L)");
        loginBtn.setMnemonic('L');

        registerBtn.setBackground(new Color(40, 167, 69));
        registerBtn.setForeground(Color.WHITE);
        registerBtn.setFocusPainted(false);
        registerBtn.setOpaque(true);
        registerBtn.setBorder(BorderFactory.createEmptyBorder(6,12,6,12));
        registerBtn.setToolTipText("Registrar cuenta (Alt+R)");
        registerBtn.setMnemonic('R');

        panel.add(new JLabel("Usuario:"));
        panel.add(userField);
        panel.add(new JLabel());
        panel.add(new JLabel("Contraseña:"));
        panel.add(passField);
        panel.add(new JLabel());
        panel.add(loginBtn);
        panel.add(registerBtn);
        panel.add(new JLabel());

        // Acciones
        loginBtn.addActionListener(e -> doLogin());
        registerBtn.addActionListener(e -> doRegister());
        passField.addActionListener(e -> loginBtn.doClick());

        // Suscripciones de eventos del servidor
        bus.subscribe("USER_LOGGED", payload -> {
            log.info("Respuesta del servidor: login exitoso para '{}'", userField.getText());
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(panel, "Login aceptado"));
        });

        bus.subscribe("AUTH_ERROR", payload -> {
            log.warn("Error de autenticación: {}", payload);
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(panel, "Error: " + (payload != null ? payload.toString() : "desconocido"))
            );
        });

        bus.subscribe("AUTH_INFO", payload -> {
            log.info("Mensaje informativo recibido del servidor: {}", payload);
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(panel, payload != null ? payload.toString() : "Info")
            );
        });
    }

    private void doLogin() {
        String u = userField.getText().trim();
        String p = new String(passField.getPassword()).trim();
        if (u.isEmpty() || p.isEmpty()) {
            JOptionPane.showMessageDialog(panel, "Usuario y contraseña requeridos");
            return;
        }
        log.info("Intentando iniciar sesión con usuario '{}'", u);
        new Thread(() -> {
            boolean result = authService.login(u, p);
            log.info("Resultado login '{}': {}", u, result ? "éxito" : "fallo");
        }).start();
    }

    private void doRegister() {
        String u = userField.getText().trim();
        String p = new String(passField.getPassword()).trim();
        if (u.isEmpty() || p.isEmpty()) {
            JOptionPane.showMessageDialog(panel, "Usuario y contraseña requeridos");
            return;
        }
        log.info("Intentando registrar nuevo usuario '{}'", u);
        new Thread(() -> {
            boolean result = authService.register(u, p);
            log.info("Resultado registro '{}': {}", u, result ? "registrado" : "rechazado (ya existe)");
        }).start();
    }

    public JPanel getPanel() {
        return panel;
    }
}
