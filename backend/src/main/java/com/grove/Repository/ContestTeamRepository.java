package com.grove.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.grove.Domain.ContestTeam;

@Repository
public interface ContestTeamRepository extends JpaRepository<ContestTeam, Long> {

	List<ContestTeam> findByContestIdOrderByPositionAsc(Long contestId);

	@Modifying
	@Query("delete from ContestTeam t where t.contestId = :contestId")
	void deleteByContestId(@Param("contestId") Long contestId);
}
