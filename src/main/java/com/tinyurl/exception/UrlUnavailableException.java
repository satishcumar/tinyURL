package com.tinyurl.exception;

public class UrlUnavailableException extends RuntimeException {

    public UrlUnavailableException(String shortCode) {
        super("Short URL is expired or inactive: " + shortCode);
    }
}
