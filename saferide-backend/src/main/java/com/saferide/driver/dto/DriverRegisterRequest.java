package com.saferide.driver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * {@code POST /api/drivers/register} - lets an already-authenticated user
 * (e.g. an existing customer) add the DRIVER role and create their driver
 * profile without creating a second account. Drivers who chose "Driver" at
 * signup already get this profile created by {@code AuthService.register}
 * and don't need to call this separately.
 */
public record DriverRegisterRequest(
        @NotBlank(message = "License number is required")
        @Pattern(regexp = "^[A-Za-z0-9-]{4,30}$", message = "License number looks invalid")
        String licenseNumber) {
}
