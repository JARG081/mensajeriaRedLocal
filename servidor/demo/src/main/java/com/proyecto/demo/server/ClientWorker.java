package com.proyecto.demo.server;

import com.proyecto.demo.auth.FileAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ClientWorker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ClientWorker.class);

    private final Socket socket;
    private final FileAuthService authService;
    private String authenticatedUser = null;
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ClientWorker(Socket socket, FileAuthService authService) {
        this.socket = socket;
        this.authService = authService;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {

            out.write("WELCOME\n");
            out.flush();
            log.info("Cliente {} conectado y saludado.", socket.getRemoteSocketAddress());

            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("REGISTER ")) {
                    handleRegister(line, out);
                    continue;
                }
                if (line.startsWith("LOGIN ")) {
                    handleLogin(line, out);
                    continue;
                }
                if (line.startsWith("MSG ")) {
                    handleMessage(line, out);
                    continue;
                }
                if (line.equals("QUIT")) {
                    out.write("BYE\n");
                    out.flush();
                    break;
                }

                out.write("ERROR comando_desconocido\n");
                out.flush();
            }

        } catch (Exception e) {
            log.error("Error en worker para cliente {}: {}", socket.getRemoteSocketAddress(), e.toString());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            if (authenticatedUser != null)
                log.info("Usuario '{}' desconectado.", authenticatedUser);
            else
                log.info("Cliente {} desconectado sin autenticarse.", socket.getRemoteSocketAddress());
        }
    }

    private void handleRegister(String line, BufferedWriter out) throws Exception {
        log.info("mensaje llega a funcion de ClientWorker.handleRegister correctamente. Raw='{}'", line);
        String payload = line.substring(9);
        String[] p = payload.split("\\|", 2);
        if (p.length < 2) {
            out.write("ERROR formato REGISTER usuario|password\n");
            out.flush();
            return;
        }

        boolean ok = authService.register(p[0], p[1]);
        if (ok) {
            log.info("Registro exitoso de usuario '{}' desde {}", p[0], socket.getRemoteSocketAddress());
            try { com.proyecto.demo.ui.UiServerWindow.publishMessageToUi("Registro exitoso: " + p[0] + " desde " + socket.getRemoteSocketAddress()); } catch (Exception ignored) {}
            out.write("REGISTERED\n");
        } else {
            log.warn("Registro fallido (usuario existente): '{}' desde {}", p[0], socket.getRemoteSocketAddress());
            try { com.proyecto.demo.ui.UiServerWindow.publishMessageToUi("Registro fallido (usuario existente): " + p[0] + " desde " + socket.getRemoteSocketAddress()); } catch (Exception ignored) {}
            out.write("ERROR usuario_existente\n");
        }
        out.flush();
    }

    private void handleLogin(String line, BufferedWriter out) throws Exception {
        log.info("mensaje llega a funcion de ClientWorker.handleLogin correctamente. Raw='{}'", line);
        String payload = line.substring(6);
        String[] p = payload.split("\\|", 2);
        if (p.length < 2) {
            out.write("ERROR formato LOGIN usuario|password\n");
            out.flush();
            return;
        }

        boolean ok = authService.login(p[0], p[1]);

        if (ok) {
            authenticatedUser = p[0];
            log.info("Login exitoso: usuario='{}' desde {}", authenticatedUser, socket.getRemoteSocketAddress());
            try { com.proyecto.demo.ui.UiServerWindow.publishMessageToUi("Login exitoso: " + authenticatedUser + " desde " + socket.getRemoteSocketAddress()); } catch (Exception ignored) {}
            out.write("LOGGED\n");
        } else {
            log.warn("Login fallido: usuario='{}' desde {}", p[0], socket.getRemoteSocketAddress());
            try { com.proyecto.demo.ui.UiServerWindow.publishMessageToUi("Login fallido: " + p[0] + " desde " + socket.getRemoteSocketAddress()); } catch (Exception ignored) {}
            out.write("ERROR credenciales\n");
        }
        out.flush();
    }

    private void handleMessage(String line, BufferedWriter out) throws IOException {
        log.info("mensaje llega a funcion de ClientWorker.handleMessage correctamente. Raw='{}'", line);
        if (authenticatedUser == null) {
            log.warn("Intento de envío sin autenticación desde {}", socket.getRemoteSocketAddress());
            try { com.proyecto.demo.ui.UiServerWindow.publishMessageToUi("Intento de mensaje sin autenticacion desde " + socket.getRemoteSocketAddress()); } catch (Exception ignored) {}
            out.write("ERROR no_autenticado\n");
            out.flush();
            return;
        }

        String mensaje = line.substring(4);
        String hora = LocalDateTime.now().format(fmt);
        log.info("[{}] {}: {}", hora, authenticatedUser, mensaje);

        // Traza visible por consola
        System.out.println("[MSG] " + hora + " " + authenticatedUser + ": " + mensaje +
                " (desde " + socket.getRemoteSocketAddress() + ")");
    try { com.proyecto.demo.ui.UiServerWindow.publishMessageToUi("[MSG] " + hora + " " + authenticatedUser + ": " + mensaje + " (desde " + socket.getRemoteSocketAddress() + ")"); } catch (Exception ignored) {}

        out.write("SENT\n");
        out.flush();
        System.out.println("[RESP] SENT -> " + authenticatedUser + " (" + socket.getRemoteSocketAddress() + ")");
    }
}
