package com.proyecto.demo.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class LogService {
    private static final Logger log = LoggerFactory.getLogger(LogService.class);
    private final Environment env;

    public LogService(Environment env) {
        this.env = env;
    }

    public Path getLogPath() {
        String p = env.getProperty("logging.file.name");
        if (p == null || p.isBlank()) p = "servidor.log";
        return Path.of(p).toAbsolutePath();
    }

    public List<String> tail(int maxLines) {
        Path p = getLogPath();
        List<String> out = new ArrayList<>();
        try {
            if (!Files.exists(p)) return out;
            List<String> all = Files.readAllLines(p, StandardCharsets.UTF_8);
            int start = Math.max(0, all.size() - maxLines);
            for (int i = start; i < all.size(); i++) out.add(all.get(i));
        } catch (IOException e) {
            log.warn("No se pudo leer log {}: {}", p, e.toString());
        }
        return out;
    }

    /**
     * Read appended lines since position. Returns pair: newPosition and list of lines.
     */
    public TailResult readAppended(long position) {
        Path p = getLogPath();
        List<String> lines = new ArrayList<>();
        long newPos = position;
        try {
            if (!Files.exists(p)) return new TailResult(newPos, lines);
            try (RandomAccessFile raf = new RandomAccessFile(p.toFile(), "r")) {
                long len = raf.length();
                if (position > len) position = 0; // file rotated/truncated
                raf.seek(position);
                String s;
                while ((s = raf.readLine()) != null) {
                    // RandomAccessFile.readLine reads ISO-8859-1 bytes; convert properly
                    byte[] bytes = s.getBytes("ISO-8859-1");
                    lines.add(new String(bytes, StandardCharsets.UTF_8));
                }
                newPos = raf.getFilePointer();
            }
        } catch (IOException e) {
            log.warn("Error leyendo append log {}: {}", p, e.toString());
        }
        return new TailResult(newPos, lines);
    }

    public static class TailResult {
        public final long position;
        public final List<String> lines;

        public TailResult(long position, List<String> lines) {
            this.position = position;
            this.lines = lines;
        }
    }
}
