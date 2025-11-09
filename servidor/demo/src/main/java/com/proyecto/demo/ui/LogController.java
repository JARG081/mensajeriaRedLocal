package com.proyecto.demo.ui;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Disabled REST wrapper. Previously this class exposed /api/ui-logs.
 * Endpoints are now served by `WebDashboardController` which reads from `LogBuffer`.
 *
 * We keep a non-annotated helper so other code can reuse the methods if needed.
 */
public class LogController {

    public static List<String> getLogs(int lines) {
        return LogBuffer.getLast(lines);
    }

    public static SseEmitter createStream() {
        return LogBuffer.createEmitter();
    }
}
