package com.cliente.cliente.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import com.cliente.cliente.service.MessageService;
import com.cliente.cliente.events.UiEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Lazy
public class ChatPanel {
    private static final Logger log = LoggerFactory.getLogger(ChatPanel.class);
    @SuppressWarnings("unused")
    private final MessageService messageService;
    @SuppressWarnings("unused")
    private final UiEventBus bus;
    private final JPanel panel;
    private final JTextArea chatArea;
    private final JTextField inputField;
    private final JButton sendBtn;

    // Executor para enviar mensajes evitando crear hilos por cada envío
    private final ExecutorService sendExec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "chat-send");
        t.setDaemon(true);
        return t;
    });

    @Autowired
    public ChatPanel(MessageService messageService, UiEventBus bus) {
        this.messageService = messageService;
        this.bus = bus;
        panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(8,8,8,8));

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setBorder(BorderFactory.createCompoundBorder(chatArea.getBorder(), new EmptyBorder(8,8,8,8)));

        inputField = new JTextField();
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        inputField.setBorder(BorderFactory.createCompoundBorder(inputField.getBorder(), new EmptyBorder(6,6,6,6)));
        // initially disabled until user logs in
        inputField.setEnabled(false);

        sendBtn = new JButton("Enviar ⏎");
        sendBtn.setPreferredSize(new Dimension(110, 36));
        sendBtn.setBackground(new Color(0, 123, 255));
        sendBtn.setForeground(Color.WHITE);
        sendBtn.setFocusPainted(false);
        sendBtn.setOpaque(true);
        sendBtn.setBorder(BorderFactory.createEmptyBorder(6,12,6,12));
        sendBtn.setToolTipText("Enviar mensaje (Enter)");
        // initially disabled until authentication
        sendBtn.setEnabled(false);

        JPanel bottom = new JPanel(new BorderLayout(6,6));
        bottom.setBorder(new EmptyBorder(6,0,0,0));
        bottom.add(inputField, BorderLayout.CENTER);
        bottom.add(sendBtn, BorderLayout.EAST);

        panel.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);

        // Enviar con Enter y botón
        sendBtn.addActionListener(e -> doSend());
        inputField.addActionListener(e -> sendBtn.doClick());

        // suscribirse a todas las líneas del servidor y mostrar en el textArea
        bus.subscribe("SERVER_LINE", payload -> {
            String s = payload == null ? "" : payload.toString();
            SwingUtilities.invokeLater(() -> {
                chatArea.append(s + "\n");
                chatArea.setCaretPosition(chatArea.getDocument().getLength());
            });
        });

        // habilitar la UI de envío cuando el servidor confirme login
        bus.subscribe("USER_LOGGED", payload -> {
            SwingUtilities.invokeLater(() -> {
                inputField.setEnabled(true);
                sendBtn.setEnabled(true);
                inputField.requestFocusInWindow();
                log.info("Usuario autenticado: habilitada UI de envío de mensajes");
            });
        });
    }

    private void doSend() {
        String msg = inputField.getText().trim();
        if (!msg.isEmpty()) {
            log.info("Enviando mensaje ({} chars)", msg.length());
            log.debug("Contenido del mensaje a enviar: {}", sanitizeLog(msg));
            sendExec.submit(() -> {
                try {
                    messageService.sendMessage(msg);
                } catch (Exception e) {
                    log.error("send failed: {}", e.toString(), e);
                }
            });
            inputField.setText("");
        }
    }

    private String sanitizeLog(String s) {
        if (s == null) return "";
        if (s.length() > 300) return s.substring(0, 300) + "...(trunc)";
        return s;
    }

    public JPanel getPanel() {
        return panel;
    }

    /**
     * Llamar al cerrar la UI para liberar el executor.
     */
    public void shutdown() {
        try {
            sendExec.shutdown();
            if (!sendExec.awaitTermination(1, TimeUnit.SECONDS)) {
                sendExec.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sendExec.shutdownNow();
        }
        log.info("ChatPanel shutdown complete");
    }
}
