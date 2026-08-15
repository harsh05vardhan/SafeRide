package com.saferide.ride;

import com.saferide.auth.AuthenticatedUser;
import com.saferide.common.ApiResponse;
import com.saferide.fare.dto.RideFareResponse;
import com.saferide.ride.dto.RideCreateRequest;
import com.saferide.ride.dto.RideDestinationChangeRequest;
import com.saferide.ride.dto.RideEstimateRequest;
import com.saferide.ride.dto.RideEstimateResponse;
import com.saferide.ride.dto.RideResponse;
import com.saferide.ride.dto.RideStartRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Rides")
@RestController
@RequestMapping("/api/rides")
@RequiredArgsConstructor
public class RideController {

    private final RideService rideService;

    @PostMapping("/estimate")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<RideEstimateResponse> estimate(@Valid @RequestBody RideEstimateRequest request) {
        return ApiResponse.ok(rideService.estimateRoute(request));
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RideResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody RideCreateRequest request) {
        return ApiResponse.ok(
                "Ride requested - confirm the fare to start searching for a driver",
                rideService.createRide(principal.userId(), request));
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<Page<RideResponse>> myRides(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ApiResponse.ok(rideService.listForCustomer(principal.userId(), pageable));
    }

    /** Fallback browse-and-claim for whenever automatic matching hasn't (yet) found a driver for a ride. */
    @GetMapping("/open")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<Page<RideResponse>> openRides(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ApiResponse.ok(rideService.listOpenRides(principal.userId(), pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<RideResponse> get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        return ApiResponse.ok(rideService.getRide(principal, id));
    }

    @GetMapping("/{id}/fare")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<RideFareResponse> getFare(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        return ApiResponse.ok(rideService.getRideFare(principal, id));
    }

    /**
     * The Fare Lock moment: locks the ride's current fare and, the first
     * time this succeeds, is what makes the ride searchable by drivers -
     * see {@link RideService}'s class-level "Fare Lock" note.
     */
    @PostMapping("/{id}/fare/confirm")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<RideFareResponse> confirmFare(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        return ApiResponse.ok("Fare locked", rideService.confirmFare(principal.userId(), id));
    }

    @PutMapping("/{id}/destination")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<RideResponse> changeDestination(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @Valid @RequestBody RideDestinationChangeRequest request) {
        return ApiResponse.ok(
                "Destination updated - please confirm the new fare",
                rideService.changeDestination(principal.userId(), id, request));
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<RideResponse> accept(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        return ApiResponse.ok("Ride accepted", rideService.acceptRide(principal.userId(), id));
    }

    /** Turns down a ride matching proactively assigned - excludes this driver from it and triggers immediate reassignment. */
    @PostMapping("/{id}/decline")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<RideResponse> decline(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        return ApiResponse.ok("Ride declined", rideService.declineRide(principal.userId(), id));
    }

    @PostMapping("/{id}/arrive")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<RideResponse> arrive(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        return ApiResponse.ok("Marked as arrived", rideService.arriveRide(principal.userId(), id));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<RideResponse> start(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id,
            @Valid @RequestBody RideStartRequest request) {
        return ApiResponse.ok("Ride started", rideService.startRide(principal.userId(), id, request.otp()));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<RideResponse> complete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        return ApiResponse.ok("Ride completed", rideService.completeRide(principal.userId(), id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<RideResponse> cancel(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        return ApiResponse.ok("Ride cancelled", rideService.cancelRide(principal, id));
    }
}
