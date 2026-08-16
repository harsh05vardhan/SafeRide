package com.saferide.user;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    default Optional<User> findByEmailOrPhone(String identifier) {
        return findByEmail(identifier).or(() -> findByPhone(identifier));
    }

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    /** {@code distinct} because a user with more than one role (e.g. CUSTOMER + DRIVER) would otherwise produce one row per matching role when the join fans out. */
    @Query("select distinct u from User u left join u.roles r where (:role is null or r.name = :role) order by u.createdAt desc")
    Page<User> findAllByOptionalRole(@Param("role") RoleName role, Pageable pageable);
}
