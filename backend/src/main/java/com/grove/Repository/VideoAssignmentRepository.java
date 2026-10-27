package com.grove.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.grove.Domain.VideoAssignment;

@Repository
public interface VideoAssignmentRepository extends JpaRepository<VideoAssignment, Long> {

	List<VideoAssignment> findByVideoId(Long videoId);

	List<VideoAssignment> findByUserId(Long userId);

	boolean existsByVideoIdAndUserId(Long videoId, Long userId);

	@Modifying
	@Query("delete from VideoAssignment a where a.videoId = :videoId")
	void deleteByVideoId(@Param("videoId") Long videoId);

	@Modifying
	@Query("delete from VideoAssignment a where a.userId = :userId")
	void deleteByUserId(@Param("userId") Long userId);
}
