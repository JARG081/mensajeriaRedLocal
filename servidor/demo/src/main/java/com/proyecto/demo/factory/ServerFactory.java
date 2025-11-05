package com.proyecto.demo.factory;

import com.proyecto.demo.auth.AuthService;
import com.proyecto.demo.server.ClientWorker;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;
import java.io.RandomAccessFile;

public final class ServerFactory {

    private ServerFactory() { }

 
    public static ServerSocket createBoundServerSocket(String bindAddress, int port) throws IOException {
        ServerSocket ss = new ServerSocket();
        ss.bind(new InetSocketAddress(bindAddress, port));
        return ss;
    }

    public static ExecutorService createCachedThreadPool() {
        return Executors.newCachedThreadPool();
    }


    public static Thread createServerThread(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(false);
        return t;
    }


    public static ClientWorker createClientWorker(Socket socket, AuthService authService, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        return new ClientWorker(socket, authService, jdbc);
    }


    public static BufferedReader createBufferedReader(Socket socket) throws java.io.IOException {
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    public static BufferedWriter createBufferedWriter(Socket socket) throws java.io.IOException {
        return new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }


    public static <T> ArrayList<T> createArrayList(Collection<T> src) {
        return new ArrayList<>(src);
    }

    public static <T> ArrayList<T> createArrayList() {
        return new ArrayList<>();
    }

    public static <K,V> ConcurrentHashMap<K,V> createConcurrentHashMap() {
        return new ConcurrentHashMap<>();
    }

    public static Properties createProperties() {
        return new Properties();
    }


    public static File createFile(String path) {
        return new File(path);
    }


    public static RandomAccessFile createRandomAccessFile(File f, String mode) throws java.io.IOException {
        return new RandomAccessFile(f, mode);
    }
}
