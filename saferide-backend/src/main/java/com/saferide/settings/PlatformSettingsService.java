package com.saferide.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads/writes the one {@link PlatformSettings} row, creating it on first
 * use rather than requiring a migration to seed it - a fresh environment
 * (or the very first admin) shouldn't need to know this row has to exist
 * before the support-contact endpoints work.
 */
@Service
@RequiredArgsConstructor
public class PlatformSettingsService {

    private final PlatformSettingsRepository repository;

    @Transactional(readOnly = true)
    public String getSupportPhoneNumber() {
        return repository.findById(PlatformSettings.SINGLETON_ID)
                .map(PlatformSettings::getSupportPhoneNumber)
                .orElse(null);
    }

    @Transactional
    public String updateSupportPhoneNumber(String supportPhoneNumber) {
        PlatformSettings settings = repository.findById(PlatformSettings.SINGLETON_ID)
                .orElseGet(() -> {
                    PlatformSettings created = new PlatformSettings();
                    created.setId(PlatformSettings.SINGLETON_ID);
                    return created;
                });
        // Treat a blank string the same as "clear it" - null, not an empty string, is what
        // getSupportPhoneNumber()'s callers (AuthService, driver-facing display) check for.
        settings.setSupportPhoneNumber(supportPhoneNumber == null || supportPhoneNumber.isBlank() ? null : supportPhoneNumber);
        return repository.save(settings).getSupportPhoneNumber();
    }
}
