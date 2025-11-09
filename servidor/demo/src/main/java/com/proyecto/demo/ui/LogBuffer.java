package com.proyecto.demo.ui;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Simple in-memory log buffer with SSE emitter support.
 * Stores the last N log lines and broadcasts new lines to connected SSE clients.
 */
public class LogBuffer {

    private static final int MAX_LINES = 2000;
    private static final Deque<String> lines = new LinkedList<>();
    private static final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public static synchronized void add(String line) {
        if (line == null) return;
        lines.addLast(line);
        if (lines.size() > MAX_LINES) {
            lines.removeFirst();
        }
        // Broadcast to SSE clients
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().data(line));
            } catch (IOException e) {
                // remove broken emitter
                emitters.remove(emitter);
                try { emitter.complete(); } catch (Exception ignored) {}
            }
        }
    }

    public static synchronized List<String> getLast(int n) {
        if (n <= 0) return List.of();
        List<String> out = new ArrayList<>(Math.min(n, lines.size()));
        int skip = Math.max(0, lines.size() - n);
        int i = 0;
        for (String s : lines) {
            if (i++ < skip) continue;
            out.add(s);
        }
        return out;
    }

    public static SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(0L); // no timeout
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }
}
