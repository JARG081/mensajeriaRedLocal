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
    panel = new JPanel(new GridBagLayout());
    panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        // === SECCIÓN 1: CONTROL GENERAL DE TAMAÑOS ===
        // Aquí puedes modificar:
        // - Porcentaje máximo de ancho para los campos (70%)
        // - Fuente general
        // - Tamaño de botones
        int fieldPercentWidth = 60; // <-- CAMBIA AQUÍ el porcentaje máximo del ancho de los campos
        Font fieldFont = new Font("Segoe UI", Font.PLAIN, 13);
        Dimension btnSize = new Dimension(100, 28); // <-- tamaño botones (ancho, alto)

        // Calcula ancho máximo de campo relativo al panel
        int panelWidth = 400; // valor base de referencia (puedes ajustarlo)
        int maxFieldWidth = (int) (panelWidth * (fieldPercentWidth / 100.0)); // <-- este 70% del panel

        Dimension fieldSize = new Dimension(maxFieldWidth, 35); // <-- tamaño campos

        // === SECCIÓN 2: CONFIGURACIÓN DE CAMPOS ===
        userField = new JTextField();
        userField.setFont(fieldFont);
        userField.setPreferredSize(fieldSize);
        userField.setMaximumSize(fieldSize);
        userField.setBorder(BorderFactory.createCompoundBorder(userField.getBorder(), new EmptyBorder(4,6,4,6)));

        passField = new JPasswordField();
        passField.setFont(fieldFont);
        passField.setPreferredSize(fieldSize);
        passField.setMaximumSize(fieldSize);
        passField.setBorder(BorderFactory.createCompoundBorder(passField.getBorder(), new EmptyBorder(4,6,4,6)));

        // === SECCIÓN 3: CONFIGURACIÓN DE BOTONES ===
        loginBtn = new JButton("Login");
        registerBtn = new JButton("Registrar");

        loginBtn.setPreferredSize(btnSize);
        registerBtn.setPreferredSize(btnSize);

        // Apariencia botones
        loginBtn.setBackground(new Color(0, 123, 255));
        loginBtn.setForeground(Color.WHITE);
        loginBtn.setFocusPainted(false);
        loginBtn.setOpaque(true);
        loginBtn.setBorder(BorderFactory.createEmptyBorder(4,10,4,10));
        loginBtn.setToolTipText("Iniciar sesión (Alt+L)");
        loginBtn.setMnemonic('L');

        registerBtn.setBackground(new Color(40, 167, 69));
        registerBtn.setForeground(Color.WHITE);
        registerBtn.setFocusPainted(false);
        registerBtn.setOpaque(true);
        registerBtn.setBorder(BorderFactory.createEmptyBorder(4,10,4,10));
        registerBtn.setToolTipText("Registrar cuenta (Alt+R)");
        registerBtn.setMnemonic('R');

        // === SECCIÓN 4: LAYOUT ===
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6,6,6,6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;

        // Fila 0
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        panel.add(new JLabel("Usuario:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(userField, gbc);

        // Fila 1
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Contraseña:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(passField, gbc);

        // Fila 2
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btnRow.setOpaque(false);
        btnRow.add(loginBtn);
        btnRow.add(registerBtn);
        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 1; gbc.fill = GridBagConstraints.NONE;
        panel.add(btnRow, gbc);

        // === SECCIÓN 5: ACCIONES ===
        loginBtn.addActionListener(e -> doLogin());
        registerBtn.addActionListener(e -> doRegister());
        passField.addActionListener(e -> loginBtn.doClick());

        // === SECCIÓN 6: EVENTOS SERVIDOR ===
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
