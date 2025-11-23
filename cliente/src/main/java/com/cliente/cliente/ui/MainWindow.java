package com.cliente.cliente.ui;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import javax.swing.*;
import java.awt.*;
import com.cliente.cliente.events.UiEventBus;

@Component
@Lazy
public class MainWindow {
    @SuppressWarnings("unused")
    private final LoginPanel loginPanel;
    @SuppressWarnings("unused")
    private final ChatPanel chatPanel;
    @SuppressWarnings("unused")
    private final UiEventBus bus;
    private final JFrame frame;
    private final JPanel cards;
    private final CardLayout cardLayout;

    @Autowired
    public MainWindow(LoginPanel loginPanel, ChatPanel chatPanel, UiEventBus bus) {
        this.loginPanel = loginPanel;
        this.chatPanel = chatPanel;
        this.bus = bus;

        frame = new JFrame("Cliente Chat");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        cards = new JPanel(cardLayout);

        cards.add(loginPanel.getPanel(), "LOGIN");
        cards.add(chatPanel.getPanel(), "CHAT");

        frame.setLayout(new BorderLayout());
        frame.add(cards, BorderLayout.CENTER);

        JMenuBar mb = new JMenuBar();
        JMenu m = new JMenu("Ventanas");
        JMenuItem miLogin = new JMenuItem("Login");
        JMenuItem miChat = new JMenuItem("Chat");
        miLogin.addActionListener(e -> showLogin());
        miChat.addActionListener(e -> showChat());
        m.add(miLogin); m.add(miChat); mb.add(m);
        frame.setJMenuBar(mb);
        bus.subscribe("USER_LOGGED", payload -> SwingUtilities.invokeLater(() -> {
            // payload is expected to be the username
            if (payload != null) {
                frame.setTitle("Cliente Chat - " + payload.toString());
            }
            showChat();
        }));
        // Manejar logout desde el cliente: mostrar login y limpiar título
        bus.subscribe("USER_LOGOUT", payload -> SwingUtilities.invokeLater(() -> {
            frame.setTitle("Cliente Chat");
            showLogin();
        }));
    }

    public void show() {
        SwingUtilities.invokeLater(() -> {
            frame.setVisible(true);
            showLogin();
        });
    }

    public void showLogin() {
        cardLayout.show(cards, "LOGIN");
    }

    public void showChat() {
        cardLayout.show(cards, "CHAT");
    }

    public JFrame getFrame() { return frame; }
}
