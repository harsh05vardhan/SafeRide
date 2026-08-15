package com.saferide.vehicle;

import com.saferide.auth.AuthenticatedUser;
import com.saferide.common.ApiResponse;
import com.saferide.vehicle.dto.VehicleRegisterRequest;
import com.saferide.vehicle.dto.VehicleResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Driver self-service vehicle management. Every route resolves the driver from the JWT principal, never a path parameter. */
@Tag(name = "Vehicles")
@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('DRIVER')")
public class VehicleController {

    private final VehicleService vehicleService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VehicleResponse> register(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody VehicleRegisterRequest request) {
        return ApiResponse.ok("Vehicle added - pending verification", vehicleService.registerVehicle(principal.userId(), request));
    }

    @GetMapping
    public ApiResponse<List<VehicleResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.ok(vehicleService.listForDriver(principal.userId()));
    }

    @PutMapping("/{id}/activate")
    public ApiResponse<VehicleResponse> activate(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long id) {
        return ApiResponse.ok("Active vehicle switched", vehicleService.activate(principal.userId(), id));
    }
}
