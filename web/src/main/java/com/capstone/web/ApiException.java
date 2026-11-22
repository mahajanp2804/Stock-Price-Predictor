package com.capstone.web;

import org.springframework.http.HttpStatus;

/** An error the API reports to the client as {"error": message} with the given status. */
class ApiException extends RuntimeException {
    final HttpStatus status;

    ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    static ApiException badRequest(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
    static ApiException notFound(String message)   { return new ApiException(HttpStatus.NOT_FOUND, message); }
}
