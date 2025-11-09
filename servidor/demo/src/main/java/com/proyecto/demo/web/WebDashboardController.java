package com.proyecto.demo.web;

import com.proyecto.demo.ui.LogBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Web controller that serves the same content as the Swing UI.
 * It reads from the in-memory LogBuffer populated by UiServerWindow so web and UI are identical.
 */
@RestController
@RequestMapping("/api/logs")
public class WebDashboardController {
    private static final Logger log = LoggerFactory.getLogger(WebDashboardController.class);

    @GetMapping("")
    public List<String> tail(@RequestParam(defaultValue = "200") int lines) {
        return LogBuffer.getLast(lines);
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return LogBuffer.createEmitter();
    }
}
