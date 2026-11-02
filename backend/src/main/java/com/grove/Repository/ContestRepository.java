package com.grove.Repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.grove.Domain.Contest;

@Repository
public interface ContestRepository extends JpaRepository<Contest, Long> {

	/** Contests that have started and haven't been wrapped up. */
	List<Contest> findByFinishedAtIsNullAndStartDateLessThanEqual(LocalDate today);
}
