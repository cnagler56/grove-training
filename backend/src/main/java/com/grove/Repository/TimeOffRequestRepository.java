package com.grove.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.grove.Domain.TimeOffRequest;

@Repository
public interface TimeOffRequestRepository extends JpaRepository<TimeOffRequest, Long> {

	List<TimeOffRequest> findByUserIdOrderByStartDateDesc(Long userId);

	List<TimeOffRequest> findByStatusOrderByStartDateAsc(TimeOffRequest.Status status);

	/** Requests in these statuses that touch any day from {@code from} through {@code to}. */
	@Query("select r from TimeOffRequest r where r.status in :statuses and r.startDate <= :to and r.endDate >= :from")
	List<TimeOffRequest> overlapping(@Param("statuses") Collection<TimeOffRequest.Status> statuses,
		@Param("from") LocalDate from, @Param("to") LocalDate to);

	@Modifying
	@Query("delete from TimeOffRequest r where r.userId = :userId")
	void deleteByUserId(@Param("userId") Long userId);
}
