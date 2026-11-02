package com.grove.Repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.grove.Domain.UserNotification;

@Repository
public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {

	List<UserNotification> findTop50ByUserIdAndCreatedAtAfterOrderByCreatedAtDescIdDesc(Long userId, LocalDateTime after);

	long countByUserIdAndReadAtIsNullAndCreatedAtAfter(Long userId, LocalDateTime after);

	@Modifying
	@Query("update UserNotification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
	int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);

	@Modifying
	@Query("update UserNotification n set n.readAt = :now where n.userId = :userId and n.readAt is null and n.url = :url")
	int markReadFor(@Param("userId") Long userId, @Param("url") String url, @Param("now") LocalDateTime now);

	@Modifying
	@Query("delete from UserNotification n where n.createdAt < :before")
	int deleteOlderThan(@Param("before") LocalDateTime before);

	@Modifying
	@Query("delete from UserNotification n where n.userId = :userId")
	void deleteByUserId(@Param("userId") Long userId);
}
