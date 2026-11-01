package com.grove.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.grove.Domain.TeamPost;

@Repository
public interface TeamPostRepository extends JpaRepository<TeamPost, Long> {

	/** Pinned posts first, then newest. */
	@Query("select p from TeamPost p order by case when p.pinned = true then 0 else 1 end, p.createdAt desc, p.id desc")
	Page<TeamPost> feed(Pageable page);
}
