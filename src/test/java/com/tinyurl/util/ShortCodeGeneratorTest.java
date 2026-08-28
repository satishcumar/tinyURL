package com.tinyurl.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShortCodeGeneratorTest {

    private final ShortCodeGenerator generator = new ShortCodeGenerator();

    @Test
    void generateReturnsSevenAlphaNumericCharacters() {
        for (int i = 0; i < 100; i++) {
            assertTrue(generator.generate().matches("[A-Za-z0-9]{7}"));
        }
    }
}
