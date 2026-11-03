package com.grove.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.grove.Domain.TrailPlayer;

@Repository
public interface TrailPlayerRepository extends JpaRepository<TrailPlayer, Long> {

	List<TrailPlayer> findByGameId(Long gameId);

	List<TrailPlayer> findByPartyId(Long partyId);

	Optional<TrailPlayer> findByGameIdAndUserId(Long gameId, Long userId);

	@Modifying
	@Query("delete from TrailPlayer p where p.gameId = :gameId")
	void deleteByGameId(@Param("gameId") Long gameId);
}
