package com.chubb.claims.api;

import java.time.Instant;

/** Consistent error body for both MVC exceptions and the security filter chain. */
public record ApiError(Instant timestamp, int status, String error, String message) {

    public static ApiError of(int status, String error, String message) {
        return new ApiError(Instant.now(), status, error, message);
    }
}
