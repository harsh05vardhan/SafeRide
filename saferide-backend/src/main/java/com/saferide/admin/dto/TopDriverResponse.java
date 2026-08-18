package com.saferide.admin.dto;

/** One row of the admin dashboard's "top rated drivers" leaderboard - verified drivers only, ranked by real rating (see DriverRepository#findTop5ByVerificationStatusOrderByRatingDesc). */
public record TopDriverResponse(Long driverId, String name, String avatarUrl, Double rating, Long completedRideCount) {
}
