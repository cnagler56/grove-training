package com.grove.Repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.grove.Domain.TeamPostComment;

@Repository
public interface TeamPostCommentRepository extends JpaRepository<TeamPostComment, Long> {

	List<TeamPostComment> findByPostIdInOrderByCreatedAtAscIdAsc(Collection<Long> postIds);

	@Modifying
	@Query("delete from TeamPostComment c where c.postId = :postId")
	void deleteByPostId(@Param("postId") Long postId);
}
