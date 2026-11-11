package com.grove.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.grove.Domain.VacationPayRequest;

@Repository
public interface VacationPayRequestRepository extends JpaRepository<VacationPayRequest, Long> {

	List<VacationPayRequest> findByUserIdOrderByCheckDateDesc(Long userId);

	List<VacationPayRequest> findByStatusOrderByCheckDateAsc(VacationPayRequest.Status status);

	List<VacationPayRequest> findByCheckDateAndStatusIn(LocalDate checkDate, Collection<VacationPayRequest.Status> statuses);

	boolean existsByUserIdAndCheckDateAndStatusIn(Long userId, LocalDate checkDate, Collection<VacationPayRequest.Status> statuses);

	@Modifying
	@Query("delete from VacationPayRequest r where r.userId = :userId")
	void deleteByUserId(@Param("userId") Long userId);
}
