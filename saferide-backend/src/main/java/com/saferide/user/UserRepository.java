package com.saferide.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    default Optional<User> findByEmailOrPhone(String identifier) {
        return findByEmail(identifier).or(() -> findByPhone(identifier));
    }

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);
}
