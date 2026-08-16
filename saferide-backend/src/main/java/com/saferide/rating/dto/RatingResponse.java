package com.saferide.rating.dto;

import java.time.Instant;

public record RatingResponse(Long rideId, int score, String comment, Instant createdAt) {
}
