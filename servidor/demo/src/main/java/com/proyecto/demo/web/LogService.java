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
    // position in bytes at application startup; used to filter only current-execution logs
    private volatile long startPosition = 0L;

    public LogService(Environment env) {
        this.env = env;
    }

    public Path getLogPath() {
        String fileName = env.getProperty("logging.file.name");
        if (fileName == null || fileName.isBlank()) fileName = "server_logs.txt";

        // Try to locate repository root named 'mensajeriaRedLocal' by walking up from working dir.
        Path userDir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path repoRoot = null;
        Path cur = userDir;
        while (cur != null) {
            Path name = cur.getFileName();
            if (name != null && "mensajeriaRedLocal".equals(name.toString())) {
                repoRoot = cur;
                break;
            }
            cur = cur.getParent();
        }

        Path logDir;
        if (repoRoot != null) {
            // ensure it's the servidor folder inside repo
            logDir = repoRoot.resolve("servidor");
        } else {
            // fallback to 'servidor' under working directory
            logDir = userDir.resolve("servidor");
        }

        try {
            if (!Files.exists(logDir)) Files.createDirectories(logDir);
        } catch (IOException e) {
            log.warn("No se pudo crear carpeta de logs {}: {}", logDir, e.toString());
        }

        Path p = logDir.resolve(fileName).toAbsolutePath();
        // Ensure file exists (create empty if missing) but do NOT truncate existing
        try {
            if (!Files.exists(p)) Files.createFile(p);
        } catch (IOException e) {
            log.warn("No se pudo crear archivo de log {}: {}", p, e.toString());
        }
        return p;
    }

    public List<String> tail(int maxLines) {
        Path p = getLogPath();
        List<String> out = new ArrayList<>();
        try {
            if (!Files.exists(p)) return out;
            boolean includePast = Boolean.parseBoolean(env.getProperty("app.log.includePast", "true"));
            if (includePast) {
                List<String> all = Files.readAllLines(p, StandardCharsets.UTF_8);
                int start = Math.max(0, all.size() - maxLines);
                for (int i = start; i < all.size(); i++) out.add(all.get(i));
            } else {
                // read only appended lines since application start
                TailResult tr = readAppended(startPosition);
                List<String> lines = tr.lines;
                int start = Math.max(0, lines.size() - maxLines);
                for (int i = start; i < lines.size(); i++) out.add(lines.get(i));
            }
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

    /**
     * Set the byte position corresponding to application startup. Use this to filter logs.
     */
    public void setStartPosition(long pos) {
        this.startPosition = pos;
    }

    public long getStartPosition() {
        return this.startPosition;
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
