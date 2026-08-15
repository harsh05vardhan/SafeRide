package com.saferide.map;

/** Straight-line-independent result of a route lookup: what {@link MapService} always returns, regardless of provider. */
public record RouteEstimate(double distanceKm, int estimatedDurationMinutes) {
}
