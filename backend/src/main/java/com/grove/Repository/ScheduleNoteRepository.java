package com.grove.Repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.grove.Domain.ScheduleNote;

@Repository
public interface ScheduleNoteRepository extends JpaRepository<ScheduleNote, Long> {

	List<ScheduleNote> findByWeekStart(LocalDate weekStart);

	@Modifying
	@Query("delete from ScheduleNote n where n.weekStart = :week")
	void deleteWeek(@Param("week") LocalDate week);

	@Modifying
	@Query("delete from ScheduleNote n where n.userId = :userId")
	void deleteByUserId(@Param("userId") Long userId);
}
