package com.demo.security.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class AuthException extends ResponseStatusException {

    public AuthException(String reason) {
        super(HttpStatus.UNAUTHORIZED, reason);
    }

    public AuthException(HttpStatus status, String reason) {
        super(status, reason);
    }
}
