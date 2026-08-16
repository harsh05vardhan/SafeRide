package com.saferide.safety.dto;

import java.time.Instant;

/** {@code shareToken} - the frontend builds the actual shareable URL (e.g. `/track/{shareToken}`) around it; the backend has no notion of the frontend's own routing. */
public record TripShareResponse(String shareToken, Instant expiresAt) {
}
