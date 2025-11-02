package com.cliente.cliente.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import javax.swing.DefaultListModel;
import java.util.List;
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
    @SuppressWarnings("unused")
    private final com.cliente.cliente.service.ClientState clientState;
    private final JPanel panel;
    private final JTextArea chatArea;
    private final JTextField inputField;
    private final JButton sendBtn;
    private final DefaultListModel<String> usersModel;
    private final JList<String> usersList;
    private volatile String targetUser = null; // null => ALL
    private final JLabel targetLabel;
    private final java.util.Map<String, java.util.List<String>> conversations = new java.util.concurrent.ConcurrentHashMap<>();

    // Executor para enviar mensajes evitando crear hilos por cada envío
    private final ExecutorService sendExec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "chat-send");
        t.setDaemon(true);
        return t;
    });

    @Autowired
    public ChatPanel(MessageService messageService, UiEventBus bus, com.cliente.cliente.service.ClientState clientState) {
        this.messageService = messageService;
        this.bus = bus;
        this.clientState = clientState;
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
    // label to show current target
    targetLabel = new JLabel("A: Todos");
    targetLabel.setBorder(new EmptyBorder(0,0,6,0));
    JPanel inputWrap = new JPanel(new BorderLayout());
    inputWrap.add(targetLabel, BorderLayout.NORTH);
    inputWrap.add(inputField, BorderLayout.CENTER);
    bottom.add(inputWrap, BorderLayout.CENTER);
    bottom.add(sendBtn, BorderLayout.EAST);

        panel.add(new JScrollPane(chatArea), BorderLayout.CENTER);
    panel.add(bottom, BorderLayout.SOUTH);

        // panel lateral con usuarios conectados
    usersModel = new DefaultListModel<>();
    usersList = new JList<>(usersModel);
    usersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    usersList.setFixedCellWidth(160);
        // click en nombre para seleccionar destinatario
        usersList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String sel = usersList.getSelectedValue();
                if (sel == null || sel.isBlank()) {
                    targetUser = null;
                    targetLabel.setText("A: Todos");
                    // clear view when selecting none
                    chatArea.setText("");
                } else {
                    targetUser = sel;
                    targetLabel.setText("A: " + sel);
                    // refresh chat area with selected conversation
                    refreshChatFor(sel);
                }
            }
        });

    JPanel side = new JPanel(new BorderLayout());
    side.setBorder(new EmptyBorder(6,6,6,6));
    side.add(new JLabel("Usuarios conectados"), BorderLayout.NORTH);
    side.add(new JScrollPane(usersList), BorderLayout.CENTER);
    side.setPreferredSize(new Dimension(180, 0));

    panel.add(side, BorderLayout.EAST);

        // Enviar con Enter y botón
        sendBtn.addActionListener(e -> doSend());
        inputField.addActionListener(e -> sendBtn.doClick());

        // recibir mensajes entrantes y colocarlos en la conversación correspondiente
        bus.subscribe("INCOMING_MSG", payload -> {
            if (!(payload instanceof com.cliente.cliente.dto.MessageDTO)) return;
            com.cliente.cliente.dto.MessageDTO dto = (com.cliente.cliente.dto.MessageDTO) payload;
            SwingUtilities.invokeLater(() -> {
                String from = dto.getSender();
                String line = String.format("[%s] %s: %s", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(dto.getTimestamp())), from, dto.getContent());
                conversations.computeIfAbsent(from, k -> new java.util.ArrayList<>()).add(line);
                // si estoy viendo la conversación con ese usuario, refrescar
                if (from.equals(targetUser)) {
                    appendLineToChat(line);
                }
            });
        });

        // actualizar la lista de usuarios conectados
        bus.subscribe("USERS_LIST", payload -> {
            SwingUtilities.invokeLater(() -> {
                usersModel.clear();
                String me = clientState == null ? null : clientState.getCurrentUser();
                if (payload instanceof List) {
                    for (Object o : (List<?>) payload) {
                        String name = o == null ? "" : o.toString();
                        // don't show myself
                        if (me != null && me.equals(name)) continue;
                        usersModel.addElement(name);
                    }
                }
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

    private void appendLineToChat(String line) {
        chatArea.append(line + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private void refreshChatFor(String user) {
        chatArea.setText("");
        if (user == null) return; // no conversation selected
        java.util.List<String> list = conversations.get(user);
        if (list == null) return;
        for (String l : list) chatArea.append(l + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private void doSend() {
        String msg = inputField.getText().trim();
        if (!msg.isEmpty()) {
            log.info("Enviando mensaje ({} chars)", msg.length());
            log.debug("Contenido del mensaje a enviar: {}", sanitizeLog(msg));
            sendExec.submit(() -> {
                try {
                    messageService.sendMessage(targetUser, msg);
                    // add to local conversation history so it persists when switching
                    String dest = targetUser == null ? "ALL" : targetUser;
                    String stamped = "[" + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()) + "] Yo -> " + dest + ": " + msg;
                    conversations.computeIfAbsent(dest, k -> new java.util.ArrayList<>()).add(stamped);
                    if ( (dest.equals("ALL") && targetUser==null) || dest.equals(targetUser) ) {
                        SwingUtilities.invokeLater(() -> appendLineToChat(stamped));
                    }
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
