package com.grove.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.grove.Domain.UserSession;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

	Optional<UserSession> findByToken(String token);

	void deleteByToken(String token);

	@Modifying
	@Query("delete from UserSession s where s.userId = :userId")
	void deleteByUserId(@Param("userId") Long userId);

	@Modifying
	@Query("delete from UserSession s where s.expiresAt < :now")
	int deleteExpiredBefore(@Param("now") LocalDateTime now);
}
