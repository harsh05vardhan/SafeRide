package com.saferide.settings.dto;

/** {@code supportPhoneNumber} is null until an admin sets one - shown as "not configured yet" rather than a fabricated number. */
public record PlatformSettingsResponse(String supportPhoneNumber) {
}
