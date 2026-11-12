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

	/** Each name Salon Ultimate has reported, with the start of the latest week they were in. */
	@Query("select s.suName as name, max(s.weekStart) as lastWeek from WeeklyStat s group by s.suName")
	List<LastSeen> lastSeen();

	interface LastSeen {
		String getName();
		LocalDate getLastWeek();
	}

	/** Every name Salon Ultimate has reported, A–Z. */
	@Query("select distinct s.suName from WeeklyStat s order by s.suName")
	List<String> names();

	/** The whole team's services by type for one week. */
	interface TeamServices {
		LocalDate getWeekStart();
		Long getColors();
		Long getTreatments();
		Long getWaxes();
	}

	/**
	 * Team totals for every week, newest first, leaving out anyone linked to a
	 * receptionist. Totals are null for weeks pulled before these were saved.
	 */
	@Query("select s.weekStart as weekStart, sum(s.colors) as colors, sum(s.treatments) as treatments, sum(s.waxes) as waxes"
		+ " from WeeklyStat s where not exists (select 1 from User u where lower(u.salonUltimateName) = lower(s.suName)"
		+ " and u.roles = com.grove.Domain.Role.RECEPTIONIST)"
		+ " group by s.weekStart order by s.weekStart desc")
	List<TeamServices> teamServices();

	@Modifying
	@Transactional
	@Query("delete from WeeklyStat s where s.weekStart = :week")
	void deleteWeek(@Param("week") LocalDate week);
}
