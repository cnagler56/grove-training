package com.grove.Repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.grove.Domain.ManagerOnDuty;

@Repository
public interface ManagerOnDutyRepository extends JpaRepository<ManagerOnDuty, LocalDate> {

	List<ManagerOnDuty> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);

	@Modifying
	@Query("delete from ManagerOnDuty m where m.userId = :userId")
	void deleteByUserId(@Param("userId") Long userId);
}
