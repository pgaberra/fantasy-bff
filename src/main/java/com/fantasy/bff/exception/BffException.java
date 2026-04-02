package com.fantasy.bff.exception;

public class BffException extends RuntimeException {

    public BffException(String message) {
        super(message);
    }

    public BffException(String message, Throwable cause) {
        super(message, cause);
    }
}
