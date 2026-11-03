package com.grove.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.grove.Domain.TrailChoice;

@Repository
public interface TrailChoiceRepository extends JpaRepository<TrailChoice, Long> {

	List<TrailChoice> findByGameId(Long gameId);

	List<TrailChoice> findByGameIdAndResolvedFalse(Long gameId);

	@Modifying
	@Query("delete from TrailChoice c where c.gameId = :gameId")
	void deleteByGameId(@Param("gameId") Long gameId);
}
