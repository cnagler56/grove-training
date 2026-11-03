package com.grove.Repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.grove.Domain.TrailGame;

@Repository
public interface TrailGameRepository extends JpaRepository<TrailGame, Long> {

	/** Games that have started (or start today) and aren't over. */
	List<TrailGame> findByFinishedAtIsNullAndStartDateLessThanEqual(LocalDate today);
}
