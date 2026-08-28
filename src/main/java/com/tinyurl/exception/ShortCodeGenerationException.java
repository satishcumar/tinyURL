package com.tinyurl.exception;

public class ShortCodeGenerationException extends RuntimeException {

    public ShortCodeGenerationException() {
        super("Unable to generate a unique short code");
    }
}
