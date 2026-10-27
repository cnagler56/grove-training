package com.grove.Repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.grove.Domain.WeeklyStat;

@Repository
public interface WeeklyStatRepository extends JpaRepository<WeeklyStat, Long> {

	List<WeeklyStat> findByWeekStart(LocalDate weekStart);

	List<WeeklyStat> findBySuNameIgnoreCaseOrderByWeekStartDesc(String suName);

	/** Every week that has numbers, newest first. */
	@Query("select distinct s.weekStart from WeeklyStat s order by s.weekStart desc")
	List<LocalDate> weeks();

	/** Every name Salon Ultimate has reported, A–Z. */
	@Query("select distinct s.suName from WeeklyStat s order by s.suName")
	List<String> names();

	@Modifying
	@Transactional
	@Query("delete from WeeklyStat s where s.weekStart = :week")
	void deleteWeek(@Param("week") LocalDate week);
}
