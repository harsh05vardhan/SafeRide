package com.saferide.user;

import com.saferide.common.Auditable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * The single identity record for every person in SafeRide - customers,
 * drivers and admins alike. Which capabilities a user has is entirely
 * determined by {@link #roles}, checked server-side; there is no
 * "accountType" flag the client can influence.
 *
 * <p>Never serialized directly to JSON - see {@code user.dto} for the
 * DTOs that cross the REST boundary, so {@link #passwordHash} can never
 * leak.
 */
@Entity
@Table(
        name = "users",
        indexes = {
            @Index(name = "idx_users_email", columnList = "email"),
            @Index(name = "idx_users_phone", columnList = "phone")
        })
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "passwordHash")
@EqualsAndHashCode(of = "id", callSuper = false)
public class User extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    /** A link to a hosted photo, same "paste a URL" pattern as DriverDocument.documentUrl - no cloud file storage is wired up yet, so this is honest about what it actually is rather than pretending to be a real upload. Null shows an initial-letter avatar instead. */
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @ManyToMany(fetch = FetchType.EAGER, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    public boolean hasRole(RoleName roleName) {
        return roles.stream().anyMatch(r -> r.getName() == roleName);
    }
}
