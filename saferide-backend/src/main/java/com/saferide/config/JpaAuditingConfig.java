package com.saferide.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Turns on {@code @CreatedDate}/{@code @LastModifiedDate} support for {@link com.saferide.common.Auditable}. */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
