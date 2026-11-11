package com.grove.Repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.grove.Domain.PayrollNote;

@Repository
public interface PayrollNoteRepository extends JpaRepository<PayrollNote, Long> {

	List<PayrollNote> findByCheckDateOrderByCreatedAtAsc(LocalDate checkDate);

	@Modifying
	@Query("delete from PayrollNote n where n.userId = :userId")
	void deleteByUserId(@Param("userId") Long userId);
}
