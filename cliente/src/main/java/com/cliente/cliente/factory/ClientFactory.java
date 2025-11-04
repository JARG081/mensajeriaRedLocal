package com.cliente.cliente.factory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;


public final class ClientFactory {
    private ClientFactory() { }

    public static Socket createSocket(String host, int port) throws IOException {
        return new Socket(host, port);
    }


    public static Socket createSocketWithTimeout(String host, int port, int connectTimeoutMillis, int soTimeoutMillis, boolean reuseAddress) throws IOException {
        Socket s = new Socket();
        if (reuseAddress) {
            try { s.setReuseAddress(true); } catch (Exception ignored) {}
        }
        s.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
        try { s.setSoTimeout(soTimeoutMillis); } catch (Exception ignored) {}
        return s;
    }

    public static BufferedReader createReader(Socket socket) throws IOException {
        return createReader(socket, StandardCharsets.UTF_8);
    }

    public static BufferedReader createReader(Socket socket, Charset cs) throws IOException {
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), cs));
    }

    public static BufferedWriter createWriter(Socket socket) throws IOException {
        return createWriter(socket, StandardCharsets.UTF_8);
    }

    public static BufferedWriter createWriter(Socket socket, Charset cs) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), cs));
    }

    public static ExecutorService createSingleThreadExecutor(String threadName, boolean daemon) {
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, threadName);
            t.setDaemon(daemon);
            return t;
        };
        return Executors.newSingleThreadExecutor(tf);
    }

    public static Thread createThread(Runnable r, String name, boolean daemon) {
        Thread t = new Thread(r, name);
        t.setDaemon(daemon);
        return t;
    }

    public static <T> ArrayList<T> createArrayList() {
        return new ArrayList<>();
    }

    public static <T> ArrayList<T> createArrayList(Collection<T> src) {
        return new ArrayList<>(src);
    }

    public static java.io.PrintWriter createPrintWriter(Socket socket, Charset cs, boolean autoFlush) throws IOException {
        return new java.io.PrintWriter(new OutputStreamWriter(socket.getOutputStream(), cs), autoFlush);
    }

    public static com.cliente.cliente.dto.MessageDTO createMessageDTO(String sender, String text, long timestamp) {
        return new com.cliente.cliente.dto.MessageDTO(sender, text, timestamp);
    }

    public static com.cliente.cliente.dto.FileDTO createFileDTO(String sender, String filename, byte[] data, long timestamp) {
        return new com.cliente.cliente.dto.FileDTO(sender, filename, data, timestamp);
    }

    public static java.util.concurrent.atomic.AtomicBoolean createAtomicBoolean(boolean value) {
        return new java.util.concurrent.atomic.AtomicBoolean(value);
    }

    public static Object createLockObject() {
        return new Object();
    }
}
