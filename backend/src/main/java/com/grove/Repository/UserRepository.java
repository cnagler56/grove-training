package com.grove.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.grove.Domain.User;

/**
 * Passwords are BCrypt-hashed, so there is no findByEmailAndPassword — always
 * fetch by identifier, then verify with {@code PasswordEncoder.matches(raw, stored)}.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);
}
