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
    private final java.util.Map<String, java.util.List<com.cliente.cliente.dto.FileDTO>> filesPerConversation = new java.util.concurrent.ConcurrentHashMap<>();
    private final DefaultListModel<String> filesModel = new DefaultListModel<>();
    private final JList<String> filesList = new JList<>(filesModel);
    private final JButton attachBtn = new JButton("Adjuntar");
    private final JButton downloadBtn = new JButton("Descargar");

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
    JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    attachBtn.setPreferredSize(new Dimension(100, 36));
    attachBtn.setFocusPainted(false);
    controls.add(attachBtn);
    controls.add(sendBtn);
    bottom.add(controls, BorderLayout.EAST);

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
                    refreshFilesListFor(sel);
                }
            }
        });

    JPanel side = new JPanel(new BorderLayout());
    side.setBorder(new EmptyBorder(6,6,6,6));
    side.add(new JLabel("Usuarios conectados"), BorderLayout.NORTH);
    side.add(new JScrollPane(usersList), BorderLayout.CENTER);
    // panel de archivos recibidos/enviados por conversación
    JPanel filesPanel = new JPanel(new BorderLayout());
    filesPanel.setBorder(new EmptyBorder(8,0,0,0));
    filesPanel.add(new JLabel("Archivos"), BorderLayout.NORTH);
    filesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    filesList.setFixedCellWidth(160);
    filesPanel.add(new JScrollPane(filesList), BorderLayout.CENTER);
    JPanel filesBtns = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
    filesBtns.add(downloadBtn);
    filesPanel.add(filesBtns, BorderLayout.SOUTH);
    side.add(filesPanel, BorderLayout.SOUTH);
    side.setPreferredSize(new Dimension(180, 0));

    panel.add(side, BorderLayout.EAST);

        // Enviar con Enter y botón
        sendBtn.addActionListener(e -> doSend());
        inputField.addActionListener(e -> sendBtn.doClick());

        // Adjuntar archivo
        attachBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            int res = chooser.showOpenDialog(panel);
            if (res == JFileChooser.APPROVE_OPTION) {
                java.io.File f = chooser.getSelectedFile();
                sendExec.submit(() -> {
                    try {
                        messageService.sendFile(targetUser, f);
                        // Do not show a generic dialog here on failure; MessageService will publish detailed errors
                        // and the UI subscribes to AUTH_ERROR to display them. This avoids duplicate alerts.
                        // On success, MessageService will publish FILE_SENT event and UI will update via subscription
                    } catch (Exception ex) {
                        log.error("Error enviando archivo: {}", ex.toString(), ex);
                    }
                });
            }
        });

        // Descargar archivo seleccionado
        downloadBtn.addActionListener(e -> {
            String sel = filesList.getSelectedValue();
            if (sel == null) return;
            String user = targetUser;
            if (user == null) return;
            java.util.List<com.cliente.cliente.dto.FileDTO> list = filesPerConversation.get(user);
            if (list == null) return;
            com.cliente.cliente.dto.FileDTO chosen = null;
            for (com.cliente.cliente.dto.FileDTO fd : list) if (fd.getFilename().equals(sel)) { chosen = fd; break; }
            if (chosen == null) return;
            JFileChooser saver = new JFileChooser();
            saver.setSelectedFile(new java.io.File(chosen.getFilename()));
            int ret = saver.showSaveDialog(panel);
            if (ret == JFileChooser.APPROVE_OPTION) {
                java.io.File out = saver.getSelectedFile();
                try { java.nio.file.Files.write(out.toPath(), chosen.getContent()); SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(panel, "Archivo guardado: " + out.getAbsolutePath())); }
                catch (Exception ex) { SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(panel, "Error guardando archivo: " + ex.getMessage())); }
            }
        });

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

        // recibir archivos entrantes: almacenar archivo y refrescar lista, pero no añadir una línea de chat
        // porque el servidor ya envía un MSGFROM "(file) filename" que se muestra como mensaje.
            bus.subscribe("INCOMING_FILE", payload -> {
                if (!(payload instanceof com.cliente.cliente.dto.FileDTO)) return;
                com.cliente.cliente.dto.FileDTO dto = (com.cliente.cliente.dto.FileDTO) payload;
                SwingUtilities.invokeLater(() -> {
                    String from = dto.getSender();
                    filesPerConversation.computeIfAbsent(from, k -> new java.util.ArrayList<>()).add(dto);
                    // actualizar lista de archivos si la conversación visible corresponde
                    if (from.equals(targetUser)) {
                        refreshFilesListFor(from);
                    }
                });
            });

        // archivo enviado con éxito (confirmación del servidor)
        // Add file to local files list, but DO NOT add a chat line because the server will broadcast a MSGFROM (file) which
        // will be shown in the conversation. This avoids duplicate messages for the sender.
        bus.subscribe("FILE_SENT", payload -> {
            if (!(payload instanceof com.cliente.cliente.dto.FileDTO)) return;
            com.cliente.cliente.dto.FileDTO dto = (com.cliente.cliente.dto.FileDTO) payload;
            SwingUtilities.invokeLater(() -> {
                String dest = targetUser == null ? "ALL" : targetUser;
                filesPerConversation.computeIfAbsent(dest, k -> new java.util.ArrayList<>()).add(dto);
                if ((dest.equals("ALL") && targetUser==null) || dest.equals(targetUser)) {
                    refreshFilesListFor(dest);
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

        // Procesar líneas crudas del servidor o histórico guardado
        bus.subscribe("SERVER_LINE", payload -> {
            if (!(payload instanceof String)) return;
            String raw = (String) payload;
            // quitar timestamp inicial si existe (formato: <ts> <rest>)
            String afterTs = raw;
            int idx = raw.indexOf(' ');
            if (idx > 0 && idx < raw.length()-1) afterTs = raw.substring(idx+1);
            // si viene con marcador [SRV] quitarlo
            if (afterTs.startsWith("[SRV] ")) afterTs = afterTs.substring(6);

            // manejar MSGFROM (servidor) -> publicar como mensaje entrante
            if (afterTs.startsWith("MSGFROM ")) {
                String payloadStr = afterTs.substring(8);
                String[] p = payloadStr.split("\\|", 2);
                String sender = p.length > 0 ? p[0] : "";
                String text = p.length > 1 ? p[1] : "";
                String line = String.format("[%s] %s: %s", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()), sender, text);
                conversations.computeIfAbsent(sender, k -> new java.util.ArrayList<>()).add(line);
                // si estoy viendo esa conversación, añadirla
                SwingUtilities.invokeLater(() -> {
                    if (sender.equals(targetUser)) appendLineToChat(line);
                });
                return;
            }

            // FILEFROM historic or live
            if (afterTs.startsWith("FILEFROM ")) {
                try {
                    String payloadStr = afterTs.substring(9);
                    String[] p = payloadStr.split("\\|", 3);
                    String sender = p.length > 0 ? p[0] : "";
                    String filename = p.length > 1 ? p[1] : "";
                    String line = String.format("[%s] %s: (archivo) %s", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()), sender, filename);
                    conversations.computeIfAbsent(sender, k -> new java.util.ArrayList<>()).add(line);
                    SwingUtilities.invokeLater(() -> { if (sender.equals(targetUser)) appendLineToChat(line); });
                } catch (Exception ignored) {}
                return;
            }

            // Mensajes que se guardaron localmente con formato "... Yo -> dest: text" o similares
            if (afterTs.contains("Yo -> ") || afterTs.contains("->")) {
                try {
                    String work = afterTs;
                    // si tiene un timestamp extra entre corchetes, quitar hasta "] "
                    int rb = work.indexOf("] ");
                    if (rb >= 0 && rb < work.length()-2) work = work.substring(rb+2);
                    // ahora buscar "Yo -> "
                    int yoIdx = work.indexOf("Yo -> ");
                    String sender = "Yo";
                    String rest = work;
                    if (yoIdx >= 0) rest = work.substring(yoIdx + "Yo -> ".length());
                    // rest expected like "DEST: message"
                    int colon = rest.indexOf(":");
                    String dest = (colon > 0) ? rest.substring(0, colon).trim() : "ALL";
                    String msg = (colon > 0 && colon < rest.length()-1) ? rest.substring(colon+1).trim() : rest.trim();
                    String line = String.format("[%s] %s: %s", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()), sender, msg);
                    // conversation key: dest (use "ALL" for broadcast)
                    String key = dest.equalsIgnoreCase("ALL") ? "ALL" : dest;
                    conversations.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(line);
                    SwingUtilities.invokeLater(() -> { if (key.equals(targetUser) || (key.equals("ALL") && targetUser==null)) appendLineToChat(line); });
                } catch (Exception ignored) {}
            }
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

    private void refreshFilesListFor(String user) {
        filesModel.clear();
        if (user == null) return;
        java.util.List<com.cliente.cliente.dto.FileDTO> list = filesPerConversation.get(user);
        if (list == null) return;
        for (com.cliente.cliente.dto.FileDTO f : list) {
            filesModel.addElement(f.getFilename());
        }
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
                    String sender = clientState == null ? "Yo" : clientState.getCurrentUser();
                    String stamped = "[" + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()) + "] " + sender + ": " + msg;
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
