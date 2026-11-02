package com.grove.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.grove.Domain.ContestEntry;

@Repository
public interface ContestEntryRepository extends JpaRepository<ContestEntry, Long> {

	List<ContestEntry> findByContestId(Long contestId);

	@Modifying
	@Query("delete from ContestEntry e where e.contestId = :contestId")
	void deleteByContestId(@Param("contestId") Long contestId);

	@Modifying
	@Query("delete from ContestEntry e where e.userId = :userId")
	void deleteByUserId(@Param("userId") Long userId);
}
