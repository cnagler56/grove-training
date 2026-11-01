package com.grove.Repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.grove.Domain.TeamPostLike;

@Repository
public interface TeamPostLikeRepository extends JpaRepository<TeamPostLike, Long> {

	List<TeamPostLike> findByPostIdIn(Collection<Long> postIds);

	boolean existsByPostIdAndUserId(Long postId, Long userId);

	@Modifying
	@Query("delete from TeamPostLike l where l.postId = :postId and l.userId = :userId")
	void unlike(@Param("postId") Long postId, @Param("userId") Long userId);

	@Modifying
	@Query("delete from TeamPostLike l where l.postId = :postId")
	void deleteByPostId(@Param("postId") Long postId);

	@Modifying
	@Query("delete from TeamPostLike l where l.userId = :userId")
	void deleteByUserId(@Param("userId") Long userId);
}
