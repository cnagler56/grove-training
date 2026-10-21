package com.grove.Repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.grove.Domain.Shift;

@Repository
public interface ShiftRepository extends JpaRepository<Shift, Long> {

	List<Shift> findByDateBetweenOrderByDateAscStartAsc(LocalDate from, LocalDate to);

	List<Shift> findByUserIdAndDateBetweenOrderByDateAsc(Long userId, LocalDate from, LocalDate to);

	@Modifying
	@Query("delete from Shift s where s.date between :from and :to")
	void deleteByDateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

	@Modifying
	@Query("delete from Shift s where s.userId = :userId")
	void deleteByUserId(@Param("userId") Long userId);
}
