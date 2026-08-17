package com.saferide.settings;

import com.saferide.common.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single always-id=1 row of platform-wide, admin-editable settings -
 * currently just the support contact number, shown to drivers (and to a
 * blocked account trying to log back in) so there's always a real way to
 * reach the platform. Deliberately one fixed row rather than a generic
 * key-value table: there is exactly one setting so far, and a key-value
 * table would trade type safety for extensibility this app doesn't need
 * yet - see {@link PlatformSettingsService} for how the singleton row is
 * created on first use.
 */
@Entity
@Table(name = "platform_settings")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id", callSuper = false)
public class PlatformSettings extends Auditable {

    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(name = "support_phone_number", length = 20)
    private String supportPhoneNumber;
}
