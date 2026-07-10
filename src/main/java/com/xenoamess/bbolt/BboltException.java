package com.xenoamess.bbolt;

public class BboltException extends RuntimeException {

    public BboltException(String message) {
        super(message);
    }

    public BboltException(String message, Throwable cause) {
        super(message, cause);
    }
}
