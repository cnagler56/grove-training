package com.grove.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.grove.Domain.TrailParty;

@Repository
public interface TrailPartyRepository extends JpaRepository<TrailParty, Long> {

	List<TrailParty> findByGameId(Long gameId);

	@Modifying
	@Query("delete from TrailParty p where p.gameId = :gameId")
	void deleteByGameId(@Param("gameId") Long gameId);
}
