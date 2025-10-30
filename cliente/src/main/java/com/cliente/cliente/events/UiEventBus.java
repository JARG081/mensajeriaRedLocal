package com.cliente.cliente.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Component
public class UiEventBus {
    private static final Logger log = LoggerFactory.getLogger(UiEventBus.class);

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Consumer<Object>>> subs = new ConcurrentHashMap<>();

    public void subscribe(String topic, Consumer<Object> handler) {
        subs.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    public void publish(String topic, Object payload) {
        var list = subs.get(topic);
        if (list == null) return;
        for (var c : list) {
            try {
                c.accept(payload);
            } catch (Exception e) {
                log.error("Handler error for topic '{}': {}", topic, e.toString(), e);
            }
        }
    }
}
