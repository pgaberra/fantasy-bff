package com.fantasy.bff.exception;

public class DownstreamServiceException extends BffException {

    public DownstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
