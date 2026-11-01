package com.grove.Repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.grove.Domain.TeamPostImage;

@Repository
public interface TeamPostImageRepository extends JpaRepository<TeamPostImage, Long> {

	/** An image's id and post, without loading the photo itself. */
	interface ImageRef {
		Long getId();
		Long getPostId();
	}

	@Query("select i.id as id, i.postId as postId from TeamPostImage i where i.postId in :postIds order by i.id")
	List<ImageRef> refs(@Param("postIds") Collection<Long> postIds);

	long countByPostId(Long postId);

	@Modifying
	@Query("delete from TeamPostImage i where i.postId = :postId")
	void deleteByPostId(@Param("postId") Long postId);
}
