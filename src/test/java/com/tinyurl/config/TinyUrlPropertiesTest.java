package com.tinyurl.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TinyUrlPropertiesTest {

    @Test
    void baseUrlRemovesTrailingSlash() {
        TinyUrlProperties properties = new TinyUrlProperties();
        properties.setBaseUrl("https://tiny.example/");

        assertEquals("https://tiny.example", properties.getBaseUrl());
    }
}
