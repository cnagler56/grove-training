package com.grove.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.grove.Domain.TrailLog;

@Repository
public interface TrailLogRepository extends JpaRepository<TrailLog, Long> {

	List<TrailLog> findByGameIdOrderByIdDesc(Long gameId);

	@Modifying
	@Query("delete from TrailLog l where l.gameId = :gameId")
	void deleteByGameId(@Param("gameId") Long gameId);
}
