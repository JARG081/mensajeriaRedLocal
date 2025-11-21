package com.proyecto.demo.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class LogStartup implements ApplicationListener<ApplicationReadyEvent> {
    private static final Logger log = LoggerFactory.getLogger(LogStartup.class);
    private final Environment env;
    private final LogService logService;

    public LogStartup(Environment env, LogService logService) {
        this.env = env;
        this.logService = logService;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Path p = logService.getLogPath();
        try {
            long fileSize = 0L;
            if (Files.exists(p)) fileSize = Files.size(p);
            boolean includePast = Boolean.parseBoolean(env.getProperty("app.log.includePast", "true"));
            if (!includePast) {
                // Only read from new lines: set startPosition to current file size
                logService.setStartPosition(fileSize);
            } else {
                // include past logs: set startPosition to 0 so tail reads from beginning
                logService.setStartPosition(0L);
            }
            log.info("LogStartup initialized. includePast={}, startPosition={}, path={}", includePast, logService.getStartPosition(), p);
        } catch (IOException e) {
            log.warn("Error initializing log startup for {}: {}", p, e.toString());
        }
    }
}
