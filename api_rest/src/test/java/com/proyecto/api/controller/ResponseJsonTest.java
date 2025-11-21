package com.proyecto.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class ResponseJsonTest {

    @Test
    public void responseJsonIsAvailableOnClasspath() throws Exception {
        ClassPathResource r = new ClassPathResource("response.json");
        assertTrue(r.exists(), "response.json should exist on classpath");
        String s = new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        assertTrue(s.startsWith("{"), "content should be JSON");
        assertTrue(s.contains("\"nombre\":\"manuel\""), "fixture should contain nombre 'manuel'");
    }
}
