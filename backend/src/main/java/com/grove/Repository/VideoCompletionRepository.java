package com.grove.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.grove.Domain.VideoCompletion;

@Repository
public interface VideoCompletionRepository extends JpaRepository<VideoCompletion, Long> {

	List<VideoCompletion> findByUserId(Long userId);

	List<VideoCompletion> findByVideoId(Long videoId);

	Optional<VideoCompletion> findByUserIdAndVideoId(Long userId, Long videoId);

	long countByUserId(Long userId);

	@Modifying
	@Query("delete from VideoCompletion c where c.videoId = :videoId")
	void deleteByVideoId(@Param("videoId") Long videoId);

	@Modifying
	@Query("delete from VideoCompletion c where c.userId = :userId")
	void deleteByUserId(@Param("userId") Long userId);
}
